package com.pureframe.player.ui.navigation

/**
 * 导航目的地定义
 */
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "本地视频")
    object Download : Screen("download", "下载")
    object Settings : Screen("settings", "设置")
    
    // 嵌套路由
    //
    // 注意：Navigation 的路径参数 {xxx} 可以匹配 `/`，
    // 如果本地播放写成 "player/{videoId}"，它会把 "player/stream/5" 整体
    // 当成 videoId 吃掉，导致边下边播路由永远匹配不到。
    // 因此两条路由必须使用互不包含的前缀。
    object Player : Screen("player/local/{videoId}", "播放器") {
        fun createRoute(videoId: String) = "player/local/$videoId"
    }

    object StreamPlayer : Screen("player/stream/{downloadId}", "边下边播") {
        fun createRoute(downloadId: String) = "player/stream/$downloadId"
    }
}