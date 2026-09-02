-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.jarvis.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.TypeConverter { *; }
-dontwarn androidx.room.paging.**

# OkHttp & WebSockets
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Supabase
-keep class io.github.jan-tennert.supabase.** { *; }
-dontwarn io.github.jan-tennert.supabase.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-dontwarn androidx.compose.**

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Data classes (keep fields for serialization)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
