from __future__ import annotations

import json
import logging
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

import jwt
from sqlalchemy.orm import Session

from models import NotificationEvent, PushToken
from notification_events import deserialize_payload


logger = logging.getLogger(__name__)

FIREBASE_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
FCM_SEND_URL_TEMPLATE = "https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass(slots=True)
class PushDispatchResult:
    delivery_channel: str
    delivery_status: str
    attempt_count: int = 0
    delivered_at: Optional[datetime] = None
    failure_reason: Optional[str] = None


def _truncate(value: str, limit: int = 500) -> str:
    if len(value) <= limit:
        return value
    return value[: limit - 3] + "..."


def _read_service_account_json() -> tuple[Optional[dict[str, Any]], Optional[str]]:
    raw_json = os.getenv("FCM_SERVICE_ACCOUNT_JSON")
    if raw_json:
        try:
            return json.loads(raw_json), None
        except json.JSONDecodeError as exc:
            return None, f"Invalid FCM_SERVICE_ACCOUNT_JSON: {exc.msg}"

    file_path = os.getenv("FCM_SERVICE_ACCOUNT_FILE") or os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    if not file_path:
        return None, "FCM credentials are not configured"

    path = Path(file_path).expanduser()
    if not path.exists():
        return None, f"FCM service account file not found: {path}"

    try:
        return json.loads(path.read_text(encoding="utf-8")), None
    except (OSError, json.JSONDecodeError) as exc:
        return None, f"Failed to load FCM credentials: {exc}"


def _resolve_project_id(service_account: dict[str, Any]) -> Optional[str]:
    project_id = os.getenv("FCM_PROJECT_ID")
    if project_id:
        return project_id
    return service_account.get("project_id")


