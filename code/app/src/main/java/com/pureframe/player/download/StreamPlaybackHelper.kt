package com.pureframe.player.download

import android.content.Context
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.player.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 边下边播助手
 * 
 * 整合 TorrentManager、StreamProxyServer 和 PlayerManager，
 * 提供统一的边下边播控制接口。
 */
@Singleton
class StreamPlaybackHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val torrentManager: TorrentManager,
    private val streamProxyServer: StreamProxyServer,
    private val playerManager: PlayerManager
) {
    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 当前播放的下载任务 ID
    private var currentPlaybackTaskId: String? = null
    
    // 边下边播状态
    private val _playbackState = MutableStateFlow<StreamPlaybackState>(StreamPlaybackState.Idle)
    val playbackState: StateFlow<StreamPlaybackState> = _playbackState.asStateFlow()
    
    // 最大可跳转位置（毫秒）
    private val _maxSeekPositionMs = MutableStateFlow(0L)
    val maxSeekPositionMs: StateFlow<Long> = _maxSeekPositionMs.asStateFlow()
    
    // 监听下载进度更新
    private var progressMonitorJob: Job? = null

    /**
     * 启动边下边播
     * 
     * @param downloadTask 下载任务
     * @return 成功返回播放 URL，失败返回 null
     */
    fun startStreamPlayback(downloadTask: DownloadTask): Result<String> {
        // 检查下载进度是否足够
        if (downloadTask.progress < MIN_PLAYBACK_PROGRESS) {
            return Result.failure(IllegalArgumentException("下载进度不足，需要至少 ${MIN_PLAYBACK_PROGRESS}%"))
        }
        
        // 检查下载状态
        if (downloadTask.status != DownloadStatus.DOWNLOADING && 
            downloadTask.status != DownloadStatus.PAUSED) {
            return Result.failure(IllegalArgumentException("下载任务状态异常: ${downloadTask.status}"))
        }
        
        // 启动代理服务器
        if (!streamProxyServer.isRunning()) {
            val portResult = streamProxyServer.start()
            if (portResult.isFailure) {
                return Result.failure(portResult.exceptionOrNull() ?: Exception("代理服务器启动失败"))
            }
        }
        
        // 获取流播放 URL
        val taskId = downloadTask.id.toString()
        val streamUrl = streamProxyServer.getStreamUrl(taskId, 0)
        
        if (streamUrl == null) {
            return Result.failure(Exception("无法获取流播放 URL"))
        }
        
        // 记录当前播放任务
        currentPlaybackTaskId = taskId
        _playbackState.value = StreamPlaybackState.Preparing
        
        // 开始播放
        playerManager.loadStreamUrl(streamUrl)
        
        // 监听下载进度
        startProgressMonitor(taskId, downloadTask)
        
        _playbackState.value = StreamPlaybackState.Playing
        
        Timber.i("StreamPlaybackHelper: 边下边播启动成功 - taskId=$taskId, url=$streamUrl")
        
        return Result.success(streamUrl)
    }
    
    /**
     * 启动进度监听
     */
    private fun startProgressMonitor(taskId: String, _initialTask: DownloadTask) {  // initialTask 未来用于初始状态设置
        progressMonitorJob?.cancel()
        
        progressMonitorJob = helperScope.launch {
            torrentManager.downloadProgress
                .filter { it.taskId == taskId }
                .collect { progress ->
                    // 更新最大可跳转位置
                    updateMaxSeekPosition(progress)
                    
                    // 检查是否可以继续播放
                    checkPlaybackStatus(progress)
                }
        }
        
        // 同时监听边下边播状态
        helperScope.launch {
            torrentManager.streamableStatus
                .filter { it.containsKey(taskId) }
                .map { it[taskId] }
                .collect { streamableInfo ->
                    streamableInfo?.let {
                        updateMaxSeekPositionFromStreamable(it)
                    }
                }
        }
    }
    
    /**
     * 更新最大可跳转位置
     */
    private fun updateMaxSeekPosition(progress: DownloadProgressInfo) {
        // 简化计算：假设 1% 进度 = 36 秒（对于 1 小时视频）
        // 实际应该根据视频总时长计算
        val maxSeekMs = (progress.progress / 100f * DEFAULT_VIDEO_DURATION_MS).toLong()
        _maxSeekPositionMs.value = maxSeekMs
        
        Timber.d("StreamPlaybackHelper: 最大可跳转 = ${maxSeekMs / 1000}s")
    }
    
    /**
     * 从 StreamableInfo 更新最大可跳转位置
     */
    private fun updateMaxSeekPositionFromStreamable(streamableInfo: StreamableInfo) {
        // StreamableInfo 的 maxSeekPosition 是字节位置
        // 需要转换为时间位置（简化：假设平均码率）
        val bytesPerSecond = DEFAULT_BITRATE_BPS
        val maxSeekMs = (streamableInfo.maxSeekPosition / bytesPerSecond * 1000)
        
        // 取两个计算结果的最大值
        _maxSeekPositionMs.value = maxOf(_maxSeekPositionMs.value, maxSeekMs)
    }
    
    /**
     * 检查播放状态
     */
    private fun checkPlaybackStatus(progress: DownloadProgressInfo) {
        if (progress.state == TorrentState.COMPLETED) {
            _playbackState.value = StreamPlaybackState.DownloadCompleted
            Timber.i("StreamPlaybackHelper: 下载完成")
        } else if (progress.state == TorrentState.ERROR) {
            _playbackState.value = StreamPlaybackState.Error("下载错误")
            Timber.e("StreamPlaybackHelper: 下载错误")
        }
    }
    
    /**
     * 暂停边下边播
     */
    fun pauseStreamPlayback() {
        playerManager.pause()
        _playbackState.value = StreamPlaybackState.Paused
    }
    
    /**
     * 恢复边下边播
     */
    fun resumeStreamPlayback() {
        playerManager.play()
        _playbackState.value = StreamPlaybackState.Playing
    }
    
    /**
     * 停止边下边播
     */
    fun stopStreamPlayback() {
        playerManager.stop()
        progressMonitorJob?.cancel()
        currentPlaybackTaskId = null
        _playbackState.value = StreamPlaybackState.Idle
        _maxSeekPositionMs.value = 0L
        
        Timber.i("StreamPlaybackHelper: 边下边播已停止")
    }
    
    /**
     * 跳转到指定位置
     * 
     * 边下边播时，跳转位置不能超过已下载的部分
     */
    fun seekTo(positionMs: Long): Result<Unit> {
        val maxSeek = _maxSeekPositionMs.value
        
        if (positionMs > maxSeek) {
            return Result.failure(IllegalArgumentException("尚未下载到该位置，请等待"))
        }
        
        playerManager.seekTo(positionMs)
        return Result.success(Unit)
    }
    
    /**
     * 检查位置是否可跳转
     */
    fun canSeekTo(positionMs: Long): Boolean {
        return positionMs <= _maxSeekPositionMs.value
    }
    
    /**
     * 获取边下边播进度信息
     */
    fun getStreamProgressInfo(): StreamProgressInfo? {
        val taskId = currentPlaybackTaskId
        if (taskId == null) return null
        
        val streamableInfo = torrentManager.getStreamableInfo(taskId.toLong())
        if (streamableInfo == null) return null
        
        return StreamProgressInfo(
            taskId = taskId,
            cachedProgress = streamableInfo.cachedProgress,
            maxSeekPositionBytes = streamableInfo.maxSeekPosition,
            maxSeekPositionMs = _maxSeekPositionMs.value,
            isStreamable = streamableInfo.isStreamable
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopStreamPlayback()
        streamProxyServer.stop()
        helperScope.cancel()
    }

    companion object {
        const val MIN_PLAYBACK_PROGRESS = 5f  // 最小边下边播进度（5%）
        const val DEFAULT_VIDEO_DURATION_MS = 3600000L  // 默认视频时长（1小时）
        const val DEFAULT_BITRATE_BPS = 1000000L  // 默认码率（1 Mbps）
    }
}

/**
 * 边下边播状态
 */
sealed class StreamPlaybackState {
    object Idle : StreamPlaybackState()
    object Preparing : StreamPlaybackState()
    object Playing : StreamPlaybackState()
    object Paused : StreamPlaybackState()
    object DownloadCompleted : StreamPlaybackState()
    data class Error(val message: String) : StreamPlaybackState()
}

/**
 * 边下边播进度信息
 */
data class StreamProgressInfo(
    val taskId: String,
    val cachedProgress: Float,
    val maxSeekPositionBytes: Long,
    val maxSeekPositionMs: Long,
    val isStreamable: Boolean
)