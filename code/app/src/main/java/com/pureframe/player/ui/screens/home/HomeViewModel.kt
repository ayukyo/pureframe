package com.pureframe.player.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.video.GetAllVideosUseCase
import com.pureframe.player.domain.usecase.video.GetFavoriteVideosUseCase
import com.pureframe.player.domain.usecase.video.GetRecentVideosUseCase
import com.pureframe.player.domain.usecase.video.SearchVideosUseCase
import com.pureframe.player.domain.usecase.video.ToggleFavoriteUseCase
import com.pureframe.player.domain.usecase.video.DeleteVideoUseCase
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
 * 本地视频页面 ViewModel
 * 
 * 负责：
 * - 提供视频列表数据（全部、收藏、最近）
 * - 搜索视频
 * - 切换收藏状态
 * - 删除视频
 * - 管理列表过滤和排序
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    getAllVideosUseCase: GetAllVideosUseCase,
    getFavoriteVideosUseCase: GetFavoriteVideosUseCase,
    getRecentVideosUseCase: GetRecentVideosUseCase,
    private val searchVideosUseCase: SearchVideosUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deleteVideoUseCase: DeleteVideoUseCase
) : ViewModel() {
    
    // 列表类型
    enum class ListType {
        ALL,      // 全部视频
        FAVORITE, // 收藏视频
        RECENT    // 最近播放
    }
    
    // UI 状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // 全部视频列表
    val allVideos: StateFlow<List<Video>> = getAllVideosUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 收藏视频列表
    val favoriteVideos: StateFlow<List<Video>> = getFavoriteVideosUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 最近播放视频列表
    val recentVideos: StateFlow<List<Video>> = getRecentVideosUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
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
    fun toggleFavorite(videoId: Long) {
        viewModelScope.launch {
            toggleFavoriteUseCase(videoId)
        }
    }
    
    /**
     * 删除视频
     */
    fun deleteVideo(videoId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                deleteVideoUseCase(videoId)
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
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