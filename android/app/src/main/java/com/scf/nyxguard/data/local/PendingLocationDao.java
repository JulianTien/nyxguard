package com.scf.nyxguard.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PendingLocationEntity> items);

    @Query(
            "SELECT * FROM pending_location " +
            "WHERE tripId = :tripId AND tripType = :tripType " +
            "ORDER BY recordedAt ASC, id ASC"
    )
    List<PendingLocationEntity> getAll(int tripId, String tripType);

    @Query("DELETE FROM pending_location WHERE tripId = :tripId AND tripType = :tripType")
    void clear(int tripId, String tripType);
}
