package com.soundman.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDetectionDao {
    @Query("SELECT * FROM sound_detections ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDetections(limit: Int = 100): Flow<List<SoundDetection>>

    @Query("SELECT * FROM sound_detections WHERE labelId = :labelId ORDER BY timestamp DESC")
    fun getDetectionsByLabel(labelId: Long): Flow<List<SoundDetection>>

    @Query("SELECT * FROM sound_detections WHERE clusterId = :clusterId ORDER BY timestamp DESC")
    fun getDetectionsByCluster(clusterId: String): Flow<List<SoundDetection>>

    @Query("SELECT * FROM sound_detections WHERE labelName IS NULL AND clusterId IS NOT NULL ORDER BY frequency ASC, duration ASC")
    fun getUnknownSoundClusters(): Flow<List<SoundDetection>>

    @Query("SELECT * FROM sound_detections WHERE id = :id")
    suspend fun getDetectionById(id: Long): SoundDetection?

    @Insert
    suspend fun insertDetection(detection: SoundDetection): Long

    @Update
    suspend fun updateDetection(detection: SoundDetection)

    @Query("DELETE FROM sound_detections WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldDetections(beforeTimestamp: Long)

    @Query("SELECT * FROM sound_detections WHERE clusterId = :clusterId ORDER BY frequency ASC, duration ASC")
    suspend fun getDetectionsByClusterSorted(clusterId: String): List<SoundDetection>
}
