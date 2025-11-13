package com.soundman.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_labels")
data class SoundLabel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val detectionCount: Int = 0,
    val confidenceThreshold: Float = 0.7f,
    val volumeMultiplier: Float = 1.0f,
    val isMuted: Boolean = false,
    val reverseToneEnabled: Boolean = false,
    val isActive: Boolean = true,
    val isRecording: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
