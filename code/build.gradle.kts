// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.6.1" apply false
    // Kotlin 2.0：media3-cast 依赖的 play-services-cast-framework 22.x 用 Kotlin 2.1
    // 元数据编译，1.9.20 编译器无法读取（kspDebugKotlin 直接报 incompatible metadata）
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Compose Compiler 自 Kotlin 2.0 起并入 Kotlin 仓库，版本必须与 Kotlin 一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.26" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
