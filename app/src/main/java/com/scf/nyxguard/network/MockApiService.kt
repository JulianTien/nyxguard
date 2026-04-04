package com.scf.nyxguard.network

import retrofit2.Call
import retrofit2.mock.BehaviorDelegate

class MockApiService(private val delegate: BehaviorDelegate<ApiService>) : ApiService {

    override fun getProfile(): Call<UserDto> {
        val response = UserDto(
            id = 1001,
            nickname = "测试用户",
            phone = "138****1234",
            email = "test@example.com",
            emergency_phone = "139****5678",
            created_at = java.time.Instant.now().minusSeconds(86_400).toString(),
            updated_at = java.time.Instant.now().toString()
        )
        return delegate.returningResponse(response).getProfile()
    }

    override fun updateProfile(body: UpdateProfileRequest): Call<UserDto> {
        val response = UserDto(
            id = 1001,
            nickname = body.nickname ?: "测试用户",
            phone = body.phone ?: "138****1234",
            email = body.email ?: "test@example.com",
            avatar_url = body.avatar_url,
            emergency_phone = body.emergency_phone ?: "139****5678",
            created_at = java.time.Instant.now().minusSeconds(86_400).toString(),
            updated_at = java.time.Instant.now().toString()
        )
        return delegate.returningResponse(response).updateProfile(body)
    }

    override fun getGuardians(): Call<List<GuardianDto>> {
        val response = listOf(
            GuardianDto(id = 1, nickname = "妈妈", phone = "138****1234", relationship = "母亲"),
            GuardianDto(id = 2, nickname = "小李", phone = "139****5678", relationship = "朋友")
        )
        return delegate.returningResponse(response).getGuardians()
    }

    override fun addGuardian(body: Map<String, String>): Call<GuardianDto> {
        val response = GuardianDto(
            id = (100..999).random(),
            nickname = body["nickname"].orEmpty(),
            phone = body["phone"].orEmpty(),
            relationship = body["relationship"].orEmpty()
        )
        return delegate.returningResponse(response).addGuardian(body)
    }

    override fun deleteGuardian(id: Int): Call<MessageResponse> {
        return delegate.returningResponse(MessageResponse("删除成功")).deleteGuardian(id)
    }

    override fun createTrip(body: CreateTripRequest): Call<TripSummaryResponse> {
        val response = TripSummaryResponse(
            id = (1000..9999).random(),
            status = "active",
            trip_type = body.trip_type,
            created_at = java.time.Instant.now().toString()
        )
        return delegate.returningResponse(response).createTrip(body)
    }

    override fun getTrip(id: Int): Call<TripDto> {
        val response = TripDto(
            id = id,
            trip_type = "walk",
            status = "active",
            start_lat = 31.2304,
            start_lng = 121.4737,
            start_name = "起点",
            end_lat = 31.2243,
            end_lng = 121.4768,
            end_name = "终点",
            estimated_minutes = 20,
            created_at = java.time.Instant.now().toString()
        )
        return delegate.returningResponse(response).getTrip(id)
    }

    override fun uploadLocations(id: Int, body: UploadLocationsRequest): Call<UploadLocationsResponse> {
        return delegate.returningResponse(UploadLocationsResponse(body.locations.size)).uploadLocations(id, body)
    }

    override fun finishTrip(id: Int): Call<FinishTripResponse> {
        val response = FinishTripResponse(
            status = "finished",
            finished_at = java.time.Instant.now().toString()
        )
        return delegate.returningResponse(response).finishTrip(id)
    }

    override fun triggerSOS(id: Int, body: SosRequest): Call<SosResponse> {
        val response = SosResponse(
            status = "emergency",
            sos_id = (1000..9999).random(),
            message = "SOS已触发"
        )
        return delegate.returningResponse(response).triggerSOS(id, body)
    }

    override fun chat(body: ChatRequest): Call<ChatResponse> {
        val response = ChatResponse(
            reply = "收到您的消息：${body.content}。我会一直陪着你的！",
            message_id = (1000..9999).random()
        )
        return delegate.returningResponse(response).chat(body)
    }

    override fun proactiveChat(body: ProactiveChatRequest): Call<ChatResponse> {
        val response = ChatResponse(
            reply = "夜深了，我会一直陪着你走到家的。",
            message_id = (1000..9999).random()
        )
        return delegate.returningResponse(response).proactiveChat(body)
    }

