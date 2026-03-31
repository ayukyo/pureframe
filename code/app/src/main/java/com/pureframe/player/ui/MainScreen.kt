package com.pureframe.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pureframe.player.ui.navigation.PureFrameNavGraph
import com.pureframe.player.ui.navigation.Screen
import com.pureframe.player.ui.theme.PureFrameTheme

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
@UnstableApi
@Composable
fun MainScreen(
    navController: NavHostController = rememberNavController()
) {
    PureFrameTheme {
        Scaffold(
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
                        currentRoute = currentRoute ?: Screen.Home.route
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                PureFrameNavGraph(navController = navController)
            }
        }
    }
}

/**
 * 底部导航栏
 */
@Composable
fun PureFrameBottomBar(
    navController: NavHostController,
    items: List<BottomNavItem>,
    currentRoute: String
) {
    NavigationBar {
        items.forEach { item ->
            val selected = currentRoute == item.screen.route
            
            NavigationBarItem(
                selected = selected,
                onClick = {
                    // 导航到选中页面，避免重复导航
                    if (currentRoute != item.screen.route) {
                        navController.navigate(item.screen.route) {
                            // 弹出到起始目的地，避免堆栈积累
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // 避免多次点击时创建多个实例
                            launchSingleTop = true
                            // 恢复状态
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(text = item.label)
                }
            )
        }
    }
}