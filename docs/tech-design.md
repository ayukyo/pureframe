# 纯帧 - 技术设计文档

> 纯粹观影，只留帧影

## 1. 技术架构

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        纯帧 App 架构                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Presentation Layer                    │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐    │   │
│  │  │ Home    │  │Download │  │ Player  │  │ Settings│    │   │
│  │  │ Screen  │  │ Screen  │  │ Screen  │  │ Screen  │    │   │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘    │   │
│  └───────┼────────────┼────────────┼────────────┼─────────┘   │
│          │            │            │            │              │
│  ┌───────┼────────────┼────────────┼────────────┼─────────┐   │
│  │       │        Domain Layer (ViewModels)      │         │   │
│  │  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐   │   │
│  │  │ HomeVM  │  │ DLVM    │  │PlayerVM │  │Settings │   │   │
│  │  │         │  │         │  │         │  │ VM      │   │   │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘   │   │
│  └───────┼────────────┼────────────┼────────────┼─────────┘   │
│          │            │            │            │              │
│  ┌───────┼────────────┼────────────┼────────────┼─────────┐   │
│  │       │        Data Layer (Repository)       │         │   │
│  │  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐   │   │
│  │  │ Video   │  │Download │  │ Player  │  │Settings │   │   │
│  │  │ Repo    │  │ Repo    │  │ Repo    │  │ Repo    │   │   │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘   │   │
│  └───────┼────────────┼────────────┼────────────┼─────────┘   │
│          │            │            │            │              │
│  ┌───────┼────────────┼────────────┼────────────┼─────────┐   │
│  │       │     Infrastructure Layer             │         │   │
│  │  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐   │   │
│  │  │ Room DB │  │Torrent  │  │ExoPlayer│  │DataStore│   │   │
│  │  │         │  │Engine   │  │         │  │         │   │   │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 技术栈

| 层级 | 技术 | 用途 |
|-----|------|-----|
| **语言** | Kotlin | 主要开发语言 |
| **UI** | Jetpack Compose | 现代化声明式 UI |
| **依赖注入** | Hilt | DI 管理 |
| **导航** | Navigation Compose | 页面导航 |
| **数据库** | Room | 本地数据存储 |
| **配置存储** | DataStore | 用户偏好设置 |
| **播放器** | ExoPlayer (Media3) | 视频播放引擎 |
| **下载引擎** | libtorrent4j | BitTorrent 下载 |
| **图片加载** | Coil | 视频缩略图加载 |
| **异步处理** | Coroutines + Flow | 异步数据流 |

---

## 2. 模块设计

### 2.1 模块结构

```
com.pureframe.player/
├── ui/                     # UI 层
│   ├── MainActivity.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   ├── screens/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── download/
│   │   │   ├── DownloadScreen.kt
│   │   │   ├── DownloadViewModel.kt
│   │   │   ├── AddDownloadDialog.kt
│   │   │   └── DownloadListItem.kt
│   │   ├── player/
│   │   │   ├── PlayerScreen.kt
│   │   │   ├── PlayerViewModel.kt
│   │   │   ├── PlayerControls.kt
│   │   │   └── GestureOverlay.kt
│   │   └── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   └ SettingsViewModel.kt
│   └── theme/
│       ├── Theme.kt
│       ├── Color.kt
│       └── Type.kt
│
├── domain/                 # 业务层
│   ├── model/
│   │   ├── VideoItem.kt
│   │   ├── DownloadTask.kt
│   │   ├── PlaybackState.kt
│   │   └── DownloadProgress.kt
│   ├── usecase/
│   │   ├── ScanVideosUseCase.kt
│   │   ├── PlayVideoUseCase.kt
│   │   ├── CreateDownloadTaskUseCase.kt
│   │   ├── GetDownloadProgressUseCase.kt
│   │   └ PlayWhileDownloadingUseCase.kt
│
├── data/                   # 数据层
│   ├── repository/
│   │   ├── VideoRepository.kt
│   │   ├── DownloadRepository.kt
│   │   ├── PlaybackRepository.kt
│   │   └ SettingsRepository.kt
│   ├── local/
│   │   ├── database/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── VideoDao.kt
│   │   │   ├── DownloadDao.kt
│   │   │   ├── PlaybackDao.kt
│   │   │   ├── entities/
│   │   │   │   ├── VideoEntity.kt
│   │   │   │   ├── DownloadEntity.kt
│   │   │   │   ├── PlaybackEntity.kt
│   │   │   └── converters/
│   │   │   │   └ Converters.kt
│   │   ├── datastore/
│   │   │   └ SettingsDataStore.kt
│   │   ├── filesystem/
│   │   │   ├── VideoScanner.kt
│   │   │   ├── ThumbnailGenerator.kt
│
├── infrastructure/         # 基础设施层
│   ├── player/
│   │   ├── ExoPlayerManager.kt
│   │   ├── PlayerGestureHandler.kt
│   │   ├── StreamPlaybackController.kt
│   │   ├── PlaybackProgressSaver.kt
│   ├── torrent/
│   │   ├── TorrentEngine.kt
│   │   ├── TorrentSession.kt
│   │   ├── TorrentDownloader.kt
│   │   ├── StreamableTorrent.kt
│   │   ├── ProgressTracker.kt
│   ├── notification/
│   │   ├── DownloadNotificationManager.kt
│   │   ├── PlaybackNotificationManager.kt
│
├── di/                     # 依赖注入
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── PlayerModule.kt
│   ├── TorrentModule.kt
│   ├── RepositoryModule.kt
│
└── util/                   # 工具类
    ├── FileUtils.kt
    ├── FormatUtils.kt
    ├── PermissionUtils.kt
    └ ClipboardUtils.kt
```

