package com.soundman.app.audio

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class MediaPipeDetectionResult(
    val label: String?,
    val confidence: Float,
    val isPerson: Boolean = false,
    val personId: Long? = null,
    val frequency: Float? = null,
    val duration: Long? = null
)

class MediaPipeSoundClassifier(private val context: Context) {
    private var audioClassifier: AudioClassifier? = null
    private val personVoicePatterns = mutableMapOf<Long, MutableList<FloatArray>>()
    private var isInitialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // Try to use YAMNet model from assets, fallback if not available
            val baseOptions = try {
                BaseOptions.builder()
                    .setModelAssetPath("yamnet.tflite")
                    .build()
            } catch (e: Exception) {
                Log.w("MediaPipeSoundClassifier", "YAMNet model not found, using fallback", e)
                null
            }

            if (baseOptions != null) {
                val options = AudioClassifierOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.AUDIO_STREAM)
                    .setScoreThreshold(0.3f)
                    .build()

                audioClassifier = AudioClassifier.createFromOptions(context, options)
                isInitialized = true
                Log.d("MediaPipeSoundClassifier", "Initialized successfully")
            } else {
                Log.w("MediaPipeSoundClassifier", "Using fallback classifier")
                isInitialized = false
            }
        } catch (e: Exception) {
            Log.e("MediaPipeSoundClassifier", "Failed to initialize", e)
            isInitialized = false
        }
    }

    suspend fun classifySound(
        audioData: ByteArray,
        sampleRate: Int = 16000,
        personLabels: List<com.soundman.app.data.PersonLabel>
    ): MediaPipeDetectionResult = withContext(Dispatchers.Default) {
        try {
            // Convert audio to float array
            val floatSamples = convertToFloatArray(audioData, sampleRate)
            
            // Check for person voice first using custom detection
            val personResult = detectPersonVoice(floatSamples, personLabels)
            if (personResult != null) {
                return@withContext personResult
            }

            // Use MediaPipe for sound classification if available
            // Note: MediaPipe AudioClassifier API may require different input format
            // For now, we'll use fallback classification until MediaPipe is properly configured
            if (isInitialized && audioClassifier != null) {
                try {
                    // MediaPipe AudioClassifier requires AudioData object
                    // This is a simplified implementation - full implementation would need proper AudioData construction
                    Log.d("MediaPipeSoundClassifier", "MediaPipe classifier available but using fallback for now")
                } catch (e: Exception) {
                    Log.e("MediaPipeSoundClassifier", "Error in MediaPipe classification", e)
                }
            }

            // Unknown sound or low confidence
            val frequency = extractFrequency(floatSamples, sampleRate)
            val duration = (audioData.size / 2.0 / sampleRate * 1000).toLong()
            
            MediaPipeDetectionResult(
                label = null,
                confidence = 0f,
                isPerson = false,
                frequency = frequency,
                duration = duration
            )
        } catch (e: Exception) {
            Log.e("MediaPipeSoundClassifier", "Error classifying sound", e)
            classifySoundBasic(audioData, personLabels)
        }
    }

    private fun isPersonSound(label: String): Boolean {
        // Filter out human speech sounds from MediaPipe results
        val personKeywords = listOf("Speech", "Human", "Voice", "Speaking", "Talk", "Conversation")
        return personKeywords.any { label.contains(it, ignoreCase = true) }
    }

    private fun classifySoundBasic(
        audioData: ByteArray,
        personLabels: List<com.soundman.app.data.PersonLabel>
    ): MediaPipeDetectionResult {
        val floatSamples = convertToFloatArray(audioData, 16000)
        val personResult = detectPersonVoice(floatSamples, personLabels)
        
        if (personResult != null) {
            return personResult
        }

        val frequency = extractFrequency(floatSamples, 16000)
        val duration = (audioData.size / 2.0 / 16000 * 1000).toLong()

        return MediaPipeDetectionResult(
            label = null,
            confidence = 0f,
            isPerson = false,
            frequency = frequency,
            duration = duration
        )
    }

    private fun convertToFloatArray(audioData: ByteArray, sampleRate: Int): FloatArray {
        val samples = audioData.size / 2
        val floatSamples = FloatArray(samples)
        val byteBuffer = ByteBuffer.wrap(audioData)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until samples) {
            val sample = byteBuffer.short.toFloat() / Short.MAX_VALUE
            floatSamples[i] = sample
        }

        return floatSamples
    }

    private fun extractFrequency(audioData: FloatArray, sampleRate: Int): Float {
        if (audioData.isEmpty()) return 0f

        // Simple frequency estimation using zero crossing rate
        var zeroCrossings = 0
        for (i in 1 until audioData.size) {
            if ((audioData[i] >= 0) != (audioData[i - 1] >= 0)) {
                zeroCrossings++
            }
        }

        val frequency = (zeroCrossings / 2.0f) / (audioData.size / sampleRate.toFloat())
        return frequency.coerceIn(0f, sampleRate / 2f)
    }

    private fun detectPersonVoice(
        features: FloatArray,
        personLabels: List<com.soundman.app.data.PersonLabel>
    ): MediaPipeDetectionResult? {
        var bestMatch: Pair<Long, Float>? = null
        var bestConfidence = 0.6f

        for (person in personLabels) {
            val patterns = personVoicePatterns[person.id] ?: continue
            if (patterns.isEmpty()) continue

            val confidence = calculateSimilarity(features, patterns)
            if (confidence > bestConfidence) {
                bestConfidence = confidence
                bestMatch = Pair(person.id, confidence)
            }
        }

        return bestMatch?.let {
            MediaPipeDetectionResult(
                label = personLabels.find { p -> p.id == it.first }?.name,
                confidence = it.second,
                isPerson = true,
                personId = it.first
            )
        }
    }

    private fun calculateSimilarity(features: FloatArray, patterns: List<FloatArray>): Float {
        if (patterns.isEmpty()) return 0f

        var totalSimilarity = 0f
        for (pattern in patterns) {
            if (pattern.size != features.size) continue

            var similarity = 0f
            for (i in features.indices) {
                val diff = abs(features[i] - pattern[i])
                similarity += 1f / (1f + diff)
            }

            totalSimilarity += similarity / features.size
        }

        return (totalSimilarity / patterns.size).coerceIn(0f, 1f)
    }

    fun learnPersonVoice(personId: Long, personName: String, audioData: ByteArray) {
        val features = convertToFloatArray(audioData, 16000)
        val patterns = personVoicePatterns.getOrPut(personId) { mutableListOf() }
        patterns.add(features)

        if (patterns.size > 50) {
            patterns.removeAt(0)
        }
    }

    fun release() {
        audioClassifier?.close()
        audioClassifier = null
        isInitialized = false
    }
}
