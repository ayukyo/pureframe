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

        // 日志只在 debug 构建植入：release 包不留任何可被 adb logcat 读取的
        // 应用日志（内部路径、播放逻辑、BT 任务状态等均不外泄）。
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 语言偏好必须在第一个 Activity attach 之前就位：
        // attachBaseContext 里会用它包装 Context，而那早于任何 Activity 的 onCreate。
        // 这里同步读一次 DataStore（与 PlayerManager 读解码器偏好是同一模式）。
        runBlocking {
            LocaleManager.update(userPreferencesRepository.userPreferencesFlow.first().appLanguage)
        }

        if (BuildConfig.DEBUG) {
            Log.i("PureFrameApp", "=== Application onCreate 开始 ===")
            Timber.d("PureFrame Application initialized")
            Log.i("PureFrameApp", "=== Application 初始化完成 ===")
        }

        // 防二次打包：release 启动时校验签名，被改包重签名的 APK 直接拒绝运行。
        // debug 构建自动跳过（BuildConfig.ORIGINAL_SIGNING_SHA256 默认 "skip"）。
        com.pureframe.player.security.SignatureGuard.checkAndKillIfTampered(this)
    }
}
