// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.9.3" apply false
    // Kotlin 2.2：nextlib-media3ext 1.10.1-0.13.0 用 Kotlin 2.2 元数据编译，
    // 2.0/2.1 编译器无法读取；KSP 用配套的 2.2.21-2.0.4
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    // Compose Compiler 自 Kotlin 2.0 起并入 Kotlin 仓库，版本必须与 Kotlin 一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
    id("com.google.dagger.hilt.android") version "2.56.2" apply false
}
