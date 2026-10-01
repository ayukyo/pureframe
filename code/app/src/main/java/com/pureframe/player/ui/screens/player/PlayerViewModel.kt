package com.pureframe.player.ui.screens.player

import android.view.View
import android.window.OnBackInvokedDispatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pureframe.player.player.PlaybackService
import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.usecase.playback.GetLastPlaybackPositionUseCase
import com.pureframe.player.domain.usecase.playback.SavePlaybackProgressUseCase
import com.pureframe.player.domain.usecase.video.GetVideoByIdUseCase
import com.pureframe.player.domain.usecase.video.UpdatePlayInfoUseCase
import com.pureframe.player.domain.usecase.download.GetDownloadByIdUseCase
import com.pureframe.player.player.PlayerManager
import com.pureframe.player.player.PlayerState
import com.pureframe.player.cast.CastDevice
import com.pureframe.player.cast.RouteContent
import com.pureframe.player.cast.RouteManager
import com.pureframe.player.download.StreamPlaybackHelper
import com.pureframe.player.download.StreamPlaybackState
import com.pureframe.player.download.StreamProgressInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pureframe.player.R
import com.pureframe.player.i18n.LocaleManager

/**
 * 播放器页面 ViewModel
 * 
 * 负责：
 * - 初始化播放器（本地/边下边播）
 * - 管理播放状态
 * - 播放控制（播放/暂停/跳转）
 * - 进度保存（续播功能）
 * - 手势响应（亮度/音量/进度）
 * - 边下边播状态监控
 * - 锁屏控制
 * - 倍速控制
 * - 画面比例控制
 * - 全屏控制
 */
