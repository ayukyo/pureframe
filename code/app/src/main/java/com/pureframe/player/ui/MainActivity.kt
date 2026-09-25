package com.pureframe.player.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.common.util.UnstableApi
import com.pureframe.player.ui.navigation.NavigationState
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge：内容绘制到系统栏后面，由 Compose 统一消费 insets。
        // 避免播放页控制栏在全屏/非全屏切换时与系统导航条重复避让（全屏按钮被压扁的问题）
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(navigationState = navigationState)
        }
    }
}
