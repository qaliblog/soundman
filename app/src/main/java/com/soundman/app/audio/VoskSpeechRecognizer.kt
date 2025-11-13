package com.soundman.app.audio

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TranscriptionResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float
)

class VoskSpeechRecognizer(private val context: Context) {
    // Use reflection to access Vosk classes to avoid import issues
    private var model: Any? = null
    private var recognizer: Any? = null
    private var isInitialized = false
    private var currentLanguage = "en" // "en" or "fa" (Persian)
    private val sampleRate = 16000

    suspend fun initialize(language: String = "en") = withContext(Dispatchers.IO) {
        try {
            currentLanguage = language
            val modelPath = copyModelFromAssets(language)
            
            if (modelPath == null) {
                Log.e("VoskSpeechRecognizer", "Failed to copy model from assets")
                isInitialized = false
                return@withContext
            }

            // Use reflection to load Vosk classes
            val modelClass = try {
                Class.forName("com.alphacephei.vosk.Model")
            } catch (e: ClassNotFoundException) {
                try {
                    Class.forName("net.sourceforge.vosk.Model")
                } catch (e2: ClassNotFoundException) {
                    Log.e("VoskSpeechRecognizer", "Vosk Model class not found", e2)
                    isInitialized = false
                    return@withContext
                }
            }
            
            val recognizerClass = try {
                Class.forName("com.alphacephei.vosk.Recognizer")
            } catch (e: ClassNotFoundException) {
                try {
                    Class.forName("net.sourceforge.vosk.Recognizer")
                } catch (e2: ClassNotFoundException) {
                    Log.e("VoskSpeechRecognizer", "Vosk Recognizer class not found", e2)
                    isInitialized = false
                    return@withContext
                }
            }

            model = modelClass.getConstructor(String::class.java).newInstance(modelPath)
            recognizer = recognizerClass.getConstructor(modelClass, Float::class.java).newInstance(model, sampleRate.toFloat())
            isInitialized = true
            Log.d("VoskSpeechRecognizer", "Vosk initialized with language: $language")
        } catch (e: Exception) {
            Log.e("VoskSpeechRecognizer", "Failed to initialize", e)
            isInitialized = false
        }
    }

    suspend fun changeLanguage(language: String) = withContext(Dispatchers.IO) {
        release()
        initialize(language)
    }

    suspend fun recognize(audioData: ByteArray): TranscriptionResult? = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized || recognizer == null) {
                return@withContext null
            }

            // Use reflection to call Vosk methods
            val acceptMethod = recognizer!!::class.java.getMethod("acceptWaveform", ByteArray::class.java, Int::class.java)
            val result = acceptMethod.invoke(recognizer, audioData, audioData.size) as? Boolean
            
            if (result == true) {
                val resultMethod = recognizer!!::class.java.getMethod("getResult")
                val jsonResult = resultMethod.invoke(recognizer) as? String
                val text = parseJsonResult(jsonResult)
                return@withContext TranscriptionResult(
                    text = text,
                    isFinal = true,
                    confidence = 1.0f
                )
            } else {
                val partialMethod = recognizer!!::class.java.getMethod("getPartialResult")
                val partialResult = partialMethod.invoke(recognizer) as? String
                val text = parseJsonResult(partialResult)
                if (text.isNotBlank()) {
                    return@withContext TranscriptionResult(
                        text = text,
                        isFinal = false,
                        confidence = 0.5f
                    )
                }
            }

            null
        } catch (e: Exception) {
            Log.e("VoskSpeechRecognizer", "Error recognizing speech", e)
            null
        }
    }

    private fun parseJsonResult(json: String?): String {
        if (json == null) return ""
        
        try {
            // Simple JSON parsing for "text" field
            val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]+)\"").find(json)
            return textMatch?.groupValues?.get(1) ?: ""
        } catch (e: Exception) {
            Log.e("VoskSpeechRecognizer", "Error parsing JSON", e)
            return ""
        }
    }

    private fun getModelPath(language: String): String {
        val modelsDir = File(context.filesDir, "vosk_models")
        modelsDir.mkdirs()
        
        return when (language) {
            "fa" -> File(modelsDir, "vosk-model-small-fa-0.42").absolutePath
            else -> File(modelsDir, "vosk-model-small-en-us-0.15").absolutePath
        }
    }

    private suspend fun copyModelFromAssets(language: String): String? = withContext(Dispatchers.IO) {
        try {
            val modelPath = getModelPath(language)
            val modelDir = File(modelPath)
            
            // Check if model already exists
            if (modelDir.exists() && File(modelDir, "am").exists()) {
                return@withContext modelPath
            }
            
            // Try to copy from assets - check both possible directory names
            val assetPath1 = "vosk_models/${getModelAssetName(language)}"
            val assetPath2 = when (language) {
                "fa" -> "vosk_models/vosk-model-fa-0.42" // Also check without "small"
                else -> assetPath1
            }
            
            for (assetPath in listOf(assetPath1, assetPath2)) {
                try {
                    val assetManager = context.assets
                    val files = assetManager.list(assetPath)
                    if (files != null && files.isNotEmpty()) {
                        modelDir.mkdirs()
                        copyAssetDirectory(assetManager, assetPath, modelDir)
                        if (File(modelDir, "am").exists()) {
                            return@withContext modelPath
                        }
                    }
                } catch (e: Exception) {
                    // Try next path
                }
            }
            
            // Download if not in assets
            downloadModel(language)
            if (modelDir.exists() && File(modelDir, "am").exists()) {
                return@withContext modelPath
            }
            
            null
        } catch (e: Exception) {
            Log.e("VoskSpeechRecognizer", "Error copying model", e)
            null
        }
    }
    
    private fun getModelAssetName(language: String): String {
        return when (language) {
            "fa" -> "vosk-model-small-fa-0.42"
            else -> "vosk-model-small-en-us-0.15"
        }
    }
    
    private fun copyAssetDirectory(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        val files = assetManager.list(assetPath) ?: return
        for (file in files) {
            val assetFilePath = "$assetPath/$file"
            val targetFile = File(targetDir, file)
            
            if (assetManager.list(assetFilePath) != null) {
                // It's a directory
                targetFile.mkdirs()
                copyAssetDirectory(assetManager, assetFilePath, targetFile)
            } else {
                // It's a file
                assetManager.open(assetFilePath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private suspend fun downloadModel(language: String) = withContext(Dispatchers.IO) {
        try {
            val modelUrl = when (language) {
                "fa" -> "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip"
                else -> "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
            }

            val modelsDir = File(context.filesDir, "vosk_models")
            modelsDir.mkdirs()

            val url = URL(modelUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            val inputStream: InputStream = connection.inputStream
            val outputFile = File(modelsDir, "${language}_model.zip")
            val outputStream = FileOutputStream(outputFile)

            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            // Extract zip file (simplified - in production use proper zip extraction)
            Log.d("VoskSpeechRecognizer", "Model downloaded, extract manually or use zip library")
        } catch (e: Exception) {
            Log.e("VoskSpeechRecognizer", "Error downloading model", e)
        }
    }

    fun release() {
        try {
            recognizer?.let {
                val closeMethod = it::class.java.getMethod("close")
                closeMethod.invoke(it)
            }
            recognizer = null
            model?.let {
                val closeMethod = it::class.java.getMethod("close")
                closeMethod.invoke(it)
            }
            model = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e("VoskSpeechRecognizer", "Error releasing resources", e)
        }
    }
}
