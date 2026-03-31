package com.pureframe.player.download

import android.content.Context
import org.libtorrent4j.*
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import com.pureframe.player.domain.model.DownloadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BitTorrent 下载引擎
 * 基于 libtorrent4j (jlibtorrent) 实现
 *
 * 核心功能：
 * - 磁力链接解析和下载
 * - torrent 文件下载
 * - 下载进度追踪
 * - 边下边播支持
 */
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessionManager: SessionManager by lazy {
        createSessionManager()
    }

    private val activeHandles = mutableMapOf<String, TorrentHandle>()
    private val engineScope = CoroutineScope(Dispatchers.IO)

    // 下载状态流
    private val _downloadProgress = MutableSharedFlow<DownloadProgressInfo>(replay = 1)
    val downloadProgress: SharedFlow<DownloadProgressInfo> = _downloadProgress.asSharedFlow()

    init {
        engineScope.launch {
            initSession()
        }
    }

    /**
     * 创建并配置 SessionManager
     */
    private fun createSessionManager(): SessionManager {
        val sm = SessionManager()

        // 配置 Session 参数
        val settings = SettingsPack()
        settings.setActiveDownloads(4)  // 同时下载任务数
        settings.setActiveSeeds(2)      // 同时上传种子数
        settings.setDownloadRateLimit(0) // 不限下载速度
        settings.setUploadRateLimit(1024 * 100) // 100KB/s 上传限制（保护隐私）
        settings.enableDht(true)        // 启用 DHT
        settings.setListenPort(6881, 6891) // 监听端口

        sm.applySettings(settings)
        sm.listenPort(6881)

        return sm
    }

    /**
     * 初始化 Session（异步）
     */
    private suspend fun initSession() {
        try {
            sessionManager.start()
            sessionManager.addListener(createAlertListener())
            Timber.d("TorrentEngine initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TorrentEngine")
        }
    }

    /**
     * 创建 Alert 监听器
     */
    private fun createAlertListener(): AlertListener {
        return AlertListener { alert ->
            when (alert.type()) {
                AlertType.ADD_TORRENT -> {
                    Timber.d("Torrent added: ${alert.message()}")
                }
                AlertType.TORRENT_FINISHED -> {
                    Timber.d("Torrent finished: ${alert.message()}")
                    // 通知下载完成
                    handleTorrentFinished(alert)
                }
                AlertType.TORRENT_ERROR -> {
                    Timber.e("Torrent error: ${alert.message()}")
                }
                AlertType.PIECE_FINISHED -> {
                    // 可用于边下边播进度追踪
                }
                else -> {}
            }
        }
    }

    /**
     * 处理下载完成
     */
    private fun handleTorrentFinished(alert: Alert) {
        // TODO: 通知 Repository 更新状态
    }

    /**
     * 添加磁力链接下载任务
     *
     * @param magnetLink 磁力链接 (magnet:?xt=urn:btih:...)
     * @param savePath 保存路径
     * @return TorrentHandle 或 null（失败时）
     */
    fun addMagnetLink(magnetLink: String, savePath: String): TorrentHandle? {
        try {
            val saveDir = File(savePath)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }

            val params = AddTorrentParams.builder()
                .magnet(magnetLink)
                .savePath(savePath)
                .seedMode(false)  // 不自动做种
                .build()

            val handle = sessionManager.addTorrent(params)

            // 设置优先下载开头片段（边下边播优化）
            handle.setPieceDeadline(0, 0)

            // 开始下载
            handle.resume()

            // 记录活跃的 handle
            val infoHash = handle.infoHash().toString()
            activeHandles[infoHash] = handle

            Timber.d("Added magnet link: $infoHash, savePath: $savePath")

            return handle
        } catch (e: Exception) {
            Timber.e(e, "Failed to add magnet link: $magnetLink")
            return null
        }
    }

    /**
     * 添加 torrent 文件下载任务
     *
     * @param torrentFile torrent 文件
     * @param savePath 保存路径
     * @return TorrentHandle 或 null（失败时）
     */
    fun addTorrentFile(torrentFile: File, savePath: String): TorrentHandle? {
        try {
            val saveDir = File(savePath)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }

            val params = AddTorrentParams.builder()
                .torrentFile(torrentFile)
                .savePath(savePath)
                .seedMode(false)
                .build()

            val handle = sessionManager.addTorrent(params)
            handle.resume()

            val infoHash = handle.infoHash().toString()
            activeHandles[infoHash] = handle

            Timber.d("Added torrent file: $infoHash")

            return handle
        } catch (e: Exception) {
            Timber.e(e, "Failed to add torrent file")
            return null
        }
    }

    /**
     * 根据 infoHash 获取 TorrentHandle
     */
    fun getHandle(infoHash: String): TorrentHandle? {
        return activeHandles[infoHash]
    }

    /**
     * 获取下载进度
     */
    fun getProgress(handle: TorrentHandle): Float {
        val status = handle.status()
        return status.progress() * 100f
    }

    /**
     * 获取下载速度 (bytes/s)
     */
    fun getDownloadSpeed(handle: TorrentHandle): Long {
        return handle.status().downloadRate()
    }

    /**
     * 获取上传速度 (bytes/s)
     */
    fun getUploadSpeed(handle: TorrentHandle): Long {
        return handle.status().uploadRate()
    }

    /**
     * 获取已下载大小 (bytes)
     */
    fun getDownloadedBytes(handle: TorrentHandle): Long {
        return handle.status().totalDone()
    }

    /**
     * 获取总大小 (bytes)
     */
    fun getTotalSize(handle: TorrentHandle): Long {
        val torrentInfo = handle.torrentFile()
        return torrentInfo?.totalSize() ?: 0L
    }

    /**
     * 获取下载状态
     */
    fun getState(handle: TorrentHandle): TorrentState {
        val status = handle.status()
        return when {
            status.isPaused -> TorrentState.PAUSED
            status.isFinished -> TorrentState.COMPLETED
            status.isDownloading -> TorrentState.DOWNLOADING
            status.isSeeding -> TorrentState.SEEDING
            status.hasError() -> TorrentState.ERROR
            else -> TorrentState.WAITING
        }
    }

    /**
     * 暂停下载
     */
    fun pause(handle: TorrentHandle) {
        handle.pause()
        Timber.d("Torrent paused: ${handle.infoHash()}")
    }

    /**
     * 恢复下载
     */
    fun resume(handle: TorrentHandle) {
        handle.resume()
        Timber.d("Torrent resumed: ${handle.infoHash()}")
    }

    /**
     * 移除下载任务
     *
     * @param infoHash 任务 ID
     * @param deleteFiles 是否删除已下载的文件
     */
    fun remove(infoHash: String, deleteFiles: Boolean = false) {
        val handle = activeHandles.remove(infoHash)
        if (handle != null) {
            sessionManager.removeTorrent(handle, deleteFiles)
            Timber.d("Torrent removed: $infoHash, deleteFiles: $deleteFiles")
        }
    }

    /**
     * 获取 torrent 信息
     */
    fun getTorrentInfo(handle: TorrentHandle): TorrentInfo? {
        return handle.torrentFile()
    }

    /**
     * 获取最大文件索引（通常是视频文件）
     */
    fun getLargestFileIndex(handle: TorrentHandle): Int {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return -1

        val fileStorage = torrentInfo.files()
        var maxSize = 0L
        var maxIndex = 0

        for (i in 0 until fileStorage.numFiles()) {
            val size = fileStorage.fileSize(i)
            if (size > maxSize) {
                maxSize = size
                maxIndex = i
            }
        }

        return maxIndex
    }

    /**
     * 获取文件路径
     */
    fun getFilePath(handle: TorrentHandle, fileIndex: Int): String {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return ""

        return torrentInfo.files().filePath(fileIndex)
    }

    /**
     * 检查是否可边下边播
     *
     * @param handle TorrentHandle
     * @param threshold 阈值（0.1 = 10%）
     */
    fun isStreamable(handle: TorrentHandle, threshold: Float = 0.1f): Boolean {
        val progress = handle.status().progress()
        return progress >= threshold
    }

    /**
     * 获取已缓存的片段范围
     */
    fun getCachedPieces(handle: TorrentHandle): List<Int> {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return emptyList()

        val numPieces = torrentInfo.numPieces()
        val cachedPieces = mutableListOf<Int>()

        for (i in 0 until numPieces) {
            if (handle.havePiece(i)) {
                cachedPieces.add(i)
            }
        }

        return cachedPieces
    }

    /**
     * 设置优先下载指定片段（边下边播优化）
     */
    fun prioritizePieces(handle: TorrentHandle, startPiece: Int, endPiece: Int) {
        for (i in startPiece..endPiece) {
            handle.setPieceDeadline(i, 0)
            handle.piecePriority(i, Priority.TOP_PRIORITY)
        }
    }

    /**
     * 启动进度追踪
     */
    fun startProgressTracking(handle: TorrentHandle, taskId: String) {
        engineScope.launch {
            while (activeHandles.containsKey(handle.infoHash().toString())) {
                val progress = getProgress(handle)
                val speed = getDownloadSpeed(handle)
                val state = getState(handle)
                val downloaded = getDownloadedBytes(handle)
                val total = getTotalSize(handle)

                _downloadProgress.emit(
                    DownloadProgressInfo(
                        taskId = taskId,
                        progress = progress,
                        downloadSpeed = speed,
                        state = state,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                )

                kotlinx.coroutines.delay(1000) // 每秒更新
            }
        }
    }

    /**
     * 关闭引擎
     */
    fun shutdown() {
        try {
            // 暂停所有任务
            activeHandles.values.forEach { handle ->
                handle.pause()
            }

            sessionManager.stop()
            Timber.d("TorrentEngine shutdown successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to shutdown TorrentEngine")
        }
    }

    companion object {
        const val TAG = "TorrentEngine"
    }
}

/**
 * 下载进度信息
 */
data class DownloadProgressInfo(
    val taskId: String,
    val progress: Float,
    val downloadSpeed: Long,
    val state: TorrentState,
    val downloadedBytes: Long,
    val totalBytes: Long
)

/**
 * 下载状态枚举
 */
enum class TorrentState {
    PAUSED,
    DOWNLOADING,
    COMPLETED,
    ERROR,
    WAITING,
    SEEDING
}