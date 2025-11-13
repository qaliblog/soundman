package com.soundman.app.audio

import android.content.Context
import android.util.Log
import com.soundman.app.data.SoundLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class DetectionResult(
    val label: String?,
    val confidence: Float,
    val isPerson: Boolean = false,
    val personId: Long? = null,
    val clusterId: String? = null
)

class SoundClassifier(private val context: Context) {
    private val soundPatterns = mutableMapOf<String, MutableList<FloatArray>>()
    private val personVoicePatterns = mutableMapOf<Long, MutableList<FloatArray>>()
    private val unknownSoundClusters = mutableMapOf<String, MutableList<FloatArray>>()

    suspend fun classifySound(
        audioData: ByteArray,
        knownLabels: List<SoundLabel>,
        personLabels: List<com.soundman.app.data.PersonLabel>
    ): DetectionResult = withContext(Dispatchers.Default) {
        try {
            val features = extractFeatures(audioData)

            // Check for person voice first
            val personResult = detectPersonVoice(features, personLabels)
            if (personResult != null) {
                return@withContext personResult
            }

            // Check for known sound labels
            var bestMatch: Pair<String, Float>? = null
            var bestConfidence = 0f

            for (label in knownLabels) {
                val patterns = soundPatterns[label.name] ?: continue
                if (patterns.isEmpty()) continue

                val confidence = calculateSimilarity(features, patterns)
                if (confidence > label.confidenceThreshold && confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestMatch = Pair(label.name, confidence)
                }
            }

            if (bestMatch != null) {
                DetectionResult(
                    label = bestMatch.first,
                    confidence = bestMatch.second,
                    isPerson = false
                )
            } else {
                // Check if this unknown sound matches any existing cluster
                val clusterId = findOrCreateCluster(features)
                DetectionResult(
                    label = null,
                    confidence = 0f,
                    isPerson = false,
                    clusterId = clusterId
                )
            }
        } catch (e: Exception) {
            Log.e("SoundClassifier", "Error classifying sound", e)
            DetectionResult(null, 0f, false, null, null)
        }
    }

    fun learnSound(labelName: String, audioData: ByteArray) {
        val features = extractFeatures(audioData)
        val patterns = soundPatterns.getOrPut(labelName) { mutableListOf() }
        patterns.add(features)
        
        // Keep only recent patterns (last 50)
        if (patterns.size > 50) {
            patterns.removeAt(0)
        }
    }

    fun learnPersonVoice(personId: Long, personName: String, audioData: ByteArray) {
        val features = extractFeatures(audioData)
        val patterns = personVoicePatterns.getOrPut(personId) { mutableListOf() }
        patterns.add(features)
        
        // Keep only recent patterns (last 50)
        if (patterns.size > 50) {
            patterns.removeAt(0)
        }
    }

    private fun extractFeatures(audioData: ByteArray): FloatArray {
        // Convert audio bytes to float array
        val samples = audioData.size / 2
        val floatSamples = FloatArray(samples)
        val byteBuffer = ByteBuffer.wrap(audioData)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until samples) {
            val sample = byteBuffer.short.toFloat() / Short.MAX_VALUE
            floatSamples[i] = sample
        }

        // Extract features: MFCC-like features, spectral features, etc.
        val features = mutableListOf<Float>()

        // 1. RMS (Root Mean Square) - energy
        val rms = sqrt(floatSamples.map { it * it }.average()).toFloat()
        features.add(rms)

        // 2. Zero Crossing Rate
        var zcr = 0
        for (i in 1 until floatSamples.size) {
            if ((floatSamples[i] >= 0) != (floatSamples[i - 1] >= 0)) {
                zcr++
            }
        }
        features.add(zcr.toFloat() / floatSamples.size)

        // 3. Spectral Centroid (simplified)
        val fftSize = kotlin.math.min(1024, floatSamples.size)
        val fftData = FloatArray(fftSize)
        System.arraycopy(floatSamples, 0, fftData, 0, kotlin.math.min(fftSize, floatSamples.size))
        
