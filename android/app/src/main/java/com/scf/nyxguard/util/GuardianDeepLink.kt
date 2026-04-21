package com.scf.nyxguard.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import kotlin.math.abs

data class GuardianDeepLink(
    val routeType: String,
    val tripId: Int? = null,
    val sosId: Int? = null,
    val guardianId: Int? = null,
    val source: String = SOURCE_NOTIFICATION,
) {

    fun toBundle(): Bundle = bundleOf(
        EXTRA_ROUTE_TYPE to routeType,
        EXTRA_TRIP_ID to tripId,
        EXTRA_SOS_ID to sosId,
        EXTRA_GUARDIAN_ID to guardianId,
        EXTRA_SOURCE to source,
    )

    fun toUri(): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .apply {
            when (routeType) {
                ROUTE_TYPE_SOS -> {
                    appendPath(ROUTE_SEGMENT_SOS)
                    sosId?.let { appendPath(it.toString()) }
                }
                else -> {
                    appendPath(ROUTE_SEGMENT_TRIP)
                    tripId?.let { appendPath(it.toString()) }
                }
            }
            guardianId?.let { appendQueryParameter(EXTRA_GUARDIAN_ID, it.toString()) }
            tripId?.let { appendQueryParameter(EXTRA_TRIP_ID, it.toString()) }
            sosId?.let { appendQueryParameter(EXTRA_SOS_ID, it.toString()) }
            appendQueryParameter(EXTRA_ROUTE_TYPE, routeType)
            appendQueryParameter(EXTRA_SOURCE, source)
        }
        .build()

    fun toIntent(context: Context): Intent = Intent(context, com.scf.nyxguard.MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = toUri()
        putExtras(toBundle())
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    companion object {
        const val SCHEME = "nyxguard"
        const val HOST = "guardian"
        const val ROUTE_TYPE_TRIP = "guardian_trip"
        const val ROUTE_TYPE_SOS = "guardian_sos"
        private const val ROUTE_SEGMENT_TRIP = "trip"
        private const val ROUTE_SEGMENT_SOS = "sos"

        const val EXTRA_ROUTE_TYPE = "extra_route_type"
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_SOS_ID = "extra_sos_id"
        const val EXTRA_GUARDIAN_ID = "extra_guardian_id"
        const val EXTRA_SOURCE = "extra_source"
        const val RESULT_KEY = "guardian_deep_link"
        const val SOURCE_NOTIFICATION = "notification"
        const val SOURCE_APP = "app"

        fun fromIntent(intent: Intent?): GuardianDeepLink? {
            if (intent == null) return null
            fromBundle(intent.extras)?.let { return it }
            intent.data?.let { return fromUri(it) }
            return null
        }

        fun fromRemoteData(data: Map<String, String>): GuardianDeepLink? {
            val explicitUri = data["route_uri"]?.takeIf { it.isNotBlank() }?.let {
                runCatching { Uri.parse(it) }.getOrNull()
            }
            if (explicitUri != null) {
                fromUri(explicitUri)?.let { return it.copy(source = SOURCE_NOTIFICATION) }
            }

            val routeType = data[EXTRA_ROUTE_TYPE]
                ?: data["route_type"]
                ?: when (data["event_type"]) {
                    "sos_triggered" -> ROUTE_TYPE_SOS
                    else -> ROUTE_TYPE_TRIP
                }

            val tripId = data[EXTRA_TRIP_ID]?.toIntOrNull()
                ?: data["trip_id"]?.toIntOrNull()
                ?: data["linked_trip_id"]?.toIntOrNull()
            val sosId = data[EXTRA_SOS_ID]?.toIntOrNull()
                ?: data["sos_id"]?.toIntOrNull()
            val guardianId = data[EXTRA_GUARDIAN_ID]?.toIntOrNull()
                ?: data["guardian_id"]?.toIntOrNull()

            return GuardianDeepLink(
                routeType = routeType,
                tripId = tripId,
                sosId = sosId,
                guardianId = guardianId,
            )
        }

        fun fromBundle(bundle: Bundle?): GuardianDeepLink? {
            if (bundle == null) return null
            val routeType = bundle.getString(EXTRA_ROUTE_TYPE)?.takeIf { it.isNotBlank() } ?: return null
            return GuardianDeepLink(
                routeType = routeType,
                tripId = bundle.getIntOrNull(EXTRA_TRIP_ID),
                sosId = bundle.getIntOrNull(EXTRA_SOS_ID),
                guardianId = bundle.getIntOrNull(EXTRA_GUARDIAN_ID),
                source = bundle.getString(EXTRA_SOURCE) ?: SOURCE_APP,
            )
        }

        private fun fromUri(uri: Uri): GuardianDeepLink? {
            if (uri.scheme != SCHEME || uri.host != HOST) {
                return null
            }

            val segments = uri.pathSegments
            val segmentType = segments.firstOrNull().orEmpty()
            val routeType = uri.getQueryParameter(EXTRA_ROUTE_TYPE)
                ?: when (segmentType) {
                    ROUTE_SEGMENT_SOS -> ROUTE_TYPE_SOS
                    else -> ROUTE_TYPE_TRIP
                }

            val tripId = uri.getQueryParameter(EXTRA_TRIP_ID)?.toIntOrNull()
                ?: segments.getOrNull(1)?.toIntOrNull()
            val sosId = uri.getQueryParameter(EXTRA_SOS_ID)?.toIntOrNull()
                ?: if (segmentType == ROUTE_SEGMENT_SOS) segments.getOrNull(1)?.toIntOrNull() else null
            val guardianId = uri.getQueryParameter(EXTRA_GUARDIAN_ID)?.toIntOrNull()

            return GuardianDeepLink(
                routeType = routeType,
                tripId = tripId,
                sosId = sosId,
                guardianId = guardianId,
                source = uri.getQueryParameter(EXTRA_SOURCE) ?: SOURCE_APP,
            )
        }

        private fun Bundle.getIntOrNull(key: String): Int? {
            return if (containsKey(key)) {
                runCatching { getInt(key) }.getOrNull()
            } else {
                null
            }
        }
    }

    fun stableNotificationId(): Int {
        return abs(listOf(routeType, tripId, sosId, guardianId, source).joinToString("|").hashCode())
    }
}
