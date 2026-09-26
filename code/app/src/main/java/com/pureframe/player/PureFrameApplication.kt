package com.pureframe.player

import android.app.Application
import android.util.Log
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.i18n.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * 纯帧应用
 *
 * 纯粹观影，只留帧影
 */
@HiltAndroidApp
class PureFrameApplication : Application() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()

        // 初始化 Timber 日志
        Timber.plant(Timber.DebugTree())

        // 语言偏好必须在第一个 Activity attach 之前就位：
        // attachBaseContext 里会用它包装 Context，而那早于任何 Activity 的 onCreate。
        // 这里同步读一次 DataStore（与 PlayerManager 读解码器偏好是同一模式）。
        runBlocking {
            LocaleManager.update(userPreferencesRepository.userPreferencesFlow.first().appLanguage)
        }

        Log.i("PureFrameApp", "=== Application onCreate 开始 ===")
        Timber.d("PureFrame Application initialized")
        Log.i("PureFrameApp", "=== Application 初始化完成 ===")
    }
}
