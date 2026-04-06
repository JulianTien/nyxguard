from __future__ import annotations

from collections import Counter
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, Body, Depends, HTTPException, Request, Response, status
from sqlalchemy.orm import Session

from ai_guardian import generate_guardian_reply, get_proactive_message, is_probably_english
from database import get_db
from deps import get_current_user
from models import ChatMessage, Guardian, GuardianLink, Location, NotificationEvent, SOSEvent, Trip, TripGuardian, User
from notification_events import create_notification_event, guardian_snapshot, trip_snapshot
from services.sos_media import (
    ResolvedMediaReference,
    build_presign_result,
    commit_media_reference,
    extract_media_key,
    load_media_bytes,
    resolve_media_reference,
    store_local_media,
)
from services.watchdog import (
    find_recent_duplicate_sos,
    maybe_emit_guardian_alert,
)
from schemas import (
    ActiveTripBriefRead,
    ChatHistoryResponse,
    ChatMessageCreateRequest,
    ChatMessageRead,
    CurrentTripResponse,
    DashboardResponse,
    ErrorResponse,
    GuardianDashboardResponse,
    GuardianEventRead,
    GuardianProtectedTravelerRead,
    GuardianSosDetailResponse,
    GuardianTripDetailResponse,
    LocationPointRead,
    ProfileSummaryResponse,
    ProactiveChatRequest,
    SosMediaCommitRequest,
    SosMediaCommitResponse,
    SosMediaPresignRequest,
    SosMediaPresignResponse,
    SosMediaUploadResponse,
    TripAlertRequest,
    TripAlertResponse,
    V2ChatMessageResponse,
    V2SosRequest,
    V2SosResponse,
    VehicleInfoRead,
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


def get_guardian_links_for_guardian(db: Session, guardian_user_id: int) -> list[GuardianLink]:
    return (
        db.query(GuardianLink)
        .filter(
            GuardianLink.guardian_user_id == guardian_user_id,
            GuardianLink.status == "accepted",
        )
        .order_by(GuardianLink.id.desc())
        .all()
    )


def get_recent_guardian_events(
    db: Session,
    *,
    traveler_user_id: int,
    guardian_id: Optional[int] = None,
    trip_id: Optional[int] = None,
    limit: int = 20,
) -> list[NotificationEvent]:
    query = db.query(NotificationEvent).filter(NotificationEvent.user_id == traveler_user_id)
    if guardian_id is not None:
        query = query.filter(NotificationEvent.guardian_id == guardian_id)
    if trip_id is not None:
        query = query.filter(NotificationEvent.trip_id == trip_id)
    return query.order_by(NotificationEvent.created_at.desc(), NotificationEvent.id.desc()).limit(limit).all()


def to_guardian_event_read(event: NotificationEvent) -> GuardianEventRead:
    return GuardianEventRead(
        id=event.id,
        event_type=event.event_type,
        title=event.title,
        body=event.body,
        status=event.status,
        created_at=event.created_at,
        trip_id=event.trip_id,
        guardian_id=event.guardian_id,
    )


def ensure_guardian_has_access_to_trip(db: Session, guardian_user_id: int, trip: Trip) -> None:
    allowed = (
        db.query(GuardianLink)
        .filter(
            GuardianLink.traveler_user_id == trip.user_id,
            GuardianLink.guardian_user_id == guardian_user_id,
            GuardianLink.status == "accepted",
        )
        .first()
    )
    if allowed is None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="无权查看该行程")


def get_guardian_summary(guardians: list[Guardian]) -> str:
    if not guardians:
        return "未选择守护人"
    if len(guardians) == 1:
        guardian = guardians[0]
        return guardian.nickname if guardian.relationship == "朋友" else f"{guardian.nickname}（{guardian.relationship}）"
    return f"{len(guardians)}位紧急联系人"


def build_greeting(prefer_english: bool = False) -> str:
    hour = datetime.now().hour
    if prefer_english:
        if hour < 12:
            return "Good morning"
        if hour < 18:
            return "Good afternoon"
        return "Good evening"
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


