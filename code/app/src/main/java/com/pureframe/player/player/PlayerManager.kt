package com.pureframe.player.player

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pureframe.player.data.preferences.DecoderType
import com.pureframe.player.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
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
 * - 倍速控制
 * - 画面比例控制
 * - 解码器选择
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val userPreferencesRepository: UserPreferencesRepository
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
    
    // 播放速度
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    
    // 画面比例
    private val _aspectRatio = MutableStateFlow("AUTO")
    val aspectRatio: StateFlow<String> = _aspectRatio.asStateFlow()

    // 解码器类型
    private val _decoderType = MutableStateFlow(DecoderType.HARDWARE)
    val decoderType: StateFlow<DecoderType> = _decoderType.asStateFlow()

    // 字幕显示开关
    private val _subtitleEnabled = MutableStateFlow(true)
    val subtitleEnabled: StateFlow<Boolean> = _subtitleEnabled.asStateFlow()

    // 进度更新 Job
    private var progressUpdateJob: Job? = null

    init {
        // 初始化时读取解码器偏好（同步读取，避免异步复杂性）
        _decoderType.value = runBlocking {
            userPreferencesRepository.userPreferencesFlow.first().decoderType
        }
    }

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
            // 画中画小窗内的播放/暂停按钮图标需要通过更新 PiP 参数来刷新，
            // 否则小窗按钮状态与实际播放状态不同步。
            // 用反射调用：部分编译变体对 Activity.updatePictureInPictureParams 解析失败
            runCatching {
                val activity = PipCurrentActivityHolder.currentActivity
                if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val method = android.app.Activity::class.java.getMethod(
                        "updatePictureInPictureParams",
                        android.app.PictureInPictureParams::class.java
                    )
                    method.invoke(activity, PiPHelper.buildParams(activity, exoPlayer))
                }
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
     * 会自动查找视频同目录、同名（不含扩展名）的侧载字幕文件
     * （.srt/.vtt/.ass/.ssa），找到则作为字幕轨附加到 MediaItem。
     *
     * @param filePath 文件路径
     */
    fun loadLocalFile(filePath: String) {
        clearError()
        val subtitleUri = findSidecarSubtitle(filePath)
        val builder = MediaItem.Builder().setUri(filePath)
        if (subtitleUri != null) {
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(subtitleMimeType(subtitleUri))
                        .setLanguage("zh")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
            Timber.i("PlayerManager: 找到侧载字幕 - %s", subtitleUri)
        }
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        // 应用当前的字幕开关状态
        applySubtitleEnabled(_subtitleEnabled.value)
    }

    /**
     * 查找视频同目录、同名的侧载字幕文件
     *
     * @param videoPath 视频文件路径
     * @return 字幕文件 Uri，未找到返回 null
     */
    private fun findSidecarSubtitle(videoPath: String): Uri? {
        val videoFile = try {
            java.io.File(videoPath)
        } catch (e: Exception) {
            return null
        }
        if (!videoFile.exists()) return null

        val baseName = videoFile.nameWithoutExtension
        // 按优先级排列：srt > ass/ssa > vtt
        val candidates = listOf(".srt", ".ass", ".ssa", ".vtt")
        for (ext in candidates) {
            val f = java.io.File(videoFile.parentFile, baseName + ext)
            if (f.exists() && f.canRead()) return Uri.fromFile(f)
        }
        return null
    }

    /**
     * 根据字幕文件扩展名推断 MIME 类型
     */
    private fun subtitleMimeType(uri: Uri): String {
        val path = uri.path?.lowercase() ?: return MimeTypes.TEXT_VTT
        return when {
            path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            else -> MimeTypes.TEXT_VTT
        }
    }

    /**
     * 设置字幕显示开关
     *
     * 通过禁用/启用文本轨选择实现，无需重新加载视频，立即生效。
     *
     * @param enabled 是否显示字幕
     */
    fun setSubtitleEnabled(enabled: Boolean) {
        _subtitleEnabled.value = enabled
        applySubtitleEnabled(enabled)
    }

    /**
     * 应用字幕开关到播放器的轨道选择参数
     */
    private fun applySubtitleEnabled(enabled: Boolean) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
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
        applySubtitleEnabled(_subtitleEnabled.value)
    }
    
    /**
     * 播放
     */
    fun play() {
        // 如果播放结束，先重置到开头
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0)
        }
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
        val duration = exoPlayer.duration
        // 直播流/未就绪时 duration 可能是 TIME_UNSET(Long.MIN_VALUE)，
        // 直接 coerceIn(0, 负数) 会抛 IllegalArgumentException 导致崩溃
        val newPos = if (duration <= 0) {
            (currentPos + deltaMs).coerceAtLeast(0)
        } else {
            (currentPos + deltaMs).coerceIn(0, duration)
        }
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
     * 设置播放速度
     * 
     * @param speed 播放速度 (0.5-3.0)
     */
    fun setPlaybackSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.5f, 3.0f)
        exoPlayer.setPlaybackSpeed(validSpeed)
        _playbackSpeed.value = validSpeed
    }
    
    /**
     * 设置画面比例
     * 
     * @param ratio 比例代码 (AUTO/16:9/4:3/FILL/ORIGINAL)
     */
    @UnstableApi
    fun setAspectRatio(ratio: String) {
        _aspectRatio.value = ratio
        // ExoPlayer 的视频缩放模式设置
        when (ratio) {
            "AUTO" -> {
                // 自动保持原始比例
                exoPlayer.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            }
            "16:9", "4:3" -> {
                // 强制指定比例（需要自定义 VideoSurface 或使用 PlayerView 的 resizeMode）
                exoPlayer.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            }
            "FILL" -> {
                // 填充屏幕，可能裁切
                exoPlayer.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            }
            "ORIGINAL" -> {
                // 原始大小（不缩放）
                exoPlayer.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            }
        }
    }

    /**
     * 设置解码器类型
     *
     * 注意：解码器类型在 ExoPlayer 创建时确定，修改后需要重启应用才能生效
     *
     * @param type 解码器类型（硬件/软件/自动）
     */
    fun setDecoderType(type: DecoderType) {
        _decoderType.value = type

        // 持久化偏好（重启后生效）
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            userPreferencesRepository.updateDecoderType(type)
        }
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