package com.scf.nyxguard.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.scf.nyxguard.network.LocationUploadItem

class PendingLocationStore private constructor(context: Context) {

    private val database = Room.databaseBuilder(
        context.applicationContext,
        PendingLocationDatabase::class.java,
        DATABASE_NAME
    ).build()

    private val dao = database.pendingLocationDao()

    suspend fun saveBatch(
        tripId: Int,
        tripType: String,
        batch: List<LocationUploadItem>
    ) {
        if (batch.isEmpty()) return
        database.withTransaction {
            dao.clear(tripId, tripType)
            dao.insertAll(batch.map { it.toEntity(tripId, tripType) })
        }
    }

    suspend fun loadBatch(tripId: Int, tripType: String): List<LocationUploadItem> {
        return dao.getAll(tripId, tripType).map { it.toLocationUploadItem() }
    }

    suspend fun clearBatch(tripId: Int, tripType: String) {
        dao.clear(tripId, tripType)
    }

    companion object {
        private const val DATABASE_NAME = "nyxguard_pending_locations.db"

        @Volatile
        private var INSTANCE: PendingLocationStore? = null

        fun getInstance(context: Context): PendingLocationStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PendingLocationStore(context).also { INSTANCE = it }
            }
        }
    }
}

private fun LocationUploadItem.toEntity(tripId: Int, tripType: String): PendingLocationEntity {
    return PendingLocationEntity(
        tripId,
        lat,
        lng,
        accuracy,
        recorded_at,
        tripType
    )
}

private fun PendingLocationEntity.toLocationUploadItem(): LocationUploadItem {
    return LocationUploadItem(
        lat,
        lng,
        accuracy,
        recordedAt
    )
}
