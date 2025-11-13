package com.soundman.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_detections")
data class SoundDetection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val labelId: Long? = null,
    val labelName: String? = null,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val audioData: ByteArray? = null,
    val isPerson: Boolean = false,
    val personId: Long? = null,
    val clusterId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SoundDetection

        if (id != other.id) return false
        if (labelId != other.labelId) return false
        if (labelName != other.labelName) return false
        if (confidence != other.confidence) return false
        if (timestamp != other.timestamp) return false
        if (audioData != null) {
            if (other.audioData == null) return false
            if (!audioData.contentEquals(other.audioData)) return false
        } else if (other.audioData != null) return false
        if (isPerson != other.isPerson) return false
        if (personId != other.personId) return false
        if (clusterId != other.clusterId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (labelId?.hashCode() ?: 0)
        result = 31 * result + (labelName?.hashCode() ?: 0)
        result = 31 * result + confidence.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        result = 31 * result + isPerson.hashCode()
        result = 31 * result + (personId?.hashCode() ?: 0)
        result = 31 * result + (clusterId?.hashCode() ?: 0)
        return result
    }
}