---

## 3. 数据模型设计

### 3.1 Room 数据库实体

#### VideoEntity（视频信息）

```kotlin
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,           // UUID
    val path: String,                     // 文件路径
    val name: String,                     // 文件名
    val size: Long,                       // 文件大小 (bytes)
    val duration: Long,                   // 视频时长 (ms)
    val thumbnailPath: String?,           // 缩略图路径
    val addedAt: Long,                    // 添加时间
    val lastPlayedAt: Long?,              // 最近播放时间
    val isFavorite: Boolean = false,      // 是否收藏
    val folderPath: String                // 所属文件夹
)
```

#### DownloadEntity（下载任务）

```kotlin
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,           // UUID
    val magnetLink: String?,              // magnet 链接
    val torrentPath: String?,             // torrent 文件路径
    val name: String,                     // 任务名称
    val totalSize: Long,                  // 总大小
    val downloadedSize: Long,             // 已下载大小
    val progress: Float,                  // 进度百分比 (0-100)
    val downloadSpeed: Long,              // 下载速度 (bytes/s)
    val state: DownloadState,             // 状态：PAUSED/DOWNLOADING/COMPLETED/ERROR
    val savePath: String,                 // 保存路径
    val createdAt: Long,                  // 创建时间
    val completedAt: Long?,               // 完成时间
    val isStreamable: Boolean,            // 是否可边下边播
    val streamableProgress: Float         // 边下边播所需进度阈值
)

enum class DownloadState {
    PAUSED, DOWNLOADING, COMPLETED, ERROR, WAITING
}
```

#### PlaybackEntity（播放记录）

```kotlin
@Entity(tableName = "playback_history")
data class PlaybackEntity(
    @PrimaryKey val id: String,           // UUID
    val videoId: String,                  // 关联视频 ID
    val position: Long,                   // 播放进度 (ms)
    val duration: Long,                   // 总时长 (ms)
    val lastPlayedAt: Long,               // 最近播放时间
    val playbackSpeed: Float = 1.0f,      // 播放倍速
    val isFromDownload: Boolean = false,  // 是否来自下载任务
    val downloadId: String?               // 关联下载任务 ID
)
```

### 3.2 DataStore 配置

```kotlin
// 用户偏好设置
object PreferencesKeys {
    val DOWNLOAD_PATH = stringPreferencesKey("download_path")
    val VIDEO_DIRECTORIES = stringSetPreferencesKey("video_directories")
    val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
    val STREAMABLE_THRESHOLD = floatPreferencesKey("streamable_threshold")  // 边下边播阈值 (0.1 = 10%)
    val THEME_MODE = stringPreferencesKey("theme_mode")  // dark/light/system
    val AUTO_SCAN = booleanPreferencesKey("auto_scan")
    val CACHE_SIZE_LIMIT = longPreferencesKey("cache_size_limit")
}
```

---

## 4. 核心组件设计

### 4.1 视频播放器

#### ExoPlayerManager

