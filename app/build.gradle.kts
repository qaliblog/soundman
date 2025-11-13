import java.net.URL
import java.util.zip.ZipInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.soundman.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.soundman.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use debug signing config for release to avoid signing key requirement
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

// Task to download and extract Vosk models
tasks.register("downloadVoskModels") {
    doLast {
        val assetsDir = file("src/main/assets/vosk_models")
        assetsDir.mkdirs()

        val models = mapOf(
            "en" to "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "fa" to "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip"
        )

        models.forEach { (lang, url) ->
            val modelName = when (lang) {
                "en" -> "vosk-model-small-en-us-0.15"
                "fa" -> "vosk-model-small-fa-0.42"
                else -> "vosk-model-small-$lang"
            }
            val modelDir = File(assetsDir, modelName)
            if (modelDir.exists() && File(modelDir, "am").exists()) {
                println("Model for $lang already exists, skipping download")
                return@forEach
            }

            println("Downloading Vosk model for $lang from $url")
            val zipFile = File(assetsDir, "${lang}_model.zip")
            
            try {
                URL(url).openStream().use { input ->
                    FileOutputStream(zipFile).use { output ->
                        input.copyTo(output)
                    }
                }
                println("Downloaded ${lang}_model.zip")

                // Extract zip file
                println("Extracting ${lang}_model.zip")
                ZipInputStream(FileInputStream(zipFile)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        // Remove the top-level folder name from the path
                        val entryPath = entry.name
                        val relativePath = if (entryPath.contains("/")) {
                            entryPath.substring(entryPath.indexOf("/") + 1)
                        } else {
                            entryPath
                        }
                        
                        if (relativePath.isEmpty()) {
                            entry = zip.nextEntry
                            continue
                        }
                        
                        val entryFile = File(modelDir, relativePath)
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile.mkdirs()
                            FileOutputStream(entryFile).use { output ->
                                zip.copyTo(output)
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
                println("Extracted model for $lang to $modelDir")
                
                // Clean up zip file
                zipFile.delete()
            } catch (e: Exception) {
                println("Error downloading/extracting model for $lang: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

// Make sure models are downloaded before building
tasks.named("preBuild").configure {
    dependsOn("downloadVoskModels")
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Audio Processing
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    
    // MediaPipe
    implementation("com.google.mediapipe:tasks-audio:0.10.8")
    implementation("com.google.mediapipe:tasks-core:0.10.8")
    
    // Vosk Speech Recognition
    implementation("com.alphacephei:vosk-android:0.3.47")
    
    // TensorFlow Lite for custom models (optional, for future ML enhancements)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    
    // AutoValue (required by TensorFlow Lite Support for R8/ProGuard)
    // Use version 1.8.1 to match MediaPipe's requirement
    // Use compileOnly for auto-value to avoid runtime dependencies on javax.lang.model
    implementation("com.google.auto.value:auto-value-annotations:1.8.1")
    compileOnly("com.google.auto.value:auto-value:1.8.1")
    
    // Exclude javax.lang.model from AutoValue if it's pulled in transitively
    configurations.all {
        exclude(group = "javax.lang.model", module = "*")
    }
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // Zip extraction for model download
    implementation("org.apache.commons:commons-compress:1.24.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
