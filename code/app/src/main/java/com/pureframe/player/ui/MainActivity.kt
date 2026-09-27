package com.pureframe.player.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.pureframe.player.data.preferences.ThemeMode
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.i18n.LocaleManager
import com.pureframe.player.player.PlayerManager
import com.pureframe.player.ui.navigation.NavigationState
import com.pureframe.player.ui.theme.PureFrameTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
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

    /**
     * 应用语言在这里落地（而不是在 setContent 里包装 LocalContext）：
     *
     * - [LocaleManager.wrapContext] 返回的是 createConfigurationContext 的产物，
     *   属于 ContextImpl。若用它去覆盖 Compose 的 LocalContext，Hilt 的
     *   hiltViewModel() 会因为拿不到 Activity Context 而直接崩溃
     *   （"Expected an activity context for creating a HiltViewModelFactory"）。
     * - 在 attachBaseContext 阶段替换 base Context 是官方支持的做法
     *   （AppCompatDelegate 也是这么做的）：Activity 本身仍是 Activity Context，
     *   Compose 的 LocalContext 依旧指向 Activity，stringResource() 会解析到目标语言。
     * - 跟随系统时 wrapContext 原样返回，per-app locale / 系统语言自然生效。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

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

        // 语言偏好变化 → 重建 Activity，让 base Context 换成新语言的资源。
        // 触发入口只有设置页的语言选择（此时不在播放），重建是安全的。
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                    if (prefs.appLanguage != LocaleManager.currentLanguage) {
                        LocaleManager.update(prefs.appLanguage)
                        Timber.i("MainActivity: language changed -> %s, recreating", prefs.appLanguage)
                        recreate()
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 用户按 Home 键离开且正在播放：
        // 有悬浮窗权限 → 悬浮窗小窗（尺寸/位置可编程，体验优于系统 PiP）
        // 无权限 → 保留系统 PiP 兜底
        val playing = playerManager.get().isPlaying.value
        val overlay = android.provider.Settings.canDrawOverlays(this)
        Timber.i("MainActivity onUserLeaveHint: playing=%s overlay=%s", playing, overlay)
        if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (android.provider.Settings.canDrawOverlays(this)) {
                val player = playerManager.get().getPlayer()
                val vs = player.videoSize
                val portrait = vs.width > 0 && vs.height > 0 && vs.height > vs.width
                com.pureframe.player.player.FloatingVideoService.start(this, portrait)
            } else {
                runCatching {
                    enterPictureInPictureMode(
                        com.pureframe.player.player.PiPHelper.buildParams(this, playerManager.get().getPlayer())
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (com.pureframe.player.player.PipCurrentActivityHolder.currentActivity === this) {
            com.pureframe.player.player.PipCurrentActivityHolder.currentActivity = null
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // 同步到 Compose 层：PlayerScreen 据此隐藏控制栏与手势
        navigationState.setPipMode(isInPictureInPictureMode)
    }
}
