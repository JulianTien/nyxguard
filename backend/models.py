from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship as orm_relationship

from database import Base


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "ng_user"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    nickname: Mapped[str] = mapped_column(String(50), nullable=False)
    phone: Mapped[Optional[str]] = mapped_column(String(20), unique=True)
    email: Mapped[Optional[str]] = mapped_column(String(100), unique=True)
    password_hash: Mapped[str] = mapped_column(String(128), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=True
    )
    avatar_url: Mapped[Optional[str]] = mapped_column(String(500))
    emergency_phone: Mapped[Optional[str]] = mapped_column(String(20))

    guardians: Mapped[list["Guardian"]] = orm_relationship(back_populates="user", cascade="all, delete-orphan")
    trips: Mapped[list["Trip"]] = orm_relationship(back_populates="user")
    chat_messages: Mapped[list["ChatMessage"]] = orm_relationship(back_populates="user")
    notification_events: Mapped[list["NotificationEvent"]] = orm_relationship(back_populates="user", cascade="all, delete-orphan")
    push_tokens: Mapped[list["PushToken"]] = orm_relationship(back_populates="user", cascade="all, delete-orphan")


class Guardian(Base):
    __tablename__ = "ng_guardian"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id"), nullable=False, index=True)
    nickname: Mapped[str] = mapped_column(String(50), nullable=False)
    phone: Mapped[str] = mapped_column(String(20), nullable=False)
    relationship: Mapped[str] = mapped_column(String(20), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)

    user: Mapped["User"] = orm_relationship(back_populates="guardians")
    trip_links: Mapped[list["TripGuardian"]] = orm_relationship(back_populates="guardian", cascade="all, delete-orphan")


class GuardianLink(Base):
    __tablename__ = "ng_guardian_link"
    __table_args__ = (
        UniqueConstraint("traveler_user_id", "guardian_user_id", name="uq_guardian_link_traveler_guardian"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    traveler_user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id", ondelete="CASCADE"), nullable=False, index=True)
    guardian_user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id", ondelete="CASCADE"), nullable=False, index=True)
    relationship: Mapped[str] = mapped_column(String(20), nullable=False, default="朋友")
    status: Mapped[str] = mapped_column(String(20), default="pending", nullable=False)
    invited_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    accepted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))


class Trip(Base):
    __tablename__ = "ng_trip"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id"), nullable=False, index=True)
    trip_type: Mapped[str] = mapped_column(String(10), nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="active", nullable=False)
    start_lat: Mapped[float] = mapped_column(Float, nullable=False)
    start_lng: Mapped[float] = mapped_column(Float, nullable=False)
    start_name: Mapped[str] = mapped_column(String(200), default="", nullable=False)
    end_lat: Mapped[float] = mapped_column(Float, nullable=False)
    end_lng: Mapped[float] = mapped_column(Float, nullable=False)
    end_name: Mapped[str] = mapped_column(String(200), default="", nullable=False)
    plate_number: Mapped[Optional[str]] = mapped_column(String(20))
    vehicle_type: Mapped[Optional[str]] = mapped_column(String(20))
    vehicle_color: Mapped[Optional[str]] = mapped_column(String(20))
    estimated_minutes: Mapped[int] = mapped_column(Integer, default=30, nullable=False)
    expected_arrive_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    finished_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))

    user: Mapped["User"] = orm_relationship(back_populates="trips")
    locations: Mapped[list["Location"]] = orm_relationship(back_populates="trip", cascade="all, delete-orphan")
    sos_events: Mapped[list["SOSEvent"]] = orm_relationship(back_populates="trip")
    chat_messages: Mapped[list["ChatMessage"]] = orm_relationship(back_populates="trip")
    guardian_links: Mapped[list["TripGuardian"]] = orm_relationship(back_populates="trip", cascade="all, delete-orphan")
    notification_events: Mapped[list["NotificationEvent"]] = orm_relationship(back_populates="trip", cascade="all, delete-orphan")


