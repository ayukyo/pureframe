# 纯帧 ProGuard 规则

# 保留 Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 保留 Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# 保留 ExoPlayer
-keep class androidx.media3.** { *; }

# 保留 Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# 保留数据类
-keep class com.pureframe.player.data.model.** { *; }
-keep class com.pureframe.player.data.local.entity.** { *; }
