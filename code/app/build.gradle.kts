import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// release 签名：本机从 local.properties 读，CI 从环境变量读（GitHub Secrets 注入）。
// 密码/keystore 路径任何情况下不进 git。
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun envOr(key: String): String? =
    System.getenv("PF_${key.uppercase()}") ?: keystoreProps.getProperty("pureframe.$key")

android {
    namespace = "com.pureframe.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pureframe.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 防二次打包：把发布签名的 SHA-256 注入 BuildConfig，运行时自校验。
        // debug / 未配置 keystore 的构建注入 "skip"（SignatureGuard 自动跳过）。
        val sha256 = keystoreProps.getProperty("pureframe.signingSha256")
            ?: System.getenv("PF_SIGNING_SHA256")
            ?: "skip"
        buildConfigField("String", "ORIGINAL_SIGNING_SHA256", "\"$sha256\"")
    }

    signingConfigs {
        create("release") {
            val storePath = envOr("storeFile")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = envOr("storePassword")
                keyAlias = envOr("keyAlias")
                keyPassword = envOr("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 只有本机/CI 拿到 keystore 时才签名，否则保持未签名可构建
            val hasKeystore = keystoreProps.getProperty("pureframe.storeFile") != null ||
                System.getenv("PF_STOREFILE") != null
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else null
        }
        debug {
            // debug 包默认不做 R8 裁剪，material-icons-extended 的 1 万多个图标类
            // 会被全部打进 dex（实测 59MB dex / 20 个 vdex），冷启动时 ART 需要
            // 解压全部 vdex，主线程被阻塞约 1.9 秒 —— 这就是"打开 APP 很慢"的根因。
            // 打开代码裁剪后只保留实际引用的图标，dex 大幅缩小、冷启动恢复正常；
            // 用 -dontobfuscate 保留原始类名，崩溃堆栈仍然可读。
            isMinifyEnabled = true
            isShrinkResources = true
            applicationIdSuffix = ".debug"
            // debug 不做日志剔除（proguard-rules.pro 的 assumenosideeffects 对
            // debug 有副作用），只带裁剪规则文件
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-debug.pro"
            )
            // debug 包强制跳过签名自校验：defaultConfig 注入的 release hash 会让
            // 本机 debug 包（debug 签名 ≠ release 签名）启动即自杀（实测踩坑）
            buildConfigField("String", "ORIGINAL_SIGNING_SHA256", "\"skip\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG 供运行时判定是否植入日志树（release 不打日志）
        buildConfig = true
    }

    // Compose Compiler 已由 org.jetbrains.kotlin.plugin.compose 插件管理
    // （Kotlin 2.0 起版本与 Kotlin 一致，不再需要 kotlinCompilerExtensionVersion）

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // DocumentFile - SAF 目录遍历（自定义视频扫描目录）
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ExoPlayer - 视频播放（1.9.3：1.9.x 末版 patch，含 decoder extensions NPE 修复 #2965；
    // 版本与 nextlib-media3ext 1.9.3-0.12.0 对齐。1.10.1+ 需要 compileSdk 36 + AGP/Gradle
    // 构建链升级，另行任务推进——届时把四模块与 nextlib 一起抬到 1.10.1-0.13.0）
    val media3Version = "1.9.3"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-cast:$media3Version")
    // HLS 流媒体播放（m3u8，需求 2.1.3）
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    // ffmpeg 软解扩展（DTS/AC3/EAC3 音轨 + H.264/HEVC/VP9 软解兜底；GPL-3.0。
    // 注意：不含 RealVideo(rmvb)/WMV 解码器，这两类容器依旧放不了。
    // 版本选 1.8.0-0.9.0：0.10.0 起上游改用新 Kotlin 编译（元数据 2.2/2.3），本项目
    // Kotlin 2.0.21 编译器无法读取（实测 1.9.1-0.11.0=2.3.0、1.9.3-0.12.0=2.2.0 均失败）。
    // 待 Task #10 构建链升级（Kotlin 2.2+）后同步升 1.10.1-0.13.0
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.8.0-0.9.0")
    // Coil - 图片加载
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")

    // Hilt - 依赖注入（2.51.1：Kotlin 2.0 元数据需要，2.48 无法读取）
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Timber - 日志框架
    implementation("com.jakewharton.timber:timber:5.0.1")

    // libtorrent4j - BitTorrent 下载引擎
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39")

    // UPnPCast - DLNA/UPnP 投屏（coroutine API，MIT）
    implementation("com.github.yinnho:UPnPCast:v1.3.0")
    // 自实现 ContentUrlProvider 的 file server（UPnPCast 用 implementation 引入，app 不可见）
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // UPnPCast 传递依赖把 androidx.core / recyclerview 抬到 1.16/1.4（要求 compileSdk 35
    // + AGP 8.6，本项目 compileSdk 34 + AGP 8.2）。constraints 压不住显式依赖，
    // 用 force 强制回兼容版本——app 未使用这些库 1.13/1.4 之后的 API，无功能影响
    constraints {
        implementation("androidx.core:core:1.13.1")
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.recyclerview:recyclerview:1.3.2")
    }
    configurations.all {
        resolutionStrategy {
            force("androidx.core:core:1.13.1")
            force("androidx.core:core-ktx:1.13.1")
            force("androidx.recyclerview:recyclerview:1.3.2")
        }
    }

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