class TripGuardian(Base):
    __tablename__ = "ng_trip_guardian"
    __table_args__ = (
        UniqueConstraint("trip_id", "guardian_id", name="uq_trip_guardian_trip_guardian"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    trip_id: Mapped[int] = mapped_column(ForeignKey("ng_trip.id", ondelete="CASCADE"), nullable=False, index=True)
    guardian_id: Mapped[int] = mapped_column(ForeignKey("ng_guardian.id", ondelete="CASCADE"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)

    trip: Mapped["Trip"] = orm_relationship(back_populates="guardian_links")
    guardian: Mapped["Guardian"] = orm_relationship(back_populates="trip_links")


class Location(Base):
    __tablename__ = "ng_location"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    trip_id: Mapped[int] = mapped_column(ForeignKey("ng_trip.id"), nullable=False, index=True)
    lat: Mapped[float] = mapped_column(Float, nullable=False)
    lng: Mapped[float] = mapped_column(Float, nullable=False)
    accuracy: Mapped[float] = mapped_column(Float, default=0, nullable=False)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)

    trip: Mapped["Trip"] = orm_relationship(back_populates="locations")


class SOSEvent(Base):
    __tablename__ = "ng_sos_event"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id"), nullable=False, index=True)
    trip_id: Mapped[Optional[int]] = mapped_column(ForeignKey("ng_trip.id"), index=True)
    lat: Mapped[float] = mapped_column(Float, nullable=False)
    lng: Mapped[float] = mapped_column(Float, nullable=False)
    audio_url: Mapped[Optional[str]] = mapped_column(String(500))
    audio_media_key: Mapped[Optional[str]] = mapped_column(String(200), index=True)
    audio_bucket: Mapped[Optional[str]] = mapped_column(String(100))
    audio_content_type: Mapped[Optional[str]] = mapped_column(String(100))
    audio_etag: Mapped[Optional[str]] = mapped_column(String(200))
    audio_size_bytes: Mapped[Optional[int]] = mapped_column(Integer)
    audio_storage_mode: Mapped[str] = mapped_column(String(20), default="legacy", nullable=False)
    audio_uploaded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    status: Mapped[str] = mapped_column(String(20), default="active", nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)

    user: Mapped["User"] = orm_relationship()
    trip: Mapped["Trip"] = orm_relationship(back_populates="sos_events")


class PushToken(Base):
    __tablename__ = "ng_push_token"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id", ondelete="CASCADE"), nullable=False, index=True)
    token: Mapped[str] = mapped_column(String(512), nullable=False, unique=True, index=True)
    platform: Mapped[str] = mapped_column(String(20), default="android", nullable=False)
    device_name: Mapped[Optional[str]] = mapped_column(String(100))
    app_version: Mapped[Optional[str]] = mapped_column(String(40))
    enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    last_seen_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=True
    )

    user: Mapped["User"] = orm_relationship(back_populates="push_tokens")


class ChatMessage(Base):
    __tablename__ = "ng_chat_message"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id"), nullable=False, index=True)
    role: Mapped[str] = mapped_column(String(20), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    trip_id: Mapped[Optional[int]] = mapped_column(ForeignKey("ng_trip.id"))
    message_type: Mapped[str] = mapped_column(String(20), default="chat", nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)

    user: Mapped["User"] = orm_relationship(back_populates="chat_messages")
    trip: Mapped[Optional["Trip"]] = orm_relationship(back_populates="chat_messages")


class NotificationEvent(Base):
    __tablename__ = "ng_notification_event"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("ng_user.id"), nullable=False, index=True)
    trip_id: Mapped[Optional[int]] = mapped_column(ForeignKey("ng_trip.id"), index=True)
    guardian_id: Mapped[Optional[int]] = mapped_column(Integer, index=True)
    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    title: Mapped[str] = mapped_column(String(120), nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    payload_json: Mapped[str] = mapped_column(Text, default="{}", nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="queued", nullable=False)
    delivery_channel: Mapped[str] = mapped_column(String(20), default="fcm", nullable=False)
    delivery_status: Mapped[str] = mapped_column(String(20), default="queued", nullable=False)
    attempt_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    delivered_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    opened_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    failure_reason: Mapped[Optional[str]] = mapped_column(String(500))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)

    user: Mapped["User"] = orm_relationship(back_populates="notification_events")
    trip: Mapped[Optional["Trip"]] = orm_relationship(back_populates="notification_events")
