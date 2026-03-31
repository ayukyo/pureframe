package com.pureframe.player.download

import android.content.Context
import com.pureframe.player.data.repository.DownloadRepository
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import org.libtorrent4j.TorrentHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Torrent 下载管理器
 *
 * 整合 TorrentEngine 和 DownloadRepository，提供完整的下载管理功能
 */
@Singleton
class TorrentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val torrentEngine: TorrentEngine,
    private val downloadRepository: DownloadRepository
) {
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 活跃的下载任务映射 (infoHash -> taskId)
    private val activeHandles = mutableMapOf<String, TorrentHandle>()
    private val taskHandles = mutableMapOf<Long, TorrentHandle>()

    // 边下边播的任务
    private val streamableTorrents = mutableMapOf<Long, StreamableTorrent>()

    // 下载状态流
    private val _downloadStates = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadStates: StateFlow<List<DownloadTask>> = _downloadStates.asStateFlow()

    init {
        // 监听 TorrentEngine 的进度更新
        managerScope.launch {
            torrentEngine.downloadProgress.collect { progressInfo ->
                updateDownloadProgress(progressInfo)
            }
        }

        // 定期同步数据库状态
        managerScope.launch {
            while (true) {
                syncDatabaseState()
                kotlinx.coroutines.delay(5000) // 每 5 秒同步
            }
        }
    }

    /**
     * 创建下载任务
     *
     * @param magnetLink 磁力链接
     * @param savePath 保存路径
     * @param name 任务名称（可选）
     * @return 任务 ID
     */
    suspend fun createDownloadTask(
        magnetLink: String,
        savePath: String,
        name: String? = null
    ): Result<Long> {
        return try {
            // 创建 TorrentHandle
            val handle = torrentEngine.addMagnetLink(magnetLink, savePath)
            if (handle == null) {
                return Result.failure(Exception("Failed to create torrent handle"))
            }

            // 获取 torrent 信息
            val infoHash = handle.infoHash().toString()
            val totalSize = torrentEngine.getTotalSize(handle)
            val torrentName = name ?: handle.name() ?: "Unknown"

            // 创建 DownloadTask
            val downloadTask = DownloadTask(
                url = magnetLink,
                title = torrentName,
                fileName = torrentName,
                savePath = savePath,
                totalSize = totalSize,
                downloadedSize = 0L,
                progress = 0f,
                speed = 0L,
                status = DownloadStatus.DOWNLOADING,
                createdAt = Date(),
                updatedAt = Date(),
                magnetLink = magnetLink,
                torrentHash = infoHash,
                isStreamable = false,
                streamableProgress = StreamableTorrent.DEFAULT_THRESHOLD
            )

            // 保存到数据库
            val taskId = downloadRepository.addTask(downloadTask)

            // 记录活跃任务
            taskHandles[taskId] = handle
            activeHandles[infoHash] = handle

            // 启动进度追踪
            torrentEngine.startProgressTracking(handle, taskId.toString())

            Timber.d("Created download task: $taskId, name: $torrentName, infoHash: $infoHash")

            Result.success(taskId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create download task")
            Result.failure(e)
        }
    }

    /**
     * 从 torrent 文件创建下载任务
     */
    suspend fun createDownloadTaskFromFile(
        torrentFile: File,
        savePath: String,
        name: String? = null
    ): Result<Long> {
        return try {
            val handle = torrentEngine.addTorrentFile(torrentFile, savePath)
            if (handle == null) {
                return Result.failure(Exception("Failed to create torrent handle"))
            }

            val infoHash = handle.infoHash().toString()
            val totalSize = torrentEngine.getTotalSize(handle)
            val torrentName = name ?: handle.name() ?: torrentFile.nameWithoutExtension

            val downloadTask = DownloadTask(
                url = torrentFile.absolutePath,
                title = torrentName,
                fileName = torrentName,
                savePath = savePath,
                totalSize = totalSize,
                downloadedSize = 0L,
                progress = 0f,
                speed = 0L,
                status = DownloadStatus.DOWNLOADING,
                createdAt = Date(),
                updatedAt = Date(),
                torrentPath = torrentFile.absolutePath,
                torrentHash = infoHash,
                isStreamable = false,
                streamableProgress = StreamableTorrent.DEFAULT_THRESHOLD
            )

            val taskId = downloadRepository.addTask(downloadTask)

            taskHandles[taskId] = handle
            activeHandles[infoHash] = handle
            torrentEngine.startProgressTracking(handle, taskId.toString())

            Result.success(taskId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create download task from file")
            Result.failure(e)
        }
    }

    /**
     * 更新下载进度
     */
    private suspend fun updateDownloadProgress(progressInfo: DownloadProgressInfo) {
        try {
            // 尝试解析 taskId
            val taskId = progressInfo.taskId.toLongOrNull()
            if (taskId == null) return

            val task = downloadRepository.getTaskById(taskId)
            if (task == null) return

            // 计算是否可边下边播
            val handle = taskHandles[taskId]
            val isStreamable = handle != null && torrentEngine.isStreamable(handle)

            val updatedTask = task.copy(
                progress = progressInfo.progress,
                downloadedSize = progressInfo.downloadedBytes,
                speed = progressInfo.downloadSpeed,
                status = mapTorrentStateToStatus(progressInfo.state),
                updatedAt = Date(),
                isStreamable = isStreamable
            )

            downloadRepository.updateTask(updatedTask)

            // 更新状态流
            refreshDownloadStates()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update download progress")
        }
    }

    /**
     * 映射 TorrentState 到 DownloadStatus
     */
    private fun mapTorrentStateToStatus(state: TorrentState): DownloadStatus {
        return when (state) {
            TorrentState.PAUSED -> DownloadStatus.PAUSED
            TorrentState.DOWNLOADING -> DownloadStatus.DOWNLOADING
            TorrentState.COMPLETED -> DownloadStatus.COMPLETED
            TorrentState.SEEDING -> DownloadStatus.COMPLETED
            TorrentState.ERROR -> DownloadStatus.ERROR
            TorrentState.WAITING -> DownloadStatus.WAITING
        }
    }

    /**
     * 暂停下载
     */
    suspend fun pauseDownload(taskId: Long): Result<Unit> {
        return try {
            val handle = taskHandles[taskId]
            if (handle == null) {
                // 标记为暂停状态
                downloadRepository.updateTaskStatus(taskId.toString(), DownloadStatus.PAUSED)
                return Result.success(Unit)
            }

            torrentEngine.pause(handle)
            downloadRepository.updateTaskStatus(taskId.toString(), DownloadStatus.PAUSED)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to pause download: $taskId")
            Result.failure(e)
        }
    }

    /**
     * 恢复下载
     */
    suspend fun resumeDownload(taskId: Long): Result<Unit> {
        return try {
            val handle = taskHandles[taskId]
            if (handle == null) {
                return Result.failure(Exception("Download not active"))
            }

            torrentEngine.resume(handle)
            downloadRepository.updateTaskStatus(taskId.toString(), DownloadStatus.DOWNLOADING)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to resume download: $taskId")
            Result.failure(e)
        }
    }

    /**
     * 删除下载任务
     *
     * @param taskId 任务 ID
     * @param deleteFiles 是否删除已下载的文件
     */
    suspend fun deleteDownload(taskId: Long, deleteFiles: Boolean = false): Result<Unit> {
        return try {
            val handle = taskHandles.remove(taskId)
            val task = downloadRepository.getTaskById(taskId)

            if (handle != null) {
                val infoHash = handle.infoHash().toString()
                torrentEngine.remove(infoHash, deleteFiles)
                activeHandles.remove(infoHash)
            }

            // 删除数据库记录
            downloadRepository.deleteTaskByLongId(taskId)

            // 删除边下边播记录
            streamableTorrents.remove(taskId)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete download: $taskId")
            Result.failure(e)
        }
    }

    /**
     * 获取所有下载任务
     */
    suspend fun getAllDownloads(): List<DownloadTask> {
        return downloadRepository.getAllTasksOnce()
    }

    /**
     * 获取活跃的下载任务
     */
    suspend fun getActiveDownloads(): List<DownloadTask> {
        return downloadRepository.getTasksByStatusOnce(DownloadStatus.DOWNLOADING)
    }

    /**
     * 获取已完成的下载任务
     */
    suspend fun getCompletedDownloads(): List<DownloadTask> {
        return downloadRepository.getTasksByStatusOnce(DownloadStatus.COMPLETED)
    }

    /**
     * 获取边下边播对象
     */
    fun getStreamableTorrent(taskId: Long): StreamableTorrent? {
        // 先检查缓存
        if (streamableTorrents.containsKey(taskId)) {
            return streamableTorrents[taskId]
        }

        // 检查是否有活跃的 handle
        val handle = taskHandles[taskId]
        if (handle != null && torrentEngine.isStreamable(handle)) {
            val streamable = StreamableTorrent(handle)
            streamableTorrents[taskId] = streamable
            return streamable
        }

        return null
    }

    /**
     * 开始边下边播
     *
     * @param taskId 任务 ID
     * @return StreamableTorrent 或 null（不可播放时）
     */
    suspend fun startStreamPlayback(taskId: Long): Result<StreamableTorrent> {
        return try {
            val handle = taskHandles[taskId]
            if (handle == null) {
                return Result.failure(Exception("Download not found"))
            }

            // 检查是否可边下边播
            if (!torrentEngine.isStreamable(handle)) {
                return Result.failure(Exception("Not enough data cached for streaming"))
            }

            // 创建 StreamableTorrent
            val streamable = StreamableTorrent(handle)

            // 设置顺序下载模式
            streamable.prioritizeStart()
            streamable.enableSequentialDownload()

            // 缓存
            streamableTorrents[taskId] = streamable

            Timber.d("Started stream playback for task: $taskId")

            Result.success(streamable)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start stream playback")
            Result.failure(e)
        }
    }

    /**
     * 检查是否可边下边播
     */
    fun canStream(taskId: Long): Boolean {
        val handle = taskHandles[taskId]
        return handle != null && torrentEngine.isStreamable(handle)
    }

    /**
     * 获取下载进度
     */
    fun getDownloadProgress(taskId: Long): Float {
        val handle = taskHandles[taskId]
        return if (handle != null) {
            torrentEngine.getProgress(handle)
        } else {
            0f
        }
    }

    /**
     * 同步数据库状态
     */
    private suspend fun syncDatabaseState() {
        try {
            val tasks = downloadRepository.getAllTasksOnce()
            _downloadStates.value = tasks
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync database state")
        }
    }

    /**
     * 刷新下载状态
     */
    private suspend fun refreshDownloadStates() {
        val tasks = downloadRepository.getAllTasksOnce()
        _downloadStates.value = tasks
    }

    /**
     * 获取下载文件路径
     */
    fun getDownloadFilePath(taskId: Long): String? {
        val handle = taskHandles[taskId]
        if (handle == null) return null

        val largestFileIndex = torrentEngine.getLargestFileIndex(handle)
        if (largestFileIndex < 0) return null

        return torrentEngine.getFilePath(handle, largestFileIndex)
    }

    /**
     * 清除已完成的下载记录
     */
    suspend fun clearCompletedDownloads(): Result<Unit> {
        return try {
            val completed = downloadRepository.getTasksByStatusOnce(DownloadStatus.COMPLETED)
            completed.forEach { task ->
                downloadRepository.deleteTaskByLongId(task.id)
                streamableTorrents.remove(task.id)
            }

            refreshDownloadStates()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear completed downloads")
            Result.failure(e)
        }
    }

    /**
     * 关闭管理器
     */
    fun shutdown() {
        managerScope.cancel()
        torrentEngine.shutdown()
        taskHandles.clear()
        activeHandles.clear()
        streamableTorrents.clear()
    }

    companion object {
        const val TAG = "TorrentManager"
    }
}