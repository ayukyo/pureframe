package com.pureframe.player.ui.screens.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 本地视频页面 ViewModel
 * 
 * 负责：
 * - 扫描本地视频文件
 * - 提供视频列表数据
 * - 管理文件夹分类
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    // TODO: 注入 VideoRepository
    // private val videoRepository: VideoRepository
) : ViewModel() {
    
    // TODO: 实现视频列表
    // val videos: StateFlow<List<VideoItem>> = videoRepository.getAllVideos()
    //     .stateIn(
    //         scope = viewModelScope,
    //         started = SharingStarted.WhileSubscribed(5000),
    //         initialValue = emptyList()
    //     )
    
    // TODO: 实现扫描功能
    // fun scanVideos() {
    //     viewModelScope.launch {
    //         videoRepository.scanLocalVideos()
    //     }
    // }
    
    // TODO: 实现文件夹分类
    // val folders: StateFlow<List<String>> = ...
}