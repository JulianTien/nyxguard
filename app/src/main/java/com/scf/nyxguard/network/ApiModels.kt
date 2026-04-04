package com.scf.nyxguard.network

data class LoginRequest(
    val account: String,
    val password: String
)

data class RegisterRequest(
    val nickname: String,
    val phone: String? = null,
    val email: String? = null,
    val password: String
)

data class UserDto(
    val id: Int,
    val nickname: String,
    val phone: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val emergency_phone: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class UpdateProfileRequest(
    val nickname: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val emergency_phone: String? = null
)

data class GuardianDto(
    val id: Int,
    val nickname: String,
    val phone: String,
    val relationship: String
)

data class MessageResponse(
    val message: String
)

data class CreateTripRequest(
    val trip_type: String,
    val start_lat: Double,
    val start_lng: Double,
    val end_lat: Double,
    val end_lng: Double,
    val start_name: String? = null,
    val end_name: String? = null,
    val plate_number: String? = null,
    val vehicle_type: String? = null,
    val vehicle_color: String? = null,
    val estimated_minutes: Int,
    val guardian_ids: List<Int>
)

data class TripSummaryResponse(
    val id: Int,
    val status: String,
    val trip_type: String,
    val created_at: String
)

data class TripDto(
    val id: Int,
    val trip_type: String,
    val status: String,
    val start_lat: Double,
    val start_lng: Double,
    val start_name: String,
    val end_lat: Double,
    val end_lng: Double,
    val end_name: String,
    val plate_number: String? = null,
    val vehicle_type: String? = null,
    val vehicle_color: String? = null,
    val estimated_minutes: Int,
    val created_at: String,
    val finished_at: String? = null,
    val guardians: List<GuardianDto> = emptyList()
)

data class LocationUploadItem(
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val recorded_at: String
)

data class UploadLocationsRequest(
    val locations: List<LocationUploadItem>
)

data class UploadLocationsResponse(
    val uploaded: Int
)

data class FinishTripResponse(
    val status: String,
    val finished_at: String? = null
)

data class SosRequest(
    val lat: Double,
    val lng: Double,
    val audio_url: String? = null
)

data class SosResponse(
    val status: String,
    val sos_id: Int,
    val message: String
)

data class ChatRequest(
    val content: String,
    val trip_id: Int? = null
)

data class ProactiveChatRequest(
    val trigger: String,
    val trip_id: Int? = null
)

data class ChatResponse(
    val reply: String,
    val message_id: Int? = null
)

data class ApiErrorResponse(
    val detail: String? = null,
    val message: String? = null
)

data class ActiveTripBriefDto(
    val id: Int,
    val mode: String,
    val status: String,
    val destination: String,
    val eta_minutes: Int,
    val guardian_count: Int
)

data class DashboardResponseDto(
    val nickname: String,
    val greeting: String,
    val guardian_count: Int,
    val active_trip_brief: ActiveTripBriefDto? = null,
    val quick_tools_state: Map<String, Any>? = null
)

data class VehicleInfoDto(
    val plate_number: String = "",
    val vehicle_type: String = "",
    val vehicle_color: String = ""
)

data class LocationPointDto(
    val lat: Double,
    val lng: Double,
    val recorded_at: String? = null
)

data class CurrentTripDto(
    val id: Int,
    val mode: String,
    val status: String,
    val destination: String,
    val eta_minutes: Int,
    val guardian_count: Int,
    val guardian_summary: String,
    val vehicle_info: VehicleInfoDto,
    val latest_location: LocationPointDto? = null,
    val route_preview: List<LocationPointDto> = emptyList(),
    val sos_state: String = "idle",
    val expected_arrive_at: String? = null
)

data class TripAlertRequest(
    val alert_type: String,
    val lat: Double? = null,
    val lng: Double? = null
)

data class TripAlertResponseDto(
    val trip_id: Int,
    val alert_type: String,
    val guardian_count: Int,
    val proactive_message: String,
    val message_id: Int? = null
)

data class ProfileSummaryDto(
    val nickname: String,
    val guardian_count: Int,
    val frequent_routes_count: Int,
    val guard_minutes_total: Int,
    val badge_days: Int,
    val settings_summary: String
)

data class ChatMessageDto(
    val id: Int,
    val role: String,
    val content: String,
    val trip_id: Int? = null,
    val message_type: String,
    val created_at: String
)

data class ChatHistoryDto(
    val messages: List<ChatMessageDto> = emptyList()
)

data class V2ChatMessageRequest(
    val content: String,
    val trip_id: Int? = null
)

data class V2ChatMessageResponseDto(
    val user_message: ChatMessageDto,
    val assistant_message: ChatMessageDto,
    val used_fallback: Boolean = false
)

data class V2SosRequest(
    val trip_id: Int? = null,
    val lat: Double,
    val lng: Double,
    val audio_url: String? = null
)

data class V2SosResponseDto(
    val status: String,
    val sos_id: Int,
    val linked_trip_id: Int? = null,
    val guardian_count: Int,
    val message: String
)
