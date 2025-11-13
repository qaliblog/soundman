package com.soundman.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundman.app.data.SoundDatabase
import com.soundman.app.data.SoundLabel
import com.soundman.app.data.PersonLabel
import com.soundman.app.service.SoundDetectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SoundDetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val detectionService = SoundDetectionService(application)
    private val database = SoundDatabase.getDatabase(application)

    val isDetecting: StateFlow<Boolean> = detectionService.isDetecting
    val currentDetection: StateFlow<com.soundman.app.data.SoundDetection?> = 
        detectionService.currentDetection
    val unknownSoundCount: StateFlow<Int> = detectionService.unknownSoundCount

    val soundLabels = database.soundLabelDao().getAllLabels()
    val activeSoundLabels = database.soundLabelDao().getActiveLabels()
    val inactiveSoundLabels = database.soundLabelDao().getInactiveLabels()
    val personLabels = database.personLabelDao().getAllPersons()
    val recentDetections = database.soundDetectionDao().getRecentDetections(50)

    fun startDetection() {
        viewModelScope.launch {
            detectionService.startDetection()
        }
    }

    fun stopDetection() {
        viewModelScope.launch {
            detectionService.stopDetection()
        }
    }

    fun labelUnknownSound(labelName: String, useExistingLabel: Boolean = false, existingLabelId: Long? = null, detectionId: Long? = null) {
        viewModelScope.launch {
            detectionService.labelUnknownSound(labelName, useExistingLabel, existingLabelId, detectionId)
        }
    }

    fun labelPerson(personName: String) {
        viewModelScope.launch {
            detectionService.labelPerson(personName)
        }
    }

    fun updateSoundLabelSettings(
        labelId: Long,
        volumeMultiplier: Float,
        isMuted: Boolean,
        reverseToneEnabled: Boolean,
        isActive: Boolean? = null,
        isRecording: Boolean? = null
    ) {
        viewModelScope.launch {
            detectionService.updateSoundLabelSettings(
                labelId,
                volumeMultiplier,
                isMuted,
                reverseToneEnabled,
                isActive,
                isRecording
            )
        }
    }

    fun updatePersonSettings(
        personId: Long,
        volumeMultiplier: Float,
        isMuted: Boolean,
        isActive: Boolean? = null
    ) {
        viewModelScope.launch {
            detectionService.updatePersonSettings(
                personId,
                volumeMultiplier,
                isMuted,
                isActive
            )
        }
    }

    val isLiveMicEnabled = detectionService.isLiveMicEnabled

    fun setLiveMicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            detectionService.setLiveMicEnabled(enabled)
        }
    }

    suspend fun getUnknownSoundClusters(): List<com.soundman.app.data.SoundDetection> {
        return detectionService.getUnknownSoundClusters()
    }

    fun toggleSoundActive(labelId: Long, isActive: Boolean) {
        viewModelScope.launch {
            detectionService.toggleSoundActive(labelId, isActive)
        }
    }

    fun toggleSoundRecording(labelId: Long, isRecording: Boolean) {
        viewModelScope.launch {
            detectionService.toggleSoundRecording(labelId, isRecording)
        }
    }

    fun togglePersonActive(personId: Long, isActive: Boolean) {
        viewModelScope.launch {
            detectionService.togglePersonActive(personId, isActive)
        }
    }

    fun setTranscriptionLanguage(language: String) {
        viewModelScope.launch {
            detectionService.setTranscriptionLanguage(language)
        }
    }
}