```kotlin
@Singleton
class ExoPlayerManager @Inject constructor(
    private val context: Context,
    private val playbackRepository: PlaybackRepository
) {
    private var player: ExoPlayer? = null
    
    fun initialize(): ExoPlayer {
        player = ExoPlayer.Builder(context)
            .setLoadControl(DefaultLoadControl.Builder()
                .setMinBufferMs(5000)
                .setMaxBufferMs(50000)
                .setBufferForPlaybackMs(1000)
                .build())
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build()
        return player!!
    }
    
    fun playLocalFile(path: String) {
        val mediaItem = MediaItem.fromUri(Uri.fromFile(File(path)))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }
    
    fun playStreamableTorrent(torrent: StreamableTorrent) {
        // 边下边播核心逻辑
        val streamUri = torrent.getStreamUri()
        val mediaItem = MediaItem.fromUri(streamUri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
    }
    
    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }
    
    fun saveProgress(videoId: String) {
        val position = player?.currentPosition ?: 0
        playbackRepository.savePlaybackProgress(videoId, position)
    }
}
```

#### PlayerGestureHandler

```kotlin
class PlayerGestureHandler(
    private val onVolumeChange: (Float) -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val onSeekChange: (Long) -> Unit
) {
    // 左侧滑动 → 亮度
    // 右侧滑动 → 音量
    // 水平滑动 → 进度
    // 双击 → 播放/暂停
    
    fun handleGesture(
        gestureType: GestureType,
        startPosition: Offset,
        currentPosition: Offset,
        screenWidth: Float,
        screenHeight: Float
    ) {
        when (gestureType) {
            GestureType.VERTICAL_SWIFT -> {
                val delta = (startPosition.y - currentPosition.y) / screenHeight
                if (startPosition.x < screenWidth / 2) {
                    // 左侧 → 亮度
                    onBrightnessChange(delta)
                } else {
                    // 右侧 → 音量
                    onVolumeChange(delta)
                }
            }
            GestureType.HORIZONTAL_SWIFT -> {
                val delta = (currentPosition.x - startPosition.x) / screenWidth
                onSeekChange(delta * 10000) // 10秒为单位
            }
            GestureType.DOUBLE_TAP -> {
                // 播放/暂停
            }
        }
    }
}
```

### 4.2 磁链下载引擎

#### TorrentEngine

```kotlin
@Singleton
class TorrentEngine @Inject constructor(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    private val sessionManager: SessionManager
    
    init {
        // 初始化 libtorrent4j
        sessionManager = SessionManager()
        sessionManager.listenPort(6881)
        sessionManager.settings().setDownloadRateLimit(0)  // 不限速
        sessionManager.settings().setUploadRateLimit(1024 * 10)  // 10KB/s 上传
    }
    
    fun addMagnetLink(magnetLink: String, savePath: String): TorrentHandle {
        val params = AddTorrentParams.builder()
            .magnet(magnetLink)
            .savePath(savePath)
            .build()
        
        val handle = sessionManager.addTorrent(params)
        handle.resume()
        
        return handle
    }
    
    fun addTorrentFile(torrentFile: File, savePath: String): TorrentHandle {
        val params = AddTorrentParams.builder()
            .torrentFile(torrentFile)
            .savePath(savePath)
            .build()
        
        return sessionManager.addTorrent(params)
    }
    
    fun getProgress(handle: TorrentHandle): Float {
        val status = handle.status()
        return status.progress() * 100
    }
    
    fun getDownloadSpeed(handle: TorrentHandle): Long {
        return handle.status().downloadRate()
    }
    
    fun pause(handle: TorrentHandle) {
        handle.pause()
    }
    
    fun resume(handle: TorrentHandle) {
        handle.resume()
    }
    
    fun remove(handle: TorrentHandle, deleteFiles: Boolean = false) {
        sessionManager.removeTorrent(handle, deleteFiles)
    }
}
```

#### StreamableTorrent（边下边播核心）

```kotlin
class StreamableTorrent(
    private val handle: TorrentHandle,
    private val threshold: Float = 0.1f  // 10% 阈值
) {
    private val pieces: BitSet
    private var largestFileIndex: Int = -1
    
    init {
        // 找出最大的文件（通常是视频）
        val torrentInfo = handle.torrentFile()
        largestFileIndex = findLargestFileIndex(torrentInfo)
        pieces = BitSet(torrentInfo.numPieces())
    }
    
    fun isStreamable(): Boolean {
        val progress = handle.status().progress()
        return progress >= threshold
    }
    
    fun getStreamUri(): Uri {
        // 创建临时文件用于流式播放
        val torrentInfo = handle.torrentFile()
        val fileEntry = torrentInfo.files().fileEntry(largestFileIndex)
        val streamFile = createStreamFile(fileEntry.path())
        
        // 使用 libtorrent 的顺序下载模式
        // 确保从开头开始下载
        handle.setPieceDeadline(0, 0)  // 第一片优先
        
        return Uri.fromFile(streamFile)
    }
    
    fun getCachedProgress(): Float {
        val torrentInfo = handle.torrentFile()
        val fileStorage = torrentInfo.files()
        
        // 计算最大文件的已下载进度
        var downloadedPieces = 0
        val totalPieces = fileStorage.fileSize(largestFileIndex) / torrentInfo.pieceLength()
        
        for (i in 0 until totalPieces.toInt()) {
            if (handle.havePiece(i)) {
                downloadedPieces++
            }
        }
        
        return downloadedPieces.toFloat() / totalPieces
    }
    
    fun getMaxSeekPosition(): Long {
        // 返回已缓存区域的最大位置
        val cachedProgress = getCachedProgress()
        val fileSize = handle.torrentFile().files().fileSize(largestFileIndex)
        return (fileSize * cachedProgress).toLong()
    }
}
```

