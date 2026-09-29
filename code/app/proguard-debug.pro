# debug 包专用的代码裁剪规则
#
# 为什么 debug 也要开 R8：
#   debug 默认 isMinifyEnabled=false，组件库 material-icons-extended 携带的
#   1 万多个图标类会全部进入 dex（实测 dex 总量 59MB / 20 个 vdex）。
#   ART 在冷启动时需要逐个解压 vdex，主线程被阻塞约 1.9 秒 ——
#   这就是"打开 APP 很慢"的根因。开启裁剪后只保留实际引用的类，
#   dex 大幅缩小，冷启动恢复到百毫秒级。
#
# 通用 keep 规则（libtorrent4j/coil/datastore 等）已统一放在 proguard-rules.pro，
# 本文件只放 debug 特有规则。

# 保留原始类名/方法名，崩溃堆栈、日志与调试体验不受影响
-dontobfuscate

# 保留行号，便于定位崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# debug 包保留完整日志。proguard-rules.pro 的 assumenosideeffects（Timber/Log
# 剔除）不再对 debug 生效 —— build.gradle.kts 中 debug 的 proguardFiles 已
# 不包含 proguard-rules.pro，两个 build type 的规则文件完全独立：
#   release: proguard-android-optimize.txt + proguard-rules.pro（混淆+剔除日志）
#   debug:   proguard-android-optimize.txt + proguard-debug.pro（只裁剪不混淆）
# 通用 keep 规则两份文件各自维护，如后续新增依赖请注意同步。

# ---- 通用 keep 规则（与 proguard-rules.pro 保持同步） ----

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# ExoPlayer（Media3）
-keep class androidx.media3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { <fields>; }
-keepnames @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# libtorrent4j（JNI 回调）
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.eclipse.jetty.**

# UPnPCast (DLNA 投屏)：库内通过反射构建 SOAP/XML 协议栈，混淆会破坏反射查找，全量 keep
-keep class com.yinnho.upnpcast.** { *; }
-dontwarn com.yinnho.upnpcast.**