        // Simple spectral features
        val spectralCentroid = calculateSpectralCentroid(fftData)
        features.add(spectralCentroid)

        // 4. Spectral Rolloff
        val spectralRolloff = calculateSpectralRolloff(fftData)
        features.add(spectralRolloff)

        // 5. Additional statistical features
        val mean = floatSamples.average().toFloat()
        features.add(mean)

        val variance = floatSamples.map { (it - mean) * (it - mean) }.average().toFloat()
        features.add(sqrt(variance))

        // 6. Peak features
        val maxAmplitude = floatSamples.maxOrNull() ?: 0f
        features.add(maxAmplitude)

        return features.toFloatArray()
    }

    private fun calculateSpectralCentroid(data: FloatArray): Float {
        var weightedSum = 0f
        var magnitudeSum = 0f

        for (i in data.indices) {
            val magnitude = abs(data[i])
            weightedSum += i * magnitude
            magnitudeSum += magnitude
        }

        return if (magnitudeSum > 0) weightedSum / magnitudeSum else 0f
    }

    private fun calculateSpectralRolloff(data: FloatArray, threshold: Float = 0.85f): Float {
        val totalEnergy = data.sumOf { abs(it).toDouble() }.toFloat()
        var cumulativeEnergy = 0f
        var rolloffIndex = 0

        for (i in data.indices) {
            cumulativeEnergy += abs(data[i])
            if (cumulativeEnergy >= totalEnergy * threshold) {
                rolloffIndex = i
                break
            }
        }

        return rolloffIndex.toFloat() / data.size
    }

    private fun calculateSimilarity(features: FloatArray, patterns: List<FloatArray>): Float {
        if (patterns.isEmpty()) return 0f

        var totalSimilarity = 0f
        for (pattern in patterns) {
            if (pattern.size != features.size) continue
            
            var similarity = 0f
            var maxDiff = 0f
            
            for (i in features.indices) {
                val diff = abs(features[i] - pattern[i])
                maxDiff = max(maxDiff, diff)
                similarity += 1f / (1f + diff)
            }
            
            totalSimilarity += similarity / features.size
        }

        return (totalSimilarity / patterns.size).coerceIn(0f, 1f)
    }

    private fun detectPersonVoice(
        features: FloatArray,
        personLabels: List<com.soundman.app.data.PersonLabel>
    ): DetectionResult? {
        var bestMatch: Pair<Long, Float>? = null
        var bestConfidence = 0.6f // Threshold for person detection

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
            DetectionResult(
                label = personLabels.find { p -> p.id == it.first }?.name,
                confidence = it.second,
                isPerson = true,
                personId = it.first
            )
        }
    }

    fun findOrCreateCluster(features: FloatArray): String {
        val clusterThreshold = 0.7f // Similarity threshold for clustering
        
        // Find the best matching cluster
        for ((clusterId, clusterFeatures) in unknownSoundClusters) {
            if (clusterFeatures.isNotEmpty()) {
                val avgSimilarity = calculateSimilarity(features, clusterFeatures)
                if (avgSimilarity > clusterThreshold) {
                    // Add to existing cluster
                    clusterFeatures.add(features)
                    if (clusterFeatures.size > 20) {
                        clusterFeatures.removeAt(0) // Keep only recent 20
                    }
                    return clusterId
                }
            }
        }
        
        // Create new cluster
        val newClusterId = "cluster_${unknownSoundClusters.size + 1}_${System.currentTimeMillis()}"
        unknownSoundClusters[newClusterId] = mutableListOf(features)
        return newClusterId
    }

    fun getClusterFeatures(clusterId: String): List<FloatArray>? {
        return unknownSoundClusters[clusterId]
    }

    fun assignClusterToLabel(clusterId: String, labelName: String, audioDataList: List<ByteArray>) {
        // Learn all sounds in the cluster with the new label
        audioDataList.forEach { audioData ->
            learnSound(labelName, audioData)
        }
        // Remove the cluster as it's now labeled
        unknownSoundClusters.remove(clusterId)
    }
}
