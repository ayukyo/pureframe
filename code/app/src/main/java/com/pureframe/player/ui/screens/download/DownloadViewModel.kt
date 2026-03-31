package com.pureframe.player.ui.screens.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.usecase.download.GetAllDownloadsUseCase
import com.pureframe.player.domain.usecase.download.GetActiveDownloadsUseCase
import com.pureframe.player.domain.usecase.download.GetDownloadsByStatusUseCase
import com.pureframe.player.domain.usecase.download.CreateDownloadTaskUseCase
import com.pureframe.player.domain.usecase.download.PauseDownloadUseCase
import com.pureframe.player.domain.usecase.download.StartDownloadUseCase
import com.pureframe.player.domain.usecase.download.DeleteDownloadUseCase
import com.pureframe.player.domain.usecase.download.ClearCompletedDownloadsUseCase
import com.pureframe.player.domain.usecase.download.GetActiveDownloadCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val getActiveDownloadCountUseCase: GetActiveDownloadCountUseCase
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
    val activeDownloadCount: StateFlow<Int> = getActiveDownloadCountUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    /**
     * 切换列表类型
     */
    fun setListType(listType: ListType) {
        _uiState.update { it.copy(listType = listType) }
    }
    
    /**
     * 添加下载任务（磁力链接）
     * 
     * @param magnetLink 磁力链接
     * @param title 任务标题（可选）
     * @param fileName 目标文件名（可选）
     */
    fun addDownloadTask(
        magnetLink: String, 
        title: String? = null, 
        fileName: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTask = true, errorMessage = null) }
            try {
                val extractedTitle = title ?: extractTitleFromMagnet(magnetLink)
                val taskId = createDownloadTaskUseCase(
                    CreateDownloadTaskUseCase.Params(
                        url = magnetLink,
                        title = extractedTitle,
                        fileName = fileName ?: "$extractedTitle.mp4",
                        savePath = "/storage/emulated/0/PureFrame/downloads"
                    )
                )
                // 创建成功，开始下载
                startDownload(taskId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isAddingTask = false) }
            }
        }
    }
    
    /**
     * 开始下载
     */
    fun startDownload(taskId: Long) {
        viewModelScope.launch {
            startDownloadUseCase(taskId)
        }
    }
    
    /**
     * 暂停下载
     */
    fun pauseDownload(taskId: Long) {
        viewModelScope.launch {
            pauseDownloadUseCase(taskId)
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
        // magnet:?xt=urn:btih:xxx&dn=标题
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