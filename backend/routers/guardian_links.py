from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query, status as http_status
from sqlalchemy import or_
from sqlalchemy.orm import Session

from database import get_db
from deps import get_current_user
from models import GuardianLink, User
from schemas import ErrorResponse, GuardianLinkInviteRequest, GuardianLinkRead, UserRead


router = APIRouter(prefix="/api/guardian-links", tags=["guardian-links"])


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def to_user_read(user: User) -> UserRead:
    return UserRead.model_validate(user)


def link_current_role(link: GuardianLink, current_user_id: int) -> Literal["traveler", "guardian"]:
    return "traveler" if link.traveler_user_id == current_user_id else "guardian"


def build_link_read(link: GuardianLink, current_user_id: int, db: Session) -> GuardianLinkRead:
    traveler_user = db.get(User, link.traveler_user_id)
    guardian_user = db.get(User, link.guardian_user_id)
    if traveler_user is None or guardian_user is None:
        raise HTTPException(status_code=http_status.HTTP_404_NOT_FOUND, detail="守护关系不存在")

    return GuardianLinkRead(
        id=link.id,
        traveler_user=to_user_read(traveler_user),
        guardian_user=to_user_read(guardian_user),
        relationship=link.relationship,
        status=link.status,
        invited_at=link.invited_at,
        accepted_at=link.accepted_at,
        revoked_at=link.revoked_at,
        current_role=link_current_role(link, current_user_id),
    )


def get_owned_link(db: Session, link_id: int, current_user_id: int) -> GuardianLink:
    link = db.get(GuardianLink, link_id)
    if link is None:
        raise HTTPException(status_code=http_status.HTTP_404_NOT_FOUND, detail="守护关系不存在")
    if current_user_id not in {link.traveler_user_id, link.guardian_user_id}:
        raise HTTPException(status_code=http_status.HTTP_403_FORBIDDEN, detail="无权操作该守护关系")
    return link


@router.post(
    "/invite",
    response_model=GuardianLinkRead,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def invite_guardian_link(
    payload: GuardianLinkInviteRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianLinkRead:
    guardian_user = None
    if payload.guardian_user_id is not None:
        guardian_user = db.get(User, payload.guardian_user_id)
    else:
        guardian_account = (payload.guardian_account or payload.guardian_phone or "").strip()
        if not guardian_account:
            raise HTTPException(status_code=http_status.HTTP_400_BAD_REQUEST, detail="守护人账号不能为空")
        guardian_user = (
            db.query(User)
            .filter(or_(User.phone == guardian_account, User.email == guardian_account))
            .first()
        )
    if guardian_user is None:
        raise HTTPException(status_code=http_status.HTTP_404_NOT_FOUND, detail="守护人账号不存在")
    if guardian_user.id == current_user.id:
        raise HTTPException(status_code=http_status.HTTP_400_BAD_REQUEST, detail="不能邀请自己作为守护人")

    link = (
        db.query(GuardianLink)
        .filter(
            GuardianLink.traveler_user_id == current_user.id,
            GuardianLink.guardian_user_id == guardian_user.id,
        )
        .first()
    )
    now = utc_now()
    if link is None:
        link = GuardianLink(
            traveler_user_id=current_user.id,
            guardian_user_id=guardian_user.id,
            relationship=payload.relationship or "朋友",
            status="pending",
            invited_at=now,
            accepted_at=None,
            revoked_at=None,
        )
        db.add(link)
    elif link.status == "revoked":
        link.relationship = payload.relationship or link.relationship or "朋友"
        link.status = "pending"
        link.invited_at = now
        link.accepted_at = None
        link.revoked_at = None
        db.add(link)
    else:
        # Retry-friendly behavior: refreshing an existing active invitation returns the current row.
        if payload.relationship and payload.relationship != link.relationship:
            link.relationship = payload.relationship
            db.add(link)

    db.commit()
    db.refresh(link)
    return build_link_read(link, current_user.id, db)


@router.get(
    "",
    response_model=list[GuardianLinkRead],
    responses={401: {"model": ErrorResponse}},
)
def list_guardian_links(
    role: Literal["all", "traveler", "guardian"] = Query("all"),
    status_filter: Literal["active", "all", "pending", "accepted", "revoked"] = Query("active", alias="status"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[GuardianLinkRead]:
    query = db.query(GuardianLink)
    if role == "traveler":
        query = query.filter(GuardianLink.traveler_user_id == current_user.id)
    elif role == "guardian":
        query = query.filter(GuardianLink.guardian_user_id == current_user.id)
    else:
        query = query.filter(
            or_(
                GuardianLink.traveler_user_id == current_user.id,
                GuardianLink.guardian_user_id == current_user.id,
            )
        )

    if status_filter == "active":
        query = query.filter(GuardianLink.status.in_(("pending", "accepted")))
    elif status_filter != "all":
        query = query.filter(GuardianLink.status == status_filter)

    links = query.order_by(GuardianLink.invited_at.desc(), GuardianLink.id.desc()).all()
    return [build_link_read(link, current_user.id, db) for link in links]


@router.post(
    "/{link_id}/accept",
    response_model=GuardianLinkRead,
    responses={401: {"model": ErrorResponse}, 403: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def accept_guardian_link(
    link_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianLinkRead:
    link = get_owned_link(db, link_id, current_user.id)
    if link.guardian_user_id != current_user.id:
        raise HTTPException(status_code=http_status.HTTP_403_FORBIDDEN, detail="只有被邀请的守护人可以接受")
    if link.status == "accepted":
        return build_link_read(link, current_user.id, db)
    if link.status == "revoked":
        raise HTTPException(status_code=http_status.HTTP_400_BAD_REQUEST, detail="该守护关系已撤销，请重新邀请")

    link.status = "accepted"
    link.accepted_at = utc_now()
    link.revoked_at = None
    db.add(link)
    db.commit()
    db.refresh(link)
    return build_link_read(link, current_user.id, db)


@router.delete(
    "/{link_id}",
    response_model=GuardianLinkRead,
    responses={401: {"model": ErrorResponse}, 403: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def revoke_guardian_link(
    link_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianLinkRead:
    link = get_owned_link(db, link_id, current_user.id)
    if link.status == "revoked":
        return build_link_read(link, current_user.id, db)

    link.status = "revoked"
    link.revoked_at = utc_now()
    db.add(link)
    db.commit()
    db.refresh(link)
    return build_link_read(link, current_user.id, db)
