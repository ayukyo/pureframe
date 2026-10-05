import java.util.Properties

plugins {
    id("com.android.application")
    // AGP 9.0 内置 Kotlin：kotlin-android 插件已移除（内置 KGP 2.2.10 驱动编译）
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pureframe.player"
        minSdk = 26
        // 36：Google Play 自 2026-08-31 起新应用/更新强制要求；Android 15/16
        // 强制 edge-to-edge —— MainActivity 已 enableEdgeToEdge + Compose insets 适配
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"

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
        // Play 渠道分发密钥指纹（Play App Signing 重签后的证书 SHA-256）。
        // 仅 Play 渠道构建注入；其他构建 "skip" → IntegrityFallback 永不激活。
        val playSha256 = keystoreProps.getProperty("pureframe.playSigningSha256")
            ?: System.getenv("PF_EXPECTED_PLAY_SHA256")
            ?: "skip"
        buildConfigField("String", "EXPECTED_PLAY_SIGNING_SHA256", "\"$playSha256\"")
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
    // 注意：必须与运行时实际解析版本一致。media3-cast 1.10.1 传递依赖 compose-bom 2024.12.01
    // （material3 1.3.1），若此处声明旧 BOM（如 2023.10.01/m3 1.1.2），会形成"编译期 1.1.x /
    // 运行期 1.3.1"错位 —— AddDownloadDialog 使用的 AlertDialog(onDismissRequest, modifier) {}
    // trailing-lambda 重载是 material3 1.2.0 新增的，在 1.1.2 编译期签名与 1.3.1 运行期不符，
    // 运行时抛 NoSuchMethodError 闪退（其余对话框用全参重载不受影响）。
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Room Database（2.8.5：KSP2 下 2.6.x 处理器报 "unexpected jvm signature V"，
    // Room 侧修复在 2.7.0-alpha11+，取 2.8 稳定线）
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // DocumentFile - SAF 目录遍历（自定义视频扫描目录）
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ExoPlayer - 视频播放（1.10.1：跳过有 Firefox topcrash 回归 #3161 的 1.10.0；
    // 需 compileSdk 36。nextlib-media3ext 同步对齐 1.10.1-0.13.0）
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-cast:$media3Version")
    // HLS 流媒体播放（m3u8，需求 2.1.3）
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    // ffmpeg 软解扩展（DTS/AC3/EAC3 音轨 + H.264/HEVC/VP9 软解兜底；GPL-3.0。
    // 注意：不含 RealVideo(rmvb)/WMV 解码器，这两类容器依旧放不了。
    // 1.10.1-0.13.0：Kotlin 2.2 元数据 + 依赖 media3 1.10.1，与本项目
    // Kotlin 2.2.21 / media3 1.10.1 完全对齐（Task #10 构建链升级后可用）
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.10.1-0.13.0")
    // Coil - 图片加载
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")

    // Hilt - 依赖注入（2.60.1：2.57 及以下不支持 AGP 9.0 新 DSL（报
    // "Android BaseExtension not found"），AGP 9 支持自 2.59 起稳定）
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Timber - 日志框架
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Play Integrity API（1.6.0：2026-10 官方最新稳定线）。
    // 仅 IntegrityFallback 探针使用；Play 渠道构建 hash 不匹配时的二轮判定。
    implementation("com.google.android.play:integrity:1.6.0")

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
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
