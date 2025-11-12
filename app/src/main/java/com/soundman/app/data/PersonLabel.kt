package com.soundman.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "person_labels")
data class PersonLabel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val voiceSignature: String? = null, // Encoded voice characteristics
    val volumeMultiplier: Float = 1.0f,
    val isMuted: Boolean = false,
    val detectionCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
