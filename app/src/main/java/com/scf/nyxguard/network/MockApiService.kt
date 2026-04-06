package com.scf.nyxguard.network

import java.util.Calendar
import retrofit2.Call
import retrofit2.mock.BehaviorDelegate

class MockApiService(private val delegate: BehaviorDelegate<ApiService>) : ApiService {

    private fun mockAssistantReply(userMessage: String): String {
        val message = userMessage.lowercase()
        return when {
            listOf("scared", "afraid", "unsafe", "follow", "following", "worried").any { it in message } ->
                "I'm with you. Move toward a brighter spot or a store if you can, and tell me what is happening around you."
            listOf("home", "arrive", "almost there", "close").any { it in message } ->
                "You're close. Stay on the main route, keep your phone handy, and let me know once you're at the door."
            listOf("ride", "taxi", "cab", "driver", "car").any { it in message } ->
                "Got it. Keep an eye on the route, and if you want, send me the car color or plate details so we can stay focused."
            listOf("walk", "walking", "street", "road").any { it in message } ->
                "Thanks for the update. Stay where the lighting is better, and keep me posted on the next landmark you pass."
            listOf("thanks", "thank you").any { it in message } ->
                "Anytime. I'm staying with you the whole way, so keep talking to me if you want the walk to feel shorter."
            else ->
                "I'm here with you. Keep a steady pace, stay aware of your surroundings, and tell me anything you notice."
        }
    }

    private fun mockProactiveReply(): String {
        return "Quick check-in: how are things around you right now? If the street feels too quiet, move toward a brighter route and stay with me here."
    }

    private fun mockGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

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

    override fun inviteGuardianLink(body: GuardianLinkInviteRequest): Call<GuardianLinkDto> {
        val traveler = GuardianLinkUserDto(
            id = 1001,
            nickname = "测试用户",
            phone = "13900001111"
        )
        val guardian = GuardianLinkUserDto(
            id = body.guardian_user_id ?: 2001,
            nickname = "守护者",
            phone = body.guardian_phone ?: body.guardian_account
        )
        val link = GuardianLinkDto(
            id = (100..999).random(),
            traveler_user = traveler,
            guardian_user = guardian,
            relationship = body.relationship,
            status = "pending",
            invited_at = java.time.Instant.now().toString(),
            current_role = "traveler"
        )
        return delegate.returningResponse(link).inviteGuardianLink(body)
    }

    override fun acceptGuardianLink(id: Int): Call<GuardianLinkDto> {
        val traveler = GuardianLinkUserDto(
            id = 1001,
            nickname = "测试用户",
            phone = "13900001111"
        )
        val guardian = GuardianLinkUserDto(
            id = 2001,
            nickname = "守护者",
            phone = "138****1234"
        )
        val link = GuardianLinkDto(
            id = id,
            traveler_user = traveler,
            guardian_user = guardian,
            relationship = "朋友",
            status = "accepted",
            invited_at = java.time.Instant.now().minusSeconds(600).toString(),
            accepted_at = java.time.Instant.now().toString(),
            current_role = "guardian"
        )
        return delegate.returningResponse(link).acceptGuardianLink(id)
    }

    override fun getGuardianLinks(): Call<List<GuardianLinkDto>> {
        val traveler = GuardianLinkUserDto(
            id = 1001,
            nickname = "测试用户",
            phone = "13900001111"
        )
        val guardian = GuardianLinkUserDto(
            id = 2001,
            nickname = "妈妈",
            phone = "138****1234"
        )
        val response = listOf(
            GuardianLinkDto(
                id = 1,
                traveler_user = traveler,
                guardian_user = guardian,
                relationship = "母亲",
                status = "accepted",
                invited_at = java.time.Instant.now().minusSeconds(86_400).toString(),
                accepted_at = java.time.Instant.now().minusSeconds(43_200).toString(),
                current_role = "traveler"
            )
        )
        return delegate.returningResponse(response).getGuardianLinks()
    }