### 4.3 边下边播控制器

#### StreamPlaybackController

```kotlin
@Singleton
class StreamPlaybackController @Inject constructor(
    private val playerManager: ExoPlayerManager,
    private val torrentEngine: TorrentEngine
) {
    private var currentStreamable: StreamableTorrent? = null
    
    fun startStreamPlayback(
        downloadId: String,
        threshold: Float = 0.1f
    ): Result<Unit> {
        val handle = torrentEngine.getHandle(downloadId)
            ?: return Result.failure(Exception("Download not found"))
        
        val streamable = StreamableTorrent(handle, threshold)
        
        if (!streamable.isStreamable()) {
            return Result.failure(Exception("Not enough data cached"))
        }
        
        currentStreamable = streamable
        
        // 开始播放
        val streamUri = streamable.getStreamUri()
        playerManager.playStream(streamUri)
        
        // 监控下载进度，更新播放约束
        monitorProgress(streamable)
        
        return Result.success(Unit)
    }
    
    private fun monitorProgress(streamable: StreamableTorrent) {
        CoroutineScope(Dispatchers.IO).launch {
            while (streamable.isStreamable()) {
                val cachedProgress = streamable.getCachedProgress()
                val maxSeekPosition = streamable.getMaxSeekPosition()
                
                // 更新播放器约束
                playerManager.setMaxSeekPosition(maxSeekPosition)
                
                delay(1000)  // 每秒更新
            }
        }
    }
    
    fun seekTo(position: Long): Result<Unit> {
        val maxPosition = currentStreamable?.getMaxSeekPosition() ?: 0
        
        if (position > maxPosition) {
            return Result.failure(Exception("Cannot seek beyond cached area"))
        }
        
        playerManager.seekTo(position)
        return Result.success(Unit)
    }
}
```

---

## 5. UI 设计

### 5.1 颜色方案

```kotlin
// Color.kt - 纯黑沉浸主题
object PureFrameColors {
    // 主色调
    val Background = Color(0xFF000000)        // 纯黑
    val Surface = Color(0xFF121212)           // 深灰表面
    val SurfaceVariant = Color(0xFF1E1E1E)    // 卡片背景
    
    // 文字颜色
    val OnBackground = Color(0xFFFFFFFF)      // 白色主文字
    val OnSurface = Color(0xFFFFFFFF)         // 白色
    val OnSurfaceVariant = Color(0xFFAAAAAA)  // 浅灰次要文字
    
    // 强调色
    val Primary = Color(0xFFAAAAAA)           // 低饱和灰
    val Secondary = Color(0xFF666666)         // 深灰
    val Accent = Color(0xFF888888)            // 中灰
    
    // 状态色
    val Success = Color(0xFF4CAF50)           // 成功
    val Warning = Color(0xFFFF9800)           // 警告
    val Error = Color(0xFFF44336)             // 错误
    
    // 播放器专用
    val PlayerProgress = Color(0xFFFFFFFF)    // 进度条白色
    val PlayerCached = Color(0xFF666666)      // 已缓存区域灰色
}
```

### 5.2 主题配置

```kotlin
// Theme.kt
@Composable
fun PureFrameTheme(
    darkTheme: Boolean = true,  // 默认深色
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = PureFrameColors.Primary,
        secondary = PureFrameColors.Secondary,
        background = PureFrameColors.Background,
        surface = PureFrameColors.Surface,
        onBackground = PureFrameColors.OnBackground,
        onSurface = PureFrameColors.OnSurface,
        surfaceVariant = PureFrameColors.SurfaceVariant,
        onSurfaceVariant = PureFrameColors.OnSurfaceVariant
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PureFrameTypography,
        content = content
    )
}
```

