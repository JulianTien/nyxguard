from __future__ import annotations

import json
import logging
from collections.abc import Iterable
from typing import Any, Optional

from sqlalchemy.orm import Session

from models import Guardian, NotificationEvent, Trip, User


logger = logging.getLogger(__name__)


EVENT_LABELS: dict[str, tuple[str, str]] = {
    "trip_started": ("行程已开始", "{nickname}已开始{trip_label}守护"),
    "walk_timeout": ("步行超时提醒", "{nickname}的步行行程已超时，请留意"),
    "walk_deviation": ("步行偏离提醒", "{nickname}的步行路线已偏离，请关注"),
    "ride_deviation": ("乘车偏离提醒", "{nickname}的乘车路线已偏离预定路线，请关注"),
    "sos_triggered": ("SOS已触发", "{nickname}已触发SOS，正在发送实时位置与行程信息"),
    "trip_finished": ("行程已结束", "{nickname}已安全到达，行程已结束"),
}


def serialize_payload(payload: Optional[dict[str, Any]]) -> str:
    return json.dumps(payload or {}, ensure_ascii=False, default=str)


def deserialize_payload(payload_json: Optional[str]) -> dict[str, Any]:
    if not payload_json:
        return {}
    try:
        parsed = json.loads(payload_json)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def guardian_snapshot(guardian: Guardian) -> dict[str, Any]:
    return {
        "id": guardian.id,
        "nickname": guardian.nickname,
        "phone": guardian.phone,
        "relationship": guardian.relationship,
    }


def trip_snapshot(trip: Trip) -> dict[str, Any]:
    return {
        "id": trip.id,
        "trip_type": trip.trip_type,
        "status": trip.status,
        "start_name": trip.start_name,
        "end_name": trip.end_name,
        "plate_number": trip.plate_number,
        "vehicle_type": trip.vehicle_type,
        "vehicle_color": trip.vehicle_color,
        "estimated_minutes": trip.estimated_minutes,
        "created_at": trip.created_at,
        "finished_at": trip.finished_at,
    }


def build_event_copy(user: User, trip: Trip, event_type: str) -> tuple[str, str]:
    title_template, body_template = EVENT_LABELS.get(
        event_type,
        ("通知事件", "{nickname}产生了一条新的通知事件"),
    )
    trip_label = "步行" if trip.trip_type == "walk" else "乘车"
    title = title_template
    body = body_template.format(nickname=user.nickname, trip_label=trip_label)
    return title, body


def create_notification_event(
    db: Session,
    *,
    user_id: int,
    trip_id: Optional[int],
    guardian_id: Optional[int],
    event_type: str,
    title: str,
    body: str,
    payload: Optional[dict[str, Any]] = None,
    status: str = "recorded",
) -> NotificationEvent:
    event = NotificationEvent(
        user_id=user_id,
        trip_id=trip_id,
        guardian_id=guardian_id,
        event_type=event_type,
        title=title,
        body=body,
        payload_json=serialize_payload(payload),
        status=status,
    )
    db.add(event)
    db.flush()
    return event


def emit_trip_guardian_events(
    db: Session,
    *,
    user: User,
    trip: Trip,
    guardians: Iterable[Guardian],
    event_type: str,
    extra_payload: Optional[dict[str, Any]] = None,
) -> list[NotificationEvent]:
    guardian_list = list(guardians)
    title, body = build_event_copy(user, trip, event_type)
    shared_payload = {
        "trip": trip_snapshot(trip),
        "guardians": [guardian_snapshot(item) for item in guardian_list],
    }
    if extra_payload:
        shared_payload.update(extra_payload)

    events: list[NotificationEvent] = []
    for guardian in guardian_list:
        payload = {
            **shared_payload,
            "guardian": guardian_snapshot(guardian),
        }
        events.append(
            create_notification_event(
                db,
                user_id=user.id,
                trip_id=trip.id,
                guardian_id=guardian.id,
                event_type=event_type,
                title=title,
                body=body,
                payload=payload,
            )
        )

    logger.info(
        "notification events recorded",
        extra={
            "event_type": event_type,
            "trip_id": trip.id,
            "guardian_count": len(events),
            "user_id": user.id,
        },
    )
    return events
