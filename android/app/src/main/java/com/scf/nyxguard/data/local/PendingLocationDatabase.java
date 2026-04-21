package com.scf.nyxguard.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {PendingLocationEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class PendingLocationDatabase extends RoomDatabase {
    public abstract PendingLocationDao pendingLocationDao();
}
