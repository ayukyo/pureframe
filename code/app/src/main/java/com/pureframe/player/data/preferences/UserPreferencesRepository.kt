package com.pureframe.player.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户偏好设置 Repository
 * 
 * 负责读写用户偏好设置，使用 DataStore 进行持久化存储
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * 获取用户偏好设置 Flow
     * 
     * 监听偏好设置变化，自动更新 UI
     */
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferences(
            // 播放器设置
            autoPlay = preferences[PreferencesKeys.AUTO_PLAY] ?: false,
            loopPlay = preferences[PreferencesKeys.LOOP_PLAY] ?: false,
            defaultPlaySpeed = preferences[PreferencesKeys.DEFAULT_PLAY_SPEED] ?: 1.0f,
            rememberPlaySpeed = preferences[PreferencesKeys.REMEMBER_PLAY_SPEED] ?: true,
            showSubtitle = preferences[PreferencesKeys.SHOW_SUBTITLE] ?: true,
            decoderType = preferences[PreferencesKeys.DECODER_TYPE]?.let {
                DecoderType.valueOf(it)
            } ?: DecoderType.AUTO,
            
            // 下载设置
            maxConcurrentDownloads = preferences[PreferencesKeys.MAX_CONCURRENT_DOWNLOADS] ?: 3,
            downloadPath = preferences[PreferencesKeys.DOWNLOAD_PATH] ?: "",
            autoDownloadOnWifi = preferences[PreferencesKeys.AUTO_DOWNLOAD_ON_WIFI] ?: true,
            downloadQuality = preferences[PreferencesKeys.DOWNLOAD_QUALITY]?.let { 
                DownloadQuality.valueOf(it) 
            } ?: DownloadQuality.HIGH,
            
            // 界面设置
            themeMode = preferences[PreferencesKeys.THEME_MODE]?.let { 
                ThemeMode.valueOf(it) 
            } ?: ThemeMode.SYSTEM,
            fullScreenMode = preferences[PreferencesKeys.FULL_SCREEN_MODE] ?: false,
            showThumbnail = preferences[PreferencesKeys.SHOW_THUMBNAIL] ?: true,
            sortBy = preferences[PreferencesKeys.SORT_BY]?.let { 
                SortBy.valueOf(it) 
            } ?: SortBy.DATE_DESC,
            
            // 其他设置
            keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: true,
            brightnessGesture = preferences[PreferencesKeys.BRIGHTNESS_GESTURE] ?: true,
            volumeGesture = preferences[PreferencesKeys.VOLUME_GESTURE] ?: true
        )
    }
    
    /**
     * 更新自动播放设置
     */
    suspend fun updateAutoPlay(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_PLAY] = enabled
        }
    }
    
    /**
     * 更新循环播放设置
     */
    suspend fun updateLoopPlay(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOOP_PLAY] = enabled
        }
    }
    
    /**
     * 更新默认播放速度
     */
    suspend fun updateDefaultPlaySpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_PLAY_SPEED] = speed
        }
    }
    
    /**
     * 更新是否记住播放速度
     */
    suspend fun updateRememberPlaySpeed(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMEMBER_PLAY_SPEED] = enabled
        }
    }
    
    /**
     * 更新是否显示字幕
     */
    suspend fun updateShowSubtitle(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_SUBTITLE] = enabled
        }
    }

    /**
     * 更新解码器类型
     */
    suspend fun updateDecoderType(type: DecoderType) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DECODER_TYPE] = type.name
        }
    }

    /**
     * 更新最大并行下载数
     */
    suspend fun updateMaxConcurrentDownloads(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5)
        }
    }
    
    /**
     * 更新下载路径
     */
    suspend fun updateDownloadPath(path: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_PATH] = path
        }
    }
    
    /**
     * 更新 WiFi 自动下载
     */
    suspend fun updateAutoDownloadOnWifi(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_DOWNLOAD_ON_WIFI] = enabled
        }
    }
    
    /**
     * 更新下载质量
     */
    suspend fun updateDownloadQuality(quality: DownloadQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_QUALITY] = quality.name
        }
    }
    
    /**
     * 更新主题模式
     */
    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }
    
    /**
     * 更新全屏模式
     */
    suspend fun updateFullScreenMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_SCREEN_MODE] = enabled
        }
    }
    
    /**
     * 更新是否显示缩略图
     */
    suspend fun updateShowThumbnail(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_THUMBNAIL] = enabled
        }
    }
    
    /**
     * 更新排序方式
     */
    suspend fun updateSortBy(sortBy: SortBy) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_BY] = sortBy.name
        }
    }
    
    /**
     * 更新是否保持屏幕常亮
     */
    suspend fun updateKeepScreenOn(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }
    
    /**
     * 更新亮度手势控制
     */
    suspend fun updateBrightnessGesture(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BRIGHTNESS_GESTURE] = enabled
        }
    }
    
    /**
     * 更新音量手势控制
     */
    suspend fun updateVolumeGesture(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VOLUME_GESTURE] = enabled
        }
    }
    
    /**
     * 批量更新用户偏好设置
     */
    suspend fun updateUserPreferences(preferences: UserPreferences) {
        dataStore.edit { prefs ->
            // 播放器设置
            prefs[PreferencesKeys.AUTO_PLAY] = preferences.autoPlay
            prefs[PreferencesKeys.LOOP_PLAY] = preferences.loopPlay
            prefs[PreferencesKeys.DEFAULT_PLAY_SPEED] = preferences.defaultPlaySpeed
            prefs[PreferencesKeys.REMEMBER_PLAY_SPEED] = preferences.rememberPlaySpeed
            prefs[PreferencesKeys.SHOW_SUBTITLE] = preferences.showSubtitle
            prefs[PreferencesKeys.DECODER_TYPE] = preferences.decoderType.name
            
            // 下载设置
            prefs[PreferencesKeys.MAX_CONCURRENT_DOWNLOADS] = preferences.maxConcurrentDownloads
            prefs[PreferencesKeys.DOWNLOAD_PATH] = preferences.downloadPath
            prefs[PreferencesKeys.AUTO_DOWNLOAD_ON_WIFI] = preferences.autoDownloadOnWifi
            prefs[PreferencesKeys.DOWNLOAD_QUALITY] = preferences.downloadQuality.name
            
            // 界面设置
            prefs[PreferencesKeys.THEME_MODE] = preferences.themeMode.name
            prefs[PreferencesKeys.FULL_SCREEN_MODE] = preferences.fullScreenMode
            prefs[PreferencesKeys.SHOW_THUMBNAIL] = preferences.showThumbnail
            prefs[PreferencesKeys.SORT_BY] = preferences.sortBy.name
            
            // 其他设置
            prefs[PreferencesKeys.KEEP_SCREEN_ON] = preferences.keepScreenOn
            prefs[PreferencesKeys.BRIGHTNESS_GESTURE] = preferences.brightnessGesture
            prefs[PreferencesKeys.VOLUME_GESTURE] = preferences.volumeGesture
        }
    }
    
    /**
     * 清除所有偏好设置
     */
    suspend fun clearAllPreferences() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}