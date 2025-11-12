package com.soundman.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AudioProcessor(
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    private val _audioData = MutableStateFlow<ByteArray?>(null)
    val audioData: StateFlow<ByteArray?> = _audioData.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    fun startRecording(): Boolean {
        if (isRecording) return true

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioProcessor", "AudioRecord initialization failed")
                return false
            }

            // Enable audio effects if available
            audioRecord?.audioSessionId?.let { sessionId ->
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)
                        acousticEchoCanceler?.enabled = true
                    }
                    if (AutomaticGainControl.isAvailable()) {
                        automaticGainControl = AutomaticGainControl.create(sessionId)
                        automaticGainControl?.enabled = true
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)
                        noiseSuppressor?.enabled = true
                    }
                } catch (e: Exception) {
                    Log.e("AudioProcessor", "Error setting up audio effects", e)
                }
            }

            audioRecord?.startRecording()
            isRecording = true

            // Start reading audio data in a separate thread
            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording && audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        _audioData.value = buffer.copyOf(bytesRead)
                        calculateAmplitude(buffer, bytesRead)
                    }
                }
            }.start()

            return true
        } catch (e: Exception) {
            Log.e("AudioProcessor", "Error starting recording", e)
            return false
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        acousticEchoCanceler?.release()
        automaticGainControl?.release()
        noiseSuppressor?.release()
    }

    private fun calculateAmplitude(buffer: ByteArray, bytesRead: Int) {
        var sum = 0.0
        val samples = bytesRead / 2 // 16-bit samples
        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until samples) {
            val sample = byteBuffer.short.toDouble()
            sum += abs(sample)
        }

        val average = sum / samples
        val amplitude = (average / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
        _amplitude.value = amplitude
    }

    fun getSampleRate(): Int = sampleRate
    fun getBufferSize(): Int = bufferSize
}
