package com.pureframe.player.ui.navigation

import androidx.annotation.StringRes
import com.pureframe.player.R

/**
 * 导航目的地定义
 */
sealed class Screen(val route: String, @StringRes val titleRes: Int) {
    object Home : Screen("home", R.string.tab_local)
    object Download : Screen("download", R.string.tab_download)
    object Settings : Screen("settings", R.string.tab_settings)
    
    // 嵌套路由
    //
    // 注意：Navigation 的路径参数 {xxx} 可以匹配 `/`，
    // 如果本地播放写成 "player/{videoId}"，它会把 "player/stream/5" 整体
    // 当成 videoId 吃掉，导致边下边播路由永远匹配不到。
    // 因此两条路由必须使用互不包含的前缀。
    object Player : Screen("player/local/{videoId}", R.string.player_title) {
        fun createRoute(videoId: String) = "player/local/$videoId"
    }

    object StreamPlayer : Screen("player/stream/{downloadId}", R.string.download_stream_play) {
        fun createRoute(downloadId: String) = "player/stream/$downloadId"
    }

    // 设置子页：致谢 / 开源许可证
    object Acknowledgements : Screen("about/acknowledgements", R.string.settings_oss_acknowledgements)
    object Licenses : Screen("about/licenses", R.string.about_licenses_title)
}