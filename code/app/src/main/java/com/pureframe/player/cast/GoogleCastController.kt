package com.pureframe.player.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.pureframe.player.cast.DlnaContentUrlProvider.Companion.mimeTypeFor
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Cast 投屏控制器（出海线）
 *
 * 设备发现走 MediaRouter（Cast 路由经 getMergedSelector 过滤），
 * 推流/控制走 CastSession 的 RemoteMediaClient（与 DlnaRoute 的轮询模型对称）。
 *
 * 无 GMS 设备（国产 ROM 常见）castContext 初始化会抛 IllegalStateException，
 * discoverDevices 返回空列表即可，UI 无需感知差异。
 *
 * 注：不用 media3-cast 的 CastPlayer 整体替换 Player —— 那要求把 MediaSession
 * 的 player 换成 CastPlayer（全局播放语义被接管），与现有「投屏 = 独立 Route、
 * 本机暂停、断开回本机」的 RouteManager 模型冲突，且 RemoteCastPlayer 构造器
 * 包私有（javap 核实）。RemoteMediaClient 直控与 DLNA 线结构对称，改动最小。
 */
@Singleton
class GoogleCastController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentUrlProvider: ContentUrlProvider
) : CastController {

    override val type: RouteType = RouteType.CAST

    /** 无 GMS 时为 null，全部接口调用直接短路 */
    private val castContext: CastContext? by lazy {
        try {
            if (GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS
            ) {
                Timber.i("Cast: 无可用 Google Play Services，Cast 线禁用")
                return@lazy null
            }
            CastContext.getSharedInstance(context)
        } catch (e: Exception) {
            Timber.w(e, "Cast: CastContext 初始化失败（无 GMS 设备属预期）")
            null
        }
    }

    private val routeSelector: MediaRouteSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }

    private val router: MediaRouter by lazy { MediaRouter.getInstance(context) }

    /** GMS 可用（设备列表里是否展示 Cast 分区） */
    fun isAvailable(): Boolean = castContext != null

    override suspend fun discoverDevices(timeoutMs: Long): List<CastDevice> {
        val ctx = castContext ?: return emptyList()
        return withContext(Dispatchers.Main) {
            try {
                // MediaRouter 路由发现是回调驱动的；注册回调后等待一小段时间收集
                val collected = mutableListOf<CastDevice>()
                suspendCancellableCoroutine { cont ->
                    val callback = object : MediaRouter.Callback() {
                        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            route.toCastDevice()?.let(collected::add)
                        }
                    }
                    router.addCallback(routeSelector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                    cont.invokeOnCancellation { router.removeCallback(callback) }
                    // 等待发现窗口后解除回调
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // 再补一次快照（部分路由在注册前已就绪）
                        router.routes.forEach { r -> r.toCastDevice()?.let { d -> if (collected.none { it.id == d.id }) collected.add(d) } }
                        router.removeCallback(callback)
                        if (cont.isActive) cont.resume(Unit)
                    }, timeoutMs)
                }
                collected.distinctBy { it.id }.filter { it.type == RouteType.CAST }
            } catch (e: Exception) {
                Timber.e(e, "Cast: 设备扫描失败")
                emptyList()
            }
        }
    }

    override suspend fun connect(device: CastDevice): PlaybackRoute? {
        val ctx = castContext ?: return null
        // Cast 连接 = 选中 MediaRouter 路由 → SessionManager 建会话，异步回调
        val session = withContext(Dispatchers.Main) {
            val route = router.routes.firstOrNull { it.id == device.id }
                ?: run {
                    Timber.w("Cast: 目标路由已消失 %s", device.name)
                    return@withContext null
                }
            val established = suspendCancellableCoroutine<CastSession?> { cont ->
                val listener = object : com.google.android.gms.cast.framework.SessionManagerListener<CastSession> {
                    override fun onSessionStarted(session: CastSession, sessionId: String) {
                        ctx.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
                        if (cont.isActive) cont.resume(session)
                    }

                    override fun onSessionStartFailed(session: CastSession, error: Int) {
                        ctx.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onSessionEnding(p0: CastSession) {}
                    override fun onSessionEnded(p0: CastSession, p1: Int) {}
                    override fun onSessionResuming(p0: CastSession, p1: String) {}
                    override fun onSessionResumed(p0: CastSession, p1: Boolean) {}
                    override fun onSessionResumeFailed(p0: CastSession, p1: Int) {}
                    override fun onSessionStarting(p0: CastSession) {}
                    override fun onSessionSuspended(p0: CastSession, p1: Int) {}
                }
                ctx.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
                cont.invokeOnCancellation {
                    ctx.sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
                }
                router.selectRoute(route)
            }
            established
        } ?: return null

        val remoteClient = session.remoteMediaClient
            ?: run {
                Timber.w("Cast: 会话已建立但 RemoteMediaClient 不可用")
                return null
            }
        return CastRoute(device.name, session, remoteClient, contentUrlProvider)
    }

    override fun release() {
        // CastContext 是全局单例，无需释放；断开由 RouteManager 通过 route.deactivate 处理
    }

    private fun MediaRouter.RouteInfo.toCastDevice(): CastDevice? {
        if (!isEnabled) return null
        // 过滤：只要 Cast 类路由（远程播放 + Cast category），排除本机/蓝牙
        if (playbackType != MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE) return null
        if (!supportsControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))) {
            return null
        }
        return CastDevice(
            id = id,
            name = name,
            type = RouteType.CAST,
            isTv = true
        )
    }
}

/**
 * Google Cast 播放路由
 *
 * RemoteMediaClient 直控（load/play/pause/seek/stop），进度走
 * getApproximateStreamPosition 轮询 + MediaStatus 状态映射，
 * 与 DlnaRoute 的轮询模型完全对称。
 */
