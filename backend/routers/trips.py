from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_db
from deps import get_current_user
from models import Guardian, Location, SOSEvent, Trip, TripGuardian, User
from notification_events import emit_trip_guardian_events
from schemas import (
    ErrorResponse,
    FinishTripResponse,
    LocationUploadRequest,
    SosRequest,
    SosResponse,
    GuardianSummary,
    TripCreateRequest,
    TripRead,
    TripSummaryResponse,
    UploadLocationsResponse,
)


router = APIRouter(prefix="/api/trips", tags=["trips"])


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def get_owned_trip(db: Session, trip_id: int, user_id: int) -> Trip:
    trip = db.query(Trip).filter(Trip.id == trip_id, Trip.user_id == user_id).first()
    if trip is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="行程不存在")
    return trip


def normalize_guardian_ids(guardian_ids: list[int]) -> list[int]:
    seen: set[int] = set()
    normalized: list[int] = []
    for guardian_id in guardian_ids:
        if guardian_id not in seen:
            seen.add(guardian_id)
            normalized.append(guardian_id)
    return normalized


def find_recent_duplicate_sos(
    db: Session,
    *,
    user_id: int,
    trip_id: int,
    lat: float,
    lng: float,
    audio_url: str | None,
    within_seconds: int = 20,
) -> SOSEvent | None:
    cutoff = utc_now() - timedelta(seconds=within_seconds)
    candidate = (
        db.query(SOSEvent)
        .filter(
            SOSEvent.user_id == user_id,
            SOSEvent.trip_id == trip_id,
            SOSEvent.status == "active",
            SOSEvent.created_at >= cutoff,
        )
        .order_by(SOSEvent.created_at.desc(), SOSEvent.id.desc())
        .first()
    )
    if candidate is None:
        return None

    same_location = abs(candidate.lat - lat) <= 0.00001 and abs(candidate.lng - lng) <= 0.00001
    same_audio = candidate.audio_url == audio_url or not candidate.audio_url or not audio_url
    return candidate if same_location and same_audio else None


@router.post(
    "",
    response_model=TripSummaryResponse,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}},
)
def create_trip(
    payload: TripCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> TripSummaryResponse:
    guardian_ids = normalize_guardian_ids(payload.guardian_ids)
    if not guardian_ids:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="至少选择1名守护者")

    guardians = (
        db.query(Guardian)
        .filter(Guardian.user_id == current_user.id, Guardian.id.in_(guardian_ids))
        .order_by(Guardian.id.asc())
        .all()
    )
    guardian_map = {guardian.id: guardian for guardian in guardians}
    if len(guardian_map) != len(guardian_ids):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="守护者选择无效")

    trip = Trip(
        user_id=current_user.id,
        trip_type=payload.trip_type,
        start_lat=payload.start_lat,
        start_lng=payload.start_lng,
        start_name=payload.start_name,
        end_lat=payload.end_lat,
        end_lng=payload.end_lng,
        end_name=payload.end_name,
        plate_number=payload.plate_number,
        vehicle_type=payload.vehicle_type,
        vehicle_color=payload.vehicle_color,
        estimated_minutes=payload.estimated_minutes,
        expected_arrive_at=utc_now() + timedelta(minutes=payload.estimated_minutes),
    )
    db.add(trip)
    db.flush()

    trip_guardians = [
        TripGuardian(trip_id=trip.id, guardian_id=guardian_id)
        for guardian_id in guardian_ids
    ]
    for link in trip_guardians:
        db.add(link)

    selected_guardians = [guardian_map[guardian_id] for guardian_id in guardian_ids]
    emit_trip_guardian_events(
        db,
        user=current_user,
        trip=trip,
        guardians=selected_guardians,
        event_type="trip_started",
        extra_payload={
            "selected_guardian_ids": guardian_ids,
            "selected_guardians": [
                {
                    "id": guardian.id,
                    "nickname": guardian.nickname,
                    "phone": guardian.phone,
                    "relationship": guardian.relationship,
                }
                for guardian in selected_guardians
            ],
        },
    )

    db.commit()
    db.refresh(trip)
    return TripSummaryResponse(
        id=trip.id,
        status=trip.status,
        trip_type=trip.trip_type,
        created_at=trip.created_at,
        expected_arrive_at=trip.expected_arrive_at,
    )


