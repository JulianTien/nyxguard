package com.scf.nyxguard.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scf.nyxguard.R
import com.scf.nyxguard.util.FirebaseBootstrap
import com.scf.nyxguard.util.GuardianDeepLink
import com.scf.nyxguard.util.PushNotificationHelper
import com.scf.nyxguard.util.PushTokenSyncManager

class NyxGuardFirebaseMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        FirebaseBootstrap.ensureInitialized(this)
    }

    override fun onNewToken(token: String) {
        PushTokenSyncManager.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val route = GuardianDeepLink.fromRemoteData(message.data)
        val resolvedRoute = route ?: GuardianDeepLink(
            routeType = when (message.data["event_type"]) {
                "sos_triggered" -> GuardianDeepLink.ROUTE_TYPE_SOS
                else -> GuardianDeepLink.ROUTE_TYPE_TRIP
            },
            tripId = message.data["trip_id"]?.toIntOrNull()
                ?: message.data["linked_trip_id"]?.toIntOrNull(),
            sosId = message.data["sos_id"]?.toIntOrNull(),
            guardianId = message.data["guardian_id"]?.toIntOrNull(),
        )

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.push_default_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(R.string.push_default_body)

        PushNotificationHelper.show(this, title, body, resolvedRoute)
    }
}
