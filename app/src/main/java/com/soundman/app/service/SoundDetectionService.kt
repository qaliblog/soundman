package com.soundman.app.service

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.soundman.app.audio.AudioOutputProcessor
import com.soundman.app.audio.AudioProcessor
import com.soundman.app.audio.MediaPipeSoundClassifier
import com.soundman.app.audio.VoskSpeechRecognizer
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
import kotlin.math.abs

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val LANGUAGE_KEY = stringPreferencesKey("transcription_language")

class SoundDetectionService(private val context: Context) {
    private val audioProcessor = AudioProcessor()
    private val mediaPipeClassifier = MediaPipeSoundClassifier(context)
    private val voskRecognizer = VoskSpeechRecognizer(context)
    private val audioOutputProcessor = AudioOutputProcessor()
    private val database = SoundDatabase.getDatabase(context)
    
    private var nextPersonId = 1L
    private val personIdMap = mutableMapOf<Long, String>() // Maps personId to Person01, Person02, etc.

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    private val _currentDetection = MutableStateFlow<SoundDetection?>(null)
    val currentDetection: StateFlow<SoundDetection?> = _currentDetection.asStateFlow()

    private val _unknownSoundCount = MutableStateFlow(0)
    val unknownSoundCount: StateFlow<Int> = _unknownSoundCount.asStateFlow()

    private val _isLiveMicEnabled = MutableStateFlow(false)
    val isLiveMicEnabled: StateFlow<Boolean> = _isLiveMicEnabled.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    
    private var currentLanguage = "en"

    init {
        scope.launch {
            loadLanguagePreference()
            initializeClassifiers()
        }
    }

