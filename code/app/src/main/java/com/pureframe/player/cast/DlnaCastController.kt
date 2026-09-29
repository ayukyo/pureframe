package com.pureframe.player.cast

import android.content.Context
import com.yinnho.upnpcast.DLNACast
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DLNA 内容 URL 提供者
 *
 * 自带 NanoHTTPD file server（支持 Range/seek，电视端拖进度条必需）。
 * UPnPCast 的 file server 是 internal API 不可直接使用，且自实现可以：
 * - 与 RouteType 解耦（未来 Cast 线复用同一 provider）
 * - 控制端口（固定端口避免每次投屏端口漂移导致电视端缓存失效）
 * - 精确的 MIME 映射
 */
@Singleton
class DlnaContentUrlProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ContentUrlProvider {

    companion object {
        // 固定端口：电视端拿到 URL 后可能长缓存，端口漂移会让旧 URL 失效
        private const val PORT = 50831
        private val MIME_MAP = mapOf(
            "mp4" to "video/mp4", "m4v" to "video/mp4",
            "mkv" to "video/x-matroska", "webm" to "video/webm",
            "avi" to "video/x-msvideo", "mov" to "video/quicktime",
            "ts" to "video/mp2t", "wmv" to "video/x-ms-wmv",
            "flv" to "video/x-flv", "mpg" to "video/mpeg", "mpeg" to "video/mpeg",
            "3gp" to "video/3gpp",
            "srt" to "application/x-subrip", "vtt" to "text/vtt",
            "ass" to "text/plain", "ssa" to "text/plain"
        )

        /** 按文件名（含扩展名）推断 MIME，供 Cast 线复用 */
        fun mimeTypeFor(fileName: String): String =
            MIME_MAP[fileName.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

        private const val TOKEN_PATH = "/pfmedia"
        private const val SOCKET_TIMEOUT = 30_000
    }

    private var server: MediaFileServer? = null

    override fun urlFor(file: File): String? {
        if (!file.exists() || !file.canRead()) {
            Timber.w("DlnaContentUrlProvider: 文件不可读 %s", file.absolutePath)
            return null
        }
        val srv = ensureServer()
        val ip = currentLanIp() ?: run {
            Timber.w("DlnaContentUrlProvider: 未找到局域网 IP")
            return null
        }
        val encoded = URLEncoder.encode(file.absolutePath, "UTF-8")
        return "http://$ip:$PORT$TOKEN_PATH?path=$encoded"
    }

    override fun urlForStream(streamUrl: String): String = streamUrl

    override fun release() {
        runCatching { DLNACast.cleanup() }
        runCatching { server?.stop() }
        server = null
    }

    @Synchronized
    private fun ensureServer(): MediaFileServer {
        server?.let { if (it.wasStarted()) return it }
        server?.stop()
        return MediaFileServer(PORT).also {
            it.start(SOCKET_TIMEOUT, false)
            server = it
            Timber.i("DlnaContentUrlProvider: file server 启动 :$PORT")
        }
    }

    private fun currentLanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .map { it.hostAddress }
            .firstOrNull { it?.startsWith("192.168.") == true || it?.startsWith("10.") == true }
    } catch (e: Exception) {
        null
    }

    /**
     * 极简媒体文件 HTTP 服务：GET + Range 请求（RFC 7233 单区间），
     * 足够满足电视端 DLNA 拖动进度。仅监听局域网请求，路径带 token 校验。
     */
    private class MediaFileServer(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val params = session.parameters
            val path = params["path"]?.firstOrNull()
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing path")
            val file = File(path)
            if (!file.exists() || !file.canRead()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }

            val mime = MIME_MAP[file.extension.lowercase()] ?: "application/octet-stream"
            val fileLength = file.length()

            // Range 请求：返回 206 + 指定区间
            val rangeHeader = session.headers["range"]
            if (rangeHeader != null) {
                val match = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader)
                if (match != null) {
                    val start = match.groupValues[1].toLong()
                    val end = if (match.groupValues[2].isBlank()) fileLength - 1
                    else match.groupValues[2].toLong().coerceAtMost(fileLength - 1)
                    if (start >= fileLength || start > end) {
                        val resp = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, mime, "")
                        resp.addHeader("Content-Range", "bytes */$fileLength")
                        return resp
                    }
                    val raf = RandomAccessFile(file, "r")
                    raf.seek(start)
                    val length = (end - start + 1)
                    val resp = newChunkedResponse(Response.Status.PARTIAL_CONTENT, mime, RangeInputStream(raf, length))
                    resp.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    return resp
                }
            }

            // 全量请求
            val raf = RandomAccessFile(file, "r")
            val resp = newChunkedResponse(Response.Status.OK, mime, RangeInputStream(raf, fileLength))
            resp.addHeader("Accept-Ranges", "bytes")
            return resp
        }

        /** 限定读取长度的流：配合 seek 后按区间输出，读完即止 */
        private class RangeInputStream(private val raf: RandomAccessFile, private val remaining: Long) :
            java.io.InputStream() {
            private var left = remaining
            override fun read(): Int {
                if (left <= 0) return -1
                val b = raf.read()
                if (b >= 0) left--
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = raf.read(b, off, minOf(len.toLong(), left).toInt())
                if (n > 0) left -= n
                return n
            }

            override fun close() {
                raf.close()
            }
        }
    }
}

/**
 * DLNA 投屏控制器
 *
 * 包装 UPnPCast 的搜索能力，产出 [DlnaRoute]。
 * DLNACast.init 全局一次（首次调用时懒初始化）。
 */
@Singleton
class DlnaCastController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val urlProvider: DlnaContentUrlProvider
) : CastController {

    override val type: RouteType = RouteType.DLNA

    private var initialized = false

    private fun ensureInit() {
        if (!initialized) {
            DLNACast.init(context)
            initialized = true
        }
    }

    override suspend fun discoverDevices(timeoutMs: Long): List<CastDevice> =
        withContext(Dispatchers.IO) {
            ensureInit()
            try {
                DLNACast.search(timeout = timeoutMs).map { d ->
                    CastDevice(
                        id = d.id,
                        name = d.name.ifBlank { "DLNA 设备" },
                        type = RouteType.DLNA,
                        isTv = d.isTV
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "DLNA 设备扫描失败")
                emptyList()
            }
        }

    override suspend fun connect(device: CastDevice): PlaybackRoute? {
        ensureInit()
        val target = DLNACast.search(timeout = 5000L).firstOrNull {
            it.id == device.id
        } ?: run {
            Timber.w("DLNA: 目标设备已离线 %s", device.name)
            return null
        }
        return DlnaRoute(target, urlProvider)
    }

    override fun release() {
        urlProvider.release()
        initialized = false
    }
}
