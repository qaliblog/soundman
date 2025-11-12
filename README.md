# SoundMan - Intelligent Sound Detection & Control App

SoundMan is an advanced Android application that detects, classifies, and controls sounds in real-time. It's designed to work optimally with earbuds, allowing users to amplify, mute, or apply noise cancellation to specific sounds.

## Features

### 🎵 Sound Detection & Classification
- Real-time sound detection and classification
- Machine learning-based sound pattern recognition
- Automatic learning from labeled sounds
- Confidence scoring for detections

### 👤 Person Voice Detection
- Separate detection for human voices
- Person-specific voice recognition
- Individual volume control per person
- Voice stream isolation to avoid confusion with other sounds

### 🎚️ Advanced Audio Control
- **Volume Control**: Increase or decrease volume for specific sounds (0-200%)
- **Mute**: Completely mute specific sounds
- **Reverse Tone**: Apply phase inversion (noise cancellation) to eliminate sounds
- **Real-time Processing**: All controls apply instantly to detected sounds

### 📊 Learning System
- Learn from unknown sounds by labeling them
- Automatic pattern recognition after labeling
- Detection count tracking
- Confidence threshold adjustment

### 🎨 Modern UI
- Beautiful Material Design 3 interface
- Real-time detection status
- Sound and person management tabs
- Easy-to-use settings dialogs
- Detection history

## Requirements

- Android 8.0 (API 26) or higher
- Microphone permission
- Recommended: Bluetooth earbuds for best experience

## Building

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17 or higher
- Android SDK 34

### Build Steps

1. Clone the repository:
```bash
git clone git@github.com:qaliblog/soundman.git
cd soundman
```

2. Open in Android Studio and sync Gradle

3. Build the release APK:
```bash
./gradlew assembleRelease
```

The APK will be located at `app/build/outputs/apk/release/app-release.apk`

## Usage

1. **Start Detection**: Tap the "Start" button to begin sound detection
2. **Label Unknown Sounds**: When unknown sounds are detected, tap "Label" to name them
3. **Configure Settings**: Tap the settings icon on any sound or person to adjust:
   - Volume multiplier (0-200%)
   - Mute toggle
   - Reverse tone (noise cancellation) for sounds
4. **Monitor Detections**: View real-time detection status and confidence scores

## How It Works

### Sound Detection Flow
1. Audio is captured from the microphone in real-time
2. Audio features are extracted (RMS, Zero Crossing Rate, Spectral features)
3. Features are compared against learned patterns
4. If a match is found above the confidence threshold, the sound is identified
5. Audio processing is applied based on label settings
6. Processed audio is output to earbuds/headphones

### Learning Process
1. Unknown sounds are detected and counted
2. User labels the sound (e.g., "breath", "snap", "door")
3. The system learns the pattern from recent detections
4. Future detections of the same sound are automatically recognized
5. Detection confidence increases with more learning samples

### Person Voice Detection
1. Voice characteristics are extracted from audio
2. Patterns are learned when a person is labeled
3. Voice streams are isolated to avoid confusion with background sounds
4. Individual volume controls apply to each person's voice

## Technical Details

### Architecture
- **MVVM Pattern**: ViewModel-based architecture
- **Room Database**: Local storage for labels and detections
- **Coroutines & Flow**: Asynchronous processing and reactive UI
- **Jetpack Compose**: Modern declarative UI framework

### Audio Processing
- Sample Rate: 44.1 kHz
- Format: 16-bit PCM
- Real-time processing with low latency
- Audio effects: Echo cancellation, noise suppression, automatic gain control

### Machine Learning
- Feature extraction: MFCC-like features, spectral analysis
- Pattern matching: Similarity-based classification
- Incremental learning: Patterns updated with each detection

## License

This project is private and proprietary.

## Contributing

This is a private repository. For issues or feature requests, please contact the repository owner.
