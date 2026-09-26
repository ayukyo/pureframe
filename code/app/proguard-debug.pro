# debug 包专用的代码裁剪规则
#
# 为什么 debug 也要开 R8：
#   debug 默认 isMinifyEnabled=false，组件库 material-icons-extended 携带的
#   1 万多个图标类会全部进入 dex（实测 dex 总量 59MB / 20 个 vdex）。
#   ART 在冷启动时需要逐个解压 vdex，主线程被阻塞约 1.9 秒 ——
#   这就是"打开 APP 很慢"的根因。开启裁剪后只保留实际引用的类，
#   dex 大幅缩小，冷启动恢复到百毫秒级。

# 保留原始类名/方法名，崩溃堆栈、日志与调试体验不受影响
-dontobfuscate

# 保留行号，便于定位崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# libtorrent4j 通过 JNI 按类名/方法名回调，必须整体保留
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
