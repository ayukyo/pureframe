# 纯帧 ProGuard 规则（release 防反编译核心配置）
#
# 原则：除反射/JNI/序列化必需外，一律交给 R8 混淆。
# 业务类名/方法名混淆成 a.b.c 后，jadx 反编译看到的也是天书；
# 数据模型类之前被全量 -keep，等于把数据结构白送给逆向者——已删除。

# 保留 Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 保留 Hilt（反射注入，必须）
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# 保留 ExoPlayer（Media3 UI 通过 XML/反射引用部分类）
-keep class androidx.media3.** { *; }

# Room：数据库类与实体经反射/编译期生成代码访问，只保留类名与列字段注解，
# 不再全量保留方法体（之前 -keep class ... { *; } 会连逻辑一起保留）
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { <fields>; }
-keepnames @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# libtorrent4j 通过 JNI 按类名/方法名回调，必须整体保留（native 侧约定）
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

# Coil 图片加载使用反射构造解码器
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore / protobuf 生成的序列化类
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# 泛型签名与注解：kotlinx.serialization / Room 反射解析依赖
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# 静默常见的编译期告警依赖
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.eclipse.jetty.**

# Timber：release 构建不植入 DebugTree（Application 里已按 BuildConfig.DEBUG 判定），
# 这里把 Timber 的 log 调用直接从字节码剔除，即使有遗漏的 Timber.x() 也不会泄露日志
-assumenosideeffects class timber.log.Timber {
    public *** v(...);
    public *** d(...);
    public *** i(...);
    public *** w(...);
    public *** e(...);
    public *** wtf(...);
}

# android.util.Log.v/d/i 剔除（release 包禁日志，防 adb logcat 泄露内部信息）；
# Log.e/wtf 保留——崩溃现场的信息对线上排查仍有价值且泄露风险低
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# UPnPCast (DLNA 投屏)：库内通过反射构建 SOAP/XML 与 Ktor/NanoHTTPD 协议栈，
# 混淆会破坏 wsdl/action 反射查找，全量 keep
-keep class com.yinnho.upnpcast.** { *; }
-dontwarn com.yinnho.upnpcast.**

# Google Cast (play-services-cast-framework)：OptionsProvider 经 manifest 类名反射创建；
# GMS 库自带 consumer rules，只需补 dontwarn（gms 内部引用的可选类在某些设备缺失）
-dontwarn com.google.android.gms.cast.**
-dontwarn com.google.android.gms.internal.**

# Play Integrity API：库自带 consumer rules；GMS Task / Condition 类在某些
# 无 GMS 设备上缺失引用，补 dontwarn（探针只在 Play 渠道构建运行）
-dontwarn com.google.android.play.core.**
-dontwarn com.google.android.gms.**
