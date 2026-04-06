from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session
from typing import Optional

from ai_guardian import generate_guardian_reply, get_proactive_message, is_probably_english
from database import get_db
from deps import get_current_user
from models import ChatMessage, Trip, User
from schemas import ChatRequest, ChatResponse, ErrorResponse, ProactiveChatRequest


router = APIRouter(prefix="/api/chat", tags=["chat"])


def build_trip_context(
    db: Session,
    current_user: User,
    trip_id: Optional[int],
    *,
    prefer_english: bool = False,
) -> Optional[str]:
    if trip_id is None:
        return None
    trip = db.query(Trip).filter(Trip.id == trip_id, Trip.user_id == current_user.id).first()
    if trip is None:
        return None
    if prefer_english:
        destination = trip.end_name or "Unnamed destination"
        mode = "walking" if trip.trip_type == "walk" else "ride"
        return f"Active {mode} guard. Destination: {destination}. Status: {trip.status}."

    destination = trip.end_name or "未命名目的地"
    mode = "步行" if trip.trip_type == "walk" else "乘车"
    return f"{mode}守护中，目的地：{destination}，状态：{trip.status}"


@router.post(
    "",
    response_model=ChatResponse,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}},
)
def chat(
    payload: ChatRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChatResponse:
    if not payload.content:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="消息内容不能为空")

    user_message = ChatMessage(
        user_id=current_user.id,
        role="user",
        content=payload.content,
        trip_id=payload.trip_id,
        message_type="chat",
    )
    db.add(user_message)

    prefer_english = is_probably_english(payload.content, request.headers.get("Accept-Language"))
    reply, _ = generate_guardian_reply(
        payload.content,
        trip_context=build_trip_context(db, current_user, payload.trip_id, prefer_english=prefer_english),
        prefer_english=prefer_english,
    )
    assistant_message = ChatMessage(
        user_id=current_user.id,
        role="assistant",
        content=reply,
        trip_id=payload.trip_id,
        message_type="chat",
    )
    db.add(assistant_message)
    db.commit()
    db.refresh(assistant_message)

    return ChatResponse(reply=reply, message_id=assistant_message.id)


@router.post(
    "/proactive",
    response_model=ChatResponse,
    responses={401: {"model": ErrorResponse}},
)
def proactive_chat(
    payload: ProactiveChatRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChatResponse:
    reply = get_proactive_message(
        payload.trigger,
        prefer_english=is_probably_english("", request.headers.get("Accept-Language")),
    )
    message = ChatMessage(
        user_id=current_user.id,
        role="assistant",
        content=reply,
        trip_id=payload.trip_id,
        message_type="proactive",
    )
    db.add(message)
    db.commit()
    db.refresh(message)
    return ChatResponse(reply=reply, message_id=message.id)
