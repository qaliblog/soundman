package com.soundman.app.service

import android.content.Context
import android.util.Log
import com.soundman.app.audio.AudioOutputProcessor
import com.soundman.app.audio.AudioProcessor
import com.soundman.app.audio.SoundClassifier
import com.soundman.app.data.SoundDatabase
import com.soundman.app.data.SoundDetection
import com.soundman.app.data.SoundLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundDetectionService(private val context: Context) {
    private val audioProcessor = AudioProcessor()
    private val soundClassifier = SoundClassifier(context)
    private val audioOutputProcessor = AudioOutputProcessor()
    private val database = SoundDatabase.getDatabase(context)

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    private val _currentDetection = MutableStateFlow<SoundDetection?>(null)
    val currentDetection: StateFlow<SoundDetection?> = _currentDetection.asStateFlow()

    private val _unknownSoundCount = MutableStateFlow(0)
    val unknownSoundCount: StateFlow<Int> = _unknownSoundCount.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun startDetection() = withContext(Dispatchers.IO) {
        if (_isDetecting.value) return@withContext

        if (!audioProcessor.startRecording()) {
            Log.e("SoundDetectionService", "Failed to start audio recording")
            return@withContext
        }

        if (!audioOutputProcessor.initialize()) {
            Log.e("SoundDetectionService", "Failed to initialize audio output")
            audioProcessor.stopRecording()
            return@withContext
        }

        _isDetecting.value = true

        scope.launch {
            audioProcessor.audioData.collect { audioData ->
                if (audioData != null && _isDetecting.value) {
                    processAudioChunk(audioData)
                }
            }
        }
    }

    suspend fun stopDetection() = withContext(Dispatchers.IO) {
        _isDetecting.value = false
        audioProcessor.stopRecording()
        audioOutputProcessor.release()
    }

    private suspend fun processAudioChunk(audioData: ByteArray) = withContext(Dispatchers.Default) {
        try {
            val soundLabels = database.soundLabelDao().getAllLabels().first()
            val personLabels = database.personLabelDao().getAllPersons().first()

            val result = soundClassifier.classifySound(audioData, soundLabels, personLabels)

            if (result.label == null && result.confidence < 0.3f) {
                // Unknown sound detected
                _unknownSoundCount.value = _unknownSoundCount.value + 1
                
                val detection = SoundDetection(
                    labelId = null,
                    labelName = null,
                    confidence = result.confidence,
                    audioData = audioData,
                    isPerson = false
                )
                _currentDetection.value = detection
                
                // Save to database
                database.soundDetectionDao().insertDetection(detection)
            } else if (result.label != null) {
                // Known sound detected
                val label = if (result.isPerson) {
                    personLabels.find { it.id == result.personId }
                } else {
                    soundLabels.find { it.name == result.label }
                }

                if (label != null) {
                    val detection = SoundDetection(
                        labelId = if (result.isPerson) null else (label as SoundLabel).id,
                        labelName = result.label,
                        confidence = result.confidence,
                        audioData = audioData,
                        isPerson = result.isPerson,
                        personId = result.personId
                    )
                    _currentDetection.value = detection

                    // Process audio output
                    val processedAudio = if (result.isPerson) {
                        val personLabel = label as com.soundman.app.data.PersonLabel
                        audioOutputProcessor.processAudio(
                            audioData,
                            null,
                            isPerson = true,
                            personVolumeMultiplier = personLabel.volumeMultiplier
                        )
                    } else {
                        audioOutputProcessor.processAudio(
                            audioData,
                            label as SoundLabel,
                            isPerson = false
                        )
                    }

                    // Write processed audio to output
                    audioOutputProcessor.writeAudio(processedAudio)

                    // Update detection count
                    if (result.isPerson) {
                        database.personLabelDao().incrementDetectionCount(result.personId!!)
                    } else {
                        database.soundLabelDao().incrementDetectionCount((label as SoundLabel).id)
                    }

                    // Save to database
                    database.soundDetectionDao().insertDetection(detection)
                }
            }
        } catch (e: Exception) {
            Log.e("SoundDetectionService", "Error processing audio chunk", e)
        }
    }

    suspend fun labelUnknownSound(labelName: String) = withContext(Dispatchers.IO) {
        val currentDet = _currentDetection.value
        if (currentDet == null || currentDet.labelName != null) return@withContext

        // Create new label
        val label = SoundLabel(
            name = labelName,
            detectionCount = 1,
            confidenceThreshold = 0.7f,
            volumeMultiplier = 1.0f
        )
        val labelId = database.soundLabelDao().insertLabel(label)

        // Learn from previous unknown detections
        val allDetections = database.soundDetectionDao().getRecentDetections(100).first()
        val unknownDetections = allDetections.filter { it.labelName == null }

        unknownDetections.forEach { detection ->
            detection.audioData?.let { audioData ->
                soundClassifier.learnSound(labelName, audioData)
            }
        }

        // Update current detection
        val updatedDetection = currentDet.copy(
            labelId = labelId,
            labelName = labelName
        )
        database.soundDetectionDao().insertDetection(updatedDetection)
        _currentDetection.value = updatedDetection
        _unknownSoundCount.value = 0
    }

    suspend fun labelPerson(personName: String) = withContext(Dispatchers.IO) {
        val currentDet = _currentDetection.value
        if (currentDet == null || !currentDet.isPerson) return@withContext

        // Create new person label
        val person = com.soundman.app.data.PersonLabel(
            name = personName,
            detectionCount = 1,
            volumeMultiplier = 1.0f
        )
        val personId = database.personLabelDao().insertPerson(person)

        // Learn from current detection
        currentDet.audioData?.let { audioData ->
            soundClassifier.learnPersonVoice(personId, personName, audioData)
        }

        // Update current detection
        val updatedDetection = currentDet.copy(
            personId = personId,
            labelName = personName
        )
        database.soundDetectionDao().insertDetection(updatedDetection)
        _currentDetection.value = updatedDetection
    }

    suspend fun updateSoundLabelSettings(
        labelId: Long,
        volumeMultiplier: Float,
        isMuted: Boolean,
        reverseToneEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val label = database.soundLabelDao().getLabelById(labelId)
        if (label != null) {
            val updated = label.copy(
                volumeMultiplier = volumeMultiplier,
                isMuted = isMuted,
                reverseToneEnabled = reverseToneEnabled
            )
            database.soundLabelDao().updateLabel(updated)
        }
    }

    suspend fun updatePersonSettings(
        personId: Long,
        volumeMultiplier: Float,
        isMuted: Boolean
    ) = withContext(Dispatchers.IO) {
        val person = database.personLabelDao().getPersonById(personId)
        if (person != null) {
            val updated = person.copy(
                volumeMultiplier = volumeMultiplier,
                isMuted = isMuted
            )
            database.personLabelDao().updatePerson(updated)
        }
    }
}
