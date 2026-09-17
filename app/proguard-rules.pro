# MarkQ R8 / ProGuard. Keep WebDAV, OkHttp, Cronet, Room, serialization, Coil, WorkManager.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, Exception
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class kotlin.Metadata { *; }

-keep class com.markq.** { *; }
-keepclassmembers class com.markq.** { *; }
-keep class com.markq.data.remote.IncrementalSyncWorker { *; }

# kotlinx.serialization
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.markq.**$$serializer { *; }
-keepclassmembers class com.markq.** {
    *** Companion;
}
-keepclasseswithmembers class com.markq.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Cronet / HTTP3 DoH
-keep class org.chromium.** { *; }
-keep class org.chromium.net.** { *; }
-dontwarn org.chromium.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# WorkManager
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Compose / coroutines
-keep class androidx.compose.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
