# RemotePad ProGuard rules

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# RemotePad model and network (used for JSON serialization)
-keep class com.remotepad.model.** { *; }
-keep class com.remotepad.network.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**
