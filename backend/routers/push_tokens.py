from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_db
from deps import get_current_user
from models import User
from schemas import (
    ErrorResponse,
    MessageResponse,
    PushTokenCompatDeregisterRequest,
    PushTokenCompatRegisterRequest,
    PushTokenCompatResponse,
)
from services.push_delivery import deregister_push_token, register_push_token


router = APIRouter(prefix="/api/push-tokens", tags=["notifications"])


@router.post(
    "/register",
    response_model=PushTokenCompatResponse,
    responses={401: {"model": ErrorResponse}},
)
def register_compat_push_token(
    payload: PushTokenCompatRegisterRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> PushTokenCompatResponse:
    token = register_push_token(
        db,
        user_id=current_user.id,
        token=payload.token,
        platform=payload.platform,
        device_name=None,
        app_version=None,
    )
    db.commit()
    db.refresh(token)
    return PushTokenCompatResponse(token=token.token, status="registered", registered=True)


@router.post(
    "/deregister",
    response_model=MessageResponse,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def deregister_compat_push_token(
    payload: PushTokenCompatDeregisterRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MessageResponse:
    deleted = deregister_push_token(db, user_id=current_user.id, token=payload.token)
    if deleted is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="推送令牌不存在")
    db.commit()
    return MessageResponse(message="推送令牌已停用")
