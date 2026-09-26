package com.pureframe.player.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.media3.common.util.UnstableApi
import com.pureframe.player.data.preferences.ThemeMode
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.player.PlayerManager
import com.pureframe.player.ui.navigation.NavigationState
import com.pureframe.player.ui.theme.PureFrameTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 主 Activity
 *
 * 使用 Jetpack Compose 构建单 Activity 架构
 * 所有页面通过 Navigation Compose 管理
 */
@AndroidEntryPoint
@UnstableApi
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationState: NavigationState

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    // 延迟初始化：PlayerManager 构造时会同步读取 DataStore（runBlocking）并构建 ExoPlayer，
    // 若在 onCreate 急切注入会把这条耗时的播放器链路拉进冷启动关键路径。
    // 用 Lazy 包装后只有真正用到（首次进入播放页 / Home 键触发 PiP）才会创建。
    @Inject
    lateinit var playerManager: dagger.Lazy<PlayerManager>

    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge：内容绘制到系统栏后面，由 Compose 统一消费 insets。
        // 避免播放页控制栏在全屏/非全屏切换时与系统导航条重复避让（全屏按钮被压扁的问题）
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 注册到单例持有者：PiP 小窗按钮状态刷新需要通过它找到当前 Activity
        com.pureframe.player.player.PipCurrentActivityHolder.currentActivity = this
        setContent {
            // 主题模式跟随设置页偏好：浅色/深色/跟随系统
            val themeMode by userPreferencesRepository.userPreferencesFlow
                .collectAsState(initial = null)
            val darkTheme = when (themeMode?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM, null -> androidx.compose.foundation.isSystemInDarkTheme()
                else -> true
            }
            PureFrameTheme(darkTheme = darkTheme) {
                MainScreen(navigationState = navigationState)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 用户按 Home 键离开时：若正在播放视频，自动进入画中画小窗继续播放
        if (playerManager.get().isPlaying.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(
                    com.pureframe.player.player.PiPHelper.buildParams(this, playerManager.get().getPlayer())
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (com.pureframe.player.player.PipCurrentActivityHolder.currentActivity === this) {
            com.pureframe.player.player.PipCurrentActivityHolder.currentActivity = null
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // 同步到 Compose 层：PlayerScreen 据此隐藏控制栏与手势
        navigationState.setPipMode(isInPictureInPictureMode)
    }
}
