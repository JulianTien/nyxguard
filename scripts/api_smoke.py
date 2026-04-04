#!/usr/bin/env python3
"""NyxGuard FastAPI smoke test.

Uses only the Python standard library so it can run in minimal environments.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone


DEFAULT_LOCAL_BASE_URL = "http://127.0.0.1:5001"


class SmokeFailure(RuntimeError):
    """Raised when a smoke step fails."""


@dataclass
class StepResult:
    name: str
    passed: bool
    detail: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run NyxGuard FastAPI smoke checks.")
    parser.add_argument("--profile", choices=("local", "staging", "prod"), required=True)
    parser.add_argument("--base-url", dest="base_url")
    parser.add_argument("--password", default="abc123")
    parser.add_argument("--timeout", type=float, default=10.0)
    return parser.parse_args()


def resolve_base_url(profile: str, cli_value: str | None) -> str:
    if cli_value:
        return cli_value.rstrip("/")

    explicit = os.getenv("NYXGUARD_API_BASE_URL")
    if explicit:
        return explicit.rstrip("/")

    env_specific = {
        "local": os.getenv("NYXGUARD_LOCAL_API_BASE_URL"),
        "staging": os.getenv("NYXGUARD_STAGING_API_BASE_URL"),
        "prod": os.getenv("NYXGUARD_PROD_API_BASE_URL"),
    }[profile]
    if env_specific:
        return env_specific.rstrip("/")

    if profile == "local":
        return DEFAULT_LOCAL_BASE_URL

    raise SmokeFailure(
        f"Missing base URL for profile={profile}. Use --base-url or NYXGUARD_{profile.upper()}_API_BASE_URL."
    )


def http_request(
    method: str,
    base_url: str,
    path: str,
    timeout: float,
    *,
    token: str | None = None,
    payload: dict | list | None = None,
) -> tuple[int, dict | list | str | None]:
    body = None
    headers = {"Accept": "application/json"}

    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(
        url=f"{base_url}{path}",
        data=body,
        headers=headers,
        method=method,
    )

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8").strip()
            return response.getcode(), parse_body(raw)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8").strip()
        return exc.code, parse_body(raw)
    except urllib.error.URLError as exc:
        raise SmokeFailure(f"Network error calling {method} {path}: {exc}") from exc


def parse_body(raw: str) -> dict | list | str | None:
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def expect_status(actual: int, expected: int, detail: str) -> None:
    if actual != expected:
        raise SmokeFailure(f"{detail} (expected HTTP {expected}, got {actual})")


def expect_json_dict(data: object, detail: str) -> dict:
    if not isinstance(data, dict):
        raise SmokeFailure(f"{detail} (expected JSON object, got {type(data).__name__})")
    return data


def run_step(results: list[StepResult], name: str, fn) -> object:
    try:
        data = fn()
        results.append(StepResult(name=name, passed=True, detail="PASS"))
        print(f"[PASS] {name}")
        return data
    except SmokeFailure as exc:
        results.append(StepResult(name=name, passed=False, detail=str(exc)))
        print(f"[FAIL] {name}: {exc}")
        raise


def main() -> int:
    args = parse_args()
    results: list[StepResult] = []

    try:
        base_url = resolve_base_url(args.profile, args.base_url)
        print(f"NyxGuard smoke profile={args.profile} base_url={base_url}")

        unique = str(int(time.time() * 1000))
        nickname = f"Smoke{unique[-6:]}"
        phone = f"139{unique[-8:]}"
        guardian_phone = f"138{unique[-8:]}"
        trip_id: int | None = None

        health = run_step(
            results,
            "health",
            lambda: expect_health(base_url, args.timeout),
        )

        auth = run_step(
            results,
            "register",
            lambda: register_user(base_url, args.timeout, nickname, phone, args.password),
        )
        token = auth["token"]

        run_step(
            results,
            "login",
            lambda: login_user(base_url, args.timeout, phone, args.password),
        )

        run_step(
            results,
            "get profile",
            lambda: get_profile(base_url, args.timeout, token, nickname, phone),
        )

        updated_profile = run_step(
            results,
            "update profile",
            lambda: update_profile(base_url, args.timeout, token, nickname),
        )

        guardian = run_step(
            results,
            "add guardian",
            lambda: add_guardian(base_url, args.timeout, token, guardian_phone),
        )

        run_step(
            results,
            "list guardians",
            lambda: list_guardians(base_url, args.timeout, token, guardian["id"]),
        )

        trip = run_step(
            results,
            "create trip",
            lambda: create_trip(base_url, args.timeout, token, guardian["id"]),
        )
        trip_id = trip["id"]

        run_step(
            results,
            "get trip",
            lambda: get_trip(base_url, args.timeout, token, trip_id, guardian["id"]),
        )

        run_step(
            results,
            "upload locations",
            lambda: upload_locations(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "chat",
            lambda: chat(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "v2 dashboard",
            lambda: get_v2_dashboard(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "v2 current trip",
            lambda: get_v2_current_trip(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "v2 chat history",
            lambda: get_v2_chat_history(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "v2 chat message",
            lambda: create_v2_chat_message(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "proactive chat",
            lambda: proactive_chat(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "v2 proactive chat",
            lambda: create_v2_proactive_chat(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "v2 profile summary",
            lambda: get_v2_profile_summary(base_url, args.timeout, token),
        )

        run_step(
            results,
            "trigger sos",
            lambda: trigger_sos(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "v2 sos",
            lambda: trigger_v2_sos(base_url, args.timeout, token),
        )

        run_step(
            results,
            "finish trip",
            lambda: finish_trip(base_url, args.timeout, token, trip_id),
        )

        run_step(
            results,
            "notification events",
            lambda: list_notification_events(
                base_url,
                args.timeout,
                token,
                trip_id,
                expected_events={"trip_started", "sos_triggered", "trip_finished"},
            ),
        )

        run_step(
            results,
            "401 unauthorized",
            lambda: expect_unauthorized_profile(base_url, args.timeout),
        )

        run_step(
            results,
            "404 missing trip",
            lambda: expect_missing_trip(base_url, args.timeout, token),
        )

        run_step(
            results,
            "400 bad request",
            lambda: expect_bad_trip_request(base_url, args.timeout, token),
        )

        print_summary(
            results,
            extra=(
                f"health={health.get('status', 'unknown')}, "
                f"emergency_phone={updated_profile.get('emergency_phone', '--')}"
            ),
        )
        return 0
    except SmokeFailure:
        print_summary(results)
        return 1


def expect_health(base_url: str, timeout: float) -> dict:
    status_code, data = http_request("GET", base_url, "/", timeout)
    expect_status(status_code, 200, "Health check failed")
    payload = expect_json_dict(data, "Health check returned invalid JSON")
    if payload.get("status") != "ok":
        raise SmokeFailure(f"Health status mismatch: {payload}")
    return payload


def register_user(base_url: str, timeout: float, nickname: str, phone: str, password: str) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/auth/register",
        timeout,
        payload={
            "nickname": nickname,
            "phone": phone,
            "password": password,
        },
    )
    expect_status(status_code, 200, "Register failed")
    payload = expect_json_dict(data, "Register returned invalid JSON")
    if "token" not in payload or "user" not in payload:
        raise SmokeFailure(f"Register response missing fields: {payload}")
    return payload


def login_user(base_url: str, timeout: float, account: str, password: str) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/auth/login",
        timeout,
        payload={"account": account, "password": password},
    )
    expect_status(status_code, 200, "Login failed")
    return expect_json_dict(data, "Login returned invalid JSON")


def get_profile(base_url: str, timeout: float, token: str, nickname: str, phone: str) -> dict:
    status_code, data = http_request("GET", base_url, "/api/user/profile", timeout, token=token)
    expect_status(status_code, 200, "Get profile failed")
    payload = expect_json_dict(data, "Profile returned invalid JSON")
    if payload.get("nickname") != nickname or payload.get("phone") != phone:
        raise SmokeFailure(f"Profile content mismatch: {payload}")
    return payload


def update_profile(base_url: str, timeout: float, token: str, nickname: str) -> dict:
    emergency_phone = "13700009999"
    status_code, data = http_request(
        "PUT",
        base_url,
        "/api/user/profile",
        timeout,
        token=token,
        payload={
            "nickname": f"{nickname} Updated",
            "emergency_phone": emergency_phone,
        },
    )
    expect_status(status_code, 200, "Update profile failed")
    payload = expect_json_dict(data, "Update profile returned invalid JSON")
    if payload.get("emergency_phone") != emergency_phone:
        raise SmokeFailure(f"Profile emergency phone mismatch: {payload}")
    return payload


def add_guardian(base_url: str, timeout: float, token: str, guardian_phone: str) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/guardians",
        timeout,
        token=token,
        payload={
            "nickname": "Smoke Guardian",
            "phone": guardian_phone,
            "relationship": "朋友",
        },
    )
    expect_status(status_code, 200, "Add guardian failed")
    return expect_json_dict(data, "Add guardian returned invalid JSON")


def list_guardians(base_url: str, timeout: float, token: str, guardian_id: int) -> list:
    status_code, data = http_request("GET", base_url, "/api/guardians", timeout, token=token)
    expect_status(status_code, 200, "List guardians failed")
    if not isinstance(data, list):
        raise SmokeFailure(f"Guardians returned invalid JSON: {data}")
    if not any(isinstance(item, dict) and item.get("id") == guardian_id for item in data):
        raise SmokeFailure(f"Guardian id={guardian_id} not found in list")
    return data


def create_trip(base_url: str, timeout: float, token: str, guardian_id: int) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/trips",
        timeout,
        token=token,
        payload={
            "trip_type": "walk",
            "start_lat": 31.2304,
            "start_lng": 121.4737,
            "start_name": "People's Square",
            "end_lat": 31.2243,
            "end_lng": 121.4768,
            "end_name": "Xintiandi",
            "estimated_minutes": 18,
            "guardian_ids": [guardian_id],
        },
    )
    expect_status(status_code, 200, "Create trip failed")
    payload = expect_json_dict(data, "Create trip returned invalid JSON")
    if "id" not in payload:
        raise SmokeFailure(f"Create trip response missing id: {payload}")
    return payload


def get_trip(base_url: str, timeout: float, token: str, trip_id: int, guardian_id: int) -> dict:
    status_code, data = http_request("GET", base_url, f"/api/trips/{trip_id}", timeout, token=token)
    expect_status(status_code, 200, "Get trip failed")
    payload = expect_json_dict(data, "Get trip returned invalid JSON")
    if payload.get("id") != trip_id:
        raise SmokeFailure(f"Trip response mismatch: {payload}")
    guardians = payload.get("guardians")
    if not isinstance(guardians, list) or not any(
        isinstance(item, dict) and item.get("id") == guardian_id for item in guardians
    ):
        raise SmokeFailure(f"Trip guardian linkage missing: {payload}")
    return payload


def upload_locations(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    now = datetime.now(timezone.utc).isoformat()
    status_code, data = http_request(
        "POST",
        base_url,
        f"/api/trips/{trip_id}/locations",
        timeout,
        token=token,
        payload={
            "locations": [
                {"lat": 31.2298, "lng": 121.4742, "accuracy": 6.5, "recorded_at": now},
                {"lat": 31.2289, "lng": 121.4751, "accuracy": 5.9, "recorded_at": now},
            ]
        },
    )
    expect_status(status_code, 200, "Upload locations failed")
    payload = expect_json_dict(data, "Upload locations returned invalid JSON")
    if payload.get("uploaded") != 2:
        raise SmokeFailure(f"Unexpected upload count: {payload}")
    return payload


def chat(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/chat",
        timeout,
        token=token,
        payload={"content": "I feel a little unsafe right now.", "trip_id": trip_id},
    )
    expect_status(status_code, 200, "Chat failed")
    payload = expect_json_dict(data, "Chat returned invalid JSON")
    if not payload.get("reply"):
        raise SmokeFailure(f"Chat reply missing: {payload}")
    return payload


def proactive_chat(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/chat/proactive",
        timeout,
        token=token,
        payload={"trigger": "deviation", "trip_id": trip_id},
    )
    expect_status(status_code, 200, "Proactive chat failed")
    payload = expect_json_dict(data, "Proactive chat returned invalid JSON")
    if not payload.get("reply"):
        raise SmokeFailure(f"Proactive chat reply missing: {payload}")
    return payload


def get_v2_dashboard(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request("GET", base_url, "/api/v2/dashboard", timeout, token=token)
    expect_status(status_code, 200, "V2 dashboard failed")
    payload = expect_json_dict(data, "V2 dashboard returned invalid JSON")
    brief = payload.get("active_trip_brief") or {}
    if brief.get("id") != trip_id:
        raise SmokeFailure(f"V2 dashboard active trip mismatch: {payload}")
    return payload


def get_v2_current_trip(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request("GET", base_url, "/api/v2/trips/current", timeout, token=token)
    expect_status(status_code, 200, "V2 current trip failed")
    payload = expect_json_dict(data, "V2 current trip returned invalid JSON")
    if payload.get("id") != trip_id:
        raise SmokeFailure(f"V2 current trip mismatch: {payload}")
    return payload


def get_v2_chat_history(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request(
        "GET",
        base_url,
        f"/api/v2/chat/messages?trip_id={trip_id}",
        timeout,
        token=token,
    )
    expect_status(status_code, 200, "V2 chat history failed")
    payload = expect_json_dict(data, "V2 chat history returned invalid JSON")
    if not isinstance(payload.get("messages"), list):
        raise SmokeFailure(f"V2 chat history missing messages: {payload}")
    return payload


def create_v2_chat_message(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/v2/chat/messages",
        timeout,
        token=token,
        payload={"content": "有点紧张，但还在走。", "trip_id": trip_id},
    )
    expect_status(status_code, 200, "V2 chat message failed")
    payload = expect_json_dict(data, "V2 chat message returned invalid JSON")
    if "assistant_message" not in payload:
        raise SmokeFailure(f"V2 chat message missing assistant payload: {payload}")
    return payload


def create_v2_proactive_chat(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/v2/chat/proactive",
        timeout,
        token=token,
        payload={"trigger": "start", "trip_id": trip_id},
    )
    expect_status(status_code, 200, "V2 proactive chat failed")
    payload = expect_json_dict(data, "V2 proactive chat returned invalid JSON")
    if payload.get("message_type") != "proactive":
        raise SmokeFailure(f"V2 proactive chat mismatch: {payload}")
    return payload


def get_v2_profile_summary(base_url: str, timeout: float, token: str) -> dict:
    status_code, data = http_request("GET", base_url, "/api/v2/profile/summary", timeout, token=token)
    expect_status(status_code, 200, "V2 profile summary failed")
    payload = expect_json_dict(data, "V2 profile summary returned invalid JSON")
    if "guardian_count" not in payload:
        raise SmokeFailure(f"V2 profile summary missing guardian_count: {payload}")
    return payload


def trigger_sos(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        f"/api/trips/{trip_id}/sos",
        timeout,
        token=token,
        payload={"lat": 31.2285, "lng": 121.4755, "audio_url": "https://example.com/smoke.mp3"},
    )
    expect_status(status_code, 200, "Trigger SOS failed")
    payload = expect_json_dict(data, "Trigger SOS returned invalid JSON")
    if payload.get("status") != "emergency":
        raise SmokeFailure(f"SOS status mismatch: {payload}")
    return payload


def trigger_v2_sos(base_url: str, timeout: float, token: str) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/v2/sos",
        timeout,
        token=token,
        payload={"lat": 31.2285, "lng": 121.4755},
    )
    expect_status(status_code, 200, "Trigger V2 SOS failed")
    payload = expect_json_dict(data, "Trigger V2 SOS returned invalid JSON")
    if payload.get("sos_id") is None:
        raise SmokeFailure(f"V2 SOS payload missing sos_id: {payload}")
    return payload


def finish_trip(base_url: str, timeout: float, token: str, trip_id: int) -> dict:
    status_code, data = http_request(
        "PUT",
        base_url,
        f"/api/trips/{trip_id}/finish",
        timeout,
        token=token,
    )
    expect_status(status_code, 200, "Finish trip failed")
    payload = expect_json_dict(data, "Finish trip returned invalid JSON")
    if payload.get("status") != "finished":
        raise SmokeFailure(f"Finish trip status mismatch: {payload}")
    return payload


def list_notification_events(
    base_url: str,
    timeout: float,
    token: str,
    trip_id: int,
    expected_events: set[str],
) -> list:
    status_code, data = http_request(
        "GET",
        base_url,
        f"/api/notifications/events?trip_id={trip_id}",
        timeout,
        token=token,
    )
    expect_status(status_code, 200, "Notification event query failed")
    if not isinstance(data, list):
        raise SmokeFailure(f"Notification events returned invalid JSON: {data}")
    event_types = {
        item.get("event_type")
        for item in data
        if isinstance(item, dict)
    }
    missing = expected_events - event_types
    if missing:
        raise SmokeFailure(f"Missing notification events: {sorted(missing)}")
    return data


def expect_unauthorized_profile(base_url: str, timeout: float) -> dict:
    status_code, data = http_request("GET", base_url, "/api/user/profile", timeout)
    expect_status(status_code, 401, "Unauthorized profile check failed")
    return expect_json_dict(data, "Unauthorized profile returned invalid JSON")


def expect_missing_trip(base_url: str, timeout: float, token: str) -> dict:
    status_code, data = http_request("GET", base_url, "/api/trips/999999999", timeout, token=token)
    expect_status(status_code, 404, "Missing trip check failed")
    return expect_json_dict(data, "Missing trip returned invalid JSON")


def expect_bad_trip_request(base_url: str, timeout: float, token: str) -> dict:
    status_code, data = http_request(
        "POST",
        base_url,
        "/api/trips",
        timeout,
        token=token,
        payload={
            "trip_type": "walk",
            "start_lat": 31.2304,
            "start_lng": 121.4737,
            "end_lat": 31.2243,
            "end_lng": 121.4768,
            "estimated_minutes": 18,
            "guardian_ids": [],
        },
    )
    expect_status(status_code, 400, "Bad request check failed")
    return expect_json_dict(data, "Bad request returned invalid JSON")


def print_summary(results: list[StepResult], extra: str | None = None) -> None:
    passed = sum(1 for item in results if item.passed)
    failed = len(results) - passed
    summary = f"Summary: {passed} passed, {failed} failed"
    if extra:
        summary = f"{summary} ({extra})"
    print(summary)


if __name__ == "__main__":
    sys.exit(main())
