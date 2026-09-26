package com.pureframe.player.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.data.repository.DownloadRepository
import com.pureframe.player.domain.model.DownloadError
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
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import com.pureframe.player.R
import com.pureframe.player.i18n.LocaleManager

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
    private val downloadRepository: DownloadRepository,
    private val userPreferencesRepository: UserPreferencesRepository
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

    // 等待并发额度的任务参数（FIFO）
    private val pendingQueue = ConcurrentLinkedQueue<PendingRequest>()

    private data class PendingRequest(
        val taskId: Long,
        val url: String,
        val savePath: String,
        val fileName: String,
        val resumePosition: Long
    )

    /** 用户设置的最大并行下载数（默认 3，范围 1~5） */
    private suspend fun maxConcurrent(): Int =
        runCatching {
            userPreferencesRepository.userPreferencesFlow.first().maxConcurrentDownloads
        }.getOrDefault(3).coerceIn(1, 5)

    /** 是否"仅 Wi-Fi 下载"（用户设置，默认开启） */
    private suspend fun onlyWifi(): Boolean =
        runCatching {
            userPreferencesRepository.userPreferencesFlow.first().autoDownloadOnWifi
        }.getOrDefault(true)

    /** 当前网络是否为 Wi-Fi（含以太网） */
    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    init {
        // 监听 HTTP 下载进度并同步到 Repository
        observeHttpProgress()
        // 网络恢复后自动拉起等待中的任务
        registerNetworkCallback()
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
     *
     * 受用户设置的「最大并行下载数」约束：额度已满时进入 waiting 队列，
     * 待有任务结束后自动出队启动（状态保持 PENDING，UI 显示"等待中"）。
     */
    private fun startDownloadInternal(
        taskId: Long,
        url: String,
        savePath: String,
        fileName: String,
        resumePosition: Long
    ) {
        managerScope.launch {
            // 仅 Wi-Fi 下载：当前是移动网络则排队，待网络恢复后自动启动
            if (onlyWifi() && !isOnWifi()) {
                Timber.i("HttpDownloadManager: 非 Wi-Fi 网络，任务等待中 - taskId=$taskId")
                pendingQueue.add(PendingRequest(taskId, url, savePath, fileName, resumePosition))
                downloadRepository.getTaskById(taskId)?.let { task ->
                    downloadRepository.updateTask(
                        task.copy(status = DownloadStatus.PENDING, updatedAt = Date())
                    )
                    refreshDownloadStates()
                }
                return@launch
            }

            if (activeHttpDownloads.size >= maxConcurrent()) {
                Timber.i("HttpDownloadManager: 并发已满(${activeHttpDownloads.size})，任务入队 - taskId=$taskId")
                pendingQueue.add(PendingRequest(taskId, url, savePath, fileName, resumePosition))
                downloadRepository.getTaskById(taskId)?.let { task ->
                    downloadRepository.updateTask(
                        task.copy(status = DownloadStatus.PENDING, updatedAt = Date())
                    )
                    refreshDownloadStates()
                }
                return@launch
            }
            launchDownloadJob(taskId, url, savePath, fileName, resumePosition)
        }
    }

    /** 实际启动一个下载协程 */
    private fun launchDownloadJob(
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
                // 落库语言无关的错误码，文案在 UI 层按当前语言解析（见 DownloadError）
                onDownloadError(taskId, DownloadError.fromException(e))
            } finally {
                activeHttpDownloads.remove(taskId)
                // 任务结束，尝试启动队列中的下一个任务
                drainPendingQueue()
            }
        }

        activeHttpDownloads[taskId] = job
    }

    /** 从等待队列取出下一个任务启动（并发额度与网络条件允许时） */
    private fun drainPendingQueue() {
        managerScope.launch {
            if (onlyWifi() && !isOnWifi()) return@launch
            while (activeHttpDownloads.size < maxConcurrent()) {
                val next = pendingQueue.poll() ?: break
                // 任务可能已被用户删除/暂停，跳过无效项
                val task = downloadRepository.getTaskById(next.taskId)
                if (task == null) continue
                if (task.status == DownloadStatus.PAUSED) continue
                launchDownloadJob(
                    next.taskId, next.url, next.savePath, next.fileName, next.resumePosition
                )
            }
        }
    }

    /**
     * 注册网络变化监听：等待中的任务在 Wi-Fi 恢复后自动启动
     */
    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (isOnWifi() && pendingQueue.isNotEmpty()) {
                        Timber.i("HttpDownloadManager: 网络恢复，拉起等待队列")
                        drainPendingQueue()
                    }
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "HttpDownloadManager: 注册网络回调失败")
        }
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
                // 失败时记录用户可读的原因；恢复下载/重新开始时清空
                errorCode = if (progress.state == HttpDownloader.DownloadState.ERROR) progress.errorMessage else null,
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
            // 排队中的任务直接出队，避免恢复后再被自动拉起
            pendingQueue.removeIf { it.taskId == taskId }

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
                Exception(LocaleManager.getString(context, R.string.error_task_not_found))
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
            // 先取消活跃协程，再断开连接，避免已删除任务仍在后台写入
            activeHttpDownloads.remove(taskId)?.cancel()
            pendingQueue.removeIf { it.taskId == taskId }
            httpDownloader.stop(taskId, deleteFiles)
            pausedPositions.remove(taskId)

            // 需要时删除数据文件（含分片进度残留）
            if (deleteFiles) {
                val task = downloadRepository.getTaskById(taskId)
                if (task != null) {
                    val file = File(task.savePath, task.fileName)
                    if (file.exists()) file.delete()
                    File(task.savePath, "${task.fileName}.parts").delete()
                }
            }

            // 删除数据库记录
            downloadRepository.deleteTaskByLongId(taskId)

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
        pendingQueue.clear()
        managerScope.cancel()
    }

    companion object {
        const val TAG = "HttpDownloadManager"
    }
}