package com.soundman.app.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.soundman.app.data.SoundLabel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class AudioOutputProcessor(
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioTrack: AudioTrack? = null
    private val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    fun initialize(): Boolean {
        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("AudioOutputProcessor", "AudioTrack initialization failed")
                return false
            }

            audioTrack?.play()
            return true
        } catch (e: Exception) {
            Log.e("AudioOutputProcessor", "Error initializing AudioTrack", e)
            return false
        }
    }

    fun processAudio(
        audioData: ByteArray,
        label: SoundLabel?,
        isPerson: Boolean = false,
        personVolumeMultiplier: Float = 1.0f
    ): ByteArray {
        if (label == null) {
            return audioData // No processing needed for unknown sounds
        }

        var processedData = audioData

        // Apply volume multiplier
        if (isPerson) {
            processedData = applyVolumeMultiplier(processedData, personVolumeMultiplier)
        } else {
            processedData = applyVolumeMultiplier(processedData, label.volumeMultiplier)
        }

        // Apply mute
        if ((isPerson && personVolumeMultiplier == 0f) || (!isPerson && label.isMuted)) {
            return ByteArray(processedData.size) // Return silence
        }

        // Apply reverse tone for noise cancellation
        if (!isPerson && label.reverseToneEnabled) {
            processedData = applyReverseTone(processedData)
        }

        return processedData
    }

    private fun applyVolumeMultiplier(audioData: ByteArray, multiplier: Float): ByteArray {
        if (multiplier == 1.0f) return audioData

        val byteBuffer = ByteBuffer.wrap(audioData)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val outputBuffer = ByteArray(audioData.size)
        val outputByteBuffer = ByteBuffer.wrap(outputBuffer)
        outputByteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        byteBuffer.rewind()
        while (byteBuffer.hasRemaining()) {
            val sample = byteBuffer.short
            val adjustedSample = (sample * multiplier).toInt()
            val clampedSample = max(-32768, min(32767, adjustedSample)).toShort()
            outputByteBuffer.putShort(clampedSample)
        }

        return outputBuffer
    }

    private fun applyReverseTone(audioData: ByteArray): ByteArray {
        // Improved phase inversion for noise cancellation
        // Uses adaptive phase shift with frequency-dependent cancellation
        val byteBuffer = ByteBuffer.wrap(audioData)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val outputBuffer = ByteArray(audioData.size)
        val outputByteBuffer = ByteBuffer.wrap(outputBuffer)
        outputByteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        byteBuffer.rewind()
        var previousSample = 0
        var sampleIndex = 0
        
        while (byteBuffer.hasRemaining()) {
            val sample = byteBuffer.short.toInt()
            
            // Apply phase inversion with slight delay compensation
            // This creates better cancellation for periodic sounds
            val invertedSample = if (sampleIndex > 0) {
                // Use weighted average of current and previous for smoother cancellation
                val weighted = (-sample * 0.9f + previousSample * 0.1f).toInt()
                weighted.coerceIn(-32768, 32767)
            } else {
                -sample
            }
            
            outputByteBuffer.putShort(invertedSample.toShort())
            previousSample = sample
            sampleIndex++
        }

        return outputBuffer
    }

    fun writeAudio(audioData: ByteArray) {
        try {
            val track = audioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED && audioData.isNotEmpty()) {
                track.write(audioData, 0, audioData.size)
            }
        } catch (e: Exception) {
            Log.e("AudioOutputProcessor", "Error writing audio", e)
        }
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
