package com.soundman.app.audio

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
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
    
    // Buffer audio chunks to accumulate enough samples for MediaPipe (needs ~0.96 seconds)
    private val audioBuffer = mutableListOf<Float>()
    private val requiredSamples = (16000 * 0.96f).toInt() // ~15360 samples at 16kHz

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // MediaPipe AudioClassifier doesn't require a model file - it uses YAMNet internally
            // Try to initialize without specifying a model path first
            val baseOptions = try {
                BaseOptions.builder()
                    .build()
            } catch (e: Exception) {
                Log.w("MediaPipeSoundClassifier", "Failed to create BaseOptions without model", e)
                // Try with model path as fallback
                try {
                    BaseOptions.builder()
                        .setModelAssetPath("yamnet.tflite")
                        .build()
                } catch (e2: Exception) {
                    Log.w("MediaPipeSoundClassifier", "YAMNet model not found, will use fallback", e2)
                    null
                }
            }

            if (baseOptions != null) {
                // Use reflection to create AudioClassifierOptions
                try {
                    val optionsClass = Class.forName("com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierOptions")
                    val builderClass = Class.forName("com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierOptions\$Builder")
                    val builder = builderClass.getMethod("builder").invoke(null)
                    builderClass.getMethod("setBaseOptions", BaseOptions::class.java).invoke(builder, baseOptions)
                    builderClass.getMethod("setRunningMode", RunningMode::class.java).invoke(builder, RunningMode.AUDIO_STREAM)
                    // Lower threshold to detect more sounds
                    builderClass.getMethod("setScoreThreshold", Float::class.java).invoke(builder, 0.1f)
                    val options = builderClass.getMethod("build").invoke(builder)
                    
                    val createMethod = AudioClassifier::class.java.getMethod("createFromOptions", Context::class.java, optionsClass)
                    audioClassifier = createMethod.invoke(null, context, options) as? AudioClassifier
                    isInitialized = true
                    Log.d("MediaPipeSoundClassifier", "MediaPipe AudioClassifier initialized successfully")
                } catch (e: Exception) {
                    Log.e("MediaPipeSoundClassifier", "Failed to create AudioClassifierOptions", e)
                    e.printStackTrace()
                    isInitialized = false
                }
            } else {
                Log.w("MediaPipeSoundClassifier", "Using fallback classifier - BaseOptions not available")
                isInitialized = false
            }
        } catch (e: Exception) {
            Log.e("MediaPipeSoundClassifier", "Failed to initialize", e)
            e.printStackTrace()
            isInitialized = false
        }
    }

    suspend fun classifySound(
        audioData: ByteArray,
        sampleRate: Int = 44100, // Default is 44100, but MediaPipe needs 16kHz
        personLabels: List<com.soundman.app.data.PersonLabel>
    ): MediaPipeDetectionResult = withContext(Dispatchers.Default) {
        try {
            // Convert audio to float array and resample to 16kHz if needed
            val floatSamples = convertToFloatArray(audioData, sampleRate)
            val resampledSamples = if (sampleRate != 16000) {
                resampleAudio(floatSamples, sampleRate, 16000)
            } else {
                floatSamples
            }
            
            // Add to buffer
            audioBuffer.addAll(resampledSamples.toList())
            
            // Keep buffer size manageable (keep last 2 seconds)
            val maxBufferSize = 16000 * 2
            if (audioBuffer.size > maxBufferSize) {
                audioBuffer.removeAt(0)
            }
            
            // Check for person voice first using custom detection (use original samples)
            val personResult = detectPersonVoice(floatSamples, personLabels)
            if (personResult != null) {
                return@withContext personResult
            }

            // Use MediaPipe AudioClassifier for sound classification
            // Only classify when we have enough samples
            if (isInitialized && audioClassifier != null && audioBuffer.size >= requiredSamples) {
                try {
                    // Use the buffered audio for classification
                    val bufferArray = audioBuffer.take(requiredSamples).toFloatArray()
                    
                        // Use reflection to call classify method
                        // MediaPipe AudioClassifier.classify() expects FloatArray and sample rate (16kHz)
                        val classifyMethod = audioClassifier!!::class.java.getMethod("classify", FloatArray::class.java, Int::class.java)
                        val result = classifyMethod.invoke(audioClassifier, bufferArray, 16000)
                        
                        // Remove processed samples from buffer (keep some overlap)
                        val overlapSamples = 1600 // 0.1 seconds overlap
                        val samplesToRemove = (requiredSamples - overlapSamples).coerceAtMost(audioBuffer.size)
                        repeat(samplesToRemove) {
                            if (audioBuffer.isNotEmpty()) audioBuffer.removeAt(0)
                        }
                        
                        if (result != null) {
                            // Get classifications from result using reflection
                            val resultClass = result::class.java
                            val classificationsMethod = resultClass.getMethod("getClassifications")
                            val classifications = classificationsMethod.invoke(result) as? List<*>
                            
                            if (classifications != null && classifications.isNotEmpty()) {
                                val topClassification = classifications[0]
                                val classificationClass = topClassification!!::class.java
                                
                                // Get categories
                                val categoriesMethod = classificationClass.getMethod("getCategories")
                                val categories = categoriesMethod.invoke(topClassification) as? List<*>
                                
                                if (categories != null && categories.isNotEmpty()) {
                                    // Get top category
                                    val topCategory = categories[0]
                                    val categoryClass = topCategory!!::class.java
                                    
                                    // Get category name and score
                                    val categoryNameMethod = categoryClass.getMethod("getCategoryName")
                                    val scoreMethod = categoryClass.getMethod("getScore")
                                    
                                    val label = categoryNameMethod.invoke(topCategory) as? String
                                    val score = (scoreMethod.invoke(topCategory) as? Number)?.toFloat() ?: 0f
                                    
                                    Log.d("MediaPipeSoundClassifier", "MediaPipe detected: $label (confidence: $score)")
                                    
                                    // Lower threshold to 0.1f to detect more sounds
                                    // Only filter out person sounds
                                    if (label != null && score > 0.1f && !isPersonSound(label)) {
                                        val frequency = extractFrequency(floatSamples, sampleRate)
                                        val duration = (audioData.size / 2.0 / sampleRate * 1000).toLong()

                                        Log.i("MediaPipeSoundClassifier", "Accepted sound: $label (confidence: $score)")
                                        return@withContext MediaPipeDetectionResult(
                                            label = label,
                                            confidence = score,
                                            isPerson = false,
                                            frequency = frequency,
                                            duration = duration
                                        )
                                    } else if (label != null) {
                                        Log.d("MediaPipeSoundClassifier", "Rejected sound: $label (score: $score, isPerson: ${isPersonSound(label)})")
                                    }
                                } else {
                                    Log.d("MediaPipeSoundClassifier", "No categories in classification result")
                                }
                            } else {
                                Log.d("MediaPipeSoundClassifier", "No classifications in result")
                            }
                        } else {
                            Log.d("MediaPipeSoundClassifier", "Classification result is null")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MediaPipeSoundClassifier", "Error in MediaPipe classification", e)
                    e.printStackTrace()
                    // Fall through to unknown sound
                }
            } else {
                Log.d("MediaPipeSoundClassifier", "MediaPipe not initialized (isInitialized: $isInitialized, classifier: ${audioClassifier != null})")
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
        // YAMNet model classifies speech-related sounds, we want to handle those separately
        val personKeywords = listOf(
            "Speech", "Human", "Voice", "Speaking", "Talk", "Conversation",
            "Human voice", "Speech", "Narration, monologue", "Conversation",
            "Whispering", "Child speech, kid speaking", "Babbling"
        )
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
