from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_db
from deps import get_current_user
from models import User, utc_now
from schemas import ErrorResponse, UpdateProfileRequest, UserRead


router = APIRouter(prefix="/api/user", tags=["user"])


@router.get(
    "/profile",
    response_model=UserRead,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_profile(current_user: User = Depends(get_current_user)) -> UserRead:
    return UserRead.model_validate(current_user)


@router.put(
    "/profile",
    response_model=UserRead,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
def update_profile(
    payload: UpdateProfileRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> UserRead:
    changed = False
    if payload.nickname is not None:
        if not payload.nickname:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="昵称不能为空")
        current_user.nickname = payload.nickname
        changed = True

    if payload.phone is not None:
        duplicated = db.query(User).filter(User.phone == payload.phone, User.id != current_user.id).first()
        if duplicated:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="该手机号已注册")
        current_user.phone = payload.phone or None
        changed = True

    if payload.email is not None:
        duplicated = db.query(User).filter(User.email == payload.email, User.id != current_user.id).first()
        if duplicated:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="该邮箱已注册")
        current_user.email = payload.email or None
        changed = True

    if payload.avatar_url is not None:
        current_user.avatar_url = payload.avatar_url or None
        changed = True

    if payload.emergency_phone is not None:
        current_user.emergency_phone = payload.emergency_phone or None
        changed = True

    if changed:
        current_user.updated_at = utc_now()

    db.add(current_user)
    db.commit()
    db.refresh(current_user)
    return UserRead.model_validate(current_user)