class CastRoute(
    private val deviceName: String,
    private val session: CastSession,
    private val client: RemoteMediaClient,
    private val contentUrlProvider: ContentUrlProvider
) : PlaybackRoute {

    override val type: RouteType = RouteType.CAST

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(
        RouteState(phase = RoutePhase.IDLE, deviceName = deviceName)
    )
    override val state: StateFlow<RouteState> = _state.asStateFlow()

    private var progressJob: Job? = null
    private var lastPositionMs = 0L

    override suspend fun activate(content: RouteContent, startPositionMs: Long): Boolean {
        _state.value = _state.value.copy(phase = RoutePhase.CONNECTING)
        return try {
            val url: String
            val subtitleUrl: String?
            when (content) {
                is RouteContent.LocalFile -> {
                    // 本机文件同样经 ContentUrlProvider 服务化（Cast receiver 不能读 file://）
                    url = contentUrlProvider.urlFor(java.io.File(content.filePath))
                        ?: run {
                            fail("文件不可读，无法投屏")
                            return false
                        }
                    subtitleUrl = content.subtitlePath?.let { contentUrlProvider.urlFor(java.io.File(it)) }
                }
                is RouteContent.Stream -> {
                    url = contentUrlProvider.urlForStream(content.streamUrl)
                    subtitleUrl = content.subtitlePath?.let { contentUrlProvider.urlFor(java.io.File(it)) }
                }
            }
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
            metadata.putString(MediaMetadata.KEY_TITLE, content.title)
            val builder = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeTypeFor(url.substringAfterLast('/').substringBefore('?')))
                .setMetadata(metadata)
            // 外挂 srt 字幕：MediaTrack TYPE_TEXT 随 load 下发，receiver 端可切
            if (subtitleUrl != null) {
                val track = com.google.android.gms.cast.MediaTrack.Builder(SUBTITLE_TRACK_ID, com.google.android.gms.cast.MediaTrack.TYPE_TEXT)
                    .setName("Subtitle")
                    .setSubtype(com.google.android.gms.cast.MediaTrack.SUBTYPE_SUBTITLES)
                    .setContentType("application/x-subrip")
                    .setContentId(subtitleUrl)
                    .build()
                builder.setMediaTracks(listOf(track))
            }
            val mediaInfo = builder.build()

            val result = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    // PendingResult.await 阻塞等待 load 完成
                    client.load(mediaInfo, true, startPositionMs).await()
                }
            }
            if (result == null || !result.status.isSuccess) {
                fail(result?.status?.toString() ?: "加载超时")
                return false
            }
            _state.value = _state.value.copy(
                phase = RoutePhase.PLAYING,
                isPlaying = true,
                positionMs = startPositionMs
            )
            startProgressPolling()
            true
        } catch (e: Exception) {
            Timber.e(e, "Cast: 激活路由失败")
            fail(e.message ?: "投屏连接失败")
            false
        }
    }

    override suspend fun playPause() {
        runCatching {
            if (_state.value.isPlaying) {
                client.pause()?.await()
                _state.value = _state.value.copy(isPlaying = false, phase = RoutePhase.PAUSED)
            } else {
                client.play()?.await()
                _state.value = _state.value.copy(isPlaying = true, phase = RoutePhase.PLAYING)
            }
        }.onFailure { Timber.e(it, "Cast playPause 失败") }
    }

    override suspend fun seekTo(positionMs: Long) {
        runCatching {
            client.seek(positionMs)?.await()
            _state.value = _state.value.copy(positionMs = positionMs)
            lastPositionMs = positionMs
        }.onFailure { Timber.e(it, "Cast seek 失败") }
    }

    override suspend fun seekRelative(deltaMs: Long) {
        val target = (_state.value.positionMs + deltaMs)
            .coerceAtLeast(0)
            .let { if (_state.value.durationMs > 0) it.coerceAtMost(_state.value.durationMs) else it }
        seekTo(target)
    }

    override suspend fun deactivate() {
        runCatching { client.stop()?.await() }
        stopProgressPolling()
        _state.value = _state.value.copy(phase = RoutePhase.IDLE, isPlaying = false)
    }

    override fun release() {
        stopProgressPolling()
        _state.value = _state.value.copy(phase = RoutePhase.IDLE, isPlaying = false)
    }

    companion object {
        /** 外挂字幕轨道 ID（Cast 约定非 0） */
        private const val SUBTITLE_TRACK_ID = 1L
    }

    /** 1s 轮询远端进度（与 DlnaRoute 对称；Cast 亦有事件推送，轮询作兜底统一模型） */
    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val status = runCatching { client.mediaStatus }.getOrNull()
                val position = runCatching { client.approximateStreamPosition }.getOrNull()
                val duration = runCatching { client.streamDuration }.getOrNull()
                if (position != null && position >= 0) {
                    lastPositionMs = position
                    _state.value = _state.value.copy(
                        positionMs = position,
                        durationMs = if (duration != null && duration > 0) duration else _state.value.durationMs
                    )
                }
                if (status != null) {
                    val playing = status.playerState == MediaStatus.PLAYER_STATE_PLAYING
                    val phase = when (status.playerState) {
                        MediaStatus.PLAYER_STATE_PLAYING -> RoutePhase.PLAYING
                        MediaStatus.PLAYER_STATE_PAUSED -> RoutePhase.PAUSED
                        MediaStatus.PLAYER_STATE_BUFFERING,
                        MediaStatus.PLAYER_STATE_LOADING -> RoutePhase.CONNECTING
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
