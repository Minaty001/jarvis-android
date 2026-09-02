-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.jarvis.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# OkHttp & WebSockets
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