    override fun getDashboard(): Call<DashboardResponseDto> {
        val response = DashboardResponseDto(
            nickname = "Sarah",
            greeting = "晚上好",
            guardian_count = 2,
            active_trip_brief = ActiveTripBriefDto(
                id = 2001,
                mode = "walk",
                status = "active",
                destination = "星光国际公寓南门",
                eta_minutes = 12,
                guardian_count = 2
            )
        )
        return delegate.returningResponse(response).getDashboard()
    }

    override fun getCurrentTrip(): Call<CurrentTripDto> {
        val response = CurrentTripDto(
            id = 2001,
            mode = "walk",
            status = "active",
            destination = "星光国际公寓南门",
            eta_minutes = 12,
            guardian_count = 2,
            guardian_summary = "小李、妈妈",
            vehicle_info = VehicleInfoDto(),
            latest_location = LocationPointDto(lat = 31.2298, lng = 121.4742, recorded_at = java.time.Instant.now().toString()),
            route_preview = listOf(
                LocationPointDto(lat = 31.2304, lng = 121.4737, recorded_at = java.time.Instant.now().minusSeconds(120).toString()),
                LocationPointDto(lat = 31.2298, lng = 121.4742, recorded_at = java.time.Instant.now().toString()),
            ),
            sos_state = "idle"
        )
        return delegate.returningResponse(response).getCurrentTrip()
    }

    override fun createTripAlert(id: Int, body: TripAlertRequest): Call<TripAlertResponseDto> {
        val proactive = when (body.alert_type) {
            "walk_timeout" -> "你好像比预计晚了一些，还顺利吗？需要帮助吗？"
            "walk_deviation", "ride_deviation" -> "你好像走了不同的路，一切还好吗？"
            else -> "我在关注你当前的状态，如果需要帮助可以随时告诉我。"
        }
        val response = TripAlertResponseDto(
            trip_id = id,
            alert_type = body.alert_type,
            guardian_count = 2,
            proactive_message = proactive,
            message_id = (1000..9999).random()
        )
        return delegate.returningResponse(response).createTripAlert(id, body)
    }

    override fun getProfileSummary(): Call<ProfileSummaryDto> {
        val response = ProfileSummaryDto(
            nickname = "Sarah",
            guardian_count = 2,
            frequent_routes_count = 3,
            guard_minutes_total = 2700,
            badge_days = 32,
            settings_summary = "假来电/警报配置"
        )
        return delegate.returningResponse(response).getProfileSummary()
    }

    override fun getChatMessages(tripId: Int?): Call<ChatHistoryDto> {
        val now = java.time.Instant.now().toString()
        val response = ChatHistoryDto(
            messages = listOf(
                ChatMessageDto(1, "assistant", "晚上好，一个人走夜路吗？我在陪着你哦。", tripId, "chat", now),
                ChatMessageDto(2, "user", "是的，刚下班准备回家。", tripId, "chat", now),
                ChatMessageDto(3, "assistant", "大概还要走多久呢？你可以和我一直保持通话。", tripId, "chat", now),
            )
        )
        return delegate.returningResponse(response).getChatMessages(tripId)
    }

    override fun createChatMessage(body: V2ChatMessageRequest): Call<V2ChatMessageResponseDto> {
        val now = java.time.Instant.now().toString()
        val response = V2ChatMessageResponseDto(
            user_message = ChatMessageDto(10, "user", body.content, body.trip_id, "chat", now),
            assistant_message = ChatMessageDto(11, "assistant", "我在这里，会一直陪着你。", body.trip_id, "chat", now),
            used_fallback = true
        )
        return delegate.returningResponse(response).createChatMessage(body)
    }

    override fun createProactiveMessage(body: ProactiveChatRequest): Call<ChatMessageDto> {
        val response = ChatMessageDto(
            id = (1000..9999).random(),
            role = "assistant",
            content = "夜深了，我会一直陪着你走到家的。",
            trip_id = body.trip_id,
            message_type = "proactive",
            created_at = java.time.Instant.now().toString()
        )
        return delegate.returningResponse(response).createProactiveMessage(body)
    }

    override fun triggerGlobalSos(body: V2SosRequest): Call<V2SosResponseDto> {
        val response = V2SosResponseDto(
            status = "emergency",
            sos_id = (1000..9999).random(),
            linked_trip_id = body.trip_id,
            guardian_count = 2,
            message = "SOS求助已记录"
        )
        return delegate.returningResponse(response).triggerGlobalSos(body)
    }
}
