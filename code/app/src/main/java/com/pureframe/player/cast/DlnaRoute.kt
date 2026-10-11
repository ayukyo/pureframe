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

    /** 最近一次确认的远端位置 + 取得该位置时的本机时钟（插值兜底用） */
    private var lastPositionMs = 0L
    private var lastPositionWallClock = 0L

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
            // 确认远端真的接管了媒体：部分设备的 UPnP 栈会接受 SetAVTransportURI
            // 却不真正播放（残缺实现/非 renderer 设备），表现为静默黑屏。
            // 观察 TransportState，进入 PLAYING/PAUSED/TRANSITIONING 才算成功。
            var confirmed = false
            val confirmDeadline = System.currentTimeMillis() + 6_000L
            while (System.currentTimeMillis() < confirmDeadline) {
                delay(600)
                val ts = runCatching { DLNACast.getPlaybackState() }.getOrNull()
                if (ts == DLNACast.PlaybackState.PLAYING ||
                    ts == DLNACast.PlaybackState.PAUSED ||
                    ts == DLNACast.PlaybackState.BUFFERING
                ) {
                    confirmed = true
                    break
                }
            }
            if (!confirmed) {
                Timber.w("DLNA: 设备接受指令但未开始播放: %s", device.name)
                fail("设备未开始播放")
                return false
            }
            if (startPositionMs > 0) {
                // 部分电视在 SetAVTransportURI 后立刻 seek 会丢指令，稍等再发
                delay(800)
                DLNACast.seek(startPositionMs)
            }
            lastPositionMs = startPositionMs
            lastPositionWallClock = System.currentTimeMillis()
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
                lastPositionWallClock = System.currentTimeMillis()
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
     * 兜底：部分电视 GetPositionInfo 响应不稳定（返回 null/超时），
     * PLAYING 期间用本机时钟从最后确认位置插值推进，避免本机进度条冻结。
     */
    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var wasPlaying = false
            while (isActive) {
                val progress = runCatching { DLNACast.getProgress() }.getOrNull()
                val remoteState = runCatching { DLNACast.getPlaybackState() }.getOrNull()
                val now = System.currentTimeMillis()
                if (progress != null && progress.second > 0L) {
                    lastPositionMs = progress.first
                    lastPositionWallClock = now
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
                        // 远端播完（电视固件通常会自动退出播放器回首页），显式给出结束语义
                        DLNACast.PlaybackState.STOPPED -> RoutePhase.ENDED
                        else -> _state.value.phase
                    }
                    _state.value = _state.value.copy(isPlaying = playing, phase = phase)
                    if (playing) {
                        if (!wasPlaying) {
                            // 从暂停/缓冲恢复：插值起点重置，避免把暂停时长算进进度
                            lastPositionWallClock = now
                        }
                        if (progress == null || progress.second <= 0L) {
                            // 远端位置查询不可用：本地插值推进（封顶在时长）
                            val interpolated = lastPositionMs + (now - lastPositionWallClock)
                            val capped = _state.value.durationMs
                                .takeIf { it > 0 }
                                ?.let { interpolated.coerceAtMost(it) }
                                ?: interpolated
                            lastPositionMs = capped
                            lastPositionWallClock = now
                            _state.value = _state.value.copy(positionMs = capped)
                        }
                    } else {
                        // 暂停/停止：冻结插值基准，恢复播放时不跳变
                        lastPositionWallClock = now
                    }
                    wasPlaying = playing
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
