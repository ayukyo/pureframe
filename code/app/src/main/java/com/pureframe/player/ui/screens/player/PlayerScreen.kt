package com.pureframe.player.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 播放器页面
 * 
 * 功能：
 * - 视频播放控制
 * - 手势控制（亮度/音量/进度）
 * - 边下边播状态显示
 * - 播放进度保存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String = "",
    downloadId: String = "",
    isStreamPlayback: Boolean = false,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // TODO: 初始化播放器
    // LaunchedEffect(videoId, downloadId) {
    //     if (isStreamPlayback) {
    //         viewModel.initStreamPlayback(downloadId)
    //     } else {
    //         viewModel.initLocalPlayback(videoId)
    //     }
    // }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isStreamPlayback) "边下边播" else "播放器",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // TODO: 实现视频播放器
            // AndroidView(
            //     factory = { context ->
            //         PlayerView(context).apply {
            //             player = viewModel.player
            //         }
            //     }
            // )
            
            // 暂显示占位
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            
            // 边下边播状态指示
            if (isStreamPlayback) {
                // TODO: 显示下载进度和可播放范围
                // StreamPlaybackIndicator(
                //     cachedProgress = viewModel.cachedProgress,
                //     maxSeekPosition = viewModel.maxSeekPosition
                // )
            }
        }
        
        // TODO: 手势控制覆盖层
        // GestureOverlay(
        //     onBrightnessChange = { viewModel.setBrightness(it) },
        //     onVolumeChange = { viewModel.setVolume(it) },
        //     onSeekChange = { viewModel.seekRelative(it) },
        //     onDoubleTap = { viewModel.togglePlayPause() }
        // )
    }
    
    // TODO: 退出时保存播放进度
    // DisposableEffect(Unit) {
    //     onDispose {
    //         viewModel.saveProgress()
    //     }
    // }
}