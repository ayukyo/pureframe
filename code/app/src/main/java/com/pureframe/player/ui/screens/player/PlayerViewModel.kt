package com.pureframe.player.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.usecase.playback.GetLastPlaybackPositionUseCase
import com.pureframe.player.domain.usecase.playback.SavePlaybackProgressUseCase
import com.pureframe.player.domain.usecase.video.GetVideoByIdUseCase
import com.pureframe.player.domain.usecase.video.UpdatePlayInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * - 手势响应准备（亮度/音量/进度）
 * - 边下边播状态监控
 * 
 * 注意：实际播放器核心（ExoPlayer）将在"播放器核心"阶段实现
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val getVideoByIdUseCase: GetVideoByIdUseCase,
    private val getLastPlaybackPositionUseCase: GetLastPlaybackPositionUseCase,
    private val savePlaybackProgressUseCase: SavePlaybackProgressUseCase,
    private val updatePlayInfoUseCase: UpdatePlayInfoUseCase
) : ViewModel() {
    
    // 播放类型
    enum class PlaybackType {
        LOCAL,      // 本地文件播放
        STREAM      // 边下边播
    }
    
    // UI 状态
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // 播放器状态（将在播放器核心阶段实现）
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()
    
    // 边下边播状态
    private val _streamProgress = MutableStateFlow(0f)
    val streamProgress: StateFlow<Float> = _streamProgress.asStateFlow()
    
    private val _maxSeekPosition = MutableStateFlow(0L)
    val maxSeekPosition: StateFlow<Long> = _maxSeekPosition.asStateFlow()
    
    /**
     * 初始化本地播放
     * 
     * @param videoId 视频 ID
     */
    fun initLocalPlayback(videoId: Long) {
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
                
                // TODO: 初始化 ExoPlayer（播放器核心阶段实现）
                // TODO: 设置媒体源
                // TODO: 如果有上次播放位置，跳转到该位置
                
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
            
            // TODO: 初始化边下边播控制器（播放器核心阶段实现）
            // TODO: 设置 HTTP 代理服务器
            // TODO: 开始监控下载进度
        }
    }
    
    /**
     * 播放/暂停切换
     * 
     * 注意：实际控制将在播放器核心阶段实现
     */
    fun togglePlayPause() {
        _isPlaying.update { !it }
        // TODO: 实际控制 ExoPlayer
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
        
        _currentPosition.value = position
        // TODO: 实际控制 ExoPlayer
    }
    
    /**
     * 相对跳转（快进/快退）
     * 
     * @param deltaMs 增量（毫秒）
     */
    fun seekRelative(deltaMs: Long) {
        val currentPos = _currentPosition.value
        val newPos = (currentPos + deltaMs).coerceIn(0, _duration.value)
        seekTo(newPos)
    }
    
    /**
     * 保存播放进度
     */
    fun saveProgress() {
        viewModelScope.launch {
            val video = _uiState.value.video
            if (video != null) {
                savePlaybackProgressUseCase(
                    SavePlaybackProgressUseCase.Params(
                        videoId = video.id,
                        videoTitle = video.title,
                        videoPath = video.filePath,
                        position = _currentPosition.value,
                        duration = _duration.value,
                        completed = _currentPosition.value >= _duration.value * 0.95f
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
        // TODO: 释放 ExoPlayer（播放器核心阶段实现）
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
    // 边下边播状态
    val streamProgress: Float = 0f,      // 下载进度 (0-1)
    val maxSeekPosition: Long = 0L       // 最大可跳转位置
)