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

# Suppress warnings for javax.lang.model (not available on Android, only compile-time)
-dontwarn javax.lang.model.**
-dontwarn javax.lang.model.element.**
-dontwarn javax.lang.model.type.**
-dontwarn javax.lang.model.util.**

# Allow R8 to remove AutoValue shaded javapoet code that references javax.lang.model
# These are only needed at compile time, not runtime
-keep class autovalue.shaded.com.squareup.javapoet.** { *; }
-dontwarn autovalue.shaded.com.squareup.javapoet.**

# Allow removal of methods that reference missing javax.lang.model classes
-assumenosideeffects class autovalue.shaded.com.squareup.javapoet.TypeName {
    static *** get(javax.lang.model.type.TypeMirror, ...);
}
-assumenosideeffects class autovalue.shaded.com.squareup.javapoet.CodeBlock$Builder {
    *** argToType(...);
}
-assumenosideeffects class autovalue.shaded.com.squareup.javapoet.AnnotationSpec {
    *** build();
}
