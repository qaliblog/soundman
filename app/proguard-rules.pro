# Add project specific ProGuard rules here.
-keep class com.soundman.app.** { *; }
-keepclassmembers class com.soundman.app.** { *; }

# Keep TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keepclassmembers class org.tensorflow.lite.support.** { *; }

# Suppress warnings for AutoValue (compile-time only, not needed at runtime)
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**

# Keep any AutoValue generated classes if they exist
-keep @com.google.auto.value.AutoValue class * { *; }
-keepclassmembers class * {
    @com.google.auto.value.AutoValue.Builder <methods>;
}
