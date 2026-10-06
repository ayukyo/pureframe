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
import com.pureframe.player.ui.screens.about.AcknowledgementsScreen
import com.pureframe.player.ui.screens.about.LicensesScreen
import com.pureframe.player.ui.screens.player.PlayerScreen
import com.pureframe.player.ui.theme.PlayerTheme

/**
 * 主导航图
 */
@UnstableApi
@Composable
fun PureFrameNavGraph(
    navController: NavHostController,
    navigationState: NavigationState,
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
                },
                navigationState = navigationState
            )
        }

        // 下载页面
        composable(Screen.Download.route) {
            DownloadScreen(
                onPlayClick = { downloadId ->
                    // 检查是否可边下边播，跳转到播放器
                    navController.navigate(Screen.StreamPlayer.createRoute(downloadId))
                },
                navigationState = navigationState
            )
        }
        
        // 设置页面
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToAcknowledgements = {
                    navController.navigate(Screen.Acknowledgements.route)
                },
                onNavigateToLicenses = {
                    navController.navigate(Screen.Licenses.route)
                }
            )
        }

        // 致谢：第三方开源库
        composable(Screen.Acknowledgements.route) {
            AcknowledgementsScreen(
                onBack = { navController.popBackStack() },
                onOpenLicense = { navController.navigate(Screen.Licenses.route) }
            )
        }

        // 开源许可证全文
        composable(Screen.Licenses.route) {
            LicensesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // 本地视频播放器
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            // 播放器使用专用深色主题：控制栏与对话框始终为深色设计，
            // 避免浅色模式下主主题与硬编码深色 UI 混搭出错
            PlayerTheme {
                PlayerScreen(
                    videoId = videoId,
                    isStreamPlayback = false,
                    onBack = { navController.popBackStack() },
                    navigationState = navigationState
                )
            }
        }
        
        // 边下边播播放器
        composable(
            route = Screen.StreamPlayer.route,
            arguments = listOf(
                navArgument("downloadId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val downloadId = backStackEntry.arguments?.getString("downloadId") ?: ""
            PlayerTheme {
                PlayerScreen(
                    downloadId = downloadId,
                    isStreamPlayback = true,
                    onBack = { navController.popBackStack() },
                    navigationState = navigationState
                )
            }
        }
    }
}