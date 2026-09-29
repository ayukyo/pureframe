package com.pureframe.player.cast

/**
 * 投屏路由抽象（接口化架构核心）
 *
 * 所有播放/投屏目标都实现此接口，PlayerManager 与 UI 层只依赖接口，
 * 新增投屏协议（Google Cast、AirPlay 等）时无需改动上层。
 *
 * 生命周期约定：
 * - [activate] 切换到该路由（本机暂停/释放移交，远程开始播放）
 * - [release]  彻底释放该路由资源（退出播放页时调用）
 * - 路由内部不持有 UI 引用，状态通过 [state] StateFlow 暴露
 */
interface PlaybackRoute {

    /** 路由类型标识 */
    val type: RouteType

    /** 当前路由状态（进度、播放状态等） */
    val state: kotlinx.coroutines.flow.StateFlow<RouteState>

    /**
     * 激活路由：在此内容上开始播放
     *
     * @param content 要播放的内容（文件路径或流 URL + 标题）
     * @param startPositionMs 起始位置（续播/设备切换时传入）
     * @return 是否成功开始播放
     */
    suspend fun activate(content: RouteContent, startPositionMs: Long = 0L): Boolean

    /** 播放/暂停 */
    suspend fun playPause()

    /** 跳转到指定位置（毫秒） */
    suspend fun seekTo(positionMs: Long)

    /** 相对跳转 */
    suspend fun seekRelative(deltaMs: Long)

    /** 停止并断开（保留设备记忆，下次可快速连接） */
    suspend fun deactivate()

    /** 释放全部资源（退出播放页） */
    fun release()
}

/** 路由类型 */
enum class RouteType {
    /** 本机 ExoPlayer 播放 */
    LOCAL,

    /** DLNA/UPnP 推流到电视/盒子 */
    DLNA,

    /** Google Cast（Chromecast/Android TV/带 Cast 的电视） */
    CAST
}

/**
 * 路由播放内容
 *
 * @param title 显示标题（投到电视端显示）
 * @param filePath 本地文件绝对路径（本地播放或经 ContentUrlProvider 转 URL）
 * @param streamUrl 已就绪的流 URL（如 BT 边下边播的 StreamProxyServer 地址，可直接下发给电视）
 */
sealed class RouteContent {
    abstract val title: String

    data class LocalFile(val filePath: String, override val title: String) : RouteContent()
    data class Stream(val streamUrl: String, override val title: String) : RouteContent()
}

/**
 * 路由状态
 *
 * @param phase 路由所处阶段
 * @param deviceName 远端设备名（LOCAL 路由为 null）
 * @param positionMs 当前播放位置（远程端进度）
 * @param durationMs 总时长（未知为 -1）
 * @param isPlaying 远端是否正在播放
 * @param errorMessage 失败原因（phase 为 ERROR 时非空）
 */
data class RouteState(
    val phase: RoutePhase = RoutePhase.IDLE,
    val deviceName: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = -1L,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)

/** 路由阶段 */
enum class RoutePhase {
    /** 未连接 */
    IDLE,

    /** 正在连接/建立会话 */
    CONNECTING,

    /** 已连接，正在播放 */
    PLAYING,

    /** 已连接，已暂停 */
    PAUSED,

    /** 连接断开或播放失败 */
    ERROR
}
