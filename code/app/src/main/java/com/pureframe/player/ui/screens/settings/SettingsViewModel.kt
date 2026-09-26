package com.pureframe.player.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.data.preferences.UserPreferences
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.data.preferences.AppLanguage
import com.pureframe.player.data.preferences.DownloadQuality
import com.pureframe.player.data.preferences.DecoderType
import com.pureframe.player.data.preferences.ThemeMode
import com.pureframe.player.data.preferences.SortBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面 ViewModel
 *
 * 负责：
 * - 提供用户偏好设置状态
 * - 保存设置变更
 * - 监听设置变化
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /**
     * 用户偏好设置状态
     */
    val userPreferences = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    /**
     * 更新自动播放设置
     */
    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAutoPlay(enabled)
        }
    }
    
    /**
     * 更新循环播放设置
     */
    fun setLoopPlay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateLoopPlay(enabled)
        }
    }
    
    /**
     * 更新默认播放速度
     */
    fun setDefaultPlaySpeed(speed: Float) {
        viewModelScope.launch {
            userPreferencesRepository.updateDefaultPlaySpeed(speed)
        }
    }
    
    /**
     * 更新是否记住播放速度
     */
    fun setRememberPlaySpeed(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateRememberPlaySpeed(enabled)
        }
    }
    
    /**
     * 更新是否显示字幕
     */
    fun setShowSubtitle(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateShowSubtitle(enabled)
        }
    }
    
    /**
     * 更新最大并行下载数
     */
    fun setMaxConcurrentDownloads(count: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateMaxConcurrentDownloads(count)
        }
    }
    
    /**
     * 更新下载路径
     */
    fun setDownloadPath(path: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateDownloadPath(path)
        }
    }
    
    /**
     * 更新 WiFi 自动下载
     */
    fun setAutoDownloadOnWifi(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAutoDownloadOnWifi(enabled)
        }
    }
    
    /**
     * 更新下载质量
     */
    fun setDownloadQuality(quality: DownloadQuality) {
        viewModelScope.launch {
            userPreferencesRepository.updateDownloadQuality(quality)
        }
    }
    
    /**
     * 更新主题模式
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.updateThemeMode(mode)
        }
    }

    /**
     * 更新应用语言
     */
    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            userPreferencesRepository.updateAppLanguage(language)
        }
    }

    /**
     * 更新排序方式
     */
    fun setSortBy(sortBy: SortBy) {
        viewModelScope.launch {
            userPreferencesRepository.updateSortBy(sortBy)
        }
    }
    
    /**
     * 更新是否保持屏幕常亮
     */
    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateKeepScreenOn(enabled)
        }
    }
    
    /**
     * 更新亮度手势控制
     */
    fun setBrightnessGesture(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateBrightnessGesture(enabled)
        }
    }
    
    /**
     * 更新音量手势控制
     */
    fun setVolumeGesture(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateVolumeGesture(enabled)
        }
    }

    /**
     * 更新解码器类型
     */
    fun setDecoderType(type: DecoderType) {
        viewModelScope.launch {
            userPreferencesRepository.updateDecoderType(type)
        }
    }

    /**
     * 清除所有偏好设置
     */
    fun clearAllPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.clearAllPreferences()
        }
    }
}