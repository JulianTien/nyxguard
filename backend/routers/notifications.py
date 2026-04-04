from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from database import get_db
from deps import get_current_user
from models import NotificationEvent, User
from notification_events import create_notification_event, deserialize_payload
from schemas import ErrorResponse, NotificationEventRead, NotificationPushRequest


router = APIRouter(prefix="/api/notifications", tags=["notifications"])


@router.post(
    "/push",
    response_model=NotificationEventRead,
    responses={401: {"model": ErrorResponse}},
)
def push_notification(
    payload: NotificationPushRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> NotificationEventRead:
    event = create_notification_event(
        db,
        user_id=current_user.id,
        trip_id=payload.trip_id,
        guardian_id=payload.guardian_id,
        event_type=payload.event_type,
        title=payload.title,
        body=payload.body,
        payload=payload.payload,
        status=payload.status,
    )
    db.commit()
    db.refresh(event)
    return NotificationEventRead(
        id=event.id,
        event_type=event.event_type,
        title=event.title,
        body=event.body,
        payload=deserialize_payload(event.payload_json),
        status=event.status,
        user_id=event.user_id,
        trip_id=event.trip_id,
        guardian_id=event.guardian_id,
        created_at=event.created_at,
    )


@router.get(
    "/events",
    response_model=list[NotificationEventRead],
    responses={401: {"model": ErrorResponse}},
)
def list_notification_events(
    trip_id: Optional[int] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[NotificationEventRead]:
    query = db.query(NotificationEvent).filter(NotificationEvent.user_id == current_user.id)
    if trip_id is not None:
        query = query.filter(NotificationEvent.trip_id == trip_id)

    events = query.order_by(NotificationEvent.created_at.desc(), NotificationEvent.id.desc()).all()
    return [
        NotificationEventRead(
            id=event.id,
            event_type=event.event_type,
            title=event.title,
            body=event.body,
            payload=deserialize_payload(event.payload_json),
            status=event.status,
            user_id=event.user_id,
            trip_id=event.trip_id,
            guardian_id=event.guardian_id,
            created_at=event.created_at,
        )
        for event in events
    ]
