package com.pureframe.player.ui.screens.player

import android.view.View
import android.window.OnBackInvokedDispatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.usecase.playback.GetLastPlaybackPositionUseCase
import com.pureframe.player.domain.usecase.playback.SavePlaybackProgressUseCase
import com.pureframe.player.domain.usecase.video.GetVideoByIdUseCase
import com.pureframe.player.domain.usecase.video.UpdatePlayInfoUseCase
import com.pureframe.player.domain.usecase.download.GetDownloadByIdUseCase
import com.pureframe.player.player.PlayerManager
import com.pureframe.player.player.PlayerState
import com.pureframe.player.download.StreamPlaybackHelper
import com.pureframe.player.download.StreamPlaybackState
import com.pureframe.player.download.StreamProgressInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val streamPlaybackHelper: StreamPlaybackHelper
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
    
    // 是否已初始化
    private var isInitialized = false
    
    /**
     * 初始化本地播放
     * 
     * @param videoId 视频 ID
     */
    fun initLocalPlayback(videoId: Long) {
        if (isInitialized) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, playbackType = PlaybackType.LOCAL) }
            
            try {
                // 获取视频信息
                val video = getVideoByIdUseCase(videoId)
                if (video == null) {
                    _uiState.update { it.copy(errorMessage = "视频不存在", isLoading = false) }
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
                
                // 如果有上次播放位置，跳转到该位置（在播放器就绪后）
                if (lastPosition != null && lastPosition > 0) {
                    // 等待播放器就绪后跳转
                    viewModelScope.launch {
                        playerManager.playbackState.collect { state ->
                            if (state == PlayerState.READY && !isInitialized) {
                                playerManager.seekTo(lastPosition)
                                isInitialized = true
                            }
                        }
                    }
                } else {
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
                    _uiState.update {
                        it.copy(
                            downloadTask = downloadTask,
                            isLoading = false,
                            streamProgress = downloadTask.progress / 100f,
                            maxSeekPosition = calculateMaxSeekPosition(downloadTask),
                            title = downloadTask.title
                        )
                    }
                    
                    // 监听边下边播状态
                    monitorStreamPlayback()
                    
                    isInitialized = true
                    Timber.i("PlayerViewModel: 边下边播启动成功 - ${downloadTask.title}")
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            errorMessage = error.message ?: "边下边播启动失败",
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
                        errorMessage = "下载任务不存在",
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
                _uiState.update { it.copy(maxSeekPosition = maxSeekMs) }
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
                _uiState.update { it.copy(errorMessage = "尚未下载到该位置") }
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
    fun saveProgress() {
        viewModelScope.launch {
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
    fun updatePlayInfo() {
        viewModelScope.launch {
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
     */
    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
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
        // 根据下载进度估算最大可播放位置
        // 简化实现：假设总时长已知，按下载比例计算
        return (task.progress / 100f * DEFAULT_VIDEO_DURATION).toLong()
    }
    
    override fun onCleared() {
        super.onCleared()
        // 保存最后播放位置
        saveProgress()
        // 更新播放信息
        updatePlayInfo()
        // 停止播放
        playerManager.pause()
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
    val title: String = "播放器"         // 视频标题
)

/**
 * 手势指示器类型
 */
sealed class GestureIndicator {
    data class Volume(val value: Float) : GestureIndicator()
    data class Brightness(val value: Float) : GestureIndicator()
    data class Seek(val deltaMs: Long) : GestureIndicator()
}