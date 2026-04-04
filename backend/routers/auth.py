import re

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import or_
from sqlalchemy.orm import Session

from auth_utils import create_access_token, hash_password, verify_password
from database import get_db
from models import User
from schemas import AuthResponse, ErrorResponse, LoginRequest, RegisterRequest, UserRead


router = APIRouter(prefix="/api/auth", tags=["auth"])
PASSWORD_PATTERN = re.compile(r"^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,20}$")


@router.post(
    "/register",
    response_model=AuthResponse,
    responses={400: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
def register(payload: RegisterRequest, db: Session = Depends(get_db)) -> AuthResponse:
    if not payload.nickname:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="昵称不能为空")
    if not payload.phone and not payload.email:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="手机号或邮箱至少填一个")
    if not PASSWORD_PATTERN.match(payload.password):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="密码需6-20位，包含字母和数字")

    if payload.phone and db.query(User).filter(User.phone == payload.phone).first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="该手机号已注册")
    if payload.email and db.query(User).filter(User.email == payload.email).first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="该邮箱已注册")

    user = User(
        nickname=payload.nickname,
        phone=payload.phone or None,
        email=payload.email or None,
        password_hash=hash_password(payload.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    return AuthResponse(token=create_access_token(user.id), user=UserRead.model_validate(user))


@router.post(
    "/login",
    response_model=AuthResponse,
    responses={401: {"model": ErrorResponse}, 403: {"model": ErrorResponse}},
)
def login(payload: LoginRequest, db: Session = Depends(get_db)) -> AuthResponse:
    if not payload.account or not payload.password:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="账号和密码不能为空")

    user = db.query(User).filter(
        or_(User.phone == payload.account, User.email == payload.account)
    ).first()

    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="账号或密码错误")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="账号已被禁用")

    return AuthResponse(token=create_access_token(user.id), user=UserRead.model_validate(user))
