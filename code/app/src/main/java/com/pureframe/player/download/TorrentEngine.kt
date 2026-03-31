package com.pureframe.player.download

import android.content.Context
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BitTorrent 下载引擎
 * 
 * 使用 libtorrent4j 实现完整的 BitTorrent 下载功能
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
    
    // 活跃的下载任务
    private val activeTorrents = ConcurrentHashMap<String, TorrentHandle>()
    
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
            
            // 配置 Session 设置
            val settings = sessionManager!!.settings()
            settings.setDownloadRateLimit(0)  // 不限下载速度
            settings.setUploadRateLimit(1024 * 100)  // 限制上传速度 100KB/s
            settings.setActiveDownloads(4)  // 同时下载 4 个任务
            settings.setActiveSeeding(2)  // 同时做种 2 个任务
            
            // 启动 DHT
            sessionManager!!.startDht()
            
            // 监听端口
            sessionManager!!.listenPort(6881)
            
            // 设置 Alert 监听器
            setupAlertListeners()
            
            Timber.i("TorrentEngine: Session 初始化成功")
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: Session 初始化失败")
        }
    }
    
    /**
     * 设置 Alert 监听器，监控下载状态
     */
    private fun setupAlertListeners() {
        sessionManager?.addListener(object : AlertListener {
            override fun types(): IntArray {
                return intArrayOf(
                    AlertType.STATE_UPDATE.swig(),
                    AlertType.PIECE_FINISHED.swig(),
                    AlertType.TORRENT_FINISHED.swig(),
                    AlertType.TORRENT_ERROR.swig(),
                    AlertType.ADD_TORRENT.swig()
                )
            }
            
            override fun alert(alert: Alert<*>) {
                handleAlert(alert)
            }
        })
    }
    
    /**
     * 处理 libtorrent Alert
     */
    private fun handleAlert(alert: Alert<*>) {
        when (alert.type()) {
            AlertType.STATE_UPDATE -> {
                val stateAlert = alert as StateUpdateAlert
                updateTorrentProgress(stateAlert)
            }
            AlertType.PIECE_FINISHED -> {
                val pieceAlert = alert as PieceFinishedAlert
                onPieceFinished(pieceAlert)
            }
            AlertType.TORRENT_FINISHED -> {
                val finishAlert = alert as TorrentFinishedAlert
                onTorrentFinished(finishAlert)
            }
            AlertType.TORRENT_ERROR -> {
                val errorAlert = alert as TorrentErrorAlert
                onTorrentError(errorAlert)
            }
            AlertType.ADD_TORRENT -> {
                val addAlert = alert as AddTorrentAlert
                onTorrentAdded(addAlert)
            }
        }
    }
    
    /**
     * 更新下载进度
     */
    private fun updateTorrentProgress(alert: StateUpdateAlert) {
        val handle = alert.handle()
        val status = handle.status()
        val torrentInfo = handle.torrentFile()
        
        if (torrentInfo != null) {
            val progressInfo = DownloadProgressInfo(
                taskId = getTorrentId(handle),
                progress = status.progress() * 100,
                downloadSpeed = status.downloadRate(),
                state = mapTorrentState(status.state()),
                downloadedBytes = status.totalDone(),
                totalBytes = torrentInfo.totalSize()
            )
            
            engineScope.launch {
                _downloadProgress.emit(progressInfo)
            }
            
            // 更新边下边播状态
            updateStreamableStatus(handle)
        }
    }
    
    /**
     * 片下载完成
     */
    private fun onPieceFinished(alert: PieceFinishedAlert) {
        val handle = alert.handle()
        updateStreamableStatus(handle)
    }
    
    /**
     * Torrent 下载完成
     */
    private fun onTorrentFinished(alert: TorrentFinishedAlert) {
        val handle = alert.handle()
        Timber.i("TorrentEngine: Torrent 完成 - ${getTorrentId(handle)}")
        
        // 发送完成状态
        val torrentInfo = handle.torrentFile()
        if (torrentInfo != null) {
            val progressInfo = DownloadProgressInfo(
                taskId = getTorrentId(handle),
                progress = 100f,
                downloadSpeed = 0,
                state = TorrentState.COMPLETED,
                downloadedBytes = torrentInfo.totalSize(),
                totalBytes = torrentInfo.totalSize()
            )
            
            engineScope.launch {
                _downloadProgress.emit(progressInfo)
            }
        }
    }
    
    /**
     * Torrent 错误
     */
    private fun onTorrentError(alert: TorrentErrorAlert) {
        val handle = alert.handle()
        Timber.e("TorrentEngine: Torrent 错误 - ${getTorrentId(handle)}: ${alert.message()}")
        
        val progressInfo = DownloadProgressInfo(
            taskId = getTorrentId(handle),
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
     * Torrent 添加成功
     */
    private fun onTorrentAdded(alert: AddTorrentAlert) {
        val handle = alert.handle()
        Timber.d("TorrentEngine: Torrent 添加成功 - ${getTorrentId(handle)}")
        
        // 开始下载
        handle.resume()
    }
    
    /**
     * 添加磁力链接下载任务
     * 
     * @param magnetLink 磁力链接 (magnet:?xt=urn:btih:...)
     * @param savePath 保存路径
     * @param taskId 任务 ID (用于追踪)
     * @return 是否成功添加
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
            
            // 解析磁力链接
            val magnetUri = MagnetUri(magnetLink)
            
            // 创建添加参数
            val params = AddTorrentParams.builder()
                .magnet(magnetLink)
                .savePath(savePath)
                .name(taskId)  // 使用 taskId 作为名称便于追踪
                .build()
            
            // 添加 Torrent
            val handle = session.addTorrent(params)
            
            // 存储 handle
            activeTorrents[taskId] = handle
            
            Timber.i("TorrentEngine: 磁力链接添加成功 - $taskId")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "TorrentEngine: 磁力链接添加失败 - $magnetLink")
            return false
        }
    }
    
    /**
     * 添加 torrent 文件下载任务
     * 
     * @param torrentFile .torrent 文件
     * @param savePath 保存路径
     * @param taskId 任务 ID
     * @return 是否成功添加
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
            
            // 创建添加参数
            val params = AddTorrentParams.builder()
                .torrentFile(torrentFile)
                .savePath(savePath)
                .name(taskId)
                .build()
            
            // 添加 Torrent
            val handle = session.addTorrent(params)
            
            // 存储 handle
            activeTorrents[taskId] = handle
            
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
        val handle = activeTorrents[taskId]
        if (handle != null) {
            handle.pause()
            Timber.d("TorrentEngine: 暂停 - $taskId")
        } else {
            Timber.w("TorrentEngine: 未找到任务 - $taskId")
        }
    }
    
    /**
     * 恢复下载
     */
    fun resume(taskId: String) {
        val handle = activeTorrents[taskId]
        if (handle != null) {
            handle.resume()
            Timber.d("TorrentEngine: 恢复 - $taskId")
        } else {
            Timber.w("TorrentEngine: 未找到任务 - $taskId")
        }
    }
    
    /**
     * 移除下载任务
     * 
     * @param taskId 任务 ID
     * @param deleteFiles 是否删除已下载文件
     */
    fun remove(taskId: String, deleteFiles: Boolean = false) {
        val handle = activeTorrents[taskId]
        if (handle != null) {
            sessionManager?.removeTorrent(handle, deleteFiles)
            activeTorrents.remove(taskId)
            Timber.d("TorrentEngine: 移除 - $taskId, deleteFiles=$deleteFiles")
        } else {
            Timber.w("TorrentEngine: 未找到任务 - $taskId")
        }
    }
    
    /**
     * 检查是否可边下边播
     * 
     * @param taskId 任务 ID
     * @param threshold 阈值 (0.1 = 10%)
     * @return 是否可边下边播
     */
    fun isStreamable(taskId: String, threshold: Float = 0.1f): Boolean {
        val handle = activeTorrents[taskId]
        if (handle == null) return false
        
        val status = handle.status()
        return status.progress() >= threshold
    }
    
    /**
     * 获取边下边播信息
     */
    fun getStreamableInfo(taskId: String): StreamableInfo? {
        val handle = activeTorrents[taskId]
        if (handle == null) return null
        
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
            isStreamable = cachedProgress > 0.1f,
            largestFileIndex = largestFileIndex,
            largestFilePath = fileStorage.filePath(largestFileIndex),
            cachedProgress = cachedProgress,
            maxSeekPosition = maxSeekPosition,
            totalSize = fileSize
        )
    }
    
    /**
     * 更新边下边播状态
     */
    private fun updateStreamableStatus(handle: TorrentHandle) {
        val taskId = getTorrentId(handle)
        val info = getStreamableInfo(taskId)
        
        if (info != null) {
            val currentMap = _streamableStatus.value.toMutableMap()
            currentMap[taskId] = info
            _streamableStatus.value = currentMap
        }
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
        
        // 计算文件对应的 piece 范围
        val fileOffset = fileStorage.fileOffset(fileIndex)
        val startPiece = (fileOffset / pieceLength).toInt()
        val endPiece = ((fileOffset + fileSize) / pieceLength).toInt()
        
        // 计算已下载的 piece 数量
        var downloadedPieces = 0
        val totalPieces = endPiece - startPiece
        
        for (i in startPiece..endPiece) {
            if (handle.havePiece(i)) {
                downloadedPieces++
            }
        }
        
        return if (totalPieces > 0) {
            downloadedPieces.toFloat() / totalPieces
        } else {
            0f
        }
    }
    
    /**
     * 获取 Torrent ID（使用 info hash 或任务名）
     */
    private fun getTorrentId(handle: TorrentHandle): String {
        return handle.name() ?: handle.infoHash().toString()
    }
    
    /**
     * 映射 libtorrent 状态到应用状态
     */
    private fun mapTorrentState(state: TorrentStatus.State): TorrentState {
        return when (state) {
            TorrentStatus.State.DOWNLOADING -> TorrentState.DOWNLOADING
            TorrentStatus.State.FINISHED -> TorrentState.COMPLETED
            TorrentStatus.State.SEEDING -> TorrentState.SEEDING
            TorrentStatus.State.PAUSED -> TorrentState.PAUSED
            TorrentStatus.State.CHECKING_FILES -> TorrentState.WAITING
            TorrentStatus.State.DOWNLOADING_METADATA -> TorrentState.WAITING
            TorrentStatus.State.ALLOCATING -> TorrentState.WAITING
            TorrentStatus.State.QUEUED_FOR_CHECKING -> TorrentState.WAITING
            else -> TorrentState.WAITING
        }
    }
    
    /**
     * 获取下载进度信息（同步版本）
     */
    fun getProgressInfo(taskId: String): DownloadProgressInfo? {
        val handle = activeTorrents[taskId]
        if (handle == null) return null
        
        val status = handle.status()
        val torrentInfo = handle.torrentFile()
        
        if (torrentInfo == null) {
            return DownloadProgressInfo(
                taskId = taskId,
                progress = 0f,
                downloadSpeed = status.downloadRate(),
                state = TorrentState.WAITING,
                downloadedBytes = 0,
                totalBytes = 0
            )
        }
        
        return DownloadProgressInfo(
            taskId = taskId,
            progress = status.progress() * 100,
            downloadSpeed = status.downloadRate(),
            state = mapTorrentState(status.state()),
            downloadedBytes = status.totalDone(),
            totalBytes = torrentInfo.totalSize()
        )
    }
    
    /**
     * 关闭引擎
     */
    fun shutdown() {
        try {
            // 暂停所有下载
            activeTorrents.values.forEach { handle ->
                handle.pause()
            }
            
            // 关闭 Session
            sessionManager?.stopDht()
            sessionManager?.pause()
            
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