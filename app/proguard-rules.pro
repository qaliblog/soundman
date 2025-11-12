# Add project specific ProGuard rules here.
-keep class com.soundman.app.** { *; }
-keepclassmembers class com.soundman.app.** { *; }

# Keep TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
