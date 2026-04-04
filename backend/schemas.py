from __future__ import annotations

from datetime import datetime
from typing import Any, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator


class TrimmedModel(BaseModel):
    @field_validator("*", mode="before")
    @classmethod
    def strip_strings(cls, value):
        if isinstance(value, str):
            return value.strip()
        return value


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    nickname: str
    phone: Optional[str] = None
    email: Optional[str] = None
    avatar_url: Optional[str] = None
    emergency_phone: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None


class AuthResponse(BaseModel):
    token: str
    user: UserRead


class RegisterRequest(TrimmedModel):
    nickname: str
    phone: Optional[str] = None
    email: Optional[str] = None
    password: str


class LoginRequest(TrimmedModel):
    account: str
    password: str


class UpdateProfileRequest(TrimmedModel):
    nickname: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    avatar_url: Optional[str] = None
    emergency_phone: Optional[str] = None


class GuardianCreateRequest(TrimmedModel):
    nickname: str
    phone: str
    relationship: str = "朋友"


class GuardianRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    nickname: str
    phone: str
    relationship: str


class GuardianSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    nickname: str
    phone: str
    relationship: str


class MessageResponse(BaseModel):
    message: str


class TripCreateRequest(TrimmedModel):
    trip_type: Literal["walk", "ride"] = "walk"
    start_lat: float
    start_lng: float
    end_lat: float
    end_lng: float
    start_name: str = ""
    end_name: str = ""
    plate_number: Optional[str] = None
    vehicle_type: Optional[str] = None
    vehicle_color: Optional[str] = None
    estimated_minutes: int = 30
    guardian_ids: list[int] = Field(default_factory=list)


class TripSummaryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    status: str
    trip_type: str
    created_at: datetime
    expected_arrive_at: Optional[datetime] = None


class TripRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    trip_type: str
    status: str
    start_lat: float
    start_lng: float
    start_name: str
    end_lat: float
    end_lng: float
    end_name: str
    plate_number: Optional[str] = None
    vehicle_type: Optional[str] = None
    vehicle_color: Optional[str] = None
    estimated_minutes: int
    expected_arrive_at: Optional[datetime] = None
    created_at: datetime
    finished_at: Optional[datetime] = None
    guardians: list[GuardianSummary] = Field(default_factory=list)


class LocationUploadItem(BaseModel):
    lat: float
    lng: float
    accuracy: float = 0
    recorded_at: Optional[datetime] = None


class LocationUploadRequest(BaseModel):
    locations: list[LocationUploadItem]


class UploadLocationsResponse(BaseModel):
    uploaded: int


class FinishTripResponse(BaseModel):
    status: str
    finished_at: Optional[datetime] = None


class SosRequest(TrimmedModel):
    lat: float
    lng: float
    audio_url: Optional[str] = None


class SosResponse(BaseModel):
    status: str
    sos_id: int
    message: str


class ChatRequest(TrimmedModel):
    content: str
    trip_id: Optional[int] = None


class ProactiveChatRequest(TrimmedModel):
    trigger: str = "start"
    trip_id: Optional[int] = None


class ChatResponse(BaseModel):
    reply: str
    message_id: Optional[int] = None


class ChatMessageRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    role: str
    content: str
    trip_id: Optional[int] = None
    message_type: str
    created_at: datetime


class ChatHistoryResponse(BaseModel):
    messages: list[ChatMessageRead] = Field(default_factory=list)


class ChatMessageCreateRequest(TrimmedModel):
    content: str
    trip_id: Optional[int] = None


class V2ChatMessageResponse(BaseModel):
    user_message: ChatMessageRead
    assistant_message: ChatMessageRead
    used_fallback: bool = False


class VehicleInfoRead(BaseModel):
    plate_number: str = ""
    vehicle_type: str = ""
    vehicle_color: str = ""


class LocationPointRead(BaseModel):
    lat: float
    lng: float
    recorded_at: Optional[datetime] = None


class ActiveTripBriefRead(BaseModel):
    id: int
    mode: str
    status: str
    destination: str
    eta_minutes: int
    guardian_count: int


class DashboardResponse(BaseModel):
    nickname: str
    greeting: str
    guardian_count: int
    active_trip_brief: Optional[ActiveTripBriefRead] = None
    quick_tools_state: dict[str, Any] = Field(default_factory=dict)


class CurrentTripResponse(BaseModel):
    id: int
    mode: str
    status: str
    destination: str
    eta_minutes: int
    guardian_count: int
    guardian_summary: str
    vehicle_info: VehicleInfoRead
    latest_location: Optional[LocationPointRead] = None
    route_preview: list[LocationPointRead] = Field(default_factory=list)
    sos_state: str = "idle"
    expected_arrive_at: Optional[datetime] = None


class TripAlertRequest(TrimmedModel):
    alert_type: Literal["walk_timeout", "walk_deviation", "ride_deviation"]
    lat: Optional[float] = None
    lng: Optional[float] = None


class TripAlertResponse(BaseModel):
    trip_id: int
    alert_type: str
    guardian_count: int
    proactive_message: str
    message_id: Optional[int] = None


class ProfileSummaryResponse(BaseModel):
    nickname: str
    guardian_count: int
    frequent_routes_count: int
    guard_minutes_total: int
    badge_days: int
    settings_summary: str


class V2SosRequest(TrimmedModel):
    trip_id: Optional[int] = None
    lat: float
    lng: float
    audio_url: Optional[str] = None


class V2SosResponse(BaseModel):
    status: str
    sos_id: int
    linked_trip_id: Optional[int] = None
    guardian_count: int
    message: str


class NotificationPushRequest(TrimmedModel):
    event_type: str
    title: str
    body: str
    trip_id: Optional[int] = None
    guardian_id: Optional[int] = None
    payload: dict[str, Any] = Field(default_factory=dict)
    status: str = "queued"


class NotificationEventRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    event_type: str
    title: str
    body: str
    payload: dict[str, Any] = Field(default_factory=dict)
    status: str
    user_id: int
    trip_id: Optional[int] = None
    guardian_id: Optional[int] = None
    created_at: datetime


class ErrorResponse(BaseModel):
    detail: str
