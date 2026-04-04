from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_db
from deps import get_current_user
from models import Guardian, User
from schemas import ErrorResponse, GuardianCreateRequest, GuardianRead, MessageResponse


router = APIRouter(prefix="/api/guardians", tags=["guardians"])


@router.get(
    "",
    response_model=list[GuardianRead],
    responses={401: {"model": ErrorResponse}},
)
def list_guardians(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[GuardianRead]:
    guardians = db.query(Guardian).filter(Guardian.user_id == current_user.id).order_by(Guardian.id.asc()).all()
    return [GuardianRead.model_validate(item) for item in guardians]


@router.post(
    "",
    response_model=GuardianRead,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}},
)
def add_guardian(
    payload: GuardianCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianRead:
    count = db.query(Guardian).filter(Guardian.user_id == current_user.id).count()
    if count >= 5:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="最多添加5名守护者")
    if not payload.nickname or not payload.phone:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="姓名和电话不能为空")

    guardian = Guardian(
        user_id=current_user.id,
        nickname=payload.nickname,
        phone=payload.phone,
        relationship=payload.relationship or "朋友",
    )
    db.add(guardian)
    db.commit()
    db.refresh(guardian)
    return GuardianRead.model_validate(guardian)


@router.delete(
    "/{guardian_id}",
    response_model=MessageResponse,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def delete_guardian(
    guardian_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MessageResponse:
    guardian = db.query(Guardian).filter(
        Guardian.id == guardian_id,
        Guardian.user_id == current_user.id,
    ).first()
    if guardian is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="守护者不存在")

    total_count = db.query(Guardian).filter(Guardian.user_id == current_user.id).count()
    if total_count <= 1:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="至少保留1名守护者后才能继续使用步行或乘车守护",
        )

    db.delete(guardian)
    db.commit()
    return MessageResponse(message="删除成功")
