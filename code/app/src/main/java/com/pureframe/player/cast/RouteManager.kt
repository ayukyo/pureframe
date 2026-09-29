package com.pureframe.player.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一路由管理器
 *
 * 持有「当前激活的 PlaybackRoute」，对上层（PlayerViewModel/UI）暴露
 * 协程友好的投屏控制 API。路由切换（本机 ↔ DLNA ↔ 未来的 Cast）全部收敛在此：
 *
 * - 投屏：LocalRoute 暂停 → 新 Route activate（进度跟随）
 * - 断开：Route deactivate → 本机从断点续播
 *
 * 上层不感知具体协议，符合接口化架构要求。
 */
@Singleton
class RouteManager @Inject constructor(
    controllers: Set<@JvmSuppressWildcards CastController>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 全部协议控制器（DLNA/Cast/...），按 device.type 路由连接 */
    private val controllers: Map<RouteType, CastController> =
        controllers.associateBy { it.type }

    /** 当前激活的远程路由（null = 本机播放） */
    private val _activeRoute = MutableStateFlow<PlaybackRoute?>(null)
    val activeRoute: StateFlow<PlaybackRoute?> = _activeRoute.asStateFlow()

    /** 当前路由状态（本机播放时为 null 的 state，UI 用 isCasting 判断） */
    private val _routeState = MutableStateFlow<RouteState?>(null)
    val routeState: StateFlow<RouteState?> = _routeState.asStateFlow()

    /** 是否正在投屏 */
    val isCasting: Boolean get() = _activeRoute.value != null

    /** 供 UI 观察的本机进度快照（切回本机时续播用） */
    var localPositionOnCastStart: Long = 0L

    /**
     * 扫描设备（聚合全部协议的结果：DLNA SSDP + Cast MediaRouter 并行扫）
     */
    suspend fun discoverDevices(timeoutMs: Long = 5000L): List<CastDevice> =
        coroutineScope {
            controllers.values.map { controller ->
                async {
                    runCatching { controller.discoverDevices(timeoutMs) }
                        .onFailure { Timber.e(it, "%s 扫描失败", controller.type) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten().sortedWith(
                compareByDescending<CastDevice> { it.isTv }.thenBy { it.name }
            )
        }

    /**
     * 连接设备并把内容投上去
     *
     * @param device 目标设备
     * @param content 播放内容
     * @param startPositionMs 起始位置（跟随时取本机当前进度）
     * @param onLocalPause 切换到远程前暂停本机播放的回调（由 PlayerManager 注入）
     * @return 是否成功
     */
    suspend fun castTo(
        device: CastDevice,
        content: RouteContent,
        startPositionMs: Long,
        onLocalPause: () -> Unit
    ): Boolean {
        // 已有投屏会话则先断开
        disconnectFromDevice(keepLocalPaused = true)

        val controller = controllers[device.type] ?: run {
            Timber.w("无 %s 协议控制器", device.type)
            return false
        }
        val route = controller.connect(device) ?: return false
        _activeRoute.value = route
        _routeState.value = route.state.value

        // 观察路由状态流
        scope.launch {
            route.state.collect { _routeState.value = it }
        }

        onLocalPause()
        val ok = route.activate(content, startPositionMs)
        if (!ok) {
            Timber.w("投屏激活失败，回退本机: %s", device.name)
            _activeRoute.value = null
            _routeState.value = null
        }
        return ok
    }

    /**
     * 断开投屏
     *
     * @param keepLocalPaused true=断开后本机保持暂停（用户手动断开）；
     *                        false 同义，本机恢复由调用方控制
     */
    suspend fun disconnectFromDevice(keepLocalPaused: Boolean = true) {
        val route = _activeRoute.value ?: return
        runCatching { route.deactivate() }
            .onFailure { Timber.e(it, "deactivate 失败") }
        route.release()
        _activeRoute.value = null
        _routeState.value = null
    }

    fun playPause() {
        scope.launch { _activeRoute.value?.playPause() }
    }

    fun seekTo(positionMs: Long) {
        scope.launch { _activeRoute.value?.seekTo(positionMs) }
    }

    fun seekRelative(deltaMs: Long) {
        scope.launch { _activeRoute.value?.seekRelative(deltaMs) }
    }

    fun release() {
        val route = _activeRoute.value
        scope.launch {
            route?.let { runCatching { it.deactivate() } }
            route?.release()
            _activeRoute.value = null
            _routeState.value = null
            controllers.values.forEach { runCatching { it.release() } }
        }
    }
}
