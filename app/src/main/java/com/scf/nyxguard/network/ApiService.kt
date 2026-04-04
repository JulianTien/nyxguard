package com.scf.nyxguard.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

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

    @POST("api/chat")
    fun chat(@Body body: ChatRequest): Call<ChatResponse>

    @POST("api/chat/proactive")
    fun proactiveChat(@Body body: ProactiveChatRequest): Call<ChatResponse>

    @GET("api/v2/dashboard")
    fun getDashboard(): Call<DashboardResponseDto>

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
    fun getChatMessages(@retrofit2.http.Query("trip_id") tripId: Int? = null): Call<ChatHistoryDto>

    @POST("api/v2/chat/messages")
    fun createChatMessage(@Body body: V2ChatMessageRequest): Call<V2ChatMessageResponseDto>

    @POST("api/v2/chat/proactive")
    fun createProactiveMessage(@Body body: ProactiveChatRequest): Call<ChatMessageDto>

    @POST("api/v2/sos")
    fun triggerGlobalSos(@Body body: V2SosRequest): Call<V2SosResponseDto>
}
