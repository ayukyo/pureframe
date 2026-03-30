package com.pureframe.player.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 设置页面 ViewModel
 * 
 * 负责：
 * - 提供用户偏好设置
 * - 保存设置变更
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    // TODO: 注入 SettingsRepository
    // private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    // TODO: 实现设置数据
    // val settings: StateFlow<UserSettings> = settingsRepository.getSettings()
    //     .stateIn(
    //         scope = viewModelScope,
    //         started = SharingStarted.WhileSubscribed(5000),
    //         initialValue = UserSettings()
    //     )
    
    // TODO: 实现设置保存
    // fun setDownloadPath(path: String) {
    //     viewModelScope.launch {
    //         settingsRepository.setDownloadPath(path)
    //     }
    // }
    
    // fun setPlaybackSpeed(speed: Float) {
    //     viewModelScope.launch {
    //         settingsRepository.setDefaultPlaybackSpeed(speed)
    //     }
    // }
    
    // fun setStreamableThreshold(threshold: Float) {
    //     viewModelScope.launch {
    //         settingsRepository.setStreamableThreshold(threshold)
    //     }
    // }
}