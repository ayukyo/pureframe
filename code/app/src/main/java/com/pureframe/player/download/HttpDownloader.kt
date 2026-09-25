package com.pureframe.player.download

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
/** 并行分片数 */
private const val PARALLEL_CHUNKS = 4
/** 文件超过该大小才启用并行分片（小于此值单线程即可） */
private const val MIN_PARALLEL_FILE_SIZE = 2L * 1024 * 1024
/** 读写缓冲：8KB 太小，64KB 明显减少系统调用 */
private const val BUFFER_SIZE = 64 * 1024
/** 进度 emit 节流间隔 */
private const val EMIT_INTERVAL_MS = 200L

/**
 * HTTP 下载器
 *
 * 使用 HttpURLConnection 实现 HTTP 下载，支持：
 * - 断点续传
 * - 进度实时反馈（节流，避免 UI 重组风暴）
 * - 暂停/恢复
 * - 多线程分块并行下载（服务器支持 Range 且文件足够大时自动启用）
 */
@Singleton
class HttpDownloader @Inject constructor() {

    private val downloadJobs = ConcurrentHashMap<Long, DownloadJobState>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 下载任务状态
     */
    private data class DownloadJobState(
        val taskId: Long,
        var job: Job? = null,
        var isPaused: Boolean = false,
        var downloadedBytes: Long = 0,
        var totalBytes: Long = 0,
        var speed: Long = 0,
        var state: DownloadState = DownloadState.IDLE,
        var inputStream: InputStream? = null,
        var outputStream: FileOutputStream? = null,
        // 并行分片模式：记录各分片进度，用于暂停后按片恢复
        var chunks: List<ChunkState>? = null,
        // 当前写盘目标，用于暂停/出错时持久化分片进度
        @Volatile var targetFile: File? = null,
        // 活跃连接（单线程 1 个 / 并行 N 个）：暂停或停止时统一 disconnect，
        // 让阻塞中的 read 立即抛异常退出，避免旧 flow 长期挂起与新 flow 并发写同一文件
        val connections: MutableList<HttpURLConnection> =
            java.util.Collections.synchronizedList(mutableListOf())
    )

    /**
     * 分片进度（并行下载用）
     * start/end 为该分片负责的字节区间（end 含头），downloaded 为本片已完成字节
     */
    private class ChunkState(
        val index: Int,
        val start: Long,
        val end: Long,
        @Volatile var downloaded: Long = 0
    ) {
        val total: Long get() = end - start + 1
        val remaining: Long get() = total - downloaded
    }

    /**
     * 下载状态
     */
    enum class DownloadState {
        IDLE,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        ERROR
    }

    /**
     * HTTP 下载进度信息
     */
    data class HttpDownloadProgress(
        val taskId: Long,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speed: Long,
        val state: DownloadState
    )

