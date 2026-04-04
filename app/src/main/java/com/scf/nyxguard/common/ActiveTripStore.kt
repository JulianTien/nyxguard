package com.scf.nyxguard.common

import android.content.Context
import android.content.SharedPreferences

data class ActiveTripSnapshot(
    val isActive: Boolean,
    val tripId: Int,
    val mode: String,
    val destination: String,
    val etaMinutes: Int,
    val guardianSummary: String,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val plateNumber: String = "",
    val vehicleType: String = "",
    val vehicleColor: String = "",
)

object ActiveTripStore {

    private const val PREFS_NAME = "nyxguard_active_trip"
    private const val KEY_ACTIVE = "active"
    private const val KEY_TRIP_ID = "trip_id"
    private const val KEY_MODE = "mode"
    private const val KEY_DESTINATION = "destination"
    private const val KEY_ETA_MINUTES = "eta_minutes"
    private const val KEY_GUARDIAN_SUMMARY = "guardian_summary"
    private const val KEY_START_LAT = "start_lat"
    private const val KEY_START_LNG = "start_lng"
    private const val KEY_END_LAT = "end_lat"
    private const val KEY_END_LNG = "end_lng"
    private const val KEY_PLATE_NUMBER = "plate_number"
    private const val KEY_VEHICLE_TYPE = "vehicle_type"
    private const val KEY_VEHICLE_COLOR = "vehicle_color"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, snapshot: ActiveTripSnapshot) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, snapshot.isActive)
            .putInt(KEY_TRIP_ID, snapshot.tripId)
            .putString(KEY_MODE, snapshot.mode)
            .putString(KEY_DESTINATION, snapshot.destination)
            .putInt(KEY_ETA_MINUTES, snapshot.etaMinutes)
            .putString(KEY_GUARDIAN_SUMMARY, snapshot.guardianSummary)
            .putString(KEY_PLATE_NUMBER, snapshot.plateNumber)
            .putString(KEY_VEHICLE_TYPE, snapshot.vehicleType)
            .putString(KEY_VEHICLE_COLOR, snapshot.vehicleColor)
            .putLong(KEY_START_LAT, java.lang.Double.doubleToRawLongBits(snapshot.startLat))
            .putLong(KEY_START_LNG, java.lang.Double.doubleToRawLongBits(snapshot.startLng))
            .putLong(KEY_END_LAT, java.lang.Double.doubleToRawLongBits(snapshot.endLat))
            .putLong(KEY_END_LNG, java.lang.Double.doubleToRawLongBits(snapshot.endLng))
            .apply()
    }

    fun updateTripId(context: Context, tripId: Int) {
        prefs(context).edit()
            .putInt(KEY_TRIP_ID, tripId)
            .apply()
    }

    fun get(context: Context): ActiveTripSnapshot? {
        val store = prefs(context)
        if (!store.getBoolean(KEY_ACTIVE, false)) return null
        return ActiveTripSnapshot(
            isActive = true,
            tripId = store.getInt(KEY_TRIP_ID, -1),
            mode = store.getString(KEY_MODE, "").orEmpty(),
            destination = store.getString(KEY_DESTINATION, "").orEmpty(),
            etaMinutes = store.getInt(KEY_ETA_MINUTES, 0),
            guardianSummary = store.getString(KEY_GUARDIAN_SUMMARY, "").orEmpty(),
            startLat = java.lang.Double.longBitsToDouble(store.getLong(KEY_START_LAT, 0L)),
            startLng = java.lang.Double.longBitsToDouble(store.getLong(KEY_START_LNG, 0L)),
            endLat = java.lang.Double.longBitsToDouble(store.getLong(KEY_END_LAT, 0L)),
            endLng = java.lang.Double.longBitsToDouble(store.getLong(KEY_END_LNG, 0L)),
            plateNumber = store.getString(KEY_PLATE_NUMBER, "").orEmpty(),
            vehicleType = store.getString(KEY_VEHICLE_TYPE, "").orEmpty(),
            vehicleColor = store.getString(KEY_VEHICLE_COLOR, "").orEmpty(),
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