@router.get(
    "/{trip_id}",
    response_model=TripRead,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_trip(
    trip_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> TripRead:
    trip = get_owned_trip(db, trip_id, current_user.id)
    guardians = (
        db.query(Guardian)
        .join(TripGuardian, TripGuardian.guardian_id == Guardian.id)
        .filter(TripGuardian.trip_id == trip.id, Guardian.user_id == current_user.id)
        .order_by(TripGuardian.created_at.asc(), Guardian.id.asc())
        .all()
    )
    return TripRead(
        id=trip.id,
        trip_type=trip.trip_type,
        status=trip.status,
        start_lat=trip.start_lat,
        start_lng=trip.start_lng,
        start_name=trip.start_name,
        end_lat=trip.end_lat,
        end_lng=trip.end_lng,
        end_name=trip.end_name,
        plate_number=trip.plate_number,
        vehicle_type=trip.vehicle_type,
        vehicle_color=trip.vehicle_color,
        estimated_minutes=trip.estimated_minutes,
        expected_arrive_at=trip.expected_arrive_at,
        created_at=trip.created_at,
        finished_at=trip.finished_at,
        guardians=[
            GuardianSummary(
                id=guardian.id,
                nickname=guardian.nickname,
                phone=guardian.phone,
                relationship=guardian.relationship,
            )
            for guardian in guardians
        ],
    )


@router.post(
    "/{trip_id}/locations",
    response_model=UploadLocationsResponse,
    responses={400: {"model": ErrorResponse}, 401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def upload_locations(
    trip_id: int,
    payload: LocationUploadRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> UploadLocationsResponse:
    trip = get_owned_trip(db, trip_id, current_user.id)
    if trip.status == "finished":
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="行程已结束")

    for point in payload.locations:
        db.add(
            Location(
                trip_id=trip.id,
                lat=point.lat,
                lng=point.lng,
                accuracy=point.accuracy,
                recorded_at=point.recorded_at or utc_now(),
            )
        )
    db.commit()
    return UploadLocationsResponse(uploaded=len(payload.locations))


@router.put(
    "/{trip_id}/finish",
    response_model=FinishTripResponse,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def finish_trip(
    trip_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> FinishTripResponse:
    trip = get_owned_trip(db, trip_id, current_user.id)
    trip.status = "finished"
    trip.finished_at = utc_now()
    db.add(trip)
    guardians = (
        db.query(Guardian)
        .join(TripGuardian, TripGuardian.guardian_id == Guardian.id)
        .filter(TripGuardian.trip_id == trip.id, Guardian.user_id == current_user.id)
        .order_by(TripGuardian.created_at.asc(), Guardian.id.asc())
        .all()
    )
    emit_trip_guardian_events(
        db,
        user=current_user,
        trip=trip,
        guardians=guardians,
        event_type="trip_finished",
        extra_payload={"finished_at": trip.finished_at},
    )
    db.commit()
    db.refresh(trip)
    return FinishTripResponse(status=trip.status, finished_at=trip.finished_at)


@router.post(
    "/{trip_id}/sos",
    response_model=SosResponse,
    responses={401: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def trigger_sos(
    trip_id: int,
    payload: SosRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> SosResponse:
    trip = get_owned_trip(db, trip_id, current_user.id)
    trip.status = "emergency"
    guardians = (
        db.query(Guardian)
        .join(TripGuardian, TripGuardian.guardian_id == Guardian.id)
        .filter(TripGuardian.trip_id == trip.id, Guardian.user_id == current_user.id)
        .order_by(TripGuardian.created_at.asc(), Guardian.id.asc())
        .all()
    )
    duplicate_sos = find_recent_duplicate_sos(
        db,
        user_id=current_user.id,
        trip_id=trip.id,
        lat=payload.lat,
        lng=payload.lng,
        audio_url=payload.audio_url,
    )
    if duplicate_sos is not None:
        return SosResponse(status=trip.status, sos_id=duplicate_sos.id, message="SOS求助已记录")

    sos = SOSEvent(
        user_id=current_user.id,
        trip_id=trip.id,
        lat=payload.lat,
        lng=payload.lng,
        audio_url=payload.audio_url,
        status="active",
    )
    db.add(trip)
    db.add(sos)
    emit_trip_guardian_events(
        db,
        user=current_user,
        trip=trip,
        guardians=guardians,
        event_type="sos_triggered",
        extra_payload={
            "sos_location": {"lat": payload.lat, "lng": payload.lng},
            "audio_url": payload.audio_url,
        },
    )
    db.commit()
    db.refresh(sos)
    return SosResponse(status=trip.status, sos_id=sos.id, message="SOS求助已记录")
