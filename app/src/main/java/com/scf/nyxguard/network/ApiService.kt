package com.scf.nyxguard.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("api/user/profile")
    fun getProfile(): Call<UserDto>

    @PUT("api/user/profile")
    fun updateProfile(@Body body: UpdateProfileRequest): Call<UserDto>

    @GET("api/guardians")
    fun getGuardians(): Call<List<GuardianDto>>

    @POST("api/guardians")
    fun addGuardian(@Body body: Map<String, String>): Call<GuardianDto>

    @DELETE("api/guardians/{id}")
    fun deleteGuardian(@Path("id") id: Int): Call<MessageResponse>

    @POST("api/guardian-links/invite")
    fun inviteGuardianLink(@Body body: GuardianLinkInviteRequest): Call<GuardianLinkDto>

    @POST("api/guardian-links/{id}/accept")
    fun acceptGuardianLink(@Path("id") id: Int): Call<GuardianLinkDto>

    @GET("api/guardian-links")
    fun getGuardianLinks(): Call<List<GuardianLinkDto>>

    @DELETE("api/guardian-links/{id}")
    fun revokeGuardianLink(@Path("id") id: Int): Call<MessageResponse>

    @POST("api/trips")
    fun createTrip(@Body body: CreateTripRequest): Call<TripSummaryResponse>

    @GET("api/trips/{id}")
    fun getTrip(@Path("id") id: Int): Call<TripDto>

    @POST("api/trips/{id}/locations")
    fun uploadLocations(@Path("id") id: Int, @Body body: UploadLocationsRequest): Call<UploadLocationsResponse>

    @PUT("api/trips/{id}/finish")
    fun finishTrip(@Path("id") id: Int): Call<FinishTripResponse>

    @POST("api/trips/{id}/sos")
    fun triggerSOS(@Path("id") id: Int, @Body body: SosRequest): Call<SosResponse>

    @POST("api/v2/sos/media/presign")
    fun createSosMediaPresign(@Body body: SosMediaPresignRequest): Call<SosMediaPresignResponse>

    @POST("api/v2/sos/media/commit")
    fun commitSosMedia(@Body body: SosMediaCommitRequest): Call<SosMediaCommitResponse>

    @POST("api/chat")
    fun chat(@Body body: ChatRequest): Call<ChatResponse>

    @POST("api/chat/proactive")
    fun proactiveChat(@Body body: ProactiveChatRequest): Call<ChatResponse>

    @GET("api/v2/dashboard")
    fun getDashboard(): Call<DashboardResponseDto>

    @GET("api/v2/guardian/dashboard")
    fun getGuardianDashboard(): Call<GuardianDashboardDto>

    @GET("api/v2/guardian/trips/{id}")
    fun getGuardianTripDetail(@Path("id") id: Int): Call<GuardianTripDetailDto>

    @GET("api/v2/guardian/sos/{id}")
    fun getGuardianSosDetail(@Path("id") id: Int): Call<GuardianSosDetailDto>

    @GET("api/v2/trips/current")
    fun getCurrentTrip(): Call<CurrentTripDto>

    @POST("api/v2/trips/{id}/alerts")
    fun createTripAlert(
        @Path("id") id: Int,
        @Body body: TripAlertRequest
    ): Call<TripAlertResponseDto>

    @GET("api/v2/profile/summary")
    fun getProfileSummary(): Call<ProfileSummaryDto>

    @GET("api/v2/chat/messages")
    fun getChatMessages(@Query("trip_id") tripId: Int? = null): Call<ChatHistoryDto>

    @POST("api/v2/chat/messages")
    fun createChatMessage(@Body body: V2ChatMessageRequest): Call<V2ChatMessageResponseDto>

    @POST("api/v2/chat/proactive")
    fun createProactiveMessage(@Body body: ProactiveChatRequest): Call<ChatMessageDto>

    @POST("api/v2/sos")
    fun triggerGlobalSos(@Body body: V2SosRequest): Call<V2SosResponseDto>

    @GET("api/notifications/events")
    fun getNotificationEvents(@Query("trip_id") tripId: Int? = null): Call<List<NotificationEventDto>>

    @POST("api/notifications/push")
    fun pushNotification(@Body body: NotificationPushRequest): Call<NotificationEventDto>

    @POST("api/push-tokens/register")
    fun registerPushToken(@Body body: PushTokenRegisterRequest): Call<PushTokenResponse>

    @POST("api/push-tokens/deregister")
    fun deregisterPushToken(@Body body: PushTokenDeregisterRequest): Call<MessageResponse>
}