def build_trip_context(trip: Optional[Trip], *, prefer_english: bool = False) -> Optional[str]:
    if trip is None:
        return None
    if prefer_english:
        mode = "walking" if trip.trip_type == "walk" else "ride"
        destination = trip.end_name or "Unnamed destination"
        return f"Active {mode} guard. Destination: {destination}. Status: {trip.status}."
    mode = "步行" if trip.trip_type == "walk" else "乘车"
    return f"{mode}守护中，目的地：{trip.end_name or '未命名目的地'}，状态：{trip.status}"


def create_proactive_chat_message(
    db: Session,
    *,
    user_id: int,
    trip_id: Optional[int],
    trigger: str,
    prefer_english: bool = False,
) -> ChatMessage:
    reply = get_proactive_message(trigger, prefer_english=prefer_english)
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


@router.post(
    "/sos/media/presign",
    response_model=SosMediaPresignResponse,
    responses={401: {"model": ErrorResponse}},
)
def presign_sos_media(
    payload: SosMediaPresignRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> SosMediaPresignResponse:
    if payload.trip_id is not None:
        trip = db.query(Trip).filter(Trip.id == payload.trip_id, Trip.user_id == current_user.id).first()
        if trip is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="行程不存在")

    result = build_presign_result(
        base_url=str(request.base_url),
        user_id=current_user.id,
        trip_id=payload.trip_id,
        filename=payload.filename,
        content_type=payload.content_type,
        size_bytes=payload.size_bytes,
    )
    return SosMediaPresignResponse(
        media_key=result.media_key,
        upload_url=result.upload_url,
        upload_method=result.upload_method,
        upload_headers=result.upload_headers,
        playback_url=result.playback_url,
        audio_url=result.audio_url,
        storage_mode=result.storage_mode,
        bucket=result.bucket,
        expires_in_seconds=result.expires_in_seconds,
    )


@router.post(
    "/sos/media/commit",
    response_model=SosMediaCommitResponse,
    responses={401: {"model": ErrorResponse}},
)
def commit_sos_media(
    payload: SosMediaCommitRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> SosMediaCommitResponse:
    if payload.trip_id is not None:
        trip = db.query(Trip).filter(Trip.id == payload.trip_id, Trip.user_id == current_user.id).first()
        if trip is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="行程不存在")
    media_key = (payload.media_key or "").strip()
    if not media_key:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="media_key不能为空")
    result = commit_media_reference(
        base_url=str(request.base_url),
        media_key=media_key,
        content_type=payload.content_type,
        size_bytes=payload.size_bytes,
    )
    return SosMediaCommitResponse(
        media_key=result.media_key,
        audio_url=result.audio_url,
        playback_url=result.playback_url,
        storage_mode=result.storage_mode,
        bucket=result.bucket,
        content_type=result.content_type,
        size_bytes=result.size_bytes,
        message="SOS媒体已准备就绪",
    )


@router.put(
    "/sos/media/{media_key}",
    response_model=SosMediaUploadResponse,
    responses={400: {"model": ErrorResponse}},
)
def upload_sos_media(
    media_key: str,
    request: Request,
    body: bytes = Body(..., media_type="application/octet-stream"),
    upload_token: str = "",
) -> SosMediaUploadResponse:
    if not upload_token:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="upload_token不能为空")
    result = store_local_media(
        media_key=media_key,
        upload_token=upload_token,
        body=body,
        content_type=request.headers.get("content-type") if request is not None else None,
        base_url=str(request.base_url) if request is not None else "",
    )
    return SosMediaUploadResponse(
        media_key=result.media_key,
        stored_bytes=result.stored_bytes,
        audio_url=result.audio_url,
        message=result.message,
    )


