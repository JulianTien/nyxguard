from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ai_guardian import PROACTIVE_MESSAGES, generate_guardian_reply
from database import get_db
from deps import get_current_user
from models import ChatMessage, Guardian, Location, SOSEvent, Trip, TripGuardian, User
from notification_events import create_notification_event, guardian_snapshot, trip_snapshot
from schemas import (
    ChatHistoryResponse,
    ChatMessageCreateRequest,
    ChatMessageRead,
    CurrentTripResponse,
    DashboardResponse,
    ErrorResponse,
    LocationPointRead,
    ProfileSummaryResponse,
    ProactiveChatRequest,
    TripAlertRequest,
    TripAlertResponse,
    V2ChatMessageResponse,
    V2SosRequest,
    V2SosResponse,
    VehicleInfoRead,
    ActiveTripBriefRead,
)


router = APIRouter(prefix="/api/v2", tags=["v2"])


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def to_utc_date(value: datetime) -> datetime.date:
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).date()


def to_utc_datetime(value: datetime) -> datetime:
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def get_active_trip(db: Session, user_id: int) -> Optional[Trip]:
    return (
        db.query(Trip)
        .filter(Trip.user_id == user_id, Trip.status.in_(("active", "emergency")))
        .order_by(Trip.created_at.desc(), Trip.id.desc())
        .first()
    )


def get_trip_guardians(db: Session, trip_id: int, user_id: int) -> list[Guardian]:
    return (
        db.query(Guardian)
        .join(TripGuardian, TripGuardian.guardian_id == Guardian.id)
        .filter(TripGuardian.trip_id == trip_id, Guardian.user_id == user_id)
        .order_by(TripGuardian.created_at.asc(), Guardian.id.asc())
        .all()
    )


def get_guardian_summary(guardians: list[Guardian]) -> str:
    if not guardians:
        return "未选择守护人"
    if len(guardians) == 1:
        guardian = guardians[0]
        return guardian.nickname if guardian.relationship == "朋友" else f"{guardian.nickname}（{guardian.relationship}）"
    return f"{len(guardians)}位紧急联系人"


def build_greeting() -> str:
    hour = datetime.now().hour
    if hour < 12:
        return "早上好"
    if hour < 18:
        return "下午好"
    return "晚上好"