@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val getVideoByIdUseCase: GetVideoByIdUseCase,
    private val getLastPlaybackPositionUseCase: GetLastPlaybackPositionUseCase,
    private val savePlaybackProgressUseCase: SavePlaybackProgressUseCase,
    private val updatePlayInfoUseCase: UpdatePlayInfoUseCase,
    private val getDownloadByIdUseCase: GetDownloadByIdUseCase,
    private val playerManager: PlayerManager,
    private val routeManager: RouteManager,
    private val streamPlaybackHelper: StreamPlaybackHelper,
    private val userPreferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // 播放类型
    enum class PlaybackType {
        LOCAL,      // 本地文件播放
        STREAM      // 边下边播
    }
    
    // UI 状态
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // 从 PlayerManager 获取播放状态
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val bufferedPosition: StateFlow<Long> = playerManager.bufferedPosition
    val playbackState: StateFlow<PlayerState> = playerManager.playbackState
    val playerError: StateFlow<String?> = playerManager.errorMessage
    val volume: StateFlow<Float> = playerManager.volume
    
    // 边下边播状态
    private val _streamProgress = MutableStateFlow(0f)
    val streamProgress: StateFlow<Float> = _streamProgress.asStateFlow()
    
    private val _maxSeekPosition = MutableStateFlow(0L)
    val maxSeekPosition: StateFlow<Long> = _maxSeekPosition.asStateFlow()
    
    // 手势状态
    private val _brightness = MutableStateFlow(0.5f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()
    
    private val _showGestureIndicator = MutableStateFlow<GestureIndicator?>(null)
    val showGestureIndicator: StateFlow<GestureIndicator?> = _showGestureIndicator.asStateFlow()
    
    // 锁屏状态
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // 播放中保持屏幕常亮（用户设置，默认开启）
    val keepScreenOn: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    
    // 倍速状态
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    
    // 画面比例状态
    private val _aspectRatio = MutableStateFlow("AUTO")
    val aspectRatio: StateFlow<String> = _aspectRatio.asStateFlow()
    
    // 全屏状态
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()
    
    // 对话框显示状态
    private val _showSpeedDialog = MutableStateFlow(false)
    val showSpeedDialog: StateFlow<Boolean> = _showSpeedDialog.asStateFlow()
    
    private val _showAspectRatioDialog = MutableStateFlow(false)
    val showAspectRatioDialog: StateFlow<Boolean> = _showAspectRatioDialog.asStateFlow()
    
    private val _showResumeDialog = MutableStateFlow(false)
    val showResumeDialog: StateFlow<Boolean> = _showResumeDialog.asStateFlow()

    private val _lastPosition = MutableStateFlow(0L)
    val lastPosition: StateFlow<Long> = _lastPosition.asStateFlow()

    // ==================== 投屏（RouteManager 接口化接入） ====================

    /** 投屏设备选择弹层 */
    private val _showCastDialog = MutableStateFlow(false)
    val showCastDialog: StateFlow<Boolean> = _showCastDialog.asStateFlow()

    /** 是否正在投屏（投屏中 UI 层切换到远程控制模式） */
    val isCasting: StateFlow<Boolean> = routeManager.activeRoute
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 远程路由状态（进度/设备名/错误），非投屏时为 null */
    val castState = routeManager.routeState

    /** 远端播完事件（RouteManager 自动断开后发出，UI 展示一次性提示） */
    val castEnded = routeManager.castEnded

    /** 投屏中本机播放器静默，手势层用它拦截 */
    val castingDeviceName: StateFlow<String?> = routeManager.routeState
        .map { it?.deviceName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 打开投屏弹层时触发设备扫描的结果 */
    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanningDevices = MutableStateFlow(false)
    val isScanningDevices: StateFlow<Boolean> = _isScanningDevices.asStateFlow()

    /** 当前内容快照（投屏时构建 RouteContent 用） */
    private var currentRouteContent: RouteContent? = null
    private var currentStreamProxyUrl: String? = null
    
    // 仅用于 onCleared() 中的收尾写入（viewModelScope 此时已被取消）
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 是否已初始化
    private var isInitialized = false

    // 当前播放的视频ID
    private var currentVideoId: Long? = null

    /**
     * MediaController 连接 PlaybackService 的引用（用于保活 + 通知栏媒体控件）。
     *
     * 持有 MediaController 期间 PlaybackService 是前台服务状态，进程不会被 cached 杀；
     * 通知栏显示当前视频标题 + 播放/暂停控件。释放后通知与 service 在 10min 超时后回收。
     */
    private var mediaController: MediaController? = null

    init {
        // 字幕开关：设置页修改后立即同步到播放器（无需重新加载视频）
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                playerManager.setSubtitleEnabled(prefs.showSubtitle)
            }
        }
        // 连接 PlaybackService：进程升为前台服务，通知栏媒体控件可见。
        // 用主线程 Handler 当 executor（1.9.0 没有 Util.mainHandlerExecutor API）。
        // 注意：这里不调 ListenableFuture.addListener（直接同步调），因为之前的实现
        // 走过 SessionToken.createCompatToken 反序列化路径，在 Android 13+ 会因
        // Parcel 严格校验抛 BadParcelableException。改用 SessionToken(Context, ComponentName)
        // 构造器路径直接构造 binder-safe token。
        val mainExecutor = java.util.concurrent.Executor { it.run() }
        runCatching {
            val token = PlaybackService.newSessionToken(appContext)
            val controllerFuture = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture.addListener({
                runCatching { mediaController = controllerFuture.get() }
                    .onSuccess { Timber.i("MediaController 已连接 PlaybackService") }
                    .onFailure { Timber.w(it, "MediaController 取值失败") }
            }, mainExecutor)
        }.onFailure { Timber.w(it, "SessionToken 构造失败") }
    }
    
    /**
     * 初始化本地播放
     * 
     * @param videoId 视频 ID
     */
    fun initLocalPlayback(videoId: Long) {
        // 如果是同一个视频且已初始化，直接播放
        if (isInitialized && currentVideoId == videoId) {
            playerManager.play()
            return
        }

        // 重置状态
        isInitialized = false
        currentVideoId = videoId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, playbackType = PlaybackType.LOCAL) }
            
            try {
                // 获取视频信息
                val video = getVideoByIdUseCase(videoId)
                if (video == null) {
                    _uiState.update { it.copy(errorMessage = LocaleManager.getString(appContext, R.string.player_video_not_found), isLoading = false) }
                    return@launch
                }
                
                // 获取上次播放位置
                val lastPosition = getLastPlaybackPositionUseCase(videoId)
                
                _uiState.update { 
                    it.copy(
                        video = video,
                        isLoading = false,
                        initialPosition = lastPosition ?: 0L
                    )
                }
                
                // 加载视频文件
                playerManager.loadLocalFile(video.filePath)
                // 记录投屏内容（本机文件，投屏时经 ContentUrlProvider 转 LAN URL；同名 srt 一并投递）
                currentRouteContent = RouteContent.LocalFile(
                    video.filePath, video.title, findExternalSubtitle(video.filePath)
                )

                // 应用用户偏好设置
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                playerManager.setPlaybackSpeed(prefs.defaultPlaySpeed)
                _playbackSpeed.value = prefs.defaultPlaySpeed
                playerManager.setLooping(prefs.loopPlay)

                // 等待播放器就绪后跳转并播放
                // 用 first { } 而不是 collect { }：StateFlow 的 collect 永不结束，
                // 每次进入播放页都会残留一个常驻协程（重复初始化 + 内存泄漏）
                playerManager.playbackState.first { it == PlayerState.READY }
                if (!isInitialized) {
                    if (lastPosition != null && lastPosition > 0) {
                        playerManager.seekTo(lastPosition)
                    }
                    playerManager.play()
                    isInitialized = true
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
            }
        }
    }
    
    /**
     * 初始化边下边播
     * 
     * @param downloadTask 下载任务
     */
    fun initStreamPlayback(downloadTask: DownloadTask) {
        if (isInitialized) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, playbackType = PlaybackType.STREAM) }
            
            // 使用 StreamPlaybackHelper 启动边下边播
            val result = streamPlaybackHelper.startStreamPlayback(downloadTask)
            
            result.fold(
                onSuccess = { streamUrl ->
                    currentStreamProxyUrl = streamUrl
                    // BT 流：字幕在下载目录按任务文件名找同名 srt（largestFile 视频文件）
                    val videoFile = downloadTask.savePath.let { path ->
                        File(path).listFiles { f ->
                            f.isFile && f.extension.lowercase() in listOf("mp4", "mkv", "avi", "webm", "ts", "mov", "m4v", "flv", "wmv", "mpg", "mpeg", "3gp")
                        }?.maxByOrNull { it.length() }
                    }
                    currentRouteContent = RouteContent.Stream(
                        streamUrl, downloadTask.title,
                        videoFile?.let { findExternalSubtitle(it.absolutePath) }
                    )
                    _uiState.update {
                        it.copy(
                            downloadTask = downloadTask,
                            isLoading = false,
                            streamProgress = downloadTask.progress / 100f,
                            maxSeekPosition = calculateMaxSeekPosition(downloadTask),
                            title = downloadTask.title
                        )
                    }
                    // 同步到 seekTo() 实际读取的 StateFlow，否则恒为 0 会导致所有拖动被拒
                    _maxSeekPosition.value = calculateMaxSeekPosition(downloadTask)

                    // 应用默认播放速度
                    val defaultSpeed = userPreferencesRepository.userPreferencesFlow.first().defaultPlaySpeed
                    playerManager.setPlaybackSpeed(defaultSpeed)
                    _playbackSpeed.value = defaultSpeed

                    // 监听边下边播状态
                    monitorStreamPlayback()

                    isInitialized = true
                    Timber.i("PlayerViewModel: 边下边播启动成功 - ${downloadTask.title}")
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            errorMessage = error.message ?: LocaleManager.getString(appContext, R.string.player_stream_start_failed),
                            isLoading = false
                        )
                    }
                    Timber.e(error, "PlayerViewModel: 边下边播启动失败")
                }
            )
        }
    }
    
    /**
     * 通过下载任务 ID 初始化边下边播
     * 
     * @param downloadId 下载任务 ID
     */
    fun initStreamPlaybackById(downloadId: Long) {
        if (isInitialized) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, playbackType = PlaybackType.STREAM) }
            
            // 获取下载任务
            val downloadTask = getDownloadByIdUseCase(downloadId)
            
            if (downloadTask != null) {
                // 调用边下边播初始化
                initStreamPlayback(downloadTask)
            } else {
                _uiState.update { 
                    it.copy(
                        errorMessage = LocaleManager.getString(appContext, R.string.player_task_not_found),
                        isLoading = false
                    )
                }
                Timber.e("PlayerViewModel: 下载任务不存在 - downloadId=$downloadId")
            }
        }
    }
    
    /**
     * 监听边下边播状态
     */
    private fun monitorStreamPlayback() {
        viewModelScope.launch {
            streamPlaybackHelper.playbackState.collect { state ->
                when (state) {
                    is StreamPlaybackState.Playing -> {
                        // 正常播放
                    }
                    is StreamPlaybackState.Paused -> {
                        // 暂停
                    }
                    is StreamPlaybackState.DownloadCompleted -> {
                        // 下载完成，可以切换到本地文件播放
                        _uiState.update { it.copy(streamProgress = 1f) }
                    }
                    is StreamPlaybackState.Error -> {
                        _uiState.update { it.copy(errorMessage = state.message) }
                    }
                    else -> {}
                }
            }
        }
        
        viewModelScope.launch {
            streamPlaybackHelper.maxSeekPositionMs.collect { maxSeekMs ->
                // 两处状态都要更新：uiState 供 UI 显示，maxSeekPosition 供 seekTo() 校验
                _uiState.update { it.copy(maxSeekPosition = maxSeekMs) }
                _maxSeekPosition.value = maxSeekMs
            }
        }
    }
    
    /**
     * 播放/暂停切换
     */
    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }
    
    /**
     * 播放
     */
    fun play() {
        // 如果播放结束，重置到开头
        if (playbackState.value == PlayerState.END) {
            playerManager.seekTo(0)
        }
        playerManager.play()
    }
    
    /**
     * 暂停
     */
    fun pause() {
        playerManager.pause()
    }
    
    /**
     * 跳转到指定位置
     * 
     * @param position 目标位置（毫秒）
     */
    fun seekTo(position: Long) {
        // 边下边播时，不能跳过已下载部分
        val type = _uiState.value.playbackType
        if (type == PlaybackType.STREAM) {
            val maxPos = _maxSeekPosition.value
            if (position > maxPos) {
                _uiState.update { it.copy(errorMessage = LocaleManager.getString(appContext, R.string.player_not_downloaded_yet)) }
                return
            }
        }
        
        playerManager.seekTo(position)
    }
    
    /**
     * 相对跳转（快进/快退）
     * 
     * @param deltaMs 增量（毫秒）
     */
    fun seekRelative(deltaMs: Long) {
        playerManager.seekRelative(deltaMs)
    }
    
    /**
     * 设置音量
     * 
     * @param volume 音量 (0-1)
     */
    fun setVolume(volume: Float) {
        playerManager.setVolume(volume)
        showGestureIndicator(GestureIndicator.Volume(volume))
    }
    
    /**
     * 设置亮度
     * 
     * @param brightness 亮度 (0-1)
     */
    fun setBrightness(brightness: Float) {
        _brightness.value = brightness.coerceIn(0f, 1f)
        showGestureIndicator(GestureIndicator.Brightness(brightness))
    }
    
    /**
     * 显示手势指示器
     */
    private fun showGestureIndicator(indicator: GestureIndicator) {
        _showGestureIndicator.value = indicator
        // 3秒后隐藏
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _showGestureIndicator.value = null
        }
    }
    
    /**
     * 设置循环播放
     * 
     * @param looping 是否循环
     */
    fun setLooping(looping: Boolean) {
        playerManager.setLooping(looping)
    }
    
    /**
     * 保存播放进度
     */
    fun saveProgress(scope: CoroutineScope = viewModelScope) {
        scope.launch {
            val video = _uiState.value.video
            if (video != null) {
                val position = currentPosition.value
                val totalDuration = duration.value
                
                savePlaybackProgressUseCase(
                    SavePlaybackProgressUseCase.Params(
                        videoId = video.id,
                        videoTitle = video.title,
                        videoPath = video.filePath,
                        position = position,
                        duration = totalDuration,
                        completed = position >= totalDuration * 0.95f
                    )
                )
            }
        }
    }
    
    /**
     * 更新播放信息（播放次数、最后播放时间）
     */
    fun updatePlayInfo(scope: CoroutineScope = viewModelScope) {
        scope.launch {
            val videoId = _uiState.value.video?.id
            if (videoId != null) {
                updatePlayInfoUseCase(UpdatePlayInfoUseCase.Params(videoId))
            }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
        playerManager.clearError()
    }
    
    /**
     * 获取 ExoPlayer 实例（用于 UI 绑定）
     */
    fun getPlayer() = playerManager.getPlayer()
    
    /**
     * 锁屏切换
     */
    fun toggleLock() {
        _isLocked.value = !_isLocked.value
    }
    
    /**
     * 设置锁屏状态
     */
    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }
    
    /**
     * 设置播放速度
     *
     * 若用户开启「记住播放速度」，同时写回偏好，下次播放沿用该倍速
     */
    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
        viewModelScope.launch {
            val prefs = runCatching { userPreferencesRepository.userPreferencesFlow.first() }.getOrNull()
            if (prefs?.rememberPlaySpeed == true) {
                userPreferencesRepository.updateDefaultPlaySpeed(speed)
            }
        }
        dismissSpeedDialog()
    }
    
    /**
     * 显示倍速选择对话框
     */
    fun showSpeedDialog() {
        _showSpeedDialog.value = true
    }
    
    /**
     * 关闭倍速选择对话框
     */
    fun dismissSpeedDialog() {
        _showSpeedDialog.value = false
    }
    
    /**
     * 设置画面比例
     */
    fun setAspectRatio(ratio: String) {
        _aspectRatio.value = ratio
        playerManager.setAspectRatio(ratio)
        dismissAspectRatioDialog()
    }
    
    /**
     * 显示画面比例选择对话框
     */
    fun showAspectRatioDialog() {
        _showAspectRatioDialog.value = true
    }
    
    /**
     * 关闭画面比例选择对话框
     */
    fun dismissAspectRatioDialog() {
        _showAspectRatioDialog.value = false
    }
    
    /**
     * 全屏切换
     */
    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }
    
    /**
     * 设置全屏状态
     */
    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }
    
    /**
     * 显示续播对话框
     */
    fun showResumeDialog() {
        _showResumeDialog.value = true
    }
    
    /**
     * 关闭续播对话框
     */
    fun dismissResumeDialog() {
        _showResumeDialog.value = false
    }
    
    /**
     * 续播（从上次位置开始）
     */
    fun resumeFromLastPosition() {
        val pos = _lastPosition.value
        if (pos > 0) {
            playerManager.seekTo(pos)
        }
        dismissResumeDialog()
    }
    
    /**
     * 从头开始播放
     */
    fun playFromStart() {
        playerManager.seekTo(0)
        dismissResumeDialog()
    }
    
    /**
     * 计算边下边播最大可跳转位置
     */
    private fun calculateMaxSeekPosition(task: DownloadTask): Long {
        // 已下载到结尾的任务（本地文件）不限制 seek，
        // 否则会用 1 小时的假设值限制长视频跳转
        if (task.isCompleted) return Long.MAX_VALUE
        // 根据下载进度估算最大可播放位置
        // 简化实现：假设总时长已知，按下载比例计算
        return (task.progress / 100f * DEFAULT_VIDEO_DURATION).toLong()
    }

    // ==================== 投屏控制（委托 RouteManager） ====================

    /**
     * 查找视频同目录同名 .srt 外挂字幕（例：Movie.mp4 -> Movie.srt）。
     * 找到则随投屏内容一起下发电视端；本地播放不受影响。
     */
    private fun findExternalSubtitle(videoPath: String): String? {
        val base = videoPath.substringBeforeLast('.')
        val srt = File("$base.srt")
        return if (srt.exists() && srt.canRead()) srt.absolutePath else null
    }

    /**
     * 打开投屏设备弹层并开始扫描
     *
     * 自动连接：设置开启且未在投屏时，扫描结果中找到上次设备则自动连接。
     * 找不到（设备离线/改名）则静默跳过，不打扰用户。
     */
    fun openCastDialog() {
        _showCastDialog.value = true
        refreshCastDevices()
        maybeAutoConnectLastDevice()
    }

    /** 扫描完成后尝试自动连接上次投屏设备 */
    private fun maybeAutoConnectLastDevice() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (!prefs.castAutoConnect || routeManager.isCasting) return@launch
            if (prefs.castLastDeviceId.isBlank()) return@launch
            // 等本次扫描结束再在结果里找目标设备
            _isScanningDevices.first { !it }
            val target = _discoveredDevices.value.firstOrNull { it.id == prefs.castLastDeviceId }
                ?: return@launch
            Timber.i("投屏自动连接上次设备: %s", target.name)
            castToDevice(target)
        }
    }

    fun dismissCastDialog() {
        _showCastDialog.value = false
    }

    /**
     * 重新扫描局域网投屏设备
     */
    fun refreshCastDevices() {
        viewModelScope.launch {
            _isScanningDevices.value = true
            _discoveredDevices.value = routeManager.discoverDevices()
            _isScanningDevices.value = false
        }
    }

    /**
     * 连接设备并投屏
     *
     * 进度跟随：以本机当前进度为起点，投屏后本机暂停。
     * 已投屏状态下切换设备：本机进度是投屏开始时冻结的旧值，
     * 应以当前远端进度为起点，避免回跳。
     */
    fun castToDevice(device: CastDevice) {
        val content = currentRouteContent ?: return
        val startPos = routeManager.routeState.value?.positionMs
            ?.takeIf { routeManager.isCasting && it > 0L }
            ?: playerManager.currentPosition.value
        viewModelScope.launch {
            val ok = routeManager.castTo(
                device = device,
                content = content,
                startPositionMs = startPos,
                onLocalPause = { playerManager.pause() }
            )
            if (ok) {
                _showCastDialog.value = false
                // 记住本次投屏设备（供「自动连接上次设备」用）
                userPreferencesRepository.updateCastLastDevice(device)
            } else {
                _uiState.update {
                    it.copy(errorMessage = LocaleManager.getString(appContext, R.string.cast_connect_failed))
                }
            }
        }
    }

    /**
     * 断开投屏，本机从断点续播
     */
    fun disconnectCast() {
        val resumePos = routeManager.routeState.value?.positionMs ?: 0L
        viewModelScope.launch {
            routeManager.disconnectFromDevice()
            if (resumePos > 0) {
                playerManager.seekTo(resumePos)
            }
            playerManager.play()
        }
    }

    /** 投屏中：播放/暂停切换（发到远端） */
    fun castPlayPause() {
        if (routeManager.isCasting) routeManager.playPause()
    }

    /** 投屏中：绝对跳转（发到远端） */
    fun castSeekTo(positionMs: Long) {
        if (routeManager.isCasting) routeManager.seekTo(positionMs)
    }

    /** 投屏中：相对跳转（发到远端） */
    fun castSeekRelative(deltaMs: Long) {
        if (routeManager.isCasting) routeManager.seekRelative(deltaMs)
    }

    override fun onCleared() {
        // 先做业务收尾，再调 super.onCleared()
        // 注意：不能用 viewModelScope —— ViewModel.clear() 会先关闭所有 Closeable
        // （viewModelScope 就是其中之一），再回调 onCleared()，此时 launch 出去的
        // 协程会立即被取消，进度根本存不进去。这里用独立的短生命周期 scope。
        saveProgress(saveScope)
        updatePlayInfo(saveScope)
        // 投屏进度落库：以远端断点为准（本机进度在投屏期间是冻结的）
        val castSt = routeManager.routeState.value
        val castVideo = _uiState.value.video
        if (castSt != null && castSt.positionMs > 0 && castVideo != null) {
            saveScope.launch {
                savePlaybackProgressUseCase(
                    SavePlaybackProgressUseCase.Params(
                        videoId = castVideo.id,
                        videoTitle = castVideo.title,
                        videoPath = castVideo.filePath,
                        position = castSt.positionMs,
                        duration = castSt.durationMs,
                        completed = castSt.durationMs > 0 && castSt.positionMs >= castSt.durationMs * 0.95f
                    )
                )
            }
        }
        // 断开投屏（远端停播），本机已暂停
        routeManager.release()
        // 停止播放
        playerManager.pause()

        // 延迟释放 MediaController：给用户「切下一个视频 / 返回播放页 / 切换 app」留出
        // 6s 复用窗口（沿用 service 进程 + ExoPlayer 缓冲，避免重建播放服务的开销）。
        // 若窗口内用户再次进入播放页，init() 会重新连接 controller 替换这个引用，
        // 旧 controller 在这里 release 之前会被新的覆盖 → 不会双重 release。
        val controller = mediaController
        if (controller != null) {
            mediaController = null
            // 用独立 handler 避免依赖 viewModelScope（onCleared 之后 viewModelScope 已 cancel）
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                runCatching { controller.release() }
                    .onFailure { Timber.w(it, "MediaController.release 失败") }
            }, 6_000L)
        }

        super.onCleared()
    }
    
    companion object {
        const val MIN_STREAM_PROGRESS = 5f  // 最小边下边播进度（5%）
        const val DEFAULT_VIDEO_DURATION = 3600000L  // 默认视频时长（1小时）
    }
}

/**
 * Player 页面 UI 状态
 */
@UnstableApi
data class PlayerUiState(
    val playbackType: PlayerViewModel.PlaybackType = PlayerViewModel.PlaybackType.LOCAL,
    val video: Video? = null,
    val downloadTask: DownloadTask? = null,
    val isLoading: Boolean = false,
    val initialPosition: Long = 0L,
    val errorMessage: String? = null,
    val streamProgress: Float = 0f,      // 下载进度 (0-1)
    val maxSeekPosition: Long = 0L,      // 最大可跳转位置
    val title: String = ""               // 视频标题（为空时由 UI 层回退到通用标题）
)

/**
 * 手势指示器类型
 */
sealed class GestureIndicator {
    data class Volume(val value: Float) : GestureIndicator()
    data class Brightness(val value: Float) : GestureIndicator()
    data class Seek(val deltaMs: Long) : GestureIndicator()
}