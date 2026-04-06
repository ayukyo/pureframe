package com.pureframe.player.download

import android.content.Context
import com.pureframe.player.data.repository.DownloadRepository
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.model.DownloadType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP 下载管理器
 *
 * 管理 HTTP 下载任务的创建、暂停、恢复、删除等操作
 * 提供与 TorrentManager 类似的接口，方便统一调用
 */
@Singleton
class HttpDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpDownloader: HttpDownloader,
    private val downloadRepository: DownloadRepository
) {
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 下载状态流（从 Repository 获取）
    private val _downloadStates = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadStates: StateFlow<List<DownloadTask>> = _downloadStates.asStateFlow()

    // HTTP 下载进度流
    private val _httpDownloadProgress = MutableSharedFlow<DownloadProgressInfo>()
    val httpDownloadProgress: SharedFlow<DownloadProgressInfo> = _httpDownloadProgress.asSharedFlow()

    // 活跃的 HTTP 下载任务
    private val activeHttpDownloads = ConcurrentHashMap<Long, Job>()

    // 暂停的任务及其断点位置
    private val pausedPositions = ConcurrentHashMap<Long, Long>()

    init {
        // 监听 HTTP 下载进度并同步到 Repository
        observeHttpProgress()
    }

    /**
     * 监听 HTTP 下载进度并更新到数据库
     */
    private fun observeHttpProgress() {
        managerScope.launch {
            // 这个观察通过 startDownload 返回的 flow 来处理
        }
    }

    /**
     * 创建 HTTP 下载任务
     *
     * @param url 下载地址
     * @param savePath 保存路径
     * @param name 任务名称（可选，从 URL 提取或使用默认名称）
     * @return 任务 ID
     */
    suspend fun createDownloadTask(
        url: String,
        savePath: String,
        name: String? = null
    ): Result<Long> {
        return try {
            // 解析文件名
            val fileName = name ?: extractFileName(url) ?: "HTTP Download"

            // 创建下载任务实体
            val downloadTask = DownloadTask(
                url = url,
                title = fileName,
                fileName = fileName,
                savePath = savePath,
                totalSize = 0L,
                downloadedSize = 0L,
                progress = 0f,
                speed = 0L,
                status = DownloadStatus.DOWNLOADING,
                createdAt = Date(),
                updatedAt = Date(),
                downloadType = DownloadType.HTTP
            )

            // 保存到数据库
            val taskId = downloadRepository.addTask(downloadTask)
            Timber.i("HttpDownloadManager: 任务已保存到数据库 - taskId=$taskId")

            // 开始下载
            startDownloadInternal(taskId, url, savePath, fileName, 0)

            Result.success(taskId)
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 创建下载任务失败")
            Result.failure(e)
        }
    }

    /**
     * 内部下载方法
     */
    private fun startDownloadInternal(
        taskId: Long,
        url: String,
        savePath: String,
        fileName: String,
        resumePosition: Long
    ) {
        val job = managerScope.launch {
            try {
                httpDownloader.startDownload(taskId, url, savePath, fileName, resumePosition)
                    .collect { progress ->
                        // 同步到数据库
                        updateTaskProgress(taskId, progress)

                        // 发送进度事件
                        val progressInfo = DownloadProgressInfo(
                            taskId = taskId.toString(),
                            progress = progress.progress,
                            downloadSpeed = progress.speed,
                            state = mapHttpStateToTorrentState(progress.state),
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes
                        )
                        _httpDownloadProgress.tryEmit(progressInfo)

                        // 检查是否完成
                        if (progress.state == HttpDownloader.DownloadState.COMPLETED) {
                            onDownloadCompleted(taskId, progress.totalBytes)
                        }
                    }
            } catch (e: CancellationException) {
                Timber.d("HttpDownloadManager: 下载任务取消 - taskId=$taskId")
            } catch (e: Exception) {
                Timber.e(e, "HttpDownloadManager: 下载异常 - taskId=$taskId")
                onDownloadError(taskId, e.message ?: "未知错误")
            } finally {
                activeHttpDownloads.remove(taskId)
            }
        }

        activeHttpDownloads[taskId] = job
    }

    /**
     * 更新任务进度到 Repository
     */
    private suspend fun updateTaskProgress(taskId: Long, progress: HttpDownloader.HttpDownloadProgress) {
        try {
            val status = when (progress.state) {
                HttpDownloader.DownloadState.DOWNLOADING -> DownloadStatus.DOWNLOADING
                HttpDownloader.DownloadState.PAUSED -> DownloadStatus.PAUSED
                HttpDownloader.DownloadState.COMPLETED -> DownloadStatus.COMPLETED
                HttpDownloader.DownloadState.ERROR -> DownloadStatus.FAILED
                HttpDownloader.DownloadState.IDLE -> DownloadStatus.PENDING
            }

            val existingTask = downloadRepository.getTaskById(taskId) ?: return

            val updatedTask = existingTask.copy(
                progress = progress.progress,
                downloadedSize = progress.downloadedBytes,
                totalSize = progress.totalBytes,
                speed = progress.speed,
                status = status,
                updatedAt = Date()
            )
            downloadRepository.updateTask(updatedTask)

            // 更新本地状态流
            refreshDownloadStates()
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 更新进度失败 - taskId=$taskId")
        }
    }

    /**
     * 下载完成处理
     */
    private suspend fun onDownloadCompleted(taskId: Long, totalBytes: Long) {
        try {
            downloadRepository.markCompleted(taskId, Date(), totalBytes)
            refreshDownloadStates()
            Timber.i("HttpDownloadManager: HTTP 下载完成 - taskId=$taskId")
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 完成处理失败 - taskId=$taskId")
        }
    }

    /**
     * 下载错误处理
     */
    private suspend fun onDownloadError(taskId: Long, errorMessage: String) {
        try {
            downloadRepository.markError(taskId, errorMessage)
            refreshDownloadStates()
            Timber.e("HttpDownloadManager: HTTP 下载失败 - taskId=$taskId, error=$errorMessage")
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 错误处理失败 - taskId=$taskId")
        }
    }

    /**
     * 暂停下载
     */
    fun pauseDownload(taskId: Long): Result<Unit> {
        return try {
            // 保存当前下载位置
            val progress = httpDownloader.getProgress(taskId)
            if (progress != null) {
                pausedPositions[taskId] = progress.downloadedBytes
            }

            httpDownloader.pause(taskId)

            managerScope.launch {
                val task = downloadRepository.getTaskById(taskId)
                if (task != null) {
                    downloadRepository.updateTask(
                        task.copy(
                            status = DownloadStatus.PAUSED,
                            updatedAt = Date()
                        )
                    )
                    refreshDownloadStates()
                }
            }

            Timber.d("HttpDownloadManager: 暂停下载 - taskId=$taskId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 暂停失败 - taskId=$taskId")
            Result.failure(e)
        }
    }

    /**
     * 恢复下载
     */
    suspend fun resumeDownload(taskId: Long): Result<Unit> {
        return try {
            val task = downloadRepository.getTaskById(taskId) ?: return Result.failure(
                Exception("任务不存在")
            )

            val resumePosition = pausedPositions.remove(taskId) ?: 0

            // 更新状态为下载中
            downloadRepository.updateTask(
                task.copy(
                    status = DownloadStatus.DOWNLOADING,
                    updatedAt = Date()
                )
            )
            refreshDownloadStates()

            // 开始下载（续传）
            startDownloadInternal(taskId, task.url, task.savePath, task.fileName, resumePosition)

            Timber.d("HttpDownloadManager: 恢复下载 - taskId=$taskId, resumePosition=$resumePosition")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 恢复失败 - taskId=$taskId")
            Result.failure(e)
        }
    }

    /**
     * 删除下载任务
     *
     * @param taskId 任务 ID
     * @param deleteFiles 是否删除已下载文件
     */
    suspend fun deleteDownload(taskId: Long, deleteFiles: Boolean = false): Result<Unit> {
        return try {
            // 停止下载
            httpDownloader.stop(taskId, deleteFiles)
            pausedPositions.remove(taskId)
            activeHttpDownloads.remove(taskId)

            // 删除数据库记录
            downloadRepository.deleteTaskByLongId(taskId)

            // 如果需要删除文件
            if (deleteFiles) {
                val task = downloadRepository.getTaskById(taskId)
                if (task != null) {
                    val file = File(task.savePath, task.fileName)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }

            refreshDownloadStates()
            Timber.d("HttpDownloadManager: 删除下载任务 - taskId=$taskId, deleteFiles=$deleteFiles")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 删除失败 - taskId=$taskId")
            Result.failure(e)
        }
    }

    /**
     * 刷新下载状态列表
     */
    private suspend fun refreshDownloadStates() {
        val tasks = downloadRepository.getAllTasksOnce()
        _downloadStates.value = tasks.filter { it.downloadType == DownloadType.HTTP }
    }

    /**
     * 从 URL 提取文件名
     */
    private fun extractFileName(url: String): String? {
        return try {
            val path = URL(url).path
            val fileName = path.substringAfterLast("/", "")
            if (fileName.isNotEmpty() && (fileName.contains(".") || fileName.contains("?"))) {
                // 处理 query string 中的文件名
                fileName.substringBefore("?").substringBefore(".torrent")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 映射 HTTP 下载状态到 TorrentState
     */
    private fun mapHttpStateToTorrentState(state: HttpDownloader.DownloadState): TorrentState {
        return when (state) {
            HttpDownloader.DownloadState.DOWNLOADING -> TorrentState.DOWNLOADING
            HttpDownloader.DownloadState.PAUSED -> TorrentState.PAUSED
            HttpDownloader.DownloadState.COMPLETED -> TorrentState.COMPLETED
            HttpDownloader.DownloadState.ERROR -> TorrentState.ERROR
            HttpDownloader.DownloadState.IDLE -> TorrentState.WAITING
        }
    }

    /**
     * 关闭管理器
     */
    fun shutdown() {
        activeHttpDownloads.keys().toList().forEach { taskId ->
            httpDownloader.stop(taskId, deleteFile = false)
        }
        activeHttpDownloads.clear()
        pausedPositions.clear()
        managerScope.cancel()
    }

    companion object {
        const val TAG = "HttpDownloadManager"
    }
}