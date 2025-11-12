package com.soundman.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundLabelDao {
    @Query("SELECT * FROM sound_labels ORDER BY name ASC")
    fun getAllLabels(): Flow<List<SoundLabel>>

    @Query("SELECT * FROM sound_labels WHERE id = :id")
    suspend fun getLabelById(id: Long): SoundLabel?

    @Query("SELECT * FROM sound_labels WHERE name = :name")
    suspend fun getLabelByName(name: String): SoundLabel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: SoundLabel): Long

    @Update
    suspend fun updateLabel(label: SoundLabel)

    @Delete
    suspend fun deleteLabel(label: SoundLabel)

    @Query("UPDATE sound_labels SET detectionCount = detectionCount + 1 WHERE id = :id")
    suspend fun incrementDetectionCount(id: Long)
}