    /**
     * 开始下载
     *
     * @param taskId 任务 ID
     * @param url 下载地址
     * @param savePath 保存目录
     * @param fileName 文件名
     * @param resumePosition 断点位置（用于恢复下载）
     */
    fun startDownload(
        taskId: Long,
        url: String,
        savePath: String,
        fileName: String,
        resumePosition: Long = 0
    ): Flow<HttpDownloadProgress> = flow {
        val jobState = DownloadJobState(
            taskId = taskId,
            downloadedBytes = resumePosition,
            state = DownloadState.DOWNLOADING
        )
        downloadJobs[taskId] = jobState

        try {
            // 确保目录存在
            val dir = File(savePath)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(savePath, fileName)
            jobState.targetFile = file
            var contentLength = 0L
            var supportsRange = false

            // 第一步：获取文件信息（HEAD 请求）
            withContext(Dispatchers.IO) {
                val headConn = URL(url).openConnection() as HttpURLConnection
                headConn.requestMethod = "HEAD"
                headConn.connectTimeout = 15000
                headConn.readTimeout = 15000
                headConn.setRequestProperty("User-Agent", USER_AGENT)

                try {
                    contentLength = headConn.contentLengthLong
                    supportsRange = headConn.getHeaderField("Accept-Ranges") == "bytes"
                    jobState.totalBytes = contentLength
                    Timber.d("HttpDownloader: HEAD 请求完成 - contentLength=$contentLength, supportsRange=$supportsRange")
                } catch (e: Exception) {
                    Timber.e(e, "HttpDownloader: HEAD 请求失败")
                } finally {
                    headConn.disconnect()
                }
            }

            val useParallel = supportsRange &&
                contentLength >= MIN_PARALLEL_FILE_SIZE &&
                contentLength > 0

            if (useParallel) {
                Timber.d("HttpDownloader: 启用 ${PARALLEL_CHUNKS} 线程分片下载 - taskId=$taskId, size=$contentLength")
                downloadParallel(jobState, taskId, url, file, contentLength)
            } else {
                downloadSingle(jobState, taskId, url, file, contentLength, supportsRange, resumePosition)
            }
        } catch (e: Exception) {
            // 暂停优先于错误：pause() 会先把 state 置为 PAUSED 再断连，
            // 并行模式下 worker 的中断会以 InterruptedException / CancellationException
            // 的形式传播到这里，统一按暂停收尾（落 parts 文件供恢复）
            val paused = jobState.state == DownloadState.PAUSED
            if (paused || (e is CancellationException && jobState.state != DownloadState.ERROR)) {
                if (!paused) jobState.state = DownloadState.PAUSED
                writePartsFile(jobState)
                Timber.d("HttpDownloader: 下载暂停 - taskId=$taskId")
                emit(
                    HttpDownloadProgress(
                        taskId = taskId,
                        progress = if (jobState.totalBytes > 0) jobState.downloadedBytes.toFloat() / jobState.totalBytes * 100 else 0f,
                        downloadedBytes = jobState.downloadedBytes,
                        totalBytes = jobState.totalBytes,
                        speed = 0,
                        state = DownloadState.PAUSED
                    )
                )
            } else {
                if (e is CancellationException) throw e
                jobState.state = DownloadState.ERROR
                Timber.e(e, "HttpDownloader: 下载错误 - taskId=$taskId")
                writePartsFile(jobState)
                emit(
                    HttpDownloadProgress(
                        taskId = taskId,
                        progress = if (jobState.totalBytes > 0) jobState.downloadedBytes.toFloat() / jobState.totalBytes * 100 else 0f,
                        downloadedBytes = jobState.downloadedBytes,
                        totalBytes = jobState.totalBytes,
                        speed = 0,
                        state = DownloadState.ERROR
                    )
                )
            }
        } finally {
            // 仅当当前 jobState 仍属于本 flow 时才移除，避免旧 flow 误删恢复后的新 flow 状态
            downloadJobs.remove(taskId, jobState)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 多线程分片并行下载
     *
     * 每个分片一个协程，独立 Range 连接，写入同一文件的不同偏移
     * （各 RandomAccessFile 实例拥有独立文件指针，可安全并发）。
     * 进度由主协程节流汇总 emit，避免跨协程 emit 破坏 Flow 不变量。
     */
    private suspend fun FlowCollector<HttpDownloadProgress>.downloadParallel(
        jobState: DownloadJobState,
        taskId: Long,
        url: String,
        file: File,
        contentLength: Long
    ) {
        // 优先从分片进度文件恢复（暂停/异常后重启的场景）
        val restoredChunks = (if (jobState.chunks == null) readPartsFile(file) else jobState.chunks)
            ?.takeIf { chunks ->
                // 分片区间必须与本次 HEAD 得到的大小一致，否则视为失效残留
                val covers = (chunks.maxOf { it.end } + 1) == contentLength
                if (!covers) {
                    Timber.w("HttpDownloader: 分片进度与当前文件大小不一致，丢弃")
                    partsFile(file).delete()
                }
                covers
            }
        val chunks = restoredChunks ?: run {
            // 全新下载：清掉旧文件，避免上一轮的稀疏数据混入
            if (file.exists()) file.delete()
            val chunkSize = (contentLength + PARALLEL_CHUNKS - 1) / PARALLEL_CHUNKS
            (0 until PARALLEL_CHUNKS).mapNotNull { i ->
                val start = i * chunkSize
                val end = minOf(start + chunkSize, contentLength) - 1
                if (start > end) null else ChunkState(i, start, end)
            }
        }
        jobState.chunks = chunks
        val resumed = restoredChunks != null
        Timber.d("HttpDownloader: 并行分片 ${if (resumed) "恢复" else "新建"} - ${chunks.size} 片, 已完成 ${chunks.sumOf { it.downloaded }}")
        jobState.downloadedBytes = chunks.sumOf { it.downloaded }

        coroutineScope {
            val workers = chunks.map { chunk ->
                launch(Dispatchers.IO) {
                    downloadChunk(jobState, taskId, url, file, chunk)
                }
            }

            // 主协程负责节流 emit（flow 不允许跨协程 emit）
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var speed = 0L

            while (true) {
                val allDone = workers.all { it.isCompleted }
                if (allDone) break

                delay(EMIT_INTERVAL_MS)

                val now = System.currentTimeMillis()
                val total = chunks.sumOf { it.downloaded }
                jobState.downloadedBytes = total

                if (jobState.isPaused) continue

                val elapsed = now - lastTime
                if (elapsed >= EMIT_INTERVAL_MS) {
                    speed = (total - lastBytes) * 1000 / elapsed
                    lastBytes = total
                    lastTime = now

                    // 进度计算先除后乘，避免 Float 精度丢失（详见 EntityMappers）
                    val progress = total.toFloat() / contentLength * 100f
                    emit(
                        HttpDownloadProgress(
                            taskId = taskId,
                            progress = progress.coerceIn(0f, 100f),
                            downloadedBytes = total,
                            totalBytes = contentLength,
                            speed = speed,
                            state = DownloadState.DOWNLOADING
                        )
                    )
                }
            }
        }

        // 校验完整性（数据齐全即视为成功，避免与暂停竞态时误判）
        val total = chunks.sumOf { it.downloaded }
        if (total != contentLength) {
            // 非完整：若是暂停/停止导致，抛出以走统一的收尾分支
            if (jobState.isPaused || jobState.state == DownloadState.PAUSED) {
                throw InterruptedException("下载已暂停")
            }
            if (jobState.state == DownloadState.ERROR) {
                throw InterruptedException("下载已停止")
            }
            throw IllegalStateException("分片下载不完整: $total/$contentLength")
        }

        jobState.downloadedBytes = contentLength
        jobState.chunks = null
        jobState.state = DownloadState.COMPLETED
        // 完成：清理分片进度文件
        partsFile(file).delete()
        emit(
            HttpDownloadProgress(
                taskId = taskId,
                progress = 100f,
                downloadedBytes = contentLength,
                totalBytes = contentLength,
                speed = 0,
                state = DownloadState.COMPLETED
            )
        )
        Timber.d("HttpDownloader: 并行下载完成 - taskId=$taskId, totalBytes=$contentLength")
    }

    /**
     * 下载单个分片（独立连接 + Range）
     */
    private suspend fun downloadChunk(
        jobState: DownloadJobState,
        taskId: Long,
        url: String,
        file: File,
        chunk: ChunkState
    ) {
        // 恢复场景下该分片此前已完整下载：无需再建连，
        // 否则 from = start + downloaded = end + 1，会产生 bytes=(end+1)-end 的非法 Range 并收到 416
        if (chunk.downloaded >= chunk.total) return

        var conn: HttpURLConnection? = null
        var raf: RandomAccessFile? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val from = chunk.start + chunk.downloaded
            if (chunk.downloaded > 0) {
                conn.setRequestProperty("Range", "bytes=$from-${chunk.end}")
            } else {
                conn.setRequestProperty("Range", "bytes=${chunk.start}-${chunk.end}")
            }
            jobState.connections.add(conn)
            conn.connect()

            val code = conn.responseCode
            if (code == 416) {
                // 请求区间已全部拿到（服务端认为超出范围）：视为该片已完成
                chunk.downloaded = chunk.total
                return
            }
            if (code != 206 && code != 200) {
                throw java.io.IOException("服务器返回 HTTP $code（分片 ${chunk.index}）")
            }

            val input = BufferedInputStream(conn.inputStream, BUFFER_SIZE)
            raf = RandomAccessFile(file, "rw")
            // 有已下载部分（恢复）时跳到对应偏移；206 响应体从 from 开始
            if (code == 206) {
                raf.seek(from)
            } else {
                // 服务器忽略 Range 返回全量：只能从头顺序写（极少见，回退全片重下）
                raf.seek(chunk.start)
                chunk.downloaded = 0
            }

            val buffer = ByteArray(BUFFER_SIZE)
            while (chunk.downloaded < chunk.total) {
                // 暂停/停止：立即退出本分片，由上层 flow 收尾（进度已随 parts 文件持久化）
                if (jobState.isPaused || jobState.state == DownloadState.ERROR) {
                    throw InterruptedException("分片下载中断")
                }
                val remaining = (chunk.total - chunk.downloaded).toInt().coerceAtMost(buffer.size)
                val n = input.read(buffer, 0, remaining)
                if (n == -1) break
                raf.write(buffer, 0, n)
                chunk.downloaded += n
            }
        } catch (e: Exception) {
            // 暂停/停止引发的读中断：向上抛出，交由 flow 的 catch 分支统一收尾
            if (jobState.isPaused || jobState.state == DownloadState.ERROR) {
                throw InterruptedException("分片下载中断")
            }
            throw e
        } finally {
            try { raf?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
            if (conn != null) jobState.connections.remove(conn)
        }
    }

    /**
     * 单线程下载（小文件或服务器不支持 Range 时回退）
     */
    private suspend fun FlowCollector<HttpDownloadProgress>.downloadSingle(
        jobState: DownloadJobState,
        taskId: Long,
        url: String,
        file: File,
        contentLength: Long,
        supportsRange: Boolean,
        resumePosition: Long
    ) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", USER_AGENT)

        if (supportsRange && resumePosition > 0) {
            conn.setRequestProperty("Range", "bytes=$resumePosition-")
            Timber.d("HttpDownloader: 使用断点续传 from=$resumePosition")
        }

        jobState.connections.add(conn)
        conn.connect()

        val responseCode = conn.responseCode
        Timber.d("HttpDownloader: 响应码=$responseCode")

        // HTTP 错误码（403/404/500 等）：连接上但拿到的是错误页，
        // 必须在此明确失败，否则会把错误页当内容读、最后抛出令人困惑的 FileNotFoundException
        if (responseCode !in 200..299) {
            throw java.io.IOException("服务器返回 HTTP $responseCode（无法下载该地址）")
        }

        var actualStartPosition = resumePosition
        if (responseCode == 206) {
            val contentRange = conn.getHeaderField("Content-Range")
            val rangeMatch = Regex("bytes (\\d+)-(\\d+)/(\\d+)").find(contentRange ?: "")
            if (rangeMatch != null) {
                actualStartPosition = rangeMatch.groupValues[1].toLong()
            }
        }

        if (responseCode != 206 && resumePosition > 0 && !supportsRange) {
            Timber.w("HttpDownloader: 服务器不支持断点续传，从头开始下载")
            actualStartPosition = 0
            jobState.downloadedBytes = 0
        }

        val effectiveLength = if (contentLength <= 0) {
            var l = conn.contentLengthLong
            if (l > 0 && responseCode == 206) l += actualStartPosition // 206 时 Content-Length 是分片长度
            l
        } else contentLength
        jobState.totalBytes = effectiveLength

        val inputStream = BufferedInputStream(conn.inputStream, BUFFER_SIZE)
        jobState.inputStream = inputStream

        val randomAccessFile = RandomAccessFile(file, "rw")
        randomAccessFile.seek(actualStartPosition)

        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var lastUpdateTime = System.currentTimeMillis()
        var bytesInLastSecond = 0L
        var totalBytesRead = actualStartPosition
        var lastEmit = 0L

        Timber.d("HttpDownloader: 开始下载数据到 $file")

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // 暂停/停止：立即退出，进度已按 downloadedBytes 落库，恢复时走 Range 续传
                if (jobState.isPaused || jobState.state == DownloadState.ERROR) {
                    throw InterruptedException("下载已中断")
                }

                randomAccessFile.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                jobState.downloadedBytes = totalBytesRead
                bytesInLastSecond += bytesRead

                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - lastUpdateTime
                if (elapsed >= 1000) {
                    jobState.speed = bytesInLastSecond * 1000 / elapsed
                    bytesInLastSecond = 0
                    lastUpdateTime = currentTime
                }

                // 节流 emit：每 200ms 才发一次进度，避免 8KB 一发拖垮 UI
                if (currentTime - lastEmit >= EMIT_INTERVAL_MS || totalBytesRead >= effectiveLength && effectiveLength > 0) {
                    lastEmit = currentTime
                    val progress = if (effectiveLength > 0) {
                        (totalBytesRead.toFloat() / effectiveLength * 100f).coerceIn(0f, 100f)
                    } else 0f
                    emit(
                        HttpDownloadProgress(
                            taskId = taskId,
                            progress = progress,
                            downloadedBytes = totalBytesRead,
                            totalBytes = effectiveLength,
                            speed = jobState.speed,
                            state = jobState.state
                        )
                    )
                }
            }
        } finally {
            try { randomAccessFile.close() } catch (_: Exception) {}
            try { inputStream.close() } catch (_: Exception) {}
            try { conn.disconnect() } catch (_: Exception) {}
            jobState.connections.remove(conn)
        }

