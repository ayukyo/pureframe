package com.pureframe.player.ui.navigation

/**
 * 导航目的地定义
 */
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "本地视频")
    object Download : Screen("download", "下载")
    object Settings : Screen("settings", "设置")
    
    // 嵌套路由
    object Player : Screen("player/{videoId}", "播放器") {
        fun createRoute(videoId: String) = "player/$videoId"
    }
    
    object StreamPlayer : Screen("player/stream/{downloadId}", "边下边播") {
        fun createRoute(downloadId: String) = "player/stream/$downloadId"
    }
}