package com.pureframe.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pureframe.player.ui.navigation.NavigationState
import com.pureframe.player.ui.navigation.PureFrameNavGraph
import com.pureframe.player.ui.navigation.Screen

/**
 * 底部导航栏项目
 */
data class BottomNavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

/**
 * 底部导航栏项目列表
 */
val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        label = "本地"
    ),
    BottomNavItem(
        screen = Screen.Download,
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        label = "下载"
    ),
    BottomNavItem(
        screen = Screen.Settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        label = "设置"
    )
)

/**
 * 主界面 - 包含底部导航栏和页面内容
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun MainScreen(
    navController: NavHostController = rememberNavController(),
    navigationState: NavigationState,
    mainViewModel: MainViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    // 活跃下载数（用于"下载"Tab 角标）
    val activeDownloadCount by mainViewModel.activeDownloadCount.collectAsState()

    // 主题由 MainActivity 的 PureFrameTheme 统一提供（跟随设置页偏好），这里不再嵌套包装
    Scaffold(
        // contentWindowInsets 清零：顶部状态栏 inset 由内层页面各自处理
        // （主页面有 TopAppBar 自带避让，播放器有 statusBarsPadding），
        // 否则外层 Scaffold + 内层 TopAppBar 会叠加两次状态栏高度，顶部空出一块。
        // 底部 paddingValues 只剩 bottomBar（NavigationBar 自带导航条避让）的高度，行为不变。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
                // 只在主页面显示底部导航栏
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // 检查是否是主页面（不包含播放器页面）
                val isMainScreen = currentRoute in listOf(
                    Screen.Home.route,
                    Screen.Download.route,
                    Screen.Settings.route
                )

                if (isMainScreen) {
                    PureFrameBottomBar(
                        navController = navController,
                        items = bottomNavItems,
                        currentRoute = currentRoute ?: Screen.Home.route,
                        activeDownloadCount = activeDownloadCount,
                        onNavigationClick = { screen ->
                            if (currentRoute == screen.route) {
                                // 已经在目标页面，触发滚动
                                when (screen) {
                                    Screen.Home -> navigationState.requestScrollToHomeTop()
                                    Screen.Download -> navigationState.requestScrollToDownloadTop()
                                    else -> {}
                                }
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                PureFrameNavGraph(
                    navController = navController,
                    navigationState = navigationState
                )
            }
        }
}

/**
 * 底部导航栏
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PureFrameBottomBar(
    navController: NavHostController,
    items: List<BottomNavItem>,
    currentRoute: String,
    activeDownloadCount: Int = 0,
    onNavigationClick: (Screen) -> Unit = {}
) {
    NavigationBar {
        items.forEach { item ->
            val selected = currentRoute == item.screen.route

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute == item.screen.route) {
                        // 已经在目标页面，只触发滚动
                        onNavigationClick(item.screen)
                    } else {
                        // 切换到其他页面
                        navController.navigate(item.screen.route) {
                            // 回到起始页并保存状态，避免底部 Tab 越点栈越深
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // 避免多次点击时创建多个实例
                            launchSingleTop = true
                            // 切回已访问过的 Tab 时恢复滚动位置等状态
                            restoreState = true
                        }
                    }
                },
                icon = {
                    // "下载"Tab 显示活跃任务数角标
                    if (item.screen == Screen.Download && activeDownloadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(
                                        text = if (activeDownloadCount > 99) "99+" else activeDownloadCount.toString()
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    }
                },
                label = {
                    Text(text = item.label)
                }
            )
        }
    }
}