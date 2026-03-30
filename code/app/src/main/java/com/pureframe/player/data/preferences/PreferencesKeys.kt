package com.pureframe.player.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore Preferences Keys
 * 
 * 定义所有偏好设置的 Key，用于读写 DataStore
 */
object PreferencesKeys {
    // 播放器设置
    val AUTO_PLAY = booleanPreferencesKey("auto_play")
    val LOOP_PLAY = booleanPreferencesKey("loop_play")
    val DEFAULT_PLAY_SPEED = floatPreferencesKey("default_play_speed")
    val REMEMBER_PLAY_SPEED = booleanPreferencesKey("remember_play_speed")
    val SHOW_SUBTITLE = booleanPreferencesKey("show_subtitle")
    
    // 下载设置
    val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
    val DOWNLOAD_PATH = stringPreferencesKey("download_path")
    val AUTO_DOWNLOAD_ON_WIFI = booleanPreferencesKey("auto_download_on_wifi")
    val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
    
    // 界面设置
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val FULL_SCREEN_MODE = booleanPreferencesKey("full_screen_mode")
    val SHOW_THUMBNAIL = booleanPreferencesKey("show_thumbnail")
    val SORT_BY = stringPreferencesKey("sort_by")
    
    // 其他设置
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness_gesture")
    val VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
}