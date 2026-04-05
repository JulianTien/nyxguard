from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Optional

from sqlalchemy.orm import Session

from config import get_settings
from models import Guardian, Location, NotificationEvent, SOSEvent, Trip, TripGuardian, User
from notification_events import create_notification_event, deserialize_payload, guardian_snapshot, trip_snapshot


ARRIVAL_RADIUS_METERS = 100.0


@dataclass(slots=True)
class WatchdogIncident:
    event_type: str
    title: str
    body: str
    payload: dict[str, object]
    incident_key: str


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def normalize_datetime(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def distance_meters(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    earth_radius = 6371000.0
    lat1_rad = math.radians(lat1)
    lat2_rad = math.radians(lat2)
    delta_lat = math.radians(lat2 - lat1)
    delta_lng = math.radians(lng2 - lng1)
    a = (
        math.sin(delta_lat / 2) ** 2
        + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(delta_lng / 2) ** 2
    )
    return 2 * earth_radius * math.asin(min(1.0, math.sqrt(a)))


def project_to_xy(lat: float, lng: float, origin_lat: float, origin_lng: float) -> tuple[float, float]:
    meters_per_lat = 111320.0
    meters_per_lng = 111320.0 * math.cos(math.radians(origin_lat))
    x = (lng - origin_lng) * meters_per_lng
    y = (lat - origin_lat) * meters_per_lat
    return x, y


def point_to_segment_distance_meters(
    lat: float,
    lng: float,
    start_lat: float,
    start_lng: float,
    end_lat: float,
    end_lng: float,
) -> float:
    start_x, start_y = project_to_xy(start_lat, start_lng, start_lat, start_lng)
    end_x, end_y = project_to_xy(end_lat, end_lng, start_lat, start_lng)
    point_x, point_y = project_to_xy(lat, lng, start_lat, start_lng)
    seg_x = end_x - start_x
    seg_y = end_y - start_y
    seg_len_sq = seg_x * seg_x + seg_y * seg_y
    if seg_len_sq == 0:
        return math.hypot(point_x - start_x, point_y - start_y)
    t = max(0.0, min(1.0, ((point_x - start_x) * seg_x + (point_y - start_y) * seg_y) / seg_len_sq))
    closest_x = start_x + t * seg_x
    closest_y = start_y + t * seg_y
    return math.hypot(point_x - closest_x, point_y - closest_y)


def get_trip_guardians(db: Session, trip_id: int, user_id: int) -> list[Guardian]:
    return (
        db.query(Guardian)
        .join(TripGuardian, TripGuardian.guardian_id == Guardian.id)
        .filter(TripGuardian.trip_id == trip_id, Guardian.user_id == user_id)
        .order_by(TripGuardian.created_at.asc(), Guardian.id.asc())
        .all()
    )


def get_latest_location(db: Session, trip_id: int) -> Optional[Location]:
    return (
        db.query(Location)
        .filter(Location.trip_id == trip_id)
        .order_by(Location.recorded_at.desc(), Location.id.desc())
        .first()
    )


def build_incident_key(
    *,
    event_type: str,
    trip: Trip,
    location: Optional[Location] = None,
    suffix: Optional[str] = None,
) -> str:
    if location is not None:
        lat_bucket = round(location.lat, 4)
        lng_bucket = round(location.lng, 4)
        return f"{event_type}:{trip.id}:{lat_bucket}:{lng_bucket}:{suffix or 'location'}"
    expected = trip.expected_arrive_at.isoformat() if trip.expected_arrive_at else "no-eta"
    return f"{event_type}:{trip.id}:{expected}:{suffix or 'generic'}"


def recent_duplicate_event(
    db: Session,
    *,
    user_id: int,
    guardian_id: int,
    trip_id: int,
    event_type: str,
    incident_key: str,
) -> Optional[NotificationEvent]:
    settings = get_settings()
    cutoff = now_utc() - timedelta(seconds=settings.watchdog_alert_dedup_seconds)
    candidate = (
        db.query(NotificationEvent)
        .filter(
            NotificationEvent.user_id == user_id,
            NotificationEvent.trip_id == trip_id,
            NotificationEvent.guardian_id == guardian_id,
            NotificationEvent.event_type == event_type,
            NotificationEvent.created_at >= cutoff,
        )
        .order_by(NotificationEvent.created_at.desc(), NotificationEvent.id.desc())
        .first()
    )
    if candidate is None:
        return None
    payload = deserialize_payload(candidate.payload_json)
    if payload.get("incident_key") == incident_key:
        return candidate
    return None


def _trip_alert_copy(user: User, event_type: str) -> tuple[str, str]:
    if event_type == "walk_timeout":
        return "步行超时提醒", f"{user.nickname}的步行行程已超时，请留意"
    if event_type == "walk_deviation":
        return "步行偏离提醒", f"{user.nickname}的步行路线已偏离，请关注"
    if event_type == "ride_deviation":
        return "乘车偏离提醒", f"{user.nickname}的乘车路线已偏离预定路线，请关注"
    if event_type == "location_loss":
        return "定位中断提醒", f"{user.nickname}的行程定位暂时中断，请留意"
    return "守护提醒", f"{user.nickname}的行程出现需要关注的情况"


def maybe_emit_guardian_alert(
    db: Session,
    *,
    user: User,
    trip: Trip,
    guardians: list[Guardian],
    event_type: str,
    incident_key: str,
    payload: dict[str, object],
    source: str,
) -> list[NotificationEvent]:
    title, body = _trip_alert_copy(user, event_type)
    events: list[NotificationEvent] = []
    for guardian in guardians:
        if recent_duplicate_event(
            db,
            user_id=user.id,
            guardian_id=guardian.id,
            trip_id=trip.id,
            event_type=event_type,
            incident_key=incident_key,
        ) is not None:
            continue

        event_payload = {
            **payload,
            "incident_key": incident_key,
            "source": source,
            "trip": trip_snapshot(trip),
            "guardians": [guardian_snapshot(item) for item in guardians],
            "guardian": guardian_snapshot(guardian),
        }
        events.append(
            create_notification_event(
                db,
                user_id=user.id,
                trip_id=trip.id,
                guardian_id=guardian.id,
                event_type=event_type,
                title=title,
                body=body,
                payload=event_payload,
            )
        )
    return events


def evaluate_trip_watchdog(
    db: Session,
    *,
    trip: Trip,
    user: User,
    now: Optional[datetime] = None,
) -> list[NotificationEvent]:
    settings = get_settings()
    current_time = normalize_datetime(now or now_utc())
    if trip.status != "active":
        return []

    guardians = get_trip_guardians(db, trip.id, user.id)
    if not guardians:
        return []

    latest_location = get_latest_location(db, trip.id)
    incidents: list[NotificationEvent] = []
    location_loss_threshold = (
        settings.watchdog_location_loss_walk_seconds
        if trip.trip_type == "walk"
        else settings.watchdog_location_loss_ride_seconds
    )

    if latest_location is not None:
        latest_location_time = normalize_datetime(latest_location.recorded_at)
        age_seconds = (current_time - latest_location_time).total_seconds()
        if age_seconds >= location_loss_threshold:
            incidents.extend(
                maybe_emit_guardian_alert(
                    db,
                    user=user,
                    trip=trip,
                    guardians=guardians,
                    event_type="location_loss",
                    incident_key=build_incident_key(
                        event_type="location_loss",
                        trip=trip,
                        location=latest_location,
                        suffix=str(int(age_seconds // location_loss_threshold)),
                    ),
                    payload={
                        "location": {
                            "lat": latest_location.lat,
                            "lng": latest_location.lng,
                            "recorded_at": latest_location.recorded_at,
                        },
                        "age_seconds": int(age_seconds),
                    },
                    source="watchdog",
                )
            )
        if trip.trip_type == "walk":
            expected_arrive_at = trip.expected_arrive_at
            if expected_arrive_at is not None and current_time > normalize_datetime(expected_arrive_at) + timedelta(
                minutes=settings.watchdog_timeout_grace_minutes
            ):
                incidents.extend(
                    maybe_emit_guardian_alert(
                        db,
                        user=user,
                        trip=trip,
                        guardians=guardians,
                        event_type="walk_timeout",
                        incident_key=build_incident_key(
                            event_type="walk_timeout",
                            trip=trip,
                            suffix=normalize_datetime(expected_arrive_at).isoformat(),
                        ),
                        payload={
                            "expected_arrive_at": expected_arrive_at,
                            "timeout_grace_minutes": settings.watchdog_timeout_grace_minutes,
                        },
                        source="watchdog",
                    )
                )

        route_distance = point_to_segment_distance_meters(
            latest_location.lat,
            latest_location.lng,
            trip.start_lat,
            trip.start_lng,
            trip.end_lat,
            trip.end_lng,
        )
        arrival_distance = distance_meters(latest_location.lat, latest_location.lng, trip.end_lat, trip.end_lng)
        deviation_threshold = 200.0 if trip.trip_type == "walk" else 500.0
        if arrival_distance > ARRIVAL_RADIUS_METERS and route_distance >= deviation_threshold:
            incident_type = "walk_deviation" if trip.trip_type == "walk" else "ride_deviation"
            incidents.extend(
                maybe_emit_guardian_alert(
                    db,
                    user=user,
                    trip=trip,
                    guardians=guardians,
                    event_type=incident_type,
                    incident_key=build_incident_key(
                        event_type=incident_type,
                        trip=trip,
                        location=latest_location,
                        suffix=f"{int(round(route_distance))}",
                    ),
                    payload={
                        "location": {
                            "lat": latest_location.lat,
                            "lng": latest_location.lng,
                            "recorded_at": latest_location.recorded_at,
                        },
                        "route_distance_meters": round(route_distance, 2),
                        "arrival_distance_meters": round(arrival_distance, 2),
                    },
                    source="watchdog",
                )
            )
    else:
        age_seconds = (current_time - normalize_datetime(trip.created_at)).total_seconds()
        if age_seconds >= location_loss_threshold:
            incidents.extend(
                maybe_emit_guardian_alert(
                    db,
                    user=user,
                    trip=trip,
                    guardians=guardians,
                    event_type="location_loss",
                    incident_key=build_incident_key(
                        event_type="location_loss",
                        trip=trip,
                        suffix="no-location",
                    ),
                    payload={
                        "age_seconds": int(age_seconds),
                        "reason": "no_location_received",
                        "location_loss_threshold": location_loss_threshold,
                    },
                    source="watchdog",
                )
            )
        if trip.trip_type == "walk":
            expected_arrive_at = trip.expected_arrive_at
            if expected_arrive_at is not None and current_time > normalize_datetime(expected_arrive_at) + timedelta(
                minutes=settings.watchdog_timeout_grace_minutes
            ):
                incidents.extend(
                    maybe_emit_guardian_alert(
                        db,
                        user=user,
                        trip=trip,
                        guardians=guardians,
                        event_type="walk_timeout",
                        incident_key=build_incident_key(
                            event_type="walk_timeout",
                            trip=trip,
                            suffix=normalize_datetime(expected_arrive_at).isoformat(),
                        ),
                        payload={
                            "expected_arrive_at": expected_arrive_at,
                            "timeout_grace_minutes": settings.watchdog_timeout_grace_minutes,
                        },
                        source="watchdog",
                    )
                )

    return incidents


def evaluate_all_watchdogs(db: Session, *, now: Optional[datetime] = None) -> list[NotificationEvent]:
    active_trips = (
        db.query(Trip)
        .filter(Trip.status == "active")
        .order_by(Trip.created_at.asc(), Trip.id.asc())
        .all()
    )
    events: list[NotificationEvent] = []
    for trip in active_trips:
        user = db.query(User).filter(User.id == trip.user_id).first()
        if user is None:
            continue
        events.extend(evaluate_trip_watchdog(db, trip=trip, user=user, now=now))
    return events


def find_recent_duplicate_sos(
    db: Session,
    *,
    user_id: int,
    trip_id: Optional[int],
    lat: float,
    lng: float,
    media_key: Optional[str],
    audio_url: Optional[str],
    within_seconds: Optional[int] = None,
) -> Optional[SOSEvent]:
    settings = get_settings()
    cutoff = now_utc() - timedelta(seconds=within_seconds or settings.sos_duplicate_window_seconds)
    query = (
        db.query(SOSEvent)
        .filter(
            SOSEvent.user_id == user_id,
            SOSEvent.status == "active",
            SOSEvent.created_at >= cutoff,
        )
        .order_by(SOSEvent.created_at.desc(), SOSEvent.id.desc())
    )
    if trip_id is None:
        query = query.filter(SOSEvent.trip_id.is_(None))
    else:
        query = query.filter(SOSEvent.trip_id == trip_id)

    candidate = query.first()
    if candidate is None:
        return None

    candidate_media_key = candidate.audio_media_key or candidate.audio_url
    request_media_key = media_key or audio_url
    same_location = distance_meters(candidate.lat, candidate.lng, lat, lng) <= 10.0
    same_media = (
        candidate.audio_media_key == media_key
        or candidate.audio_url == audio_url
        or candidate_media_key == request_media_key
        or not candidate_media_key
        or not request_media_key
    )
    return candidate if same_location and same_media else None
