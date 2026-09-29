package com.pureframe.player.cast

import com.yinnho.upnpcast.DLNACast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * DLNA 播放路由
 *
 * 把内容推到 DLNA renderer（电视/盒子）上播放，本机轮询远端进度。
 * 一个 Route 实例对应一次设备连接，deactivate 后不应复用。
 */
class DlnaRoute(
    private val device: DLNACast.Device,
    private val urlProvider: DlnaContentUrlProvider
) : PlaybackRoute {

    override val type: RouteType = RouteType.DLNA

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(
        RouteState(phase = RoutePhase.IDLE, deviceName = device.name)
    )
    override val state: StateFlow<RouteState> = _state.asStateFlow()

    private var progressJob: Job? = null
    private var currentContent: RouteContent? = null
    private var lastPositionMs = 0L

    override suspend fun activate(content: RouteContent, startPositionMs: Long): Boolean {
        _state.value = _state.value.copy(phase = RoutePhase.CONNECTING)
        currentContent = content
        return try {
            val videoUrl: String
            val subtitleUrl: String?
            when (content) {
                is RouteContent.LocalFile -> {
                    videoUrl = urlProvider.urlFor(File(content.filePath))
                        ?: run {
                            fail("文件不可读，无法投屏")
                            return false
                        }
                    subtitleUrl = content.subtitlePath?.let { urlProvider.urlFor(File(it)) }
                }
                is RouteContent.Stream -> {
                    videoUrl = urlProvider.urlForStream(content.streamUrl)
                    subtitleUrl = content.subtitlePath?.let { urlProvider.urlFor(File(it)) }
                }
            }
            // 字幕文件：file server 已支持任意文件 + Range，srt 走同一通道下发
            val options = subtitleUrl?.let {
                com.yinnho.upnpcast.CastOptions(
                    subtitleUri = it,
                    subtitleMimeType = "application/x-subrip"
                )
            }
            Timber.i("DLNA: 推流 %s -> %s (subtitle=%s)", videoUrl, device.name, subtitleUrl != null)
            val ok = if (options != null) {
                DLNACast.castToDevice(
                    device = device,
                    url = videoUrl,
                    title = content.title,
                    options = options
                )
            } else {
                DLNACast.castToDevice(
                    device = device,
                    url = videoUrl,
                    title = content.title
                )
            }
            if (!ok) {
                fail("设备拒绝了投屏请求")
                return false
            }
            if (startPositionMs > 0) {
                // 部分电视在 SetAVTransportURI 后立刻 seek 会丢指令，稍等再发
                delay(800)
                DLNACast.seek(startPositionMs)
            }
            _state.value = _state.value.copy(
                phase = RoutePhase.PLAYING,
                isPlaying = true,
                positionMs = startPositionMs
            )
            startProgressPolling()
            true
        } catch (e: Exception) {
            Timber.e(e, "DLNA: 激活路由失败")
            fail(e.message ?: "投屏连接失败")
            false
        }
    }

    override suspend fun playPause() {
        runCatching {
            if (_state.value.isPlaying) {
                if (DLNACast.pause()) {
                    _state.value = _state.value.copy(isPlaying = false, phase = RoutePhase.PAUSED)
                }
            } else {
                if (DLNACast.play()) {
                    _state.value = _state.value.copy(isPlaying = true, phase = RoutePhase.PLAYING)
                }
            }
        }.onFailure { Timber.e(it, "DLNA playPause 失败") }
    }

    override suspend fun seekTo(positionMs: Long) {
        runCatching { DLNACast.seek(positionMs) }
            .onSuccess {
                _state.value = _state.value.copy(positionMs = positionMs)
                lastPositionMs = positionMs
            }
            .onFailure { Timber.e(it, "DLNA seek 失败") }
    }

    override suspend fun seekRelative(deltaMs: Long) {
        val target = (_state.value.positionMs + deltaMs)
            .coerceAtLeast(0)
            .let { if (_state.value.durationMs > 0) it.coerceAtMost(_state.value.durationMs) else it }
        seekTo(target)
    }

    override suspend fun deactivate() {
        runCatching { DLNACast.stop() }
        stopProgressPolling()
        _state.value = _state.value.copy(phase = RoutePhase.IDLE, isPlaying = false)
    }

    override fun release() {
        stopProgressPolling()
        _state.value = _state.value.copy(phase = RoutePhase.IDLE, isPlaying = false)
    }

    /**
     * 轮询远端进度（1s 间隔）。
     * DLNA 无推送事件，只能拉；getProgress 自带缓存+插值，频率安全。
     */
    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val progress = runCatching { DLNACast.getProgress() }.getOrNull()
                val remoteState = runCatching { DLNACast.getPlaybackState() }.getOrNull()
                if (progress != null) {
                    lastPositionMs = progress.first
                    _state.value = _state.value.copy(
                        positionMs = progress.first,
                        durationMs = progress.second
                    )
                }
                if (remoteState != null) {
                    val playing = remoteState == DLNACast.PlaybackState.PLAYING
                    val phase = when (remoteState) {
                        DLNACast.PlaybackState.PLAYING -> RoutePhase.PLAYING
                        DLNACast.PlaybackState.PAUSED -> RoutePhase.PAUSED
                        DLNACast.PlaybackState.BUFFERING -> RoutePhase.CONNECTING
                        else -> _state.value.phase
                    }
                    _state.value = _state.value.copy(isPlaying = playing, phase = phase)
                }
                delay(1_000L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(phase = RoutePhase.ERROR, errorMessage = message, isPlaying = false)
    }
}