---

## 6. 导航设计

### 6.1 导航图

```kotlin
// NavGraph.kt
@Composable
fun PureFrameNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onVideoClick = { videoId ->
                    navController.navigate("player/$videoId")
                },
                onNavigateToDownload = {
                    navController.navigate("download")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        
        composable("download") {
            DownloadScreen(
                onDownloadClick = { downloadId ->
                    // 检查是否可边下边播
                    navController.navigate("player/stream/$downloadId")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = "player/{videoId}",
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId")
            PlayerScreen(
                videoId = videoId,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "player/stream/{downloadId}",
            arguments = listOf(navArgument("downloadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val downloadId = backStackEntry.arguments?.getString("downloadId")
            PlayerScreen(
                downloadId = downloadId,
                isStreamPlayback = true,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## 7. 依赖注入配置

### 7.1 模块配置

```kotlin
// AppModule.kt
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideContext(application: Application): Context = application
    
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

// DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pureframe_db"
        )
            .addTypeConverter(Converters())
            .build()
    }
    
    @Provides
    fun provideVideoDao(db: AppDatabase): VideoDao = db.videoDao()
    
    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()
    
    @Provides
    fun providePlaybackDao(db: AppDatabase): PlaybackDao = db.playbackDao()
}

// PlayerModule.kt
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun provideExoPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(DefaultLoadControl.Builder().build())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideExoPlayerManager(
        context: Context,
        playbackRepository: PlaybackRepository
    ): ExoPlayerManager = ExoPlayerManager(context, playbackRepository)
}

// TorrentModule.kt
@Module
@InstallIn(SingletonComponent::class)
object TorrentModule {
    @Provides
    @Singleton
    fun provideSessionManager(): SessionManager {
        val session = SessionManager()
        session.listenPort(6881)
        return session
    }
    
    @Provides
    @Singleton
    fun provideTorrentEngine(
        context: Context,
        sessionManager: SessionManager,
        downloadRepository: DownloadRepository
    ): TorrentEngine = TorrentEngine(context, sessionManager, downloadRepository)
}
```

---

## 8. 权限设计

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.pureframe.player">
    
    <!-- 存储权限（Android 13+ 使用细分权限） -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />
    
    <!-- Android 13+ 细分权限 -->
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    
    <!-- 后台服务 -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    
    <!-- 网络权限（下载） -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <!-- 通知 -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <!-- 剪贴板读取 -->
    <uses-permission android:name="android.permission.READ_CLIPBOARD" />
    
    <application
        android:name=".PureFrameApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PureFrame">
        
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PureFrame">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            
            <!-- 支持 magnet 链接分享 -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
            
            <!-- 支持 torrent 文件打开 -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/x-bittorrent" />
            </intent-filter>
        </activity>
        
        <!-- 后台下载服务 -->
        <service
            android:name=".infrastructure.torrent.DownloadService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
        
    </application>
</manifest>
```

---

## 9. 构建配置

### 9.1 Gradle 配置

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.pureframe.player"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.pureframe.player"
        minSdk = 26  // Android 8.0
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    ksp("com.google.dagger:hilt-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // ExoPlayer (Media3)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    
    // libtorrent4j
    implementation("org.libtorrent4j:libtorrent4j:2.2.0")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.2.0")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.2.0")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.2.0")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.2.0")
    
    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

## 10. 开发计划

### 10.1 分阶段实现

| 阶段 | 任务 | 预计时间 |
|-----|------|---------|
| **阶段 1** | 基础架构搭建 | 1 天 |
| | - Room 数据库 | |
| | - Hilt DI 配置 | |
| | - Navigation 配置 | |
| **阶段 2** | 本地视频播放 | 2 天 |
| | - 视频扫描 | |
| | - ExoPlayer 播放器 | |
| | - 基础控制 | |
| **阶段 3** | 磁链下载 | 2 天 |
| | - TorrentEngine | |
| | - 下载任务管理 | |
| | - 下载列表 UI | |
| **阶段 4** | 边下边播 | 2 天 |
| | - StreamableTorrent | |
| | - 进度约束 | |
| | - UI 显示 | |
| **阶段 5** | UI 完善 | 1 天 |
| | - 纯黑主题 | |
| | - 手势控制 | |
| | - 设置页面 | |
| **阶段 6** | 测试优化 | 1 天 |
| | - 功能测试 | |
| | - 性能优化 | |

---

**文档版本**: v1.0
**创建日期**: 2026-03-31
**创建者**: Dev Agent