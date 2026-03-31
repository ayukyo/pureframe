package com.pureframe.player.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 本地视频页面
 * 
 * 功能：
 * - 显示本地视频列表
 * - 支持文件夹分类
 * - 点击视频跳转播放器
 */
@Composable
fun HomeScreen(
    _onVideoClick: (String) -> Unit,  // 未来使用：点击视频跳转播放器
    _viewModel: HomeViewModel = hiltViewModel(),  // 未来使用：获取视频列表
    modifier: Modifier = Modifier
) {
    // TODO: 从 ViewModel 获取视频列表
    // val videos by viewModel.videos.collectAsState()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 页面标题
        Text(
            text = "本地视频",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        // 视频列表（暂显示占位）
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "🎬",
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = "扫描本地视频...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "暂无视频",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        // TODO: 实现视频列表
        // LazyColumn {
        //     items(videos) { video ->
        //         VideoListItem(
        //             video = video,
        //             onClick = { onVideoClick(video.id) }
        //         )
        //     }
        // }
    }
}