def _get_access_token(service_account: dict[str, Any]) -> tuple[Optional[str], Optional[str]]:
    client_email = service_account.get("client_email")
    private_key = service_account.get("private_key")
    private_key_id = service_account.get("private_key_id")
    if not client_email or not private_key:
        return None, "FCM service account is missing client_email or private_key"

    issued_at = int(time.time())
    try:
        assertion = jwt.encode(
            {
                "iss": client_email,
                "scope": FIREBASE_SCOPE,
                "aud": OAUTH_TOKEN_URL,
                "iat": issued_at,
                "exp": issued_at + 3600,
            },
            private_key,
            algorithm="RS256",
            headers={"kid": private_key_id} if private_key_id else None,
        )
    except Exception as exc:  # pragma: no cover - depends on local credential material
        return None, f"Failed to sign FCM access token request: {exc}"
    if isinstance(assertion, bytes):
        assertion = assertion.decode("utf-8")

    body = urllib.parse.urlencode(
        {
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": assertion,
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        OAUTH_TOKEN_URL,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        return None, f"Failed to obtain FCM access token: {raw or exc.reason}"
    except urllib.error.URLError as exc:
        return None, f"Failed to obtain FCM access token: {exc.reason}"
    except (OSError, json.JSONDecodeError) as exc:
        return None, f"Failed to obtain FCM access token: {exc}"

    token = payload.get("access_token")
    if not token:
        return None, "FCM access token response missing access_token"
    return token, None


def _send_fcm_message(
    *,
    access_token: str,
    project_id: str,
    push_token: str,
    title: str,
    body: str,
    data: dict[str, Any],
) -> tuple[Optional[str], Optional[str]]:
    message = {
        "message": {
            "token": push_token,
            "notification": {
                "title": title,
                "body": body,
            },
            "data": {key: json.dumps(value, ensure_ascii=False, default=str) if not isinstance(value, str) else value for key, value in data.items()},
        }
    }

    request = urllib.request.Request(
        FCM_SEND_URL_TEMPLATE.format(project_id=project_id),
        data=json.dumps(message, ensure_ascii=False, default=str).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; charset=utf-8",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        return None, f"FCM send failed: {raw or exc.reason}"
    except urllib.error.URLError as exc:
        return None, f"FCM send failed: {exc.reason}"
    except (OSError, json.JSONDecodeError) as exc:
        return None, f"FCM send failed: {exc}"

    return str(payload.get("name") or ""), None


def _active_push_tokens(db: Session, user_id: int) -> list[PushToken]:
    return (
        db.query(PushToken)
        .filter(PushToken.user_id == user_id, PushToken.enabled.is_(True))
        .order_by(PushToken.updated_at.desc(), PushToken.id.desc())
        .all()
    )


def _apply_delivery_result(event: NotificationEvent, result: PushDispatchResult) -> NotificationEvent:
    event.delivery_channel = result.delivery_channel
    event.delivery_status = result.delivery_status
    event.status = result.delivery_status
    event.attempt_count = result.attempt_count
    event.delivered_at = result.delivered_at
    event.failure_reason = result.failure_reason
    return event


def deliver_notification_event(
    db: Session,
    event: NotificationEvent,
    *,
    recipient_user_id: Optional[int] = None,
) -> PushDispatchResult:
    target_user_id = recipient_user_id or event.user_id
    tokens = _active_push_tokens(db, target_user_id)
    if not tokens:
        result = PushDispatchResult(
            delivery_channel="fcm",
            delivery_status="skipped",
            attempt_count=0,
            failure_reason="No active push tokens registered",
        )
        return _apply_delivery_result(event, result)

    service_account, credential_error = _read_service_account_json()
    if credential_error:
        result = PushDispatchResult(
            delivery_channel="fcm",
            delivery_status="skipped",
            attempt_count=0,
            failure_reason=credential_error,
        )
        return _apply_delivery_result(event, result)

    project_id = _resolve_project_id(service_account or {})
    if not project_id:
        result = PushDispatchResult(
            delivery_channel="fcm",
            delivery_status="skipped",
            attempt_count=0,
            failure_reason="FCM_PROJECT_ID is not configured",
        )
        return _apply_delivery_result(event, result)

    access_token, token_error = _get_access_token(service_account or {})
    if token_error or not access_token:
        result = PushDispatchResult(
            delivery_channel="fcm",
            delivery_status="failed",
            attempt_count=0,
            failure_reason=token_error or "Failed to resolve FCM access token",
        )
        return _apply_delivery_result(event, result)

    payload = deserialize_payload(event.payload_json)
    attempt_count = 0
    failures: list[str] = []
    first_message_name: Optional[str] = None
    for push_token in tokens:
        attempt_count += 1
        message_name, error = _send_fcm_message(
            access_token=access_token,
            project_id=project_id,
            push_token=push_token.token,
            title=event.title,
            body=event.body,
            data={
                "event_id": event.id,
                "event_type": event.event_type,
                "user_id": event.user_id,
                "trip_id": event.trip_id,
                "guardian_id": event.guardian_id,
                "payload": payload,
            },
        )
        if error:
            failures.append(f"token_id={push_token.id}: {error}")
            continue
        if first_message_name is None:
            first_message_name = message_name

    if attempt_count == 0:
        result = PushDispatchResult(
            delivery_channel="fcm",
            delivery_status="skipped",
            attempt_count=0,
            failure_reason="No active push tokens registered",
        )
        return _apply_delivery_result(event, result)

    if failures and len(failures) == attempt_count:
        result = PushDispatchResult(
            delivery_channel="fcm",
            delivery_status="failed",
            attempt_count=attempt_count,
            failure_reason=_truncate("; ".join(failures)),
        )
        return _apply_delivery_result(event, result)

    failure_reason = _truncate("; ".join(failures)) if failures else None
    result = PushDispatchResult(
        delivery_channel="fcm",
        delivery_status="sent",
        attempt_count=attempt_count,
        delivered_at=utc_now(),
        failure_reason=failure_reason,
    )
    if first_message_name:
        logger.info(
            "fcm notification sent",
            extra={
                "event_id": event.id,
                "event_type": event.event_type,
                "message_name": first_message_name,
                "attempt_count": attempt_count,
                "failure_reason": failure_reason,
            },
        )
    return _apply_delivery_result(event, result)


def mark_notification_opened(event: NotificationEvent) -> NotificationEvent:
    opened_at = utc_now()
    event.opened_at = opened_at
    event.delivery_status = "opened"
    event.status = "opened"
    if event.delivered_at is None:
        event.delivered_at = opened_at
    return event


def register_push_token(
    db: Session,
    *,
    user_id: int,
    token: str,
    platform: str = "android",
    device_name: Optional[str] = None,
    app_version: Optional[str] = None,
) -> PushToken:
    push_token = db.query(PushToken).filter(PushToken.token == token).first()
    if push_token is None:
        push_token = PushToken(token=token, user_id=user_id)
    push_token.user_id = user_id
    push_token.token = token
    push_token.platform = platform or "android"
    push_token.device_name = device_name
    push_token.app_version = app_version
    push_token.enabled = True
    push_token.last_seen_at = utc_now()
    push_token.revoked_at = None
    db.add(push_token)
    db.flush()
    return push_token


def deregister_push_token(db: Session, *, user_id: int, token: str) -> PushToken | None:
    push_token = (
        db.query(PushToken)
        .filter(PushToken.user_id == user_id, PushToken.token == token)
        .first()
    )
    if push_token is None:
        return None
    push_token.enabled = False
    push_token.revoked_at = utc_now()
    push_token.last_seen_at = push_token.last_seen_at or utc_now()
    db.add(push_token)
    db.flush()
    return push_token