    private suspend fun loadLanguagePreference() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.dataStore.data.first()
            currentLanguage = prefs[LANGUAGE_KEY] ?: "en"
        } catch (e: Exception) {
            Log.e("SoundDetectionService", "Error loading language preference", e)
            currentLanguage = "en"
        }
    }

    private suspend fun initializeClassifiers() = withContext(Dispatchers.IO) {
        try {
            mediaPipeClassifier.initialize()
            voskRecognizer.initialize(currentLanguage)
            Log.d("SoundDetectionService", "Classifiers initialized")
        } catch (e: Exception) {
            Log.e("SoundDetectionService", "Error initializing classifiers", e)
        }
    }

    suspend fun setTranscriptionLanguage(language: String) = withContext(Dispatchers.IO) {
        try {
            context.dataStore.edit { prefs ->
                prefs[LANGUAGE_KEY] = language
            }
            currentLanguage = language
            voskRecognizer.changeLanguage(language)
        } catch (e: Exception) {
            Log.e("SoundDetectionService", "Error setting language", e)
        }
    }

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
                    // If live mic is enabled, pass through raw audio
                    if (_isLiveMicEnabled.value) {
                        audioOutputProcessor.writeAudio(audioData)
                    } else {
                        processAudioChunk(audioData)
                    }
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
            
            // Use MediaPipe for classification
            val result = mediaPipeClassifier.classifySound(
                audioData,
                audioProcessor.getSampleRate(),
                personLabels
            )

            if (result.isPerson && result.personId != null) {
                // Person voice detected - use Vosk for transcription
                processPersonVoice(audioData, result, personLabels)
            } else if (result.label != null && result.confidence > 0.1f) {
                // Known sound detected (lowered threshold to 0.1f)
                processKnownSound(audioData, result, soundLabels)
            } else {
                // Unknown sound detected - cluster by frequency and duration
                processUnknownSound(audioData, result)
            }
        } catch (e: Exception) {
            Log.e("SoundDetectionService", "Error processing audio chunk", e)
        }
    }

    private suspend fun processPersonVoice(
        audioData: ByteArray,
        result: com.soundman.app.audio.MediaPipeDetectionResult,
        personLabels: List<com.soundman.app.data.PersonLabel>
    ) = withContext(Dispatchers.Default) {
        val person = personLabels.find { it.id == result.personId }
        if (person == null) return@withContext

        // Get or assign person label (Person01, Person02, etc.)
        val personId = result.personId ?: return@withContext
        val personLabelName = personIdMap.getOrPut(personId) {
            val labelName = "Person%02d".format(nextPersonId++)
            // Update person name if not already set
            if (person.name.startsWith("Person")) {
                database.personLabelDao().updatePerson(person.copy(name = labelName))
            }
            labelName
        }

        // Use Vosk for transcription
        val transcriptionResult = voskRecognizer.recognize(audioData)
        val transcription = transcriptionResult?.text?.takeIf { it.isNotBlank() }

        // Update transcription in database
        if (transcription != null) {
            val updatedTranscription = if (person.transcription.isNullOrBlank()) {
                transcription
            } else {
                "${person.transcription}\n[$personLabelName]: $transcription"
            }
            database.personLabelDao().updateTranscription(result.personId!!, updatedTranscription)
        }

        val detection = SoundDetection(
            labelId = null,
            labelName = personLabelName,
            confidence = result.confidence,
            audioData = audioData,
            isPerson = true,
            personId = result.personId,
            transcription = transcription,
            frequency = result.frequency,
            duration = result.duration,
            isActive = person.isActive
        )
        _currentDetection.value = detection

        // Process audio output with person settings
        if (person.isActive && !person.isMuted) {
            val processedAudio = audioOutputProcessor.processAudio(
                audioData,
                null,
                isPerson = true,
                personVolumeMultiplier = person.volumeMultiplier
            )
            audioOutputProcessor.writeAudio(processedAudio)
        }

        // Update detection count
        database.personLabelDao().incrementDetectionCount(personId)
        database.soundDetectionDao().insertDetection(detection)
    }

    private suspend fun processKnownSound(
        audioData: ByteArray,
        result: com.soundman.app.audio.MediaPipeDetectionResult,
        soundLabels: List<SoundLabel>
    ) = withContext(Dispatchers.Default) {
        val label = soundLabels.find { it.name == result.label }
        if (label == null) return@withContext

        val detection = SoundDetection(
            labelId = label.id,
            labelName = result.label,
            confidence = result.confidence,
            audioData = audioData,
            isPerson = false,
            frequency = result.frequency,
            duration = result.duration,
            isActive = label.isActive
        )
        _currentDetection.value = detection

        // Process audio output with label settings
        if (label.isActive && !label.isMuted && label.isRecording) {
            val processedAudio = audioOutputProcessor.processAudio(
                audioData,
                label,
                isPerson = false
            )
            audioOutputProcessor.writeAudio(processedAudio)
        }

        // Update detection count
        database.soundLabelDao().incrementDetectionCount(label.id)
        database.soundDetectionDao().insertDetection(detection)
    }

    private suspend fun processUnknownSound(
        audioData: ByteArray,
        result: com.soundman.app.audio.MediaPipeDetectionResult
    ) = withContext(Dispatchers.Default) {
        // Find or create cluster based on frequency and duration similarity
        val clusterId = findOrCreateCluster(result.frequency ?: 0f, result.duration ?: 0L)
        
        _unknownSoundCount.value = _unknownSoundCount.value + 1

        val detection = SoundDetection(
            labelId = null,
            labelName = null,
            confidence = result.confidence,
            audioData = audioData,
            isPerson = false,
            clusterId = clusterId,
            frequency = result.frequency,
            duration = result.duration,
            isActive = false
        )
        _currentDetection.value = detection

        // Save to database
        database.soundDetectionDao().insertDetection(detection)
    }

    private suspend fun findOrCreateCluster(frequency: Float, duration: Long): String = withContext(Dispatchers.Default) {
        val clusters = database.soundDetectionDao().getUnknownSoundClusters().first()
        
        // Find similar cluster (within 10% frequency and 20% duration)
        for (cluster in clusters) {
            if (cluster.clusterId != null && cluster.frequency != null && cluster.duration != null) {
                val freqDiff = abs(cluster.frequency!! - frequency) / frequency.coerceAtLeast(1f)
                val durDiff = abs(cluster.duration!! - duration).toFloat() / duration.coerceAtLeast(1L).toFloat()
                
                if (freqDiff < 0.1f && durDiff < 0.2f) {
                    return@withContext cluster.clusterId!!
                }
            }
        }
        
        // Create new cluster
        "cluster_${System.currentTimeMillis()}_${frequency.toInt()}_${duration}"
    }

    suspend fun labelUnknownSound(
        labelName: String,
        useExistingLabel: Boolean = false,
        existingLabelId: Long? = null,
        detectionId: Long? = null
    ) = withContext(Dispatchers.IO) {
        val currentDet = if (detectionId != null) {
            database.soundDetectionDao().getDetectionById(detectionId)
        } else {
            _currentDetection.value
        }
        if (currentDet == null || currentDet.labelName != null) return@withContext

        val labelId: Long
        if (useExistingLabel && existingLabelId != null) {
            labelId = existingLabelId
            val label = database.soundLabelDao().getLabelById(existingLabelId)
            if (label == null) return@withContext
        } else {
            val label = SoundLabel(
                name = labelName,
                detectionCount = 1,
                confidenceThreshold = 0.7f,
                volumeMultiplier = 1.0f,
                isActive = true,
                isRecording = false
            )
            labelId = database.soundLabelDao().insertLabel(label)
        }

        // Get all detections in the same cluster
        val clusterId = currentDet.clusterId
        val clusterDetections = if (clusterId != null) {
            database.soundDetectionDao().getDetectionsByClusterSorted(clusterId)
        } else {
            listOf(currentDet)
        }

        // Note: MediaPipe uses pre-trained models, so we don't need to "learn" sounds
        // The classification will happen automatically based on the model

        // Update all detections in the cluster
        clusterDetections.forEach { detection ->
            val updatedDetection = detection.copy(
                labelId = labelId,
                labelName = labelName,
                clusterId = null
            )
            database.soundDetectionDao().updateDetection(updatedDetection)
        }

        if (detectionId == null) {
            val updatedDetection = currentDet.copy(
                labelId = labelId,
                labelName = labelName,
                clusterId = null
            )
            database.soundDetectionDao().updateDetection(updatedDetection)
            _currentDetection.value = updatedDetection
            _unknownSoundCount.value = 0
        } else {
            val remainingUnknown = database.soundDetectionDao().getUnknownSoundClusters().first().size
            _unknownSoundCount.value = remainingUnknown
        }
    }

    suspend fun labelPerson(personName: String) = withContext(Dispatchers.IO) {
        val currentDet = _currentDetection.value
        if (currentDet == null || !currentDet.isPerson) return@withContext

        // Create new person label
        val person = com.soundman.app.data.PersonLabel(
            name = personName,
            detectionCount = 1,
            volumeMultiplier = 1.0f,
            isActive = true
        )
        val personId = database.personLabelDao().insertPerson(person)

        // Learn from current detection
        currentDet.audioData?.let { audioData ->
            mediaPipeClassifier.learnPersonVoice(personId, personName, audioData)
        }

        // Assign person label
        val personLabelName = personIdMap.getOrPut(personId) {
            val labelName = "Person%02d".format(nextPersonId++)
            database.personLabelDao().updatePerson(person.copy(name = labelName))
            labelName
        }

        // Update current detection
        val updatedDetection = currentDet.copy(
            personId = personId,
            labelName = personLabelName
        )
        database.soundDetectionDao().insertDetection(updatedDetection)
        _currentDetection.value = updatedDetection
    }

    suspend fun updateSoundLabelSettings(
        labelId: Long,
        volumeMultiplier: Float,
        isMuted: Boolean,
        reverseToneEnabled: Boolean,
        isActive: Boolean? = null,
        isRecording: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val label = database.soundLabelDao().getLabelById(labelId)
        if (label != null) {
            val updated = label.copy(
                volumeMultiplier = volumeMultiplier,
                isMuted = isMuted,
                reverseToneEnabled = reverseToneEnabled,
                isActive = isActive ?: label.isActive,
                isRecording = isRecording ?: label.isRecording
            )
            database.soundLabelDao().updateLabel(updated)
        }
    }

    suspend fun updatePersonSettings(
        personId: Long,
        volumeMultiplier: Float,
        isMuted: Boolean,
        isActive: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val person = database.personLabelDao().getPersonById(personId)
        if (person != null) {
            val updated = person.copy(
                volumeMultiplier = volumeMultiplier,
                isMuted = isMuted,
                isActive = isActive ?: person.isActive
            )
            database.personLabelDao().updatePerson(updated)
        }
    }

    suspend fun setLiveMicEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        _isLiveMicEnabled.value = enabled
    }

    suspend fun getUnknownSoundClusters() = withContext(Dispatchers.IO) {
        database.soundDetectionDao().getUnknownSoundClusters().first()
    }

    suspend fun toggleSoundActive(labelId: Long, isActive: Boolean) = withContext(Dispatchers.IO) {
        database.soundLabelDao().setActive(labelId, isActive)
    }

    suspend fun toggleSoundRecording(labelId: Long, isRecording: Boolean) = withContext(Dispatchers.IO) {
        database.soundLabelDao().setRecording(labelId, isRecording)
    }

    suspend fun togglePersonActive(personId: Long, isActive: Boolean) = withContext(Dispatchers.IO) {
        database.personLabelDao().setActive(personId, isActive)
    }
}
