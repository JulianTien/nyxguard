package com.scf.nyxguard.util

import android.content.Context
import android.content.SharedPreferences

object GuardianRouteStore {

    private const val PREFS_NAME = "nyxguard_guardian_route"
    private const val KEY_ROUTE_TYPE = "route_type"
    private const val KEY_TRIP_ID = "trip_id"
    private const val KEY_SOS_ID = "sos_id"
    private const val KEY_GUARDIAN_ID = "guardian_id"
    private const val KEY_SOURCE = "source"
    private const val KEY_UPDATED_AT = "updated_at"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, route: GuardianDeepLink) {
        prefs(context).edit()
            .putString(KEY_ROUTE_TYPE, route.routeType)
            .putInt(KEY_TRIP_ID, route.tripId ?: -1)
            .putInt(KEY_SOS_ID, route.sosId ?: -1)
            .putInt(KEY_GUARDIAN_ID, route.guardianId ?: -1)
            .putString(KEY_SOURCE, route.source)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun get(context: Context): GuardianDeepLink? {
        val store = prefs(context)
        val routeType = store.getString(KEY_ROUTE_TYPE, null)?.takeIf { it.isNotBlank() } ?: return null
        val tripId = store.getInt(KEY_TRIP_ID, -1).takeIf { it >= 0 }
        val sosId = store.getInt(KEY_SOS_ID, -1).takeIf { it >= 0 }
        val guardianId = store.getInt(KEY_GUARDIAN_ID, -1).takeIf { it >= 0 }
        return GuardianDeepLink(
            routeType = routeType,
            tripId = tripId,
            sosId = sosId,
            guardianId = guardianId,
            source = store.getString(KEY_SOURCE, GuardianDeepLink.SOURCE_APP) ?: GuardianDeepLink.SOURCE_APP,
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
