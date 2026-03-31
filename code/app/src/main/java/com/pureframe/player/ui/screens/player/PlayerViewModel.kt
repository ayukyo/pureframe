package com.pureframe.player.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.usecase.playback.GetLastPlaybackPositionUseCase
import com.pureframe.player.domain.usecase.playback.SavePlaybackProgressUseCase
import com.pureframe.player.domain.usecase.video.GetVideoByIdUseCase
import com.pureframe.player.domain.usecase.video.UpdatePlayInfoUseCase
import com.pureframe.player.player.PlayerManager
import com.pureframe.player.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val getVideoByIdUseCase: GetVideoByIdUseCase,
    private val getLastPlaybackPositionUseCase: GetLastPlaybackPositionUseCase,
    private val savePlaybackProgressUseCase: SavePlaybackProgressUseCase,
    private val updatePlayInfoUseCase: UpdatePlayInfoUseCase,
    private val playerManager: PlayerManager
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
            
            // 检查下载进度是否足够播放
            if (downloadTask.progress < MIN_STREAM_PROGRESS) {
                _uiState.update { 
                    it.copy(
                        errorMessage = "下载进度不足，请等待更多数据下载",
                        isLoading = false
                    )
                }
                return@launch
            }
            
            _uiState.update {
                it.copy(
                    downloadTask = downloadTask,
                    isLoading = false,
                    streamProgress = downloadTask.progress / 100f,
                    maxSeekPosition = calculateMaxSeekPosition(downloadTask)
                )
            }
            
            // TODO: 边下边播需要 HTTP 代理服务器，将在下载引擎阶段实现
            // 目前先使用原始 URL 测试
            if (downloadTask.url.isNotEmpty()) {
                playerManager.loadStreamUrl(downloadTask.url)
            }
            
            isInitialized = true
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
data class PlayerUiState(
    val playbackType: PlayerViewModel.PlaybackType = PlayerViewModel.PlaybackType.LOCAL,
    val video: Video? = null,
    val downloadTask: DownloadTask? = null,
    val isLoading: Boolean = false,
    val initialPosition: Long = 0L,
    val errorMessage: String? = null,
    val streamProgress: Float = 0f,      // 下载进度 (0-1)
    val maxSeekPosition: Long = 0L       // 最大可跳转位置
)

/**
 * 手势指示器类型
 */
sealed class GestureIndicator {
    data class Volume(val value: Float) : GestureIndicator()
    data class Brightness(val value: Float) : GestureIndicator()
    data class Seek(val deltaMs: Long) : GestureIndicator()
}