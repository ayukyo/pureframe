package com.pureframe.player.ui.screens.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.model.DownloadType
import timber.log.Timber
import com.pureframe.player.domain.usecase.download.GetAllDownloadsUseCase
import com.pureframe.player.domain.usecase.download.GetActiveDownloadsUseCase
import com.pureframe.player.domain.usecase.download.GetDownloadsByStatusUseCase
import com.pureframe.player.domain.usecase.download.CreateDownloadTaskUseCase
import com.pureframe.player.domain.usecase.download.PauseDownloadUseCase
import com.pureframe.player.domain.usecase.download.StartDownloadUseCase
import com.pureframe.player.domain.usecase.download.DeleteDownloadUseCase
import com.pureframe.player.domain.usecase.download.ClearCompletedDownloadsUseCase
import com.pureframe.player.domain.usecase.download.GetActiveDownloadCountUseCase
import com.pureframe.player.download.TorrentManager
import com.pureframe.player.download.TorrentMetadataInfo
import com.pureframe.player.download.HttpDownloadManager
import com.pureframe.player.download.DownloadDirectories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject
import com.pureframe.player.R
import com.pureframe.player.i18n.LocaleManager

/**
 * 下载页面 ViewModel
 *
 * 负责：
 * - 提供下载任务列表（全部、活跃、已完成等）
 * - 添加新下载任务（磁力链接）
 * - 控制下载进度（暂停/恢复/删除）
 * - 清理已完成任务
 * - 监控下载状态
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    getAllDownloadsUseCase: GetAllDownloadsUseCase,
    getActiveDownloadsUseCase: GetActiveDownloadsUseCase,
    getDownloadsByStatusUseCase: GetDownloadsByStatusUseCase,
    private val createDownloadTaskUseCase: CreateDownloadTaskUseCase,
    private val pauseDownloadUseCase: PauseDownloadUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
    private val deleteDownloadUseCase: DeleteDownloadUseCase,
    private val clearCompletedDownloadsUseCase: ClearCompletedDownloadsUseCase,
    private val getActiveDownloadCountUseCase: GetActiveDownloadCountUseCase,
    private val torrentManager: TorrentManager,
    private val httpDownloadManager: HttpDownloadManager,
    private val userPreferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
) : ViewModel() {

    // 列表类型
    enum class ListType {
        ALL,       // 全部任务
        ACTIVE,    // 活跃任务（下载中/暂停）
        COMPLETED, // 已完成
        FAILED     // 失败任务
    }

    // UI 状态
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    // 全部下载任务
    val allDownloads: StateFlow<List<DownloadTask>> = getAllDownloadsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 活跃下载任务（下载中 + 暂停）
    val activeDownloads: StateFlow<List<DownloadTask>> = getActiveDownloadsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 已完成下载任务
    val completedDownloads: StateFlow<List<DownloadTask>> = getDownloadsByStatusUseCase(DownloadStatus.COMPLETED)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 失败下载任务
    val failedDownloads: StateFlow<List<DownloadTask>> = getDownloadsByStatusUseCase(DownloadStatus.FAILED)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 活跃下载数量（用于限制并行下载数）
    private val _activeDownloadCount = MutableStateFlow(0)
    val activeDownloadCount: StateFlow<Int> = _activeDownloadCount.asStateFlow()

    // 待处理文件选择的 torrent 元数据
    private val _pendingMetadata = MutableStateFlow<TorrentMetadataInfo?>(null)
    val pendingMetadata: StateFlow<TorrentMetadataInfo?> = _pendingMetadata.asStateFlow()

    // 当前正在准备的任务 ID（用于在 metadata 到达前取消）
    private var _preparingTaskId: Long? = null

    // metadata 超时检测任务（用户确认/取消后需要取消，否则会弹出假的超时错误）
    private var metadataTimeoutJob: kotlinx.coroutines.Job? = null

    init {
        // 初始化时获取活跃下载数量
        viewModelScope.launch {
            _activeDownloadCount.value = getActiveDownloadCountUseCase()
        }

        // 监听 metadata 获取事件，用于文件选择
        viewModelScope.launch {
            torrentManager.metadataReceived.collect { metadata ->
                Timber.d("DownloadViewModel: 收到 metadata - taskId=${metadata.taskId}")
                _pendingMetadata.value = metadata
            }
        }
    }

    /**
     * 切换列表类型
     */
    fun setListType(listType: ListType) {
        _uiState.update { it.copy(listType = listType) }
    }

    /**
     * 添加下载任务（磁力链接或直链）
     * 磁力链接：先添加到引擎获取 metadata，弹出文件选择对话框
     *         用户确认后才正式开始下载
     * 直链：直接开始下载
     *
     * @param url 磁力链接或直链
     * @param title 任务标题（可选）
     * @param linkType 链接类型
     */
    fun addDownloadTask(
        url: String,
        title: String? = null,
        linkType: LinkType = LinkType.MAGNET
    ) {
        Timber.d("DownloadViewModel.addDownloadTask 被调用 - url=$url, linkType=$linkType")

        // .torrent 种子文件直链：先下载种子文件到缓存，再走 BT 任务流程
        if (linkType == LinkType.TORRENT_FILE) {
            addTorrentLinkDownload(url, title)
            return
        }

        // 如果是 HTTP 直链，直接开始下载
        if (linkType == LinkType.HTTP) {
            addHttpDownloadTask(url, title)
            return
        }

        // 磁力链接处理
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTask = true, errorMessage = null) }
            try {
                val extractedTitle = title ?: extractTitleFromMagnet(url)
                val savePath = resolveDownloadDir()
                Timber.d("DownloadViewModel: 开始准备下载任务 - title=$extractedTitle")

                // 引擎侧会在主线程阻塞（内部 Thread.sleep 等待 metadata），必须切到 IO
                val result = withContext(Dispatchers.IO) {
                    // 使用 torrentManager.prepareMagnetLink 仅添加到引擎获取 metadata
                    // 不创建正式任务，不显示在列表中
                    torrentManager.prepareMagnetLink(
                        magnetLink = url,
                        savePath = savePath,
                        name = extractedTitle
                    )
                }

                result.fold(
                    onSuccess = { taskId ->
                        Timber.d("DownloadViewModel: 磁力链接已添加，等待 metadata - taskId=$taskId")
                        _preparingTaskId = taskId
                        // 启动 metadata 超时检测（30秒）
                        startMetadataTimeout(taskId, url)
                    },
                    onFailure = { e ->
                        Timber.e(e, "DownloadViewModel: 准备下载任务失败")
                        _uiState.update { it.copy(errorMessage = e.message, isAddingTask = false) }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "DownloadViewModel: 添加下载任务失败")
                _uiState.update { it.copy(errorMessage = e.message, isAddingTask = false) }
            }
            // 注意：isAddingTask = false 不在这里设置
            // 会在 cancelFileSelection 或 confirmFileSelection 时设置
        }
    }

    /**
     * 添加 HTTP 直链下载任务
     *
     * @param url 下载地址
     * @param title 任务标题（可选）
     */
    private fun addHttpDownloadTask(
        url: String,
        title: String? = null
    ) {
        Timber.d("DownloadViewModel.addHttpDownloadTask 被调用 - url=$url")
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTask = true, errorMessage = null) }
            try {
                val savePath = resolveDownloadDir()
                val result = httpDownloadManager.createDownloadTask(
                    url = url,
                    savePath = savePath,
                    name = title
                )

                result.fold(
                    onSuccess = { taskId ->
                        Timber.d("DownloadViewModel: HTTP 下载任务创建成功 - taskId=$taskId")
                        // 任务已创建，关闭"正在获取文件列表"弹窗（否则会永久卡住）
                        _uiState.update { it.copy(isAddingTask = false) }
                    },
                    onFailure = { e ->
                        Timber.e(e, "DownloadViewModel: HTTP 下载任务创建失败")
                        _uiState.update { it.copy(errorMessage = e.message, isAddingTask = false) }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "DownloadViewModel: 添加 HTTP 下载任务失败")
                _uiState.update { it.copy(errorMessage = e.message, isAddingTask = false) }
            }
        }
    }

    /**
     * 添加 .torrent 种子文件链接下载任务
     *
     * .torrent 不是媒体文件，不能当 HTTP 直链下载；正确流程是：
     * 1. 把 .torrent 文件下载到应用缓存目录
     * 2. 交给 [addTorrentFileDownload] 走既有 BT 流程（创建任务 + 弹出文件选择对话框）
     *
     * @param url .torrent 文件的下载链接
     * @param title 任务标题（可选，默认用种子内名称）
     */
    private fun addTorrentLinkDownload(url: String, title: String? = null) {
        Timber.d("DownloadViewModel.addTorrentLinkDownload 被调用 - url=$url")
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTask = true, errorMessage = null) }
            try {
                val torrentFile = withContext(Dispatchers.IO) { downloadTorrentFile(url) }
                Timber.d("DownloadViewModel: 种子文件已下载 - ${torrentFile.absolutePath}")
                addTorrentFileDownload(torrentFile.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "DownloadViewModel: 种子文件下载失败")
                _uiState.update {
                    it.copy(
                        errorMessage = LocaleManager.getString(appContext, R.string.error_torrent_download_failed),
                        isAddingTask = false
                    )
                }
            }
        }
    }

    /**
     * 下载 .torrent 种子文件到缓存目录
     * 种子文件通常只有几十 KB，单连接即可；下载失败（非 2xx / IO 异常）直接抛出
     */
    private fun downloadTorrentFile(url: String): File {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "PureFrame/1.0")
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("server returned HTTP $code")
            }
            val cacheDir = File(appContext.cacheDir, "torrents").apply { mkdirs() }
            // 命名：优先 URL 文件名，保证以 .torrent 结尾（TorrentInfo 解析不依赖扩展名，仅便于识别）
            val name = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
                .takeIf { it.isNotEmpty() } ?: "torrent_${System.currentTimeMillis()}"
            val safeName = (if (name.lowercase().endsWith(".torrent")) name else "$name.torrent")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val outFile = File(cacheDir, safeName)
            conn.inputStream.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (outFile.length() == 0L) {
                throw java.io.IOException("empty torrent file")
            }
            return outFile
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 启动 metadata 获取超时检测
     */
    @Suppress("UNUSED_PARAMETER")
    private fun startMetadataTimeout(taskId: Long, magnetLink: String) {
        // 取消上一个超时任务，避免旧任务误判
        metadataTimeoutJob?.cancel()
        metadataTimeoutJob = viewModelScope.launch {
            delay(30_000) // 30秒超时
            // 用户已经确认或取消过这个任务，不再当作超时处理
            if (_preparingTaskId != taskId) {
                Timber.d("DownloadViewModel: metadata 超时检查跳过（任务已被处理）- taskId=$taskId")
                return@launch
            }
            // 检查是否仍然没有收到 metadata
            val pending = _pendingMetadata.value
            if (pending == null || pending.taskId.toLongOrNull() != taskId) {
                Timber.e("DownloadViewModel: metadata 获取超时 - taskId=$taskId, magnetLink=$magnetLink")
                _uiState.update {
                    it.copy(
                        errorMessage = LocaleManager.getString(appContext, R.string.error_invalid_magnet),
                        isAddingTask = false
                    )
                }
                // 清理临时任务
                _pendingMetadata.value = null
                _preparingTaskId = null
                torrentManager.cancelFileSelection(taskId)
            }
        }
    }

    /**
     * 确认文件选择并开始下载
     *
     * @param selectedIndices 选中的文件索引集合
     */
    fun confirmFileSelection(selectedIndices: Set<Int>) {
        val metadata = _pendingMetadata.value
        Timber.d("DownloadViewModel: confirmFileSelection called, selectedIndices=$selectedIndices, metadata=$metadata")
        // 用户已处理，取消超时检测
        metadataTimeoutJob?.cancel()
        metadataTimeoutJob = null
        if (metadata == null) {
            Timber.d("DownloadViewModel: pendingMetadata is null, doing nothing")
            _uiState.update { it.copy(isAddingTask = false) }
            _preparingTaskId = null
            return
        }
        val taskId = metadata.taskId.toLongOrNull()
        if (taskId == null) {
            Timber.e("DownloadViewModel: invalid taskId=${metadata.taskId}")
            _uiState.update { it.copy(isAddingTask = false) }
            _preparingTaskId = null
            return
        }

        Timber.d("DownloadViewModel: 确认文件选择 - taskId=$taskId, 选择 ${selectedIndices.size} 个文件")
        // 只勾选部分文件时，引擎上报的总量仍是整种子大小，
        // 这里把"已选文件大小之和"算出并传给 Manager 校准，否则进度永远到不了 100%
        val selectedTotalBytes = metadata.files
            .filter { it.index in selectedIndices }
            .sumOf { it.size }
        _pendingMetadata.value = null
        _preparingTaskId = null

        viewModelScope.launch {
            torrentManager.confirmFileSelectionAndStart(taskId, selectedIndices, selectedTotalBytes)
            _uiState.update { it.copy(isAddingTask = false) }
        }
    }

    /**
     * 添加下载任务（Torrent 文件）
     *
     * @param torrentFilePath Torrent 文件路径
     */
    fun addTorrentFileDownload(torrentFilePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTask = true, errorMessage = null) }
            try {
                val savePath = resolveDownloadDir()
                // 读取 torrent 文件并交给引擎，属 IO 操作
                val result = withContext(Dispatchers.IO) {
                    torrentManager.createDownloadTaskFromFile(
                        torrentFile = java.io.File(torrentFilePath),
                        savePath = savePath
                    )
                }

                result.fold(
                    onSuccess = { taskId ->
                        Timber.d("DownloadViewModel: Torrent 文件任务创建成功 - $taskId")
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isAddingTask = false) }
            }
        }
    }

    /**
     * 取消文件选择
     * 取消后删除任务（用户不想要这个下载了）
     */
    fun cancelFileSelection() {
        val metadata = _pendingMetadata.value
        Timber.d("DownloadViewModel: cancelFileSelection called, pendingMetadata=$metadata, preparingTaskId=$_preparingTaskId")

        // 用户已处理，取消超时检测
        metadataTimeoutJob?.cancel()
        metadataTimeoutJob = null
        _preparingTaskId = null

        // 标记加载结束
        _uiState.update { it.copy(isAddingTask = false) }
        _pendingMetadata.value = null

        if (metadata != null) {
            // metadata 已到达，使用 metadata 中的 taskId 删除
            val taskIdStr = metadata.taskId
            val taskId = taskIdStr.toLongOrNull()
            Timber.d("DownloadViewModel: 取消文件选择，删除任务 - taskIdStr=$taskIdStr, taskId=$taskId")

            if (taskId != null) {
                viewModelScope.launch {
                    torrentManager.cancelFileSelection(taskId)
                }
            } else {
                viewModelScope.launch {
                    torrentManager.cancelFileSelectionByStringId(taskIdStr)
                }
            }
        } else if (_preparingTaskId != null) {
            // metadata 还没到达，但任务已创建，使用 _preparingTaskId 删除
            val taskId = _preparingTaskId!!
            Timber.d("DownloadViewModel: 取消文件选择（metadata 未到达），删除任务 - taskId=$taskId")
            _preparingTaskId = null
            viewModelScope.launch {
                torrentManager.cancelFileSelection(taskId)
            }
        } else {
            // 没有可以取消的任务
            Timber.w("DownloadViewModel: 没有可取消的任务")
        }
    }

    /**
     * 开始下载（恢复下载）
     *
     * @param task 下载任务
     */
    fun startDownload(task: DownloadTask) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (task.downloadType) {
                    DownloadType.BT -> torrentManager.resumeDownload(task.id)
                    DownloadType.HTTP -> httpDownloadManager.resumeDownload(task.id)
                }
            }
        }
    }

    /**
     * 暂停下载
     *
     * @param task 下载任务
     */
    fun pauseDownload(task: DownloadTask) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (task.downloadType) {
                    DownloadType.BT -> torrentManager.pauseDownload(task.id)
                    DownloadType.HTTP -> httpDownloadManager.pauseDownload(task.id)
                }
            }
        }
    }

    /**
     * 删除下载任务
     *
     * 必须按下载类型分流到各自的 Manager：
     *  - Manager 会先取消正在跑的下载协程 / 断开连接，再删记录
     *  - 只有 Manager 知道真正的落盘位置（BT 在种子子目录里），能正确删文件
     *
     * 之前统一走 DeleteDownloadUseCase（只删数据库记录），导致
     * 1) "删除任务和文件" 从不真正删文件  2) 删除进行中的任务后协程仍在后台写入
     *
     * @param task 下载任务对象
     * @param deleteFiles 是否删除已下载文件
     */
    fun deleteDownload(task: DownloadTask, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                withContext(Dispatchers.IO) {
                    when (task.downloadType) {
                        DownloadType.BT -> torrentManager.deleteDownload(task.id, deleteFiles)
                        DownloadType.HTTP -> httpDownloadManager.deleteDownload(task.id, deleteFiles)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "DownloadViewModel: 删除下载任务失败 - taskId=${task.id}")
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    /**
     * 清理已完成的下载任务
     */
    fun clearCompletedDownloads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                clearCompletedDownloadsUseCase()
            } finally {
                _uiState.update { it.copy(isClearing = false) }
            }
        }
    }

    /**
     * 选择任务（准备播放边下边播）
     */
    fun selectTask(task: DownloadTask) {
        _uiState.update { it.copy(selectedTask = task) }
    }

    /**
     * 清除选中
     */
    fun clearSelection() {
        _uiState.update { it.copy(selectedTask = null) }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 解析可用的下载保存目录（实际逻辑见 [DownloadDirectories.resolve]）
     *
     * 优先级：用户配置 > 公共媒体目录 Movies/PureFrame（有存储权限时，
     * 文件管理器与「本地」页都可见）> 应用专属目录（兜底，一定可写）。
     */
    private suspend fun resolveDownloadDir(): String {
        val configured = runCatching {
            userPreferencesRepository.userPreferencesFlow.first().downloadPath
        }.getOrNull()
        val resolved = DownloadDirectories.resolve(appContext, configured)
        if (!configured.isNullOrBlank() && resolved != configured) {
            Timber.w("DownloadViewModel: 设置的下载目录不可用，已回退 - $configured -> $resolved")
        }
        return resolved
    }

    /**
     * 从磁力链接提取标题（简单实现）
     */
    private fun extractTitleFromMagnet(magnetLink: String): String {
        val dnParam = magnetLink.findParameter("dn")
        return dnParam ?: LocaleManager.getString(appContext, R.string.error_unknown_resource)
    }

    /**
     * 从 URL 参数中提取值
     */
    private fun String.findParameter(key: String): String? {
        val prefix = "$key="
        return this.split("&")
            .find { it.startsWith(prefix) || it.contains(prefix) }
            ?.substringAfter(prefix)
            ?.substringBefore("&")
    }
}

/**
 * Download 页面 UI 状态
 */
data class DownloadUiState(
    val listType: DownloadViewModel.ListType = DownloadViewModel.ListType.ALL,
    val isAddingTask: Boolean = false,
    val isDeleting: Boolean = false,
    val isClearing: Boolean = false,
    val selectedTask: DownloadTask? = null,
    val errorMessage: String? = null
)