def compute_eta_minutes(trip: Trip) -> int:
    if trip.status == "finished":
        return 0
    expected = trip.expected_arrive_at
    if expected is None:
        return trip.estimated_minutes
    delta = to_utc_datetime(expected) - now_utc()
    return max(0, int(delta.total_seconds() // 60))


def serialize_message(message: ChatMessage) -> ChatMessageRead:
    return ChatMessageRead.model_validate(message)


def build_trip_context(trip: Optional[Trip]) -> Optional[str]:
    if trip is None:
        return None
    mode = "步行" if trip.trip_type == "walk" else "乘车"
    return f"{mode}守护中，目的地：{trip.end_name or '未命名目的地'}，状态：{trip.status}"


def create_proactive_chat_message(
    db: Session,
    *,
    user_id: int,
    trip_id: Optional[int],
    trigger: str,
) -> ChatMessage:
    reply = PROACTIVE_MESSAGES.get(trigger, PROACTIVE_MESSAGES["periodic"])
    message = ChatMessage(
        user_id=user_id,
        role="assistant",
        content=reply,
        trip_id=trip_id,
        message_type="proactive",
    )
    db.add(message)
    db.flush()
    return message


@router.get(
    "/dashboard",
    response_model=DashboardResponse,
    responses={401: {"model": ErrorResponse}},
)
def get_dashboard(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DashboardResponse:
    active_trip = get_active_trip(db, current_user.id)
    guardian_count = db.query(Guardian).filter(Guardian.user_id == current_user.id).count()
    active_trip_brief = None
    if active_trip is not None:
        active_trip_brief = ActiveTripBriefRead(
            id=active_trip.id,
            mode=active_trip.trip_type,
            status=active_trip.status,
            destination=active_trip.end_name,
            eta_minutes=compute_eta_minutes(active_trip),
            guardian_count=len(get_trip_guardians(db, active_trip.id, current_user.id)),
        )

    return DashboardResponse(
        nickname=current_user.nickname,
        greeting=build_greeting(),
        guardian_count=guardian_count,
        active_trip_brief=active_trip_brief,
        quick_tools_state={
            "fake_call": {"available": True},
            "alarm": {"available": True},
        },
    )


@router.get(
    "/trips/current",
    response_model=CurrentTripResponse,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_current_trip(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CurrentTripResponse:
    trip = get_active_trip(db, current_user.id)
    if trip is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="当前没有进行中的守护行程")

    guardians = get_trip_guardians(db, trip.id, current_user.id)
    latest_location = (
        db.query(Location)
        .filter(Location.trip_id == trip.id)
        .order_by(Location.recorded_at.desc(), Location.id.desc())
        .first()
    )
    preview_points = (
        db.query(Location)
        .filter(Location.trip_id == trip.id)
        .order_by(Location.recorded_at.desc(), Location.id.desc())
        .limit(20)
        .all()
    )
    preview_points.reverse()
    latest_sos = (
        db.query(SOSEvent)
        .filter(SOSEvent.user_id == current_user.id, SOSEvent.trip_id == trip.id)
        .order_by(SOSEvent.created_at.desc(), SOSEvent.id.desc())
        .first()
    )

    return CurrentTripResponse(
        id=trip.id,
        mode=trip.trip_type,
        status=trip.status,
        destination=trip.end_name,
        eta_minutes=compute_eta_minutes(trip),
        guardian_count=len(guardians),
        guardian_summary=get_guardian_summary(guardians),
        vehicle_info=VehicleInfoRead(
            plate_number=trip.plate_number or "",
            vehicle_type=trip.vehicle_type or "",
            vehicle_color=trip.vehicle_color or "",
        ),
        latest_location=LocationPointRead(
            lat=latest_location.lat,
            lng=latest_location.lng,
            recorded_at=latest_location.recorded_at,
        ) if latest_location is not None else None,
        route_preview=[
            LocationPointRead(lat=point.lat, lng=point.lng, recorded_at=point.recorded_at)
            for point in preview_points
        ],
        sos_state=latest_sos.status if latest_sos is not None else "idle",
        expected_arrive_at=trip.expected_arrive_at,
    )


@router.get(
    "/profile/summary",
    response_model=ProfileSummaryResponse,
    responses={401: {"model": ErrorResponse}},
)
def get_profile_summary(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ProfileSummaryResponse:
    guardians_count = db.query(Guardian).filter(Guardian.user_id == current_user.id).count()
    finished_trips = (
        db.query(Trip)
        .filter(Trip.user_id == current_user.id, Trip.status == "finished")
        .order_by(Trip.created_at.asc(), Trip.id.asc())
        .all()
    )

    route_counter = Counter(
        f"{trip.start_name.strip().lower()}->{trip.end_name.strip().lower()}"
        for trip in finished_trips
        if trip.start_name.strip() or trip.end_name.strip()
    )
    total_minutes = 0
    trip_days: set[datetime.date] = set()
    for trip in finished_trips:
        end = trip.finished_at or trip.expected_arrive_at or trip.created_at
        total_minutes += max(0, int((to_utc_datetime(end) - to_utc_datetime(trip.created_at)).total_seconds() // 60))
        trip_days.add(to_utc_date(trip.created_at))

    if trip_days:
        streak = 1
        day_cursor = max(trip_days)
        while True:
            previous = day_cursor.fromordinal(day_cursor.toordinal() - 1)
            if previous not in trip_days:
                break
            streak += 1
            day_cursor = previous
        badge_days = streak
    else:
        badge_days = max(1, (now_utc().date() - to_utc_date(current_user.created_at)).days + 1)

    return ProfileSummaryResponse(
        nickname=current_user.nickname,
        guardian_count=guardians_count,
        frequent_routes_count=len(route_counter),
        guard_minutes_total=total_minutes,
        badge_days=badge_days,
        settings_summary="假来电 / 警报配置 / 主题",
    )


@router.get(
    "/chat/messages",
    response_model=ChatHistoryResponse,
    responses={401: {"model": ErrorResponse}},
)
def list_chat_messages(
    trip_id: Optional[int] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChatHistoryResponse:
    query = db.query(ChatMessage).filter(ChatMessage.user_id == current_user.id)
    if trip_id is not None:
        query = query.filter(ChatMessage.trip_id == trip_id)
    messages = query.order_by(ChatMessage.created_at.asc(), ChatMessage.id.asc()).all()
    return ChatHistoryResponse(messages=[serialize_message(message) for message in messages])


@router.post(
    "/chat/messages",
    response_model=V2ChatMessageResponse,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}},
)
def create_chat_message(
    payload: ChatMessageCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> V2ChatMessageResponse:
    if not payload.content:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="消息内容不能为空")

    trip = None
    if payload.trip_id is not None:
        trip = db.query(Trip).filter(Trip.id == payload.trip_id, Trip.user_id == current_user.id).first()

    user_message = ChatMessage(
        user_id=current_user.id,
        role="user",
        content=payload.content,
        trip_id=payload.trip_id,
        message_type="chat",
    )
    db.add(user_message)
    db.flush()

    reply, used_fallback = generate_guardian_reply(payload.content, build_trip_context(trip))
    assistant_message = ChatMessage(
        user_id=current_user.id,
        role="assistant",
        content=reply,
        trip_id=payload.trip_id,
        message_type="chat",
    )
    db.add(assistant_message)
    db.commit()
    db.refresh(user_message)
    db.refresh(assistant_message)

    return V2ChatMessageResponse(
        user_message=serialize_message(user_message),
        assistant_message=serialize_message(assistant_message),
        used_fallback=used_fallback,
    )


@router.post(
    "/chat/proactive",
    response_model=ChatMessageRead,
    responses={401: {"model": ErrorResponse}},
)
def create_proactive_message(
    payload: ProactiveChatRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChatMessageRead:
    message = create_proactive_chat_message(
        db,
        user_id=current_user.id,
        trip_id=payload.trip_id,
        trigger=payload.trigger,
    )
    db.commit()
    db.refresh(message)
    return serialize_message(message)


@router.post(
    "/trips/{trip_id}/alerts",
    response_model=TripAlertResponse,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def create_trip_alert(
    trip_id: int,
    payload: TripAlertRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> TripAlertResponse:
    trip = db.query(Trip).filter(Trip.id == trip_id, Trip.user_id == current_user.id).first()
    if trip is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="行程不存在")
    if trip.status == "finished":
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="已结束行程不能再创建告警事件")

    guardians = get_trip_guardians(db, trip.id, current_user.id)
    trigger_map = {
        "walk_timeout": "timeout",
        "walk_deviation": "deviation",
        "ride_deviation": "deviation",
    }
    proactive_message = create_proactive_chat_message(
        db,
        user_id=current_user.id,
        trip_id=trip.id,
        trigger=trigger_map[payload.alert_type],
    )

    shared_payload = {
        "trip": trip_snapshot(trip),
        "guardians": [guardian_snapshot(item) for item in guardians],
        "alert_type": payload.alert_type,
        "proactive_message": proactive_message.content,
    }
    if payload.lat is not None and payload.lng is not None:
        shared_payload["alert_location"] = {"lat": payload.lat, "lng": payload.lng}

    labels = {
        "walk_timeout": ("步行超时提醒", f"{current_user.nickname}的步行行程已超时，请留意"),
        "walk_deviation": ("步行偏离提醒", f"{current_user.nickname}的步行路线已偏离，请关注"),
        "ride_deviation": ("乘车偏离提醒", f"{current_user.nickname}的乘车路线已偏离预定路线，请关注"),
    }
    title, body = labels[payload.alert_type]

    for guardian in guardians:
        create_notification_event(
            db,
            user_id=current_user.id,
            trip_id=trip.id,
            guardian_id=guardian.id,
            event_type=payload.alert_type,
            title=title,
            body=body,
            payload={**shared_payload, "guardian": guardian_snapshot(guardian)},
        )

    db.commit()
    db.refresh(proactive_message)
    return TripAlertResponse(
        trip_id=trip.id,
        alert_type=payload.alert_type,
        guardian_count=len(guardians),
        proactive_message=proactive_message.content,
        message_id=proactive_message.id,
    )


@router.post(
    "/sos",
    response_model=V2SosResponse,
    responses={401: {"model": ErrorResponse}},
)
def create_v2_sos(
    payload: V2SosRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> V2SosResponse:
    trip = None
    if payload.trip_id is not None:
        trip = db.query(Trip).filter(Trip.id == payload.trip_id, Trip.user_id == current_user.id).first()
    if trip is None:
        trip = get_active_trip(db, current_user.id)

    guardians = (
        get_trip_guardians(db, trip.id, current_user.id) if trip is not None else
        db.query(Guardian).filter(Guardian.user_id == current_user.id).order_by(Guardian.id.asc()).all()
    )

    if trip is not None:
        trip.status = "emergency"
        db.add(trip)

    sos = SOSEvent(
        user_id=current_user.id,
        trip_id=trip.id if trip is not None else None,
        lat=payload.lat,
        lng=payload.lng,
        audio_url=payload.audio_url,
        status="active",
    )
    db.add(sos)
    db.flush()

    title = "SOS已触发"
    body = f"{current_user.nickname}已触发SOS，正在发送实时位置与行程信息"
    shared_payload = {
        "sos_location": {"lat": payload.lat, "lng": payload.lng},
        "audio_url": payload.audio_url,
    }
    if trip is not None:
        shared_payload["trip"] = trip_snapshot(trip)
    shared_payload["guardians"] = [guardian_snapshot(item) for item in guardians]

    for guardian in guardians:
        payload_json = {**shared_payload, "guardian": guardian_snapshot(guardian)}
        create_notification_event(
            db,
            user_id=current_user.id,
            trip_id=trip.id if trip is not None else None,
            guardian_id=guardian.id,
            event_type="sos_triggered",
            title=title,
            body=body,
            payload=payload_json,
        )

    db.commit()
    db.refresh(sos)
    return V2SosResponse(
        status=trip.status if trip is not None else "emergency",
        sos_id=sos.id,
        linked_trip_id=trip.id if trip is not None else None,
        guardian_count=len(guardians),
        message="SOS求助已记录",
    )
