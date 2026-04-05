package com.pureframe.player.download

import android.content.Context
import com.pureframe.player.data.repository.DownloadRepository
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
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
 * 管理下载任务的创建、暂停、恢复、删除等操作
 * 整合 TorrentEngine 和 DownloadRepository
 */
@Singleton
class TorrentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val torrentEngine: TorrentEngine,
    private val downloadRepository: DownloadRepository
) {
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 下载状态流（从 Repository 获取）
    private val _downloadStates = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadStates: StateFlow<List<DownloadTask>> = _downloadStates.asStateFlow()
    
    // 下载进度流（从 Engine 获取）
    val downloadProgress: SharedFlow<DownloadProgressInfo> = torrentEngine.downloadProgress

    // 边下边播状态流
    val streamableStatus: StateFlow<Map<String, StreamableInfo>> = torrentEngine.streamableStatus

    // Metadata 获取事件流（用于文件选择）
    val metadataReceived: SharedFlow<TorrentMetadataInfo> = torrentEngine.metadataReceived

    init {
        // 监听引擎进度更新，同步到 Repository
        observeProgressUpdates()
        // 监听 Torrent 添加事件，保存 infoHash 到数据库
        observeTorrentAdded()
    }

    /**
     * 监听下载进度更新
     */
    private fun observeProgressUpdates() {
        managerScope.launch {
            torrentEngine.downloadProgress.collect { progress ->
                updateTaskProgress(progress)
            }
        }
    }

    /**
     * 监听 Torrent 添加事件，保存 infoHash 到数据库
     */
    private fun observeTorrentAdded() {
        managerScope.launch {
            torrentEngine.torrentAdded.collect { info ->
                saveTorrentHash(info)
            }
        }
    }

    /**
     * 保存 torrentHash 到数据库
     */
    private suspend fun saveTorrentHash(info: TorrentAddedInfo) {
        try {
            val taskIdLong = info.taskId.toLongOrNull() ?: return
            val existingTask = downloadRepository.getTaskById(taskIdLong) ?: return

            // 更新 torrentHash
            val updatedTask = existingTask.copy(torrentHash = info.infoHash)
            downloadRepository.updateTask(updatedTask)

            Timber.d("TorrentManager: 保存 torrentHash 成功 - taskId=$taskIdLong, hash=${info.infoHash}")
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 保存 torrentHash 失败 - taskId=${info.taskId}")
        }
    }
    
    /**
     * 更新任务进度到 Repository
     */
    private suspend fun updateTaskProgress(progress: DownloadProgressInfo) {
        try {
            val status = mapTorrentStateToStatus(progress.state)
            val taskIdLong = progress.taskId.toLongOrNull()
            if (taskIdLong == null) {
                Timber.w("TorrentManager: taskId 解析失败 - ${progress.taskId}")
                return
            }

            // 获取现有任务
            val existingTask = downloadRepository.getTaskById(taskIdLong)
            if (existingTask == null) {
                Timber.w("TorrentManager: 任务不存在，跳过进度更新 - taskId=$taskIdLong")
                return
            }

            // 更新任务字段
            val updatedTask = existingTask.copy(
                progress = progress.progress,
                downloadedSize = progress.downloadedBytes,
                totalSize = progress.totalBytes,
                speed = progress.downloadSpeed,
                status = status,
                updatedAt = Date()
            )
            downloadRepository.updateTask(updatedTask)

            // 更新本地状态流
            refreshDownloadStates()
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 更新进度失败 - ${progress.taskId}")
        }
    }
    
    /**
     * 创建下载任务（磁力链接）
     */
    suspend fun createDownloadTask(
        magnetLink: String,
        savePath: String,
        name: String? = null
    ): Result<Long> {
        return try {
            // 解析磁力链接获取名称
            val torrentName = name ?: parseMagnetName(magnetLink) ?: "Torrent Download"

            // 创建下载任务实体
            val downloadTask = DownloadTask(
                url = magnetLink,
                title = torrentName,
                fileName = torrentName,
                savePath = savePath,
                totalSize = 0L,  // 未知，等待元数据下载
                downloadedSize = 0L,
                progress = 0f,
                speed = 0L,
                status = DownloadStatus.WAITING,
                createdAt = Date(),
                updatedAt = Date(),
                magnetLink = magnetLink
            )

            // 保存到数据库
            val taskId = downloadRepository.addTask(downloadTask)
            val taskIdStr = taskId.toString()
            Timber.i("TorrentManager: 任务已保存到数据库 - taskId=$taskId")

            // 添加到下载引擎
            val added = torrentEngine.addMagnetLink(magnetLink, savePath, taskIdStr)

            if (added) {
                Timber.i("TorrentManager: 添加到引擎成功 - $taskId")
                // 更新状态为下载中
                downloadRepository.updateTaskStatus(taskIdStr, DownloadStatus.DOWNLOADING)
                Result.success(taskId)
            } else {
                Timber.w("TorrentManager: 添加到引擎失败，但任务已保存 - taskId=$taskId")
                // 不删除任务，只标记状态为等待
                downloadRepository.updateTaskStatus(taskIdStr, DownloadStatus.WAITING)
                Result.success(taskId) // 仍然返回成功，因为任务已保存
            }

        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 创建下载任务失败")
            Result.failure(e)
        }
    }
    
    /**
     * 创建下载任务（Torrent 文件）
     */
    suspend fun createDownloadTaskFromFile(
        torrentFile: File,
        savePath: String,
        name: String? = null
    ): Result<Long> {
        return try {
            val torrentName = name ?: torrentFile.nameWithoutExtension
            
            // 创建下载任务实体
            val downloadTask = DownloadTask(
                url = torrentFile.absolutePath,
                title = torrentName,
                fileName = torrentName,
                savePath = savePath,
                totalSize = 0L,
                downloadedSize = 0L,
                progress = 0f,
                speed = 0L,
                status = DownloadStatus.WAITING,
                createdAt = Date(),
                updatedAt = Date(),
                magnetLink = null,
                torrentPath = torrentFile.absolutePath
            )

            // 保存到数据库
            val taskId = downloadRepository.addTask(downloadTask)
            val taskIdStr = taskId.toString()
            
            // 添加到下载引擎
            val added = torrentEngine.addTorrentFile(torrentFile, savePath, taskIdStr)
            
            if (added) {
                Timber.i("TorrentManager: 创建 Torrent 文件下载任务成功 - $taskId")
                downloadRepository.updateTaskStatus(taskIdStr, DownloadStatus.DOWNLOADING)
                refreshDownloadStates()
                Result.success(taskId)
            } else {
                downloadRepository.deleteTaskByLongId(taskId)
                Result.failure(Exception("添加到下载引擎失败"))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 创建 Torrent 文件下载任务失败")
            Result.failure(e)
        }
    }
    
    /**
     * 暂停下载
     */
    suspend fun pauseDownload(taskId: Long): Result<Unit> {
        return try {
            val taskIdStr = taskId.toString()
            torrentEngine.pause(taskIdStr)
            downloadRepository.updateTaskStatus(taskIdStr, DownloadStatus.PAUSED)
            refreshDownloadStates()
            Timber.d("TorrentManager: 暂停下载 - $taskId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 暂停下载失败 - $taskId")
            Result.failure(e)
        }
    }
    
    /**
     * 恢复下载
     */
    suspend fun resumeDownload(taskId: Long): Result<Unit> {
        return try {
            val taskIdStr = taskId.toString()
            torrentEngine.resume(taskIdStr)
            downloadRepository.updateTaskStatus(taskIdStr, DownloadStatus.DOWNLOADING)
            refreshDownloadStates()
            Timber.d("TorrentManager: 恢复下载 - $taskId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 恢复下载失败 - $taskId")
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
            val taskIdStr = taskId.toString()
            torrentEngine.remove(taskIdStr, deleteFiles)
            downloadRepository.deleteTaskByLongId(taskId)
            refreshDownloadStates()
            Timber.d("TorrentManager: 删除下载任务 - $taskId, deleteFiles=$deleteFiles")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 删除下载任务失败 - $taskId")
            Result.failure(e)
        }
    }
    
    /**
     * 检查是否可边下边播
     */
    fun isStreamable(taskId: Long, threshold: Float = TorrentEngine.DEFAULT_STREAMABLE_THRESHOLD): Boolean {
        return torrentEngine.isStreamable(taskId.toString(), threshold)
    }
    
    /**
     * 获取边下边播信息
     */
    fun getStreamableInfo(taskId: Long): StreamableInfo? {
        return torrentEngine.getStreamableInfo(taskId.toString())
    }

    /**
     * 获取 torrent 元数据信息（需要在 metadata 已加载后调用）
     */
    fun getMetadata(taskId: Long): TorrentMetadataInfo? {
        return torrentEngine.getTorrentMetadata(taskId.toString())
    }

    /**
     * 选择要下载的文件并开始下载
     *
     * @param taskId 任务 ID
     * @param fileIndices 要下载的文件索引集合
     */
    suspend fun selectFilesAndStart(taskId: Long, fileIndices: Set<Int>) {
        val taskIdStr = taskId.toString()
        torrentEngine.setDownloadFiles(taskIdStr, fileIndices)
        torrentEngine.startDownload(taskIdStr)

        // 更新任务状态为下载中
        downloadRepository.updateTaskStatus(taskIdStr, DownloadStatus.DOWNLOADING)
        refreshDownloadStates()

        Timber.d("TorrentManager: 已选择 ${fileIndices.size} 个文件并开始下载 - $taskId")
    }

    /**
     * 获取所有下载任务
     */
    suspend fun getAllDownloads(): List<DownloadTask> {
        return downloadRepository.getAllTasksOnce()
    }
    
    /**
     * 刷新下载状态列表
     */
    private suspend fun refreshDownloadStates() {
        val tasks = downloadRepository.getAllTasksOnce()
        _downloadStates.value = tasks
    }
    
    /**
     * 解析磁力链接中的名称
     */
    private fun parseMagnetName(magnetLink: String): String? {
        // magnet:?xt=urn:btih:xxx&dn=名称
        val dnPattern = "&dn=([^&]+)"
        val regex = Regex(dnPattern)
        val match = regex.find(magnetLink)
        return match?.groupValues?.getOrNull(1)?.let { 
            // URL 解码
            java.net.URLDecoder.decode(it, "UTF-8")
        }
    }
    
    /**
     * 映射 TorrentState 到 DownloadStatus
     */
    private fun mapTorrentStateToStatus(state: TorrentState): DownloadStatus {
        return when (state) {
            TorrentState.DOWNLOADING -> DownloadStatus.DOWNLOADING
            TorrentState.COMPLETED -> DownloadStatus.COMPLETED
            TorrentState.PAUSED -> DownloadStatus.PAUSED
            TorrentState.ERROR -> DownloadStatus.ERROR
            TorrentState.SEEDING -> DownloadStatus.COMPLETED
            TorrentState.WAITING -> DownloadStatus.WAITING
        }
    }
    
    /**
     * 关闭管理器
     */
    fun shutdown() {
        managerScope.cancel()
        torrentEngine.shutdown()
    }

    companion object {
        const val TAG = "TorrentManager"
    }
}