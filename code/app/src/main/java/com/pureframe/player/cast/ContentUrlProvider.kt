package com.pureframe.player.cast

import java.io.File

/**
 * 内容 URL 提供者（接口）
 *
 * 把播放内容转换为「局域网内可被电视访问的 HTTP URL」。
 * 投屏协议（DLNA/Cast）本质都是把 URL 交给电视端播，
 * 本机文件必须先经此层服务化。
 *
 * 实现方可自由选择：复用 UPnPCast 内置 file server、
 * 自建 NanoHTTPD、或转发 StreamProxyServer（BT 流）。
 */
interface ContentUrlProvider {

    /**
     * 将本地文件转换为局域网 URL
     *
     * @return 可被同网段设备访问的 http URL；文件不可读/服务启动失败返回 null
     */
    fun urlFor(file: File): String?

    /**
     * 直接返回流 URL（BT 边下边播的 StreamProxyServer 地址已天然可访问）
     */
    fun urlForStream(streamUrl: String): String = streamUrl

    /** 释放服务资源 */
    fun release()
}
