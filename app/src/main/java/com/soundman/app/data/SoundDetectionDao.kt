package com.soundman.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDetectionDao {
    @Query("SELECT * FROM sound_detections ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDetections(limit: Int = 100): Flow<List<SoundDetection>>

    @Query("SELECT * FROM sound_detections WHERE labelId = :labelId ORDER BY timestamp DESC")
    fun getDetectionsByLabel(labelId: Long): Flow<List<SoundDetection>>

    @Insert
    suspend fun insertDetection(detection: SoundDetection): Long

    @Query("DELETE FROM sound_detections WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldDetections(beforeTimestamp: Long)
}