    override fun revokeGuardianLink(id: Int): Call<MessageResponse> {
        return delegate.returningResponse(MessageResponse("解绑成功")).revokeGuardianLink(id)
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
            message = "SOS已触发",
            media_key = body.media_key ?: "s3://nyxguard/mock/$id",
            audio_url = body.audio_url
        )
        return delegate.returningResponse(response).triggerSOS(id, body)
    }

    override fun createSosMediaPresign(body: SosMediaPresignRequest): Call<SosMediaPresignResponse> {
        val response = SosMediaPresignResponse(
            media_key = "mock-media-${(1000..9999).random()}",
            upload_url = "https://mock-upload.nyxguard.com/presigned/${(1000..9999).random()}",
            upload_method = "PUT",
            upload_headers = mapOf("Content-Type" to body.content_type),
            playback_url = "https://mock-storage.nyxguard.com/audio/${(1000..9999).random()}.m4a",
            audio_url = "https://mock-storage.nyxguard.com/audio/${(1000..9999).random()}.m4a",
            storage_mode = "s3",
            bucket = "mock-nyxguard",
            expires_in_seconds = 900
        )
        return delegate.returningResponse(response).createSosMediaPresign(body)
    }

    override fun commitSosMedia(body: SosMediaCommitRequest): Call<SosMediaCommitResponse> {
        val response = SosMediaCommitResponse(
            media_key = body.media_key,
            audio_url = "https://mock-storage.nyxguard.com/audio/${body.media_key}.m4a",
            playback_url = "https://mock-storage.nyxguard.com/audio/${body.media_key}.m4a",
            storage_mode = "s3",
            bucket = "mock-nyxguard",
            content_type = body.content_type ?: "audio/m4a",
            size_bytes = body.size_bytes,
            message = "SOS媒体已准备就绪"
        )
        return delegate.returningResponse(response).commitSosMedia(body)
    }

    override fun chat(body: ChatRequest): Call<ChatResponse> {
        val response = ChatResponse(
            reply = mockAssistantReply(body.content),
            message_id = (1000..9999).random()
        )
        return delegate.returningResponse(response).chat(body)
    }

    override fun proactiveChat(body: ProactiveChatRequest): Call<ChatResponse> {
        val response = ChatResponse(
            reply = mockProactiveReply(),
            message_id = (1000..9999).random()
        )
        return delegate.returningResponse(response).proactiveChat(body)
    }

    override fun getDashboard(): Call<DashboardResponseDto> {
        val response = DashboardResponseDto(
            nickname = "Sarah",
            greeting = mockGreeting(),
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

    override fun getGuardianDashboard(): Call<GuardianDashboardDto> {
        val response = GuardianDashboardDto(
            guardian_user_id = 2001,
            guardian_nickname = "妈妈",
            greeting = mockGreeting(),
            protected_users = listOf(
                GuardianProtectedTravelerDto(
                    traveler_user_id = 1001,
                    traveler_nickname = "测试用户",
                    guardian_link_id = 1,
                    relationship = "母亲",
                    active_trip = ActiveTripBriefDto(
                        id = 2001,
                        mode = "walk",
                        status = "active",
                        destination = "星光国际公寓南门",
                        eta_minutes = 12,
                        guardian_count = 2
                    ),
                    last_location = LocationPointDto(
                        lat = 31.2298,
                        lng = 121.4742,
                        recorded_at = java.time.Instant.now().toString()
                    ),
                    last_event = GuardianEventDto(
                        id = 5001,
                        event_type = "walk_deviation",
                        title = "步行偏离提醒",
                        body = "测试用户的步行路线已偏离，请关注",
                        status = "sent",
                        created_at = java.time.Instant.now().minusSeconds(60).toString(),
                        trip_id = 2001,
                        guardian_id = 1
                    )
                )
            ),
            active_trip_count = 1,
            pending_alert_count = 1
        )
        return delegate.returningResponse(response).getGuardianDashboard()
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

    override fun getGuardianTripDetail(id: Int): Call<GuardianTripDetailDto> {
        val response = GuardianTripDetailDto(
            trip_id = id,
            traveler_user_id = 1001,
            traveler_nickname = "测试用户",
            mode = "walk",
            status = "active",
            destination = "星光国际公寓南门",
            eta_minutes = 12,
            guardian_count = 2,
            latest_location = LocationPointDto(
                lat = 31.2298,
                lng = 121.4742,
                recorded_at = java.time.Instant.now().toString()
            ),
            route_preview = listOf(
                LocationPointDto(lat = 31.2304, lng = 121.4737, recorded_at = java.time.Instant.now().minusSeconds(120).toString()),
                LocationPointDto(lat = 31.2298, lng = 121.4742, recorded_at = java.time.Instant.now().toString()),
            ),
            recent_events = listOf(
                GuardianEventDto(
                    id = 5001,
                    event_type = "walk_deviation",
                    title = "步行偏离提醒",
                    body = "测试用户的步行路线已偏离，请关注",
                    status = "sent",
                    created_at = java.time.Instant.now().minusSeconds(60).toString(),
                    trip_id = id,
                    guardian_id = 1
                )
            ),
            sos_state = "idle",
            expected_arrive_at = java.time.Instant.now().plusSeconds(720).toString()
        )
        return delegate.returningResponse(response).getGuardianTripDetail(id)
    }

    override fun getGuardianSosDetail(id: Int): Call<GuardianSosDetailDto> {
        val response = GuardianSosDetailDto(
            sos_id = id,
            traveler_user_id = 1001,
            traveler_nickname = "测试用户",
            trip_id = 2001,
            mode = "walk",
            status = "active",
            created_at = java.time.Instant.now().minusSeconds(30).toString(),
            lat = 31.2298,
            lng = 121.4742,
            media_key = "mock-media-$id",
            audio_url = "https://mock-storage.nyxguard.com/audio/mock-media-$id.m4a",
            playback_url = "https://mock-storage.nyxguard.com/audio/mock-media-$id.m4a",
            recent_events = listOf(
                GuardianEventDto(
                    id = 5002,
                    event_type = "sos_triggered",
                    title = "SOS已触发",
                    body = "测试用户已触发SOS，正在发送实时位置与行程信息",
                    status = "sent",
                    created_at = java.time.Instant.now().minusSeconds(10).toString(),
                    trip_id = 2001,
                    guardian_id = 1
                )
            )
        )
        return delegate.returningResponse(response).getGuardianSosDetail(id)
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

    override fun getNotificationEvents(tripId: Int?): Call<List<NotificationEventDto>> {
        val response = listOf(
            NotificationEventDto(
                id = 7001,
                event_type = "trip_started",
                title = "行程已开始",
                body = "测试用户已开始步行守护",
                payload = mapOf("trip_id" to 2001, "channel" to "fcm"),
                status = "delivered",
                delivery_channel = "fcm",
                delivery_status = "delivered",
                attempt_count = 1,
                delivered_at = java.time.Instant.now().toString(),
                opened_at = null,
                failure_reason = null,
                user_id = 1001,
                trip_id = tripId ?: 2001,
                guardian_id = 1,
                created_at = java.time.Instant.now().minusSeconds(120).toString()
            )
        )
        return delegate.returningResponse(response).getNotificationEvents(tripId)
    }

    override fun pushNotification(body: NotificationPushRequest): Call<NotificationEventDto> {
        val response = NotificationEventDto(
            id = (7000..7999).random(),
            event_type = body.event_type,
            title = body.title,
            body = body.body,
            payload = body.payload,
            status = body.status,
            delivery_channel = body.delivery_channel ?: "manual",
            delivery_status = body.delivery_status ?: "queued",
            attempt_count = body.attempt_count,
            delivered_at = body.delivered_at,
            opened_at = body.opened_at,
            failure_reason = body.failure_reason,
            user_id = 1001,
            trip_id = body.trip_id,
            guardian_id = body.guardian_id,
            created_at = java.time.Instant.now().toString()
        )
        return delegate.returningResponse(response).pushNotification(body)
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
                ChatMessageDto(1, "assistant", "Hi, I'm your NyxGuard AI Care companion. I'll stay with you while you head home tonight.", tripId, "chat", now),
                ChatMessageDto(2, "user", "Thanks. I just left the subway and I'm walking home alone.", tripId, "chat", now),
                ChatMessageDto(3, "assistant", "I'm with you. Is the route ahead busy, or is it getting quiet now?", tripId, "chat", now),
                ChatMessageDto(4, "user", "It's getting quieter, but there are still a few convenience stores open.", tripId, "chat", now),
                ChatMessageDto(5, "assistant", "That's helpful. Stay on the brighter side of the street if you can. About how many minutes do you have left?", tripId, "chat", now),
                ChatMessageDto(6, "user", "Around ten minutes. I can already see the main road near my apartment.", tripId, "chat", now),
                ChatMessageDto(7, "assistant", "Perfect. Keep to the main road, avoid distractions, and tell me if anyone starts matching your pace.", tripId, "chat", now),
            )
        )
        return delegate.returningResponse(response).getChatMessages(tripId)
    }

    override fun createChatMessage(body: V2ChatMessageRequest): Call<V2ChatMessageResponseDto> {
        val now = java.time.Instant.now().toString()
        val response = V2ChatMessageResponseDto(
            user_message = ChatMessageDto(10, "user", body.content, body.trip_id, "chat", now),
            assistant_message = ChatMessageDto(11, "assistant", mockAssistantReply(body.content), body.trip_id, "chat", now),
            used_fallback = true
        )
        return delegate.returningResponse(response).createChatMessage(body)
    }

    override fun createProactiveMessage(body: ProactiveChatRequest): Call<ChatMessageDto> {
        val response = ChatMessageDto(
            id = (1000..9999).random(),
            role = "assistant",
            content = mockProactiveReply(),
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
            message = "SOS求助已记录",
            media_key = body.media_key ?: "s3://nyxguard/mock/${body.trip_id ?: 0}",
            audio_url = body.audio_url
        )
        return delegate.returningResponse(response).triggerGlobalSos(body)
    }

    override fun registerPushToken(body: PushTokenRegisterRequest): Call<PushTokenResponse> {
        val response = PushTokenResponse(
            token = body.token,
            status = "registered",
            registered = true
        )
        return delegate.returningResponse(response).registerPushToken(body)
    }

    override fun deregisterPushToken(body: PushTokenDeregisterRequest): Call<MessageResponse> {
        return delegate.returningResponse(MessageResponse("注销成功")).deregisterPushToken(body)
    }
}
