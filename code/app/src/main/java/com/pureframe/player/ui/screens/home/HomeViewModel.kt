package com.pureframe.player.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.data.preferences.SortBy
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.video.GetAllVideosUseCase
import com.pureframe.player.domain.usecase.video.GetFavoriteVideosUseCase
import com.pureframe.player.domain.usecase.video.GetRecentVideosUseCase
import com.pureframe.player.domain.usecase.video.ScanLocalVideosUseCase
import com.pureframe.player.domain.usecase.video.SearchVideosUseCase
import com.pureframe.player.domain.usecase.video.ToggleFavoriteUseCase
import com.pureframe.player.domain.usecase.video.DeleteVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 本地视频页面 ViewModel
 *
 * 负责：
 * - 提供视频列表数据（全部、收藏、最近）
 * - 搜索视频
 * - 切换收藏状态
 * - 删除视频
 * - 管理列表过滤和排序
 * - 扫描本地视频
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    getAllVideosUseCase: GetAllVideosUseCase,
    getFavoriteVideosUseCase: GetFavoriteVideosUseCase,
    getRecentVideosUseCase: GetRecentVideosUseCase,
    private val searchVideosUseCase: SearchVideosUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deleteVideoUseCase: DeleteVideoUseCase,
    private val scanLocalVideosUseCase: ScanLocalVideosUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // 列表类型
    enum class ListType {
        ALL,      // 全部视频
        FAVORITE, // 收藏视频
        RECENT    // 最近播放
    }

    // 当前排序类型（从缓存加载）
    private val _currentSort = MutableStateFlow(SortBy.DATE_DESC)
    val currentSort: StateFlow<SortBy> = _currentSort.asStateFlow()

    init {
        // 从缓存加载排序方式
        viewModelScope.launch {
            _currentSort.value = userPreferencesRepository.userPreferencesFlow.first().sortBy
        }
    }

    // 排序后的全部视频
    val allVideos: StateFlow<List<Video>> = combine(
        getAllVideosUseCase(),
        _currentSort
    ) { videos, sort -> sortVideos(videos, sort) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 排序后的收藏视频
    val favoriteVideos: StateFlow<List<Video>> = combine(
        getFavoriteVideosUseCase(),
        _currentSort
    ) { videos, sort -> sortVideos(videos, sort) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 排序后的最近视频
    val recentVideos: StateFlow<List<Video>> = combine(
        getRecentVideosUseCase(20),
        _currentSort
    ) { videos, sort -> sortVideos(videos, sort) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 排序视频列表
     */
    private fun sortVideos(videos: List<Video>, sortBy: SortBy): List<Video> {
        return when (sortBy) {
            SortBy.NAME_ASC -> videos.sortedBy { it.title.lowercase() }
            SortBy.NAME_DESC -> videos.sortedByDescending { it.title.lowercase() }
            SortBy.DATE_ASC -> videos.sortedBy { it.createdAt }
            SortBy.DATE_DESC -> videos.sortedByDescending { it.createdAt }
            SortBy.SIZE_ASC -> videos.sortedBy { it.fileSize }
            SortBy.SIZE_DESC -> videos.sortedByDescending { it.fileSize }
            SortBy.DURATION_ASC -> videos.sortedBy { it.duration }
            SortBy.DURATION_DESC -> videos.sortedByDescending { it.duration }
        }
    }

    /**
     * 切换排序方式
     */
    fun toggleSort() {
        val nextSort = when (_currentSort.value) {
            SortBy.DATE_DESC -> SortBy.SIZE_DESC
            SortBy.SIZE_DESC -> SortBy.DURATION_DESC
            SortBy.DURATION_DESC -> SortBy.DATE_DESC
            else -> SortBy.DATE_DESC
        }
        _currentSort.value = nextSort
        // 缓存排序方式
        viewModelScope.launch {
            userPreferencesRepository.updateSortBy(nextSort)
        }
    }
    
    // UI 状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // 扫描状态
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    sealed class ScanState {
        object Idle : ScanState()
        object Scanning : ScanState()
        data class Success(val count: Int) : ScanState()
        data class Error(val message: String) : ScanState()
    }

    // 当前显示的视频列表（根据 ListType）
    val currentVideos: StateFlow<List<Video>> = MutableStateFlow(emptyList<Video>())
        .apply {
            viewModelScope.launch {
                _uiState.collect { state ->
                    when (state.listType) {
                        ListType.ALL -> allVideos.collect { emit(it) }
                        ListType.FAVORITE -> favoriteVideos.collect { emit(it) }
                        ListType.RECENT -> recentVideos.collect { emit(it) }
                    }
                }
            }
        }.asStateFlow()
    
    // 搜索结果
    private val _searchResults = MutableStateFlow<List<Video>>(emptyList())
    val searchResults: StateFlow<List<Video>> = _searchResults.asStateFlow()
    
    /**
     * 切换列表类型
     */
    fun setListType(listType: ListType) {
        _uiState.update { it.copy(listType = listType) }
    }
    
    /**
     * 搜索视频
     */
    fun searchVideos(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchQuery = query) }
            try {
                searchVideosUseCase(query).collect { results ->
                    _searchResults.value = results
                }
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }
    
    /**
     * 清除搜索
     */
    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", isSearching = false) }
        _searchResults.value = emptyList()
    }
    
    /**
     * 切换收藏状态
     */
    fun toggleFavorite(videoId: Long, currentFavorite: Boolean) {
        viewModelScope.launch {
            toggleFavoriteUseCase(ToggleFavoriteUseCase.Params(videoId, !currentFavorite))
        }
    }
    
    /**
     * 删除视频
     *
     * @param videoId 视频 ID
     * @param deleteFile 是否同时删除磁盘上的视频文件（默认仅移除记录）
     */
    fun deleteVideo(videoId: Long, deleteFile: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                // 需要删文件时先取出路径（删完记录就查不到了）
                val filePath = if (deleteFile) {
                    getAllVideosOnce(videoId)?.filePath
                } else null
                deleteVideoUseCase(videoId)
                // 文件删除放 IO 线程，失败不影响记录移除
                if (filePath != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val f = java.io.File(filePath)
                        if (f.exists()) f.delete()
                    }
                }
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    /** 按 ID 查询单个视频（用于删除前取文件路径） */
    private suspend fun getAllVideosOnce(videoId: Long): Video? {
        return allVideos.value.firstOrNull { it.id == videoId }
            ?: favoriteVideos.value.firstOrNull { it.id == videoId }
            ?: recentVideos.value.firstOrNull { it.id == videoId }
    }
    
    /**
     * 选择视频（准备播放）
     */
    fun selectVideo(video: Video) {
        _uiState.update { it.copy(selectedVideo = video) }
    }
    
    /**
     * 清除选中
     */
    fun clearSelection() {
        _uiState.update { it.copy(selectedVideo = null) }
    }

    /**
     * 扫描本地视频
     */
    fun scanVideos() {
        if (_scanState.value == ScanState.Scanning) {
            return
        }

        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            try {
                val result = scanLocalVideosUseCase(forceRefresh = false)
                result.fold(
                    onSuccess = { count ->
                        _scanState.value = ScanState.Success(count)
                        // 3秒后恢复 Idle
                        kotlinx.coroutines.delay(3000)
                        if (_scanState.value is ScanState.Success) {
                            _scanState.value = ScanState.Idle
                        }
                    },
                    onFailure = { e ->
                        _scanState.value = ScanState.Error(e.message ?: "扫描失败")
                        kotlinx.coroutines.delay(3000)
                        if (_scanState.value is ScanState.Error) {
                            _scanState.value = ScanState.Idle
                        }
                    }
                )
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.message ?: "扫描失败")
            }
        }
    }

    /**
     * 重置扫描状态
     */
    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }
}

/**
 * Home 页面 UI 状态
 */
data class HomeUiState(
    val listType: HomeViewModel.ListType = HomeViewModel.ListType.ALL,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isDeleting: Boolean = false,
    val selectedVideo: Video? = null,
    val errorMessage: String? = null
)