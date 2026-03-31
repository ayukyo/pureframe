package com.pureframe.player.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放器管理器
 * 
 * 负责管理 ExoPlayer 播放状态，提供统一的播放控制接口。
 * 
 * 功能：
 * - 播放/暂停控制
 * - 进度监控
 * - 跳转控制
 * - 错误处理
 * - 音量控制
 * - 循环播放
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer
) {
    // 播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // 当前播放位置
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    // 视频总时长
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    // 缓冲进度
    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()
    
    // 播放状态（空闲/加载/就绪/结束/错误）
    private val _playbackState = MutableStateFlow(PlayerState.IDLE)
    val playbackState: StateFlow<PlayerState> = _playbackState.asStateFlow()
    
    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 音量
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    
    // 是否循环播放
    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()
    
    // 进度更新 Job
    private var progressUpdateJob: Job? = null
    
    // 播放器监听器
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> {
                    _playbackState.value = PlayerState.IDLE
                    _isPlaying.value = false
                }
                Player.STATE_BUFFERING -> {
                    _playbackState.value = PlayerState.BUFFERING
                    updateProgress()
                }
                Player.STATE_READY -> {
                    _playbackState.value = PlayerState.READY
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    startProgressUpdate()
                }
                Player.STATE_ENDED -> {
                    _playbackState.value = PlayerState.END
                    _isPlaying.value = false
                    stopProgressUpdate()
                    // 如果循环播放，重新开始
                    if (_isLooping.value) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    }
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdate()
            } else {
                stopProgressUpdate()
            }
        }
        
        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = PlayerState.ERROR
            _errorMessage.value = error.message ?: "播放错误"
            _isPlaying.value = false
        }
    }
    
    init {
        // 注册播放器监听器
        exoPlayer.addListener(playerListener)
    }
    
    /**
     * 加载本地视频文件
     * 
     * @param filePath 文件路径
     */
    fun loadLocalFile(filePath: String) {
        clearError()
        val mediaItem = MediaItem.fromUri(filePath)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }
    
    /**
     * 加载网络视频流
     * 
     * @param url 视频地址
     */
    fun loadStreamUrl(url: String) {
        clearError()
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }
    
    /**
     * 播放
     */
    fun play() {
        exoPlayer.play()
    }
    
    /**
     * 暂停
     */
    fun pause() {
        exoPlayer.pause()
    }
    
    /**
     * 播放/暂停切换
     */
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }
    
    /**
     * 跳转到指定位置
     * 
     * @param position 目标位置（毫秒）
     */
    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
        updateProgress()
    }
    
    /**
     * 相对跳转（快进/快退）
     * 
     * @param deltaMs 增量（毫秒）
     */
    fun seekRelative(deltaMs: Long) {
        val currentPos = exoPlayer.currentPosition
        val newPos = (currentPos + deltaMs).coerceIn(0, exoPlayer.duration)
        seekTo(newPos)
    }
    
    /**
     * 设置音量
     * 
     * @param volume 音量 (0-1)
     */
    fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
        _volume.value = exoPlayer.volume
    }
    
    /**
     * 设置循环播放
     * 
     * @param looping 是否循环
     */
    fun setLooping(looping: Boolean) {
        exoPlayer.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _isLooping.value = looping
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _errorMessage.value = null
        _playbackState.value = PlayerState.IDLE
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        exoPlayer.stop()
        stopProgressUpdate()
        _playbackState.value = PlayerState.IDLE
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }
    
    /**
     * 释放播放器
     */
    fun release() {
        stopProgressUpdate()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
    
    /**
     * 获取 ExoPlayer 实例（用于 UI 绑定）
     */
    fun getPlayer(): ExoPlayer = exoPlayer
    
    /**
     * 开始进度更新
     */
    private fun startProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateProgress()
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }
    
    /**
     * 停止进度更新
     */
    private fun stopProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
    
    /**
     * 更新进度状态
     */
    private fun updateProgress() {
        _currentPosition.value = exoPlayer.currentPosition
        _bufferedPosition.value = exoPlayer.bufferedPosition
    }
    
    companion object {
        const val PROGRESS_UPDATE_INTERVAL = 100L  // 100ms
    }
}

/**
 * 播放器状态枚举
 */
enum class PlayerState {
    IDLE,       // 空闲
    BUFFERING,  // 缓冲中
    READY,      // 就绪（可播放）
    END,        // 播放结束
    ERROR       // 错误
}