@router.get(
    "/sos/media/{media_key}",
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_sos_media(
    media_key: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    allowed = (
        db.query(SOSEvent)
        .join(Trip, Trip.id == SOSEvent.trip_id, isouter=True)
        .filter(
            SOSEvent.audio_media_key == media_key,
        )
        .filter(
            (SOSEvent.user_id == current_user.id)
            | (Trip.user_id == current_user.id)
        )
        .first()
    )
    if allowed is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SOS媒体不存在")

    body, content_type, _ = load_media_bytes(media_key)
    return Response(content=body, media_type=content_type or "application/octet-stream")


@router.get(
    "/dashboard",
    response_model=DashboardResponse,
    responses={401: {"model": ErrorResponse}},
)
def get_dashboard(
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DashboardResponse:
    prefer_english = is_probably_english("", request.headers.get("Accept-Language"))
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
        greeting=build_greeting(prefer_english=prefer_english),
        guardian_count=guardian_count,
        active_trip_brief=active_trip_brief,
        quick_tools_state={
            "fake_call": {"available": True},
            "alarm": {"available": True},
        },
    )


@router.get(
    "/guardian/dashboard",
    response_model=GuardianDashboardResponse,
    responses={401: {"model": ErrorResponse}},
)
def get_guardian_dashboard(
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianDashboardResponse:
    prefer_english = is_probably_english("", request.headers.get("Accept-Language"))
    links = get_guardian_links_for_guardian(db, current_user.id)
    protected_users: list[GuardianProtectedTravelerRead] = []
    active_trip_count = 0
    pending_alert_count = 0

    for link in links:
        traveler_user = db.get(User, link.traveler_user_id)
        if traveler_user is None:
            continue

        active_trip = (
            db.query(Trip)
            .filter(
                Trip.user_id == traveler_user.id,
                Trip.status.in_(("active", "emergency")),
            )
            .order_by(Trip.created_at.desc(), Trip.id.desc())
            .first()
        )
        active_trip_brief = None
        last_location = None
        if active_trip is not None:
            active_trip_count += 1
            pending_alert_count += 1 if active_trip.status == "emergency" else 0
            active_trip_brief = ActiveTripBriefRead(
                id=active_trip.id,
                mode=active_trip.trip_type,
                status=active_trip.status,
                destination=active_trip.end_name,
                eta_minutes=compute_eta_minutes(active_trip),
                guardian_count=len(get_trip_guardians(db, active_trip.id, traveler_user.id)),
            )
            latest_location = (
                db.query(Location)
                .filter(Location.trip_id == active_trip.id)
                .order_by(Location.recorded_at.desc(), Location.id.desc())
                .first()
            )
            if latest_location is not None:
                last_location = LocationPointRead(
                    lat=latest_location.lat,
                    lng=latest_location.lng,
                    recorded_at=latest_location.recorded_at,
                )

        recent_events = get_recent_guardian_events(
            db,
            traveler_user_id=traveler_user.id,
            trip_id=active_trip.id if active_trip is not None else None,
            limit=1,
        )
        last_event = to_guardian_event_read(recent_events[0]) if recent_events else None
        if last_event is not None and last_event.status != "opened":
            pending_alert_count += 1

        protected_users.append(
            GuardianProtectedTravelerRead(
                traveler_user_id=traveler_user.id,
                traveler_nickname=traveler_user.nickname,
                guardian_link_id=link.id,
                relationship=link.relationship,
                active_trip=active_trip_brief,
                last_location=last_location,
                last_event=last_event,
            )
        )

    return GuardianDashboardResponse(
        guardian_user_id=current_user.id,
        guardian_nickname=current_user.nickname,
        greeting=build_greeting(prefer_english=prefer_english),
        protected_users=protected_users,
        active_trip_count=active_trip_count,
        pending_alert_count=pending_alert_count,
        updated_at=now_utc(),
    )


@router.get(
    "/guardian/trips/{trip_id}",
    response_model=GuardianTripDetailResponse,
    responses={401: {"model": ErrorResponse}, 403: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_guardian_trip_detail(
    trip_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianTripDetailResponse:
    trip = db.query(Trip).filter(Trip.id == trip_id).first()
    if trip is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="行程不存在")
    ensure_guardian_has_access_to_trip(db, current_user.id, trip)

    traveler_user = db.get(User, trip.user_id)
    if traveler_user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="行程用户不存在")

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
        .filter(SOSEvent.user_id == trip.user_id, SOSEvent.trip_id == trip.id)
        .order_by(SOSEvent.created_at.desc(), SOSEvent.id.desc())
        .first()
    )
    events = get_recent_guardian_events(db, traveler_user_id=trip.user_id, trip_id=trip.id, limit=30)

    return GuardianTripDetailResponse(
        trip_id=trip.id,
        traveler_user_id=traveler_user.id,
        traveler_nickname=traveler_user.nickname,
        mode=trip.trip_type,
        status=trip.status,
        destination=trip.end_name,
        eta_minutes=compute_eta_minutes(trip),
        guardian_count=len(get_trip_guardians(db, trip.id, trip.user_id)),
        latest_location=LocationPointRead(
            lat=latest_location.lat,
            lng=latest_location.lng,
            recorded_at=latest_location.recorded_at,
        ) if latest_location is not None else None,
        route_preview=[
            LocationPointRead(lat=item.lat, lng=item.lng, recorded_at=item.recorded_at)
            for item in preview_points
        ],
        recent_events=[to_guardian_event_read(event) for event in events],
        sos_state=latest_sos.status if latest_sos is not None else "idle",
        expected_arrive_at=trip.expected_arrive_at,
    )


@router.get(
    "/guardian/sos/{sos_id}",
    response_model=GuardianSosDetailResponse,
    responses={401: {"model": ErrorResponse}, 403: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_guardian_sos_detail(
    sos_id: int,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianSosDetailResponse:
    sos = db.query(SOSEvent).filter(SOSEvent.id == sos_id).first()
    if sos is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SOS事件不存在")

    allowed = (
        db.query(GuardianLink)
        .filter(
            GuardianLink.traveler_user_id == sos.user_id,
            GuardianLink.guardian_user_id == current_user.id,
            GuardianLink.status == "accepted",
        )
        .first()
    )
    if allowed is None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="无权查看该SOS")

    traveler_user = db.get(User, sos.user_id)
    if traveler_user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SOS用户不存在")

    trip = db.query(Trip).filter(Trip.id == sos.trip_id).first() if sos.trip_id is not None else None
    events = get_recent_guardian_events(
        db,
        traveler_user_id=sos.user_id,
        trip_id=sos.trip_id if sos.trip_id is not None else None,
        limit=30,
    )
    playback_url = sos.audio_url
    if sos.audio_media_key:
        playback_url = f"{str(request.base_url).rstrip('/')}/api/v2/sos/media/{sos.audio_media_key}"

    return GuardianSosDetailResponse(
        sos_id=sos.id,
        traveler_user_id=traveler_user.id,
        traveler_nickname=traveler_user.nickname,
        trip_id=sos.trip_id,
        mode=trip.trip_type if trip is not None else None,
        status=sos.status,
        created_at=sos.created_at,
        lat=sos.lat,
        lng=sos.lng,
        media_key=sos.audio_media_key,
        audio_url=sos.audio_url,
        playback_url=playback_url,
        recent_events=[to_guardian_event_read(event) for event in events],
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
    request: Request,
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

    prefer_english = is_probably_english(payload.content, request.headers.get("Accept-Language"))
    reply, used_fallback = generate_guardian_reply(
        payload.content,
        build_trip_context(trip, prefer_english=prefer_english),
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
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChatMessageRead:
    message = create_proactive_chat_message(
        db,
        user_id=current_user.id,
        trip_id=payload.trip_id,
        trigger=payload.trigger,
        prefer_english=is_probably_english("", request.headers.get("Accept-Language")),
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
    request: Request,
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
    prefer_english = is_probably_english("", request.headers.get("Accept-Language"))
    proactive_copy = get_proactive_message(trigger_map[payload.alert_type], prefer_english=prefer_english)

    shared_payload = {
        "trip": trip_snapshot(trip),
        "guardians": [guardian_snapshot(item) for item in guardians],
        "alert_type": payload.alert_type,
        "proactive_message": proactive_copy,
        "source": "client",
    }
    if payload.lat is not None and payload.lng is not None:
        shared_payload["alert_location"] = {"lat": payload.lat, "lng": payload.lng}

    incident_key = f"{payload.alert_type}:{trip.id}:{round(payload.lat or 0.0, 4)}:{round(payload.lng or 0.0, 4)}"
    created_events = maybe_emit_guardian_alert(
        db,
        user=current_user,
        trip=trip,
        guardians=guardians,
        event_type=payload.alert_type,
        incident_key=incident_key,
        payload=shared_payload,
        source="client",
    )

    proactive_message = None
    if created_events:
        proactive_message = create_proactive_chat_message(
            db,
            user_id=current_user.id,
            trip_id=trip.id,
            trigger=trigger_map[payload.alert_type],
            prefer_english=prefer_english,
        )
    db.commit()
    if proactive_message is not None:
        db.refresh(proactive_message)

    return TripAlertResponse(
        trip_id=trip.id,
        alert_type=payload.alert_type,
        guardian_count=len(guardians),
        proactive_message=proactive_message.content if proactive_message is not None else proactive_copy,
        message_id=proactive_message.id if proactive_message is not None else None,
    )


@router.post(
    "/sos",
    response_model=V2SosResponse,
    responses={401: {"model": ErrorResponse}},
)
def create_v2_sos(
    payload: V2SosRequest,
    request: Request,
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

    duplicate_sos = find_recent_duplicate_sos(
        db,
        user_id=current_user.id,
        trip_id=trip.id if trip is not None else None,
        lat=payload.lat,
        lng=payload.lng,
        media_key=payload.audio_media_key or payload.media_key or extract_media_key(payload.audio_url),
        audio_url=payload.audio_url,
    )
    if duplicate_sos is not None:
        return V2SosResponse(
            status=trip.status if trip is not None else "emergency",
            sos_id=duplicate_sos.id,
            linked_trip_id=trip.id if trip is not None else None,
            guardian_count=len(guardians),
            message="SOS求助已记录",
            audio_url=duplicate_sos.audio_url or payload.audio_url,
            media_key=duplicate_sos.audio_media_key or payload.media_key or payload.audio_media_key,
            audio_media_key=duplicate_sos.audio_media_key or payload.media_key or payload.audio_media_key,
        )

    media_reference: ResolvedMediaReference = resolve_media_reference(
        base_url=str(request.base_url),
        audio_url=payload.audio_url,
        media_key=payload.audio_media_key or payload.media_key,
    )

    sos = SOSEvent(
        user_id=current_user.id,
        trip_id=trip.id if trip is not None else None,
        lat=payload.lat,
        lng=payload.lng,
        audio_url=media_reference.audio_url or payload.audio_url,
        audio_media_key=media_reference.media_key,
        audio_bucket=media_reference.bucket,
        audio_content_type=media_reference.content_type,
        audio_size_bytes=media_reference.size_bytes,
        audio_storage_mode=media_reference.storage_mode,
        audio_uploaded_at=now_utc() if media_reference.media_key else None,
        status="active",
    )
    db.add(sos)
    db.flush()

    title = "SOS已触发"
    body = f"{current_user.nickname}已触发SOS，正在发送实时位置与行程信息"
    shared_payload = {
        "sos_location": {"lat": payload.lat, "lng": payload.lng},
        "audio_url": sos.audio_url,
        "audio_media_key": sos.audio_media_key,
        "audio_storage_mode": sos.audio_storage_mode,
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
        audio_url=sos.audio_url,
        media_key=sos.audio_media_key,
        audio_media_key=sos.audio_media_key,
    )
