package com.pureframe.player.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(navigationState = navigationState)
        }
    }
}
