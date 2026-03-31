package com.pureframe.player.download

import android.content.Context
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import org.libtorrent4j.swig.torrent_flags_t
import org.libtorrent4j.swig.add_torrent_params
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BitTorrent 下载引擎
 * 
 * 使用 org.libtorrent4j 实现完整的 BitTorrent 下载功能
 * 支持：
 * - 磁力链接下载
 * - Torrent 文件下载
 * - 下载进度监控
 * - 边下边播
 */
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // libtorrent4j SessionManager
    private var sessionManager: SessionManager? = null
    
    // 任务 ID 到 info hash 的映射
    private val taskIdToInfoHash = ConcurrentHashMap<String, Sha1Hash>()
    
    // 下载状态流
    private val _downloadProgress = MutableSharedFlow<DownloadProgressInfo>(replay = 1, extraBufferCapacity = 64)
    val downloadProgress: SharedFlow<DownloadProgressInfo> = _downloadProgress.asSharedFlow()
    
    // 边下边播状态流
    private val _streamableStatus = MutableStateFlow<Map<String, StreamableInfo>>(emptyMap())
    val streamableStatus: StateFlow<Map<String, StreamableInfo>> = _streamableStatus.asStateFlow()

    init {
        initializeSession()
    }
    
    /**
     * 初始化 libtorrent4j Session
     */
    private fun initializeSession() {
        try {
            sessionManager = SessionManager()
            
            // 启动 Session
            sessionManager!!.start()
            
            // 启动 DHT
            sessionManager!!.startDht()
            
            // 设置 Alert 监听器
            setupAlertListeners()
            
            Timber.i("TorrentEngine: Session 初始化成功")
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: Session 初始化失败")
        }
    }
    
    /**
     * 设置 Alert 监听器
     */
    private fun setupAlertListeners() {
        sessionManager?.addListener(object : AlertListener {
            override fun types(): IntArray? = null // 监听所有类型
            
            override fun alert(alert: Alert<*>) {
                handleAlert(alert)
            }
        })
    }
    
    /**
     * 处理 libtorrent Alert
     */
    private fun handleAlert(alert: Alert<*>) {
        try {
            when (alert) {
                is StateChangedAlert -> {
                    updateTorrentStatus(alert.handle())
                }
                is AddTorrentAlert -> {
                    onTorrentAdded(alert)
                }
                is TorrentFinishedAlert -> {
                    onTorrentFinished(alert)
                }
                is TorrentErrorAlert -> {
                    onTorrentError(alert)
                }
                is BlockFinishedAlert -> {
                    updateStreamableStatus(alert.handle())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: Alert 处理错误")
        }
    }
    
    /**
     * 更新 Torrent 状态
     */
    private fun updateTorrentStatus(handle: TorrentHandle) {
        try {
            val infoHash = handle.infoHash()
            val taskId = findTaskIdByInfoHash(infoHash)
            
            if (taskId == null) {
                return
            }
            
            val status = handle.status()
            val torrentInfo = handle.torrentFile()
            
            val totalBytes = torrentInfo?.totalSize() ?: 0L
            
            val progressInfo = DownloadProgressInfo(
                taskId = taskId,
                progress = status.progress() * 100,
                downloadSpeed = status.downloadRate().toLong(),
                state = mapTorrentState(handle),
                downloadedBytes = status.totalDone(),
                totalBytes = totalBytes
            )
            
            engineScope.launch {
                _downloadProgress.emit(progressInfo)
            }
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: 更新状态错误")
        }
    }
    
    /**
     * 根据 infoHash 查找 taskId
     */
    private fun findTaskIdByInfoHash(infoHash: Sha1Hash): String? {
        for ((taskId, hash) in taskIdToInfoHash) {
            if (hash == infoHash) {
                return taskId
            }
        }
        return null
    }
    
    /**
     * Torrent 添加成功
     */
    private fun onTorrentAdded(alert: AddTorrentAlert) {
        val handle = alert.handle()
        val infoHash = handle.infoHash()
        
        Timber.d("TorrentEngine: Torrent 添加成功 - ${infoHash}")
        
        // 开始下载
        handle.resume()
        
        // 更新状态
        updateTorrentStatus(handle)
    }
    
    /**
     * Torrent 下载完成
     */
    private fun onTorrentFinished(alert: TorrentFinishedAlert) {
        val handle = alert.handle()
        val taskId = findTaskIdByInfoHash(handle.infoHash())
        
        if (taskId == null) return
        
        Timber.i("TorrentEngine: Torrent 完成 - $taskId")
        
        val torrentInfo = handle.torrentFile()
        val totalBytes = torrentInfo?.totalSize() ?: 0L
        
        val progressInfo = DownloadProgressInfo(
            taskId = taskId,
            progress = 100f,
            downloadSpeed = 0,
            state = TorrentState.COMPLETED,
            downloadedBytes = totalBytes,
            totalBytes = totalBytes
        )
        
        engineScope.launch {
            _downloadProgress.emit(progressInfo)
        }
    }
    
    /**
     * Torrent 错误
     */
    private fun onTorrentError(alert: TorrentErrorAlert) {
        val handle = alert.handle()
        val taskId = findTaskIdByInfoHash(handle.infoHash())
        
        if (taskId == null) return
        
        Timber.e("TorrentEngine: Torrent 错误 - $taskId")
        
        val progressInfo = DownloadProgressInfo(
            taskId = taskId,
            progress = 0f,
            downloadSpeed = 0,
            state = TorrentState.ERROR,
            downloadedBytes = 0,
            totalBytes = 0
        )
        
        engineScope.launch {
            _downloadProgress.emit(progressInfo)
        }
    }
    
    /**
     * 更新边下边播状态
     */
    private fun updateStreamableStatus(handle: TorrentHandle) {
        val taskId = findTaskIdByInfoHash(handle.infoHash())
        if (taskId == null) return
        
        val info = getStreamableInfo(taskId)
        
        if (info != null) {
            val currentMap = _streamableStatus.value.toMutableMap()
            currentMap[taskId] = info
            _streamableStatus.value = currentMap
        }
    }
    
    /**
     * 添加磁力链接下载任务
     */
    fun addMagnetLink(magnetLink: String, savePath: String, taskId: String): Boolean {
        try {
            val session = sessionManager ?: run {
                Timber.e("TorrentEngine: Session 未初始化")
                return false
            }
            
            // 确保保存目录存在
            val saveDir = File(savePath)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }
            
            // 使用 SessionManager 的 download(magnetUri, saveDir, flags) 方法
            // 这是 libtorrent4j 提供的正确添加磁力链接的方式
            session.download(magnetLink, saveDir, torrent_flags_t())
            
            // 解析并存储 taskId 到 infoHash 的映射（用于后续查找）
            val infoHashStr = parseInfoHashFromMagnet(magnetLink)
            if (infoHashStr != null) {
                taskIdToInfoHash[taskId] = Sha1Hash.parseHex(infoHashStr)
            }
            
            Timber.i("TorrentEngine: 磁力链接添加成功 - $taskId")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: 磁力链接添加失败 - $magnetLink")
            return false
        }
    }
    
    /**
     * 从磁力链接解析 info hash
     */
    private fun parseInfoHashFromMagnet(magnetLink: String): String? {
        // magnet:?xt=urn:btih:INFO_HASH&...
        val pattern = "urn:btih:([a-fA-F0-9]{40})"
        val regex = Regex(pattern)
        val match = regex.find(magnetLink)
        return match?.groupValues?.getOrNull(1)?.lowercase()
    }
    
    /**
     * 添加 torrent 文件下载任务
     */
    fun addTorrentFile(torrentFile: File, savePath: String, taskId: String): Boolean {
        try {
            val session = sessionManager ?: run {
                Timber.e("TorrentEngine: Session 未初始化")
                return false
            }
            
            // 确保保存目录存在
            val saveDir = File(savePath)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }
            
            // 从 torrent 文件创建 TorrentInfo
            val torrentInfo = TorrentInfo(torrentFile)
            
            // 存储 infoHash 映射
            taskIdToInfoHash[taskId] = torrentInfo.infoHash()
            
            // 使用 download 方法添加 torrent
            session.download(torrentInfo, saveDir)
            
            Timber.i("TorrentEngine: Torrent 文件添加成功 - $taskId")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: Torrent 文件添加失败 - ${torrentFile.name}")
            return false
        }
    }
    
    /**
     * 暂停下载
     */
    fun pause(taskId: String) {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) {
            Timber.w("TorrentEngine: 未找到任务 - $taskId")
            return
        }
        
        val handle = sessionManager?.find(infoHash)
        if (handle != null && handle.isValid()) {
            handle.pause()
            Timber.d("TorrentEngine: 暂停 - $taskId")
        } else {
            Timber.w("TorrentEngine: 未找到 handle - $taskId")
        }
    }
    
    /**
     * 恢复下载
     */
    fun resume(taskId: String) {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) {
            Timber.w("TorrentEngine: 未找到任务 - $taskId")
            return
        }
        
        val handle = sessionManager?.find(infoHash)
        if (handle != null && handle.isValid()) {
            handle.resume()
            Timber.d("TorrentEngine: 恢复 - $taskId")
        } else {
            Timber.w("TorrentEngine: 未找到 handle - $taskId")
        }
    }
    
    /**
     * 移除下载任务
     */
    fun remove(taskId: String, deleteFiles: Boolean = false) {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) {
            Timber.w("TorrentEngine: 未找到任务 - $taskId")
            return
        }
        
        val handle = sessionManager?.find(infoHash)
        if (handle != null && handle.isValid()) {
            sessionManager?.remove(handle)
            taskIdToInfoHash.remove(taskId)
            
            // 如果需要删除文件
            if (deleteFiles) {
                // TODO: 删除下载文件
            }
            
            Timber.d("TorrentEngine: 移除 - $taskId, deleteFiles=$deleteFiles")
        } else {
            Timber.w("TorrentEngine: 未找到 handle - $taskId")
        }
    }
    
    /**
     * 检查是否可边下边播
     */
    fun isStreamable(taskId: String, threshold: Float = DEFAULT_STREAMABLE_THRESHOLD): Boolean {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) return false
        
        val handle = sessionManager?.find(infoHash)
        if (handle == null || !handle.isValid()) return false
        
        val status = handle.status()
        return status.progress() >= threshold
    }
    
    /**
     * 获取边下边播信息
     */
    fun getStreamableInfo(taskId: String): StreamableInfo? {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) return null
        
        val handle = sessionManager?.find(infoHash)
        if (handle == null || !handle.isValid()) return null
        
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return null
        
        // 找出最大的文件（通常是视频）
        val largestFileIndex = findLargestFileIndex(torrentInfo)
        val fileStorage = torrentInfo.files()
        val fileSize = fileStorage.fileSize(largestFileIndex)
        
        // 计算已缓存的进度
        val cachedProgress = calculateCachedProgress(handle, largestFileIndex, torrentInfo)
        val maxSeekPosition = (fileSize * cachedProgress).toLong()
        
        return StreamableInfo(
            taskId = taskId,
            isStreamable = cachedProgress > DEFAULT_STREAMABLE_THRESHOLD,
            largestFileIndex = largestFileIndex,
            largestFilePath = fileStorage.filePath(largestFileIndex),
            cachedProgress = cachedProgress,
            maxSeekPosition = maxSeekPosition,
            totalSize = fileSize
        )
    }
    
    /**
     * 找出最大的文件索引
     */
    private fun findLargestFileIndex(torrentInfo: TorrentInfo): Int {
        val fileStorage = torrentInfo.files()
        var maxIndex = 0
        var maxSize = 0L
        
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
     * 计算指定文件的已缓存进度
     */
    private fun calculateCachedProgress(
        handle: TorrentHandle,
        fileIndex: Int,
        torrentInfo: TorrentInfo
    ): Float {
        val fileStorage = torrentInfo.files()
        val fileSize = fileStorage.fileSize(fileIndex)
        val pieceLength = torrentInfo.pieceLength()
        
        if (pieceLength <= 0 || fileSize <= 0) return 0f
        
        // 计算文件对应的 piece 范围
        val fileOffset = fileStorage.fileOffset(fileIndex)
        val startPiece = (fileOffset / pieceLength).toInt()
        val endPiece = ((fileOffset + fileSize) / pieceLength).toInt()
        
        val totalPieces = endPiece - startPiece + 1
        if (totalPieces <= 0) return 0f
        
        // 计算已下载的 piece 数量
        var downloadedPieces = 0
        
        for (i in startPiece..endPiece) {
            if (i < torrentInfo.numPieces() && handle.havePiece(i)) {
                downloadedPieces++
            }
        }
        
        return downloadedPieces.toFloat() / totalPieces
    }
    
    /**
     * 映射 Torrent 状态
     */
    private fun mapTorrentState(handle: TorrentHandle): TorrentState {
        val status = handle.status()
        val state = status.state()
        
        // 检查是否暂停（通过 flags）- 未来实现
        val _flags = handle.getFlags()  // torrent_flags_t 中有 paused 标志
        
        return when (state) {
            TorrentStatus.State.DOWNLOADING -> TorrentState.DOWNLOADING
            TorrentStatus.State.FINISHED -> TorrentState.COMPLETED
            TorrentStatus.State.SEEDING -> TorrentState.SEEDING
            TorrentStatus.State.CHECKING_FILES,
            TorrentStatus.State.DOWNLOADING_METADATA,
            TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentState.WAITING
            TorrentStatus.State.UNKNOWN -> TorrentState.ERROR
            else -> TorrentState.WAITING
        }
    }
    
    /**
     * 获取下载进度信息（同步版本）
     */
    fun getProgressInfo(taskId: String): DownloadProgressInfo? {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) return null
        
        val handle = sessionManager?.find(infoHash)
        if (handle == null || !handle.isValid()) return null
        
        val status = handle.status()
        val torrentInfo = handle.torrentFile()
        
        val totalBytes = torrentInfo?.totalSize() ?: 0L
        
        return DownloadProgressInfo(
            taskId = taskId,
            progress = status.progress() * 100,
            downloadSpeed = status.downloadRate().toLong(),
            state = mapTorrentState(handle),
            downloadedBytes = status.totalDone(),
            totalBytes = totalBytes
        )
    }
    
    /**
     * 读取数据块（边下边播）
     * 
     * @param taskId 任务 ID
     * @param fileIndex 文件索引
     * @param offset 文件内偏移
     * @param length 读取长度
     * @return 读取的数据，如果失败返回 null
     */
    fun readDataBlock(taskId: String, fileIndex: Int, offset: Long, length: Int): ByteArray? {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) return null
        
        val handle = sessionManager?.find(infoHash)
        if (handle == null || !handle.isValid()) return null
        
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return null
        
        try {
            // 获取保存路径
            val fileStorage = torrentInfo.files()
            val filePath = fileStorage.filePath(fileIndex)
            val savePath = handle.savePath()
            val fullPath = File(savePath, filePath)
            
            // 检查文件是否存在
            if (!fullPath.exists()) {
                Timber.w("TorrentEngine: 文件不存在 - ${fullPath.absolutePath}")
                return null
            }
            
            // 检查请求范围是否已下载
            val pieceLength = torrentInfo.pieceLength()
            val fileOffset = fileStorage.fileOffset(fileIndex)
            val globalOffset = fileOffset + offset
            
            val startPiece = (globalOffset / pieceLength).toInt()
            val endPiece = ((globalOffset + length) / pieceLength).toInt()
            
            // 检查所有需要的 piece 是否已下载
            for (i in startPiece..endPiece) {
                if (i < torrentInfo.numPieces() && !handle.havePiece(i)) {
                    Timber.w("TorrentEngine: Piece $i 未下载")
                    return null
                }
            }
            
            // 从文件读取数据
            val raf = RandomAccessFile(fullPath, "r")
            raf.seek(offset)
            val buffer = ByteArray(length)
            raf.read(buffer)
            raf.close()
            
            return buffer
            
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: 读取数据失败 - taskId=$taskId, offset=$offset")
            return null
        }
    }
    
    /**
     * 获取文件路径（边下边播）
     * 
     * @param taskId 任务 ID
     * @param fileIndex 文件索引
     * @return 文件完整路径
     */
    fun getFilePath(taskId: String, fileIndex: Int): String? {
        val infoHash = taskIdToInfoHash[taskId]
        if (infoHash == null) return null
        
        val handle = sessionManager?.find(infoHash)
        if (handle == null || !handle.isValid()) return null
        
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return null
        
        val fileStorage = torrentInfo.files()
        val filePath = fileStorage.filePath(fileIndex)
        val savePath = handle.savePath()
        
        return File(savePath, filePath).absolutePath
    }
    
    /**
     * 关闭引擎
     */
    fun shutdown() {
        try {
            // 暂停所有下载
            taskIdToInfoHash.values.forEach { infoHash ->
                val handle = sessionManager?.find(infoHash)
                if (handle != null && handle.isValid()) {
                    handle.pause()
                }
            }
            
            // 关闭 Session
            sessionManager?.stopDht()
            sessionManager?.stop()
            
            engineScope.cancel()
            
            Timber.i("TorrentEngine: 已关闭")
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: 关闭时出错")
        }
    }

    companion object {
        const val TAG = "TorrentEngine"
        
        // 边下边播默认阈值（10%）
        const val DEFAULT_STREAMABLE_THRESHOLD = 0.1f
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
 * 边下边播信息
 */
data class StreamableInfo(
    val taskId: String,
    val isStreamable: Boolean,
    val largestFileIndex: Int,
    val largestFilePath: String,
    val cachedProgress: Float,
    val maxSeekPosition: Long,
    val totalSize: Long
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