package com.pureframe.player.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pureframe.player.ui.screens.home.HomeScreen
import com.pureframe.player.ui.screens.download.DownloadScreen
import com.pureframe.player.ui.screens.settings.SettingsScreen
import com.pureframe.player.ui.screens.player.PlayerScreen

/**
 * 主导航图
 */
@UnstableApi
@Composable
fun PureFrameNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 主页面 - 本地视频列表
        composable(Screen.Home.route) {
            HomeScreen(
                onVideoClick = { videoId ->
                    navController.navigate(Screen.Player.createRoute(videoId))
                }
            )
        }
        
        // 下载页面
        composable(Screen.Download.route) {
            DownloadScreen(
                onDownloadClick = { downloadId ->
                    // 检查是否可边下边播，跳转到播放器
                    navController.navigate(Screen.StreamPlayer.createRoute(downloadId))
                }
            )
        }
        
        // 设置页面
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        
        // 本地视频播放器
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            PlayerScreen(
                videoId = videoId,
                isStreamPlayback = false,
                onBack = { navController.popBackStack() }
            )
        }
        
        // 边下边播播放器
        composable(
            route = Screen.StreamPlayer.route,
            arguments = listOf(
                navArgument("downloadId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val downloadId = backStackEntry.arguments?.getString("downloadId") ?: ""
            PlayerScreen(
                downloadId = downloadId,
                isStreamPlayback = true,
                onBack = { navController.popBackStack() }
            )
        }
    }
}