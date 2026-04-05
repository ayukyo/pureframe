package com.pureframe.player.ui.screens.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
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
    private val torrentManager: TorrentManager
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
     * 添加下载任务（磁力链接）
     * 添加后会等待获取 metadata，然后弹出文件选择对话框
     *
     * @param magnetLink 磁力链接
     * @param title 任务标题（可选）
     */
    fun addDownloadTask(
        magnetLink: String,
        title: String? = null
    ) {
        Timber.d("DownloadViewModel.addDownloadTask 被调用 - magnetLink=$magnetLink")
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTask = true, errorMessage = null) }
            try {
                val extractedTitle = title ?: extractTitleFromMagnet(magnetLink)
                val savePath = "/storage/emulated/0/PureFrame/downloads"
                Timber.d("DownloadViewModel: 开始创建下载任务 - title=$extractedTitle")

                // 使用 torrentManager 创建任务，它会保存到数据库并添加到引擎
                val result = torrentManager.createDownloadTask(
                    magnetLink = magnetLink,
                    savePath = savePath,
                    name = extractedTitle
                )

                result.fold(
                    onSuccess = { taskId ->
                        Timber.d("DownloadViewModel: 下载任务创建成功 - taskId=$taskId，等待 metadata...")
                        // 启动 metadata 超时检测（30秒）
                        startMetadataTimeout(taskId, magnetLink)
                    },
                    onFailure = { e ->
                        Timber.e(e, "DownloadViewModel: 下载任务创建失败")
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "DownloadViewModel: 添加下载任务失败")
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isAddingTask = false) }
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
                _uiState.update { it.copy(errorMessage = "无法获取资源信息，请检查磁力链接是否有效") }
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
            return
        }
        val taskId = metadata.taskId.toLongOrNull()
        if (taskId == null) {
            Timber.e("DownloadViewModel: invalid taskId=${metadata.taskId}")
            return
        }

        Timber.d("DownloadViewModel: 确认文件选择 - taskId=$taskId, 选择 ${selectedIndices.size} 个文件")
        _pendingMetadata.value = null

        viewModelScope.launch {
            torrentManager.selectFilesAndStart(taskId, selectedIndices)
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
        Timber.d("DownloadViewModel: cancelFileSelection called, pendingMetadata=$metadata")
        if (metadata == null) {
            Timber.d("DownloadViewModel: pendingMetadata is null, doing nothing")
            return
        }

        val taskId = metadata.taskId.toLongOrNull()
        Timber.d("DownloadViewModel: 取消文件选择，删除任务 - taskId=${metadata.taskId}")
        _pendingMetadata.value = null

        // 删除任务（不删除文件，因为还没开始下载）
        if (taskId != null) {
            viewModelScope.launch {
                try {
                    torrentManager.deleteDownload(taskId, deleteFiles = false)
                    Timber.d("DownloadViewModel: 任务已删除 - taskId=$taskId")
                } catch (e: Exception) {
                    Timber.e(e, "DownloadViewModel: 删除任务失败 - taskId=$taskId")
                }
            }
        }
    }

    /**
     * 开始下载（恢复下载）
     */
    fun startDownload(taskId: Long) {
        viewModelScope.launch {
            torrentManager.resumeDownload(taskId)
        }
    }

    /**
     * 暂停下载
     */
    fun pauseDownload(taskId: Long) {
        viewModelScope.launch {
            torrentManager.pauseDownload(taskId)
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