        jobState.state = DownloadState.COMPLETED
        emit(
            HttpDownloadProgress(
                taskId = taskId,
                progress = 100f,
                downloadedBytes = totalBytesRead,
                totalBytes = effectiveLength,
                speed = 0,
                state = DownloadState.COMPLETED
            )
        )
        Timber.d("HttpDownloader: 下载完成 - taskId=$taskId, totalBytes=$totalBytesRead")
    }

    /** 分片进度文件：与被下载文件同目录，命名为 `<文件名>.parts` */
    private fun partsFile(file: File): File = File(file.parentFile, "${file.name}.parts")

    /**
     * 持久化分片进度（仅并行模式），供进程重启后按片续传。
     * 格式：首行 `size=<总大小>`，随后每行 `index,start,end,downloaded`。
     */
    private fun writePartsFile(jobState: DownloadJobState) {
        val chunks = jobState.chunks ?: return
        val file = jobState.targetFile ?: return
        if (jobState.state == DownloadState.COMPLETED) return
        try {
            val sb = StringBuilder()
            sb.append("size=").append(jobState.totalBytes).append('\n')
            chunks.forEach { c ->
                sb.append(c.index).append(',').append(c.start).append(',')
                    .append(c.end).append(',').append(c.downloaded).append('\n')
            }
            partsFile(file).writeText(sb.toString())
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloader: 写分片进度失败 - taskId=${jobState.taskId}")
        }
    }

    /**
     * 读取分片进度；文件缺失、格式不符、总大小不一致或落盘数据不足时返回 null（视为全新下载）
     */
    private fun readPartsFile(file: File): List<ChunkState>? {
        val pf = partsFile(file)
        if (!pf.exists()) return null
        return try {
            val lines = pf.readLines().filter { it.isNotBlank() }
            val size = lines.firstOrNull()?.removePrefix("size=")?.toLongOrNull()
            if (size == null || size <= 0) return null

            val chunks = lines.drop(1).mapNotNull { line ->
                val p = line.split(',')
                if (p.size != 4) return@mapNotNull null
                val idx = p[0].toIntOrNull() ?: return@mapNotNull null
                val start = p[1].toLongOrNull() ?: return@mapNotNull null
                val end = p[2].toLongOrNull() ?: return@mapNotNull null
                val done = p[3].toLongOrNull() ?: return@mapNotNull null
                ChunkState(idx, start, end, done.coerceIn(0, end - start + 1))
            }
            if (chunks.isEmpty()) return null

            val sorted = chunks.sortedBy { it.start }
            // 分片必须完整覆盖 [0, size)
            if (sorted.first().start != 0L || sorted.last().end != size - 1) {
                Timber.w("HttpDownloader: 分片进度与文件不匹配，丢弃")
                pf.delete()
                return null
            }
            // 落盘数据必须覆盖已记录的下载量，否则说明数据文件被外部改动过
            val needLength = sorted.maxOf { it.start + it.downloaded }
            if (!file.exists() || file.length() < needLength) {
                Timber.w("HttpDownloader: 数据文件缺失或不足（${file.length()}/$needLength），丢弃分片进度")
                pf.delete()
                return null
            }
            chunks
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloader: 读分片进度失败")
            null
        }
    }

    /**
     * 暂停下载
     *
     * 仅置标志并主动断开连接，让阻塞中的 read 立即退出；随后对应 flow 会走到
     * PAUSED 分支收尾（并行模式同时落 parts 文件），确保不会与恢复后的新 flow 并发写同一文件。
     */
    fun pause(taskId: Long) {
        downloadJobs[taskId]?.let { state ->
            state.isPaused = true
            state.state = DownloadState.PAUSED
            disconnectAll(state)
            Timber.d("HttpDownloader: 暂停下载 - taskId=$taskId, downloadedBytes=${state.downloadedBytes}")
        }
    }

    /**
     * 恢复下载
     */
    fun resume(taskId: Long, url: String, savePath: String, fileName: String): Flow<HttpDownloadProgress> {
        val currentState = downloadJobs[taskId]
        val resumePosition = currentState?.downloadedBytes ?: 0
        Timber.d("HttpDownloader: 恢复下载 - taskId=$taskId, resumePosition=$resumePosition")
        return startDownload(taskId, url, savePath, fileName, resumePosition)
    }

    /**
     * 停止下载（并删除文件）
     */
    fun stop(taskId: Long, deleteFile: Boolean = false) {
        downloadJobs[taskId]?.let { state ->
            state.state = DownloadState.ERROR
            state.isPaused = false
            state.job?.cancel()
            disconnectAll(state)

            downloadJobs.remove(taskId)
            Timber.d("HttpDownloader: 停止下载 - taskId=$taskId")
        }
    }

    /** 断开该任务的所有活跃连接 */
    private fun disconnectAll(state: DownloadJobState) {
        val snapshot = synchronized(state.connections) { state.connections.toList() }
        snapshot.forEach { conn ->
            try { conn.disconnect() } catch (_: Exception) {}
        }
        try { state.inputStream?.close() } catch (_: Exception) {}
        try { state.outputStream?.close() } catch (_: Exception) {}
    }

    /**
     * 获取当前下载进度
     */
    fun getProgress(taskId: Long): HttpDownloadProgress? {
        return downloadJobs[taskId]?.let { state ->
            HttpDownloadProgress(
                taskId = taskId,
                progress = if (state.totalBytes > 0) state.downloadedBytes.toFloat() / state.totalBytes * 100 else 0f,
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                speed = state.speed,
                state = state.state
            )
        }
    }

    /**
     * 检查是否正在下载
     */
    fun isDownloading(taskId: Long): Boolean {
        return downloadJobs[taskId]?.let { it.state == DownloadState.DOWNLOADING } ?: false
    }

    /**
     * 关闭下载器
     */
    fun shutdown() {
        downloadJobs.keys().toList().forEach { taskId ->
            stop(taskId, deleteFile = false)
        }
        scope.cancel()
    }
}