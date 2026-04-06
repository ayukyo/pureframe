package com.pureframe.player.ui.screens.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val httpDownloadManager: HttpDownloadManager
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
                val savePath = "/storage/emulated/0/PureFrame/downloads"
                Timber.d("DownloadViewModel: 开始准备下载任务 - title=$extractedTitle")

                // 使用 torrentManager.prepareMagnetLink 仅添加到引擎获取 metadata
                // 不创建正式任务，不显示在列表中
                val result = torrentManager.prepareMagnetLink(
                    magnetLink = url,
                    savePath = savePath,
                    name = extractedTitle
                )

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
                val savePath = "/storage/emulated/0/PureFrame/downloads"
                val result = httpDownloadManager.createDownloadTask(
                    url = url,
                    savePath = savePath,
                    name = title
                )

                result.fold(
                    onSuccess = { taskId ->
                        Timber.d("DownloadViewModel: HTTP 下载任务创建成功 - taskId=$taskId")
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
     * 启动 metadata 获取超时检测
     */
    @Suppress("UNUSED_PARAMETER")
    private fun startMetadataTimeout(taskId: Long, magnetLink: String) {
        viewModelScope.launch {
            delay(30_000) // 30秒超时
            // 检查是否仍然没有收到 metadata
            val pending = _pendingMetadata.value
            if (pending == null || pending.taskId.toLongOrNull() != taskId) {
                Timber.e("DownloadViewModel: metadata 获取超时 - taskId=$taskId, magnetLink=$magnetLink")
                _uiState.update {
                    it.copy(
                        errorMessage = "无法获取资源信息，请检查磁力链接是否有效",
                        isAddingTask = false
                    )
                }
                // 清理临时任务
                _pendingMetadata.value = null
                if (_preparingTaskId == taskId) {
                    _preparingTaskId = null
                    torrentManager.cancelFileSelection(taskId)
                }
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
        _pendingMetadata.value = null
        _preparingTaskId = null

        viewModelScope.launch {
            torrentManager.confirmFileSelectionAndStart(taskId, selectedIndices)
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
                val savePath = "/storage/emulated/0/PureFrame/downloads"
                val result = torrentManager.createDownloadTaskFromFile(
                    torrentFile = java.io.File(torrentFilePath),
                    savePath = savePath
                )

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
            when (task.downloadType) {
                DownloadType.BT -> torrentManager.resumeDownload(task.id)
                DownloadType.HTTP -> httpDownloadManager.resumeDownload(task.id)
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
            when (task.downloadType) {
                DownloadType.BT -> torrentManager.pauseDownload(task.id)
                DownloadType.HTTP -> httpDownloadManager.pauseDownload(task.id)
            }
        }
    }

    /**
     * 删除下载任务
     *
     * @param task 下载任务对象
     * @param deleteFiles 是否删除已下载文件
     */
    fun deleteDownload(task: DownloadTask, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                deleteDownloadUseCase(DeleteDownloadUseCase.Params(task, deleteFiles))
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
     * 从磁力链接提取标题（简单实现）
     */
    private fun extractTitleFromMagnet(magnetLink: String): String {
        val dnParam = magnetLink.findParameter("dn")
        return dnParam ?: "未知资源"
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
