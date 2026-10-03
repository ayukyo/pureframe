// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9.0 起内置 Kotlin（built-in Kotlin）：不再需要 org.jetbrains.kotlin.android
    // 插件，Kotlin 编译由 AGP 直接驱动（内置 KGP 2.2.10）。回退开关（临时）：
    // gradle.properties 加 android.builtInKotlin=false
    id("com.android.application") version "9.0.1" apply false
    // Compose Compiler 插件：版本需与内置 KGP（2.2.10）一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // KSP 2.3.x 起独立版本号（不再绑 Kotlin），2.3.1+ 明确支持 AGP 9.0 内置 Kotlin
    id("com.google.devtools.ksp") version "2.3.9" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}
