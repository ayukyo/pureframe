package com.pureframe.player.download

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP 下载器
 *
 * 使用 HttpURLConnection 实现 HTTP 下载，支持：
 * - 断点续传
 * - 进度实时反馈
 * - 暂停/恢复
 * - 多线程分块下载（可选）
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
        var connection: HttpURLConnection? = null,
        var outputStream: FileOutputStream? = null,
        var inputStream: InputStream? = null
    )

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
            var contentLength = 0L
            var supportsRange = false

            // 第一步：获取文件信息（HEAD 请求）
            withContext(Dispatchers.IO) {
                val headConn = URL(url).openConnection() as HttpURLConnection
                headConn.requestMethod = "HEAD"
                headConn.connectTimeout = 15000
                headConn.readTimeout = 15000
                headConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

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

            // 第二步：建立下载连接
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            // 支持断点续传
            if (supportsRange && resumePosition > 0) {
                conn.setRequestProperty("Range", "bytes=$resumePosition-")
                Timber.d("HttpDownloader: 使用断点续传 from=$resumePosition")
            }

            jobState.connection = conn
            conn.connect()

            val responseCode = conn.responseCode
            Timber.d("HttpDownloader: 响应码=$responseCode")

            // 处理 206 Partial Content（断点续传响应）
            var actualStartPosition = resumePosition
            if (responseCode == 206) {
                val contentRange = conn.getHeaderField("Content-Range")
                Timber.d("HttpDownloader: Content-Range=$contentRange")
                // Content-Range: bytes <start>-<end>/<total>
                val rangeMatch = Regex("bytes (\\d+)-(\\d+)/(\\d+)").find(contentRange ?: "")
                if (rangeMatch != null) {
                    actualStartPosition = rangeMatch.groupValues[1].toLong()
                }
            }

            // 如果服务器不支持断点续传，且是恢复下载，则从头开始
            if (responseCode != 206 && resumePosition > 0 && !supportsRange) {
                Timber.w("HttpDownloader: 服务器不支持断点续传，从头开始下载")
                actualStartPosition = 0
                jobState.downloadedBytes = 0
            }

            // 获取总大小
            if (contentLength <= 0) {
                contentLength = conn.contentLengthLong
                if (contentLength <= 0) {
                    // 如果无法获取大小，设为 0
                    contentLength = 0
                }
            }
            jobState.totalBytes = contentLength

            // 打开输入输出流
            val inputStream = conn.inputStream
            jobState.inputStream = inputStream

            // 使用 RandomAccessFile 进行断点续传
            val randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.seek(actualStartPosition)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastUpdateTime = System.currentTimeMillis()
            var bytesInLastSecond = 0L
            var totalBytesRead = actualStartPosition

            Timber.d("HttpDownloader: 开始下载数据到 $file")

            // 下载主循环
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // 检查是否暂停
                while (jobState.isPaused) {
                    delay(100)
                    if (jobState.state == DownloadState.ERROR) {
                        throw InterruptedException("下载已停止")
                    }
                }

                randomAccessFile.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                jobState.downloadedBytes = totalBytesRead
                bytesInLastSecond += bytesRead

                // 计算速度（每秒）
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - lastUpdateTime
                if (elapsed >= 1000) {
                    jobState.speed = bytesInLastSecond * 1000 / elapsed
                    bytesInLastSecond = 0
                    lastUpdateTime = currentTime
                }

                // 计算进度
                val progress = if (contentLength > 0) {
                    (totalBytesRead.toFloat() / contentLength * 100).coerceIn(0f, 100f)
                } else {
                    0f
                }

                emit(
                    HttpDownloadProgress(
                        taskId = taskId,
                        progress = progress,
                        downloadedBytes = totalBytesRead,
                        totalBytes = contentLength,
                        speed = jobState.speed,
                        state = jobState.state
                    )
                )
            }

            // 下载完成
            randomAccessFile.close()
            inputStream.close()
            conn.disconnect()

            jobState.state = DownloadState.COMPLETED
            emit(
                HttpDownloadProgress(
                    taskId = taskId,
                    progress = 100f,
                    downloadedBytes = totalBytesRead,
                    totalBytes = contentLength,
                    speed = 0,
                    state = DownloadState.COMPLETED
                )
            )

            Timber.d("HttpDownloader: 下载完成 - taskId=$taskId, totalBytes=$totalBytesRead")

        } catch (e: InterruptedException) {
            jobState.state = DownloadState.PAUSED
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
        } catch (e: Exception) {
            jobState.state = DownloadState.ERROR
            Timber.e(e, "HttpDownloader: 下载错误 - taskId=$taskId")
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
        } finally {
            downloadJobs.remove(taskId)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 暂停下载
     */
    fun pause(taskId: Long) {
        downloadJobs[taskId]?.let { state ->
            state.isPaused = true
            state.state = DownloadState.PAUSED
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

            try {
                state.inputStream?.close()
                state.outputStream?.close()
                state.connection?.disconnect()
            } catch (e: Exception) {
                Timber.e(e, "HttpDownloader: 关闭连接失败")
            }

            downloadJobs.remove(taskId)
            Timber.d("HttpDownloader: 停止下载 - taskId=$taskId")
        }
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