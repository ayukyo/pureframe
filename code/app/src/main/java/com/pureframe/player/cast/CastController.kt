package com.pureframe.player.cast

/**
 * 投屏设备
 *
 * 统一模型：屏蔽 DLNA/Cast 的设备描述差异，UI 只看此模型。
 *
 * @param id 协议内唯一标识（DLNA 为 UDN，Cast 为 device id）
 * @param name 设备显示名（如「小米电视5」）
 * @param type 路由类型（决定连接方式）
 * @param isTv 是否电视类设备（DLNA 根据设备类型推断，用于排序置顶）
 */
data class CastDevice(
    val id: String,
    val name: String,
    val type: RouteType,
    val isTv: Boolean = true
)

/**
 * 投屏控制器（接口）
 *
 * 负责设备发现与连接会话管理，返回的 [PlaybackRoute] 承担具体播放控制。
 * 每种投屏协议一个实现（DLNA → DlnaCastController，Cast → CastCastController）。
 */
interface CastController {

    /** 协议类型 */
    val type: RouteType

    /**
     * 扫描局域网内可用设备
     *
     * @param timeoutMs 扫描超时
     * @return 发现的设备列表（可能为空）
     */
    suspend fun discoverDevices(timeoutMs: Long = 5000L): List<CastDevice>

    /**
     * 连接设备并创建播放路由
     *
     * @param device 目标设备（来自 [discoverDevices]）
     * @return 播放路由；连接失败返回 null
     */
    suspend fun connect(device: CastDevice): PlaybackRoute?

    /** 释放控制器资源（App 退出时） */
    fun release()
}
