from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from database import get_db
from deps import get_current_user
from models import NotificationEvent, PushToken, User
from notification_events import create_notification_event, notification_event_to_dict
from schemas import (
    ErrorResponse,
    MessageResponse,
    NotificationEventRead,
    NotificationPushRequest,
    PushTokenRead,
    PushTokenRegisterRequest,
)
from services.push_delivery import (
    deliver_notification_event,
    deregister_push_token,
    mark_notification_opened,
    register_push_token,
)


router = APIRouter(prefix="/api/notifications", tags=["notifications"])


def to_event_read(event: NotificationEvent) -> NotificationEventRead:
    return NotificationEventRead.model_validate(notification_event_to_dict(event))


def to_token_read(token: PushToken) -> PushTokenRead:
    return PushTokenRead.model_validate(token)


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
        delivery_channel=payload.delivery_channel,
        delivery_status="queued",
    )
    deliver_notification_event(db, event, recipient_user_id=current_user.id)
    db.commit()
    db.refresh(event)
    return to_event_read(event)


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
    return [to_event_read(event) for event in events]


@router.post(
    "/events/{event_id}/opened",
    response_model=NotificationEventRead,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def mark_event_opened(
    event_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> NotificationEventRead:
    event = (
        db.query(NotificationEvent)
        .filter(NotificationEvent.id == event_id, NotificationEvent.user_id == current_user.id)
        .first()
    )
    if event is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="通知事件不存在")

    mark_notification_opened(event)
    db.commit()
    db.refresh(event)
    return to_event_read(event)


@router.post(
    "/tokens",
    response_model=PushTokenRead,
    responses={401: {"model": ErrorResponse}},
)
def upsert_push_token(
    payload: PushTokenRegisterRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> PushTokenRead:
    token = register_push_token(
        db,
        user_id=current_user.id,
        token=payload.token,
        platform=payload.platform,
        device_name=payload.device_name,
        app_version=payload.app_version,
    )
    db.commit()
    db.refresh(token)
    return to_token_read(token)


@router.delete(
    "/tokens",
    response_model=MessageResponse,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def delete_push_token(
    token: str = Query(..., min_length=1),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MessageResponse:
    deleted = deregister_push_token(db, user_id=current_user.id, token=token)
    if deleted is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="推送令牌不存在")
    db.commit()
    return MessageResponse(message="推送令牌已停用")


@router.delete(
    "/tokens/{token}",
    response_model=MessageResponse,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def delete_push_token_path(
    token: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MessageResponse:
    deleted = deregister_push_token(db, user_id=current_user.id, token=token)
    if deleted is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="推送令牌不存在")
    db.commit()
    return MessageResponse(message="推送令牌已停用")
