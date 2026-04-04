package com.scf.nyxguard.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pending_location",
        indices = {
                @Index(value = {"tripId", "tripType", "recordedAt"})
        }
)
public class PendingLocationEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public int tripId;
    public double lat;
    public double lng;
    public double accuracy;

    @NonNull
    public String recordedAt;

    @NonNull
    public String tripType;

    public PendingLocationEntity(
            int tripId,
            double lat,
            double lng,
            double accuracy,
            @NonNull String recordedAt,
            @NonNull String tripType
    ) {
        this.tripId = tripId;
        this.lat = lat;
        this.lng = lng;
        this.accuracy = accuracy;
        this.recordedAt = recordedAt;
        this.tripType = tripType;
    }
}
