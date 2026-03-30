package com.pureframe.player.ui.screens.download

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 下载页面 ViewModel
 * 
 * 负责：
 * - 提供下载任务列表
 * - 添加新下载任务
 * - 控制下载进度（暂停/恢复/删除）
 * - 判断边下边播可行性
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    // TODO: 注入 DownloadRepository
    // private val downloadRepository: DownloadRepository
) : ViewModel() {
    
    // TODO: 实现下载任务列表
    // val downloadTasks: StateFlow<List<DownloadTask>> = downloadRepository.getAllTasks()
    //     .stateIn(
    //         scope = viewModelScope,
    //         started = SharingStarted.WhileSubscribed(5000),
    //         initialValue = emptyList()
    //     )
    
    // TODO: 实现添加下载功能
    // fun addDownloadTask(magnetLink: String) {
    //     viewModelScope.launch {
    //         downloadRepository.createTask(magnetLink)
    //     }
    // }
    
    // TODO: 实现暂停/恢复功能
    // fun pauseTask(taskId: String) {
    //     viewModelScope.launch {
    //         downloadRepository.pauseTask(taskId)
    //     }
    // }
    
    // fun resumeTask(taskId: String) {
    //     viewModelScope.launch {
    //         downloadRepository.resumeTask(taskId)
    //     }
    // }
    
    // TODO: 实现删除功能
    // fun deleteTask(taskId: String, deleteFiles: Boolean = false) {
    //     viewModelScope.launch {
    //         downloadRepository.deleteTask(taskId, deleteFiles)
    //     }
    // }
}