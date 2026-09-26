package com.pureframe.player.download

import android.content.Context
import android.media.MediaScannerConnection
import com.pureframe.player.data.repository.DownloadRepository
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.model.DownloadType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.pureframe.player.R
import com.pureframe.player.i18n.LocaleManager

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
        // 恢复未完成的下载任务
        restorePendingDownloads()
    }

    /**
     * 恢复 APP 重启前的未完成下载任务
     */
    private fun restorePendingDownloads() {
        managerScope.launch {
            try {
                val pendingTasks = downloadRepository.getTasksByStatusOnce(DownloadStatus.DOWNLOADING)
                    .filter { it.downloadType == DownloadType.BT && it.magnetLink != null }

                val pausedTasks = downloadRepository.getTasksByStatusOnce(DownloadStatus.PAUSED)
                    .filter { it.downloadType == DownloadType.BT && it.magnetLink != null }

                val allTasks = pendingTasks + pausedTasks
                Timber.d("TorrentManager: 找到 ${allTasks.size} 个未完成的 BT 任务需要恢复")

                for (task in allTasks) {
                    try {
                        // 重新添加到引擎恢复下载
                        val added = torrentEngine.addMagnetLink(task.magnetLink!!, task.savePath, task.id.toString())
                        if (added) {
                            Timber.d("TorrentManager: 任务已恢复 - taskId=${task.id}")
                        } else {
                            Timber.w("TorrentManager: 任务恢复失败 - taskId=${task.id}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "TorrentManager: 恢复任务异常 - taskId=${task.id}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "TorrentManager: 恢复下载任务失败")
            }
        }
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
     * 种子完成后触发媒体扫描。
     * 多文件种子产出整个目录，需要递归找出其中的视频文件逐个扫描。
     */
    private fun scanTorrentOutput(task: DownloadTask) {
        runCatching {
            val base = File(task.savePath)
            if (!base.exists()) return
            val files = if (base.isDirectory) {
                base.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in LocalVideoScanner.SUPPORTED_FORMATS }
                    .toList()
            } else {
                listOf(base)
            }
            if (files.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    files.map { it.absolutePath }.toTypedArray(),
                    arrayOf("video/*"),
                    null
                )
                Timber.i("TorrentManager: 已触发媒体扫描 - ${files.size} 个文件 @${task.savePath}")
            }
        }.onFailure { Timber.w(it, "TorrentManager: 媒体扫描失败 - taskId=${task.id}") }
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
            //
            // 总大小以"已校准值"为准：用户只勾选部分文件时，我们在
            // selectFilesAndStart 里把 totalBytes 改成了已选文件之和；
            // 但引擎每次上报的 progress.totalBytes 仍是整种子大小，
            // 若直接覆盖会让进度条永远到不了 100%。所以当已有值更小且为正时保留它。
            val calibratedTotal = when {
                existingTask.totalSize > 0 &&
                    progress.totalBytes > existingTask.totalSize -> existingTask.totalSize
                else -> progress.totalBytes
            }

            val updatedTask = existingTask.copy(
                progress = progress.progress,
                // 引擎的 totalDone 可能包含未勾选文件，钳一下避免出现 >100% 的进度
                downloadedSize = if (calibratedTotal > 0) {
                    progress.downloadedBytes.coerceAtMost(calibratedTotal)
                } else {
                    progress.downloadedBytes
                },
                totalSize = calibratedTotal,
                speed = progress.downloadSpeed,
                status = status,
                updatedAt = Date()
            )
            downloadRepository.updateTask(updatedTask)

            // 完成时把产出文件交给 MediaStore：「本地」页与文件管理器才能立刻看到
            if (status == DownloadStatus.COMPLETED) {
                scanTorrentOutput(existingTask)
            }

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
     * 准备磁力链接下载（创建数据库条目但不显示，添加到引擎获取 metadata）
     * 调用后会等待 metadata 获取完成，然后弹出文件选择对话框
     * 用户确认后才正式显示在列表中并开始下载
     *
     * @param magnetLink 磁力链接
     * @param savePath 保存路径
     * @param name 任务名称（可选）
     * @return 任务 ID，null 表示添加失败
     */
    suspend fun prepareMagnetLink(
        magnetLink: String,
        savePath: String,
        name: String? = null
    ): Result<Long> {
        return try {
            val torrentName = name ?: parseMagnetName(magnetLink) ?: "Torrent Download"

            // 创建下载任务实体，状态为 PENDING_SELECTION
            val downloadTask = DownloadTask(
                url = magnetLink,
                title = torrentName,
                fileName = torrentName,
                savePath = savePath,
                totalSize = 0L,
                downloadedSize = 0L,
                progress = 0f,
                speed = 0L,
                status = DownloadStatus.PENDING_SELECTION,
                createdAt = Date(),
                updatedAt = Date(),
                magnetLink = magnetLink
            )

            // 保存到数据库
            val taskId = downloadRepository.addTask(downloadTask)
            val taskIdStr = taskId.toString()
            Timber.i("TorrentManager: 准备磁力链接 - taskId=$taskId")

            // 添加到下载引擎（用于获取 metadata）
            val added = torrentEngine.addMagnetLink(magnetLink, savePath, taskIdStr)

            if (!added) {
                Timber.e("TorrentManager: 添加到引擎失败 - taskId=$taskId")
                downloadRepository.deleteTaskByLongId(taskId)
                return Result.failure(Exception(LocaleManager.getString(context, R.string.error_add_to_engine_failed)))
            }

            Timber.i("TorrentManager: 磁力链接已添加到引擎，等待 metadata - taskId=$taskId")
            Result.success(taskId)
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 准备磁力链接失败")
            Result.failure(e)
        }
    }

    /**
     * 确认文件选择并开始下载
     * 在用户确认文件选择后调用
     *
     * @param taskId 任务 ID
     * @param selectedFileIndices 选中的文件索引
     */
    suspend fun confirmFileSelectionAndStart(
        taskId: Long,
        selectedFileIndices: Set<Int>,
        selectedTotalBytes: Long = 0L
    ) {
        selectFilesAndStart(taskId, selectedFileIndices, selectedTotalBytes)
    }

    /**
     * 取消文件选择并删除任务
     *
     * @param taskId 任务 ID
     */
    suspend fun cancelFileSelection(taskId: Long) {
        val taskIdStr = taskId.toString()
        try {
            // 从引擎移除
            torrentEngine.remove(taskIdStr, false)
            // 从数据库删除
            downloadRepository.deleteTaskByLongId(taskId)
            refreshDownloadStates()
            Timber.i("TorrentManager: 取消文件选择并删除任务 - taskId=$taskId")
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 取消文件选择失败 - taskId=$taskId")
        }
    }

    /**
     * 取消文件选择并删除任务（通过字符串 ID）
     * 用于取消临时 ID 或字符串 ID 的任务
     *
     * @param taskIdStr 任务 ID（字符串格式）
     */
    suspend fun cancelFileSelectionByStringId(taskIdStr: String) {
        try {
            // 从引擎移除
            torrentEngine.remove(taskIdStr, false)
            // 尝试解析为数字 ID 并删除
            val taskId = taskIdStr.toLongOrNull()
            if (taskId != null) {
                downloadRepository.deleteTaskByLongId(taskId)
            } else {
                // 如果不是数字 ID，尝试按字符串 ID 删除
                downloadRepository.deleteTaskById(taskIdStr)
            }
            refreshDownloadStates()
            Timber.i("TorrentManager: 取消文件选择并删除任务 - taskIdStr=$taskIdStr")
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 取消文件选择失败 - taskIdStr=$taskIdStr")
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
                Result.failure(Exception(LocaleManager.getString(context, R.string.error_add_to_engine_failed)))
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

            // 必须在删除 DB 记录 / 从引擎移除之前取任务信息并定位文件，
            // 否则拿不到 savePath，且引擎移除后查不到落盘路径
            val task = downloadRepository.getTaskById(taskId)
            val filesToDelete = if (deleteFiles && task != null) {
                collectTaskFiles(task)
            } else {
                emptyList()
            }

            // 从引擎移除（不依赖 SWIG 的 delete_files flag，文件删除由下面自己完成）
            torrentEngine.remove(taskIdStr, false)
            downloadRepository.deleteTaskByLongId(taskId)
            refreshDownloadStates()

            // 删除磁盘文件
            filesToDelete.forEach { target ->
                runCatching {
                    if (target.isDirectory) {
                        target.deleteRecursively()
                        Timber.i("TorrentManager: 已删除种子目录 - ${target.absolutePath}")
                    } else {
                        target.delete()
                        File(target.absolutePath + ".parts").delete()
                        Timber.i("TorrentManager: 已删除文件 - ${target.absolutePath}")
                    }
                }.onFailure { Timber.w(it, "TorrentManager: 删除文件失败 - ${target.absolutePath}") }
            }

            Timber.d("TorrentManager: 删除下载任务 - $taskId, deleteFiles=$deleteFiles, 清理 ${filesToDelete.size} 项")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 删除下载任务失败 - $taskId")
            Result.failure(e)
        }
    }

    /**
     * 计算某个 BT 任务删除时应清理哪些文件/目录。
     *
     * BT 的落盘结构有两种：
     *  - 多文件种子：savePath/<种子名>/ 下放各文件 → 整目录都归这个任务，可递归删
     *  - 单文件种子：savePath/xxx.mp4            → 只删文件本身，绝不能删 savePath
     *
     * 注意不能直接用任务标题拼目录名：库里存的是磁力链接 dn（下划线写法，
     * 如 Big_Buck_Bunny），而 libtorrent 用的是种子真实名（Big Buck Bunny），
     * 两者并不一致。所以按"最大视频文件所在的父目录"来定位。
     */
    private fun collectTaskFiles(task: DownloadTask): List<File> {
        return try {
            val base = File(task.savePath)
            if (task.downloadType != DownloadType.BT) {
                // HTTP 直链：固定的 savePath/fileName
                val f = File(base, task.fileName)
                return if (f.exists()) listOf(f) else emptyList()
            }

            val target = resolveLargestVideo(task) ?: return emptyList()
            val parent = target.parentFile
            when {
                // 在独立子目录里 → 整个种子目录一起删
                parent != null &&
                    parent.absolutePath != base.absolutePath &&
                    parent.absolutePath.startsWith(base.absolutePath) -> listOf(parent)
                // 直接躺在 savePath 根下 → 只删该文件
                else -> listOf(target)
            }
        } catch (e: Exception) {
            Timber.e(e, "TorrentManager: 计算待删除文件失败 - taskId=${task.id}")
            emptyList()
        }
    }

    /**
     * 找到该任务落盘的最大视频文件（与 StreamPlaybackHelper 的定位策略一致）。
     *
     * 注意必须在 `torrentEngine.remove()` 之前调用：一旦从 session 移除，
     * 引擎侧就查不到路径了，只剩目录扫描这一条路。
     */
    private fun resolveLargestVideo(task: DownloadTask): File? {
        val base = File(task.savePath)
        val exts = setOf("mp4", "mkv", "avi", "mov", "flv", "ts", "wmv", "webm", "m4v", "mpg", "mpeg", "3gp")
        fun isVideo(f: File) = f.isFile && f.extension.lowercase() in exts

        // 引擎还在时优先用引擎路径
        getStreamableInfo(task.id)?.let { info ->
            val f = File(base, info.largestFilePath)
            if (isVideo(f)) return f
        }

        // 扫描子目录
        val subs = base.listFiles { f -> f.isDirectory }?.flatMap { dir ->
            dir.listFiles { f -> isVideo(f) }?.toList().orEmpty()
        }.orEmpty()
        if (subs.isNotEmpty()) return subs.maxByOrNull { it.length() }

        // 根目录兜底
        val roots = base.listFiles { f -> isVideo(f) }?.toList().orEmpty()
        return roots.maxByOrNull { it.length() }
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
     * @param selectedTotalBytes 已选文件大小之和（可选）。只勾选部分文件时用它
     *        覆盖整种子大小，否则进度会按整种子计算而永远到不了 100%
     */
    suspend fun selectFilesAndStart(
        taskId: Long,
        fileIndices: Set<Int>,
        selectedTotalBytes: Long = 0L
    ) {
        val taskIdStr = taskId.toString()
        torrentEngine.setDownloadFiles(taskIdStr, fileIndices)
        torrentEngine.startDownload(taskIdStr)

        // 校准总大小（必须在 updateTaskStatus 之前，避免一次多余的进度回退）
        if (selectedTotalBytes > 0) {
            downloadRepository.updateTotalBytes(taskId, selectedTotalBytes)
        }

        // 更新任务状态为下载中
        downloadRepository.updateTaskStatus(taskIdStr, DownloadStatus.DOWNLOADING)
        refreshDownloadStates()

        Timber.d("TorrentManager: 已选择 ${fileIndices.size} 个文件并开始下载 - $taskId, 总大小=${selectedTotalBytes}B")
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