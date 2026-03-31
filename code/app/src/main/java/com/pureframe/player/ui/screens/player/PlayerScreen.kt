package com.pureframe.player.ui.screens.player

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.pureframe.player.player.PlayerState
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    
    // 将 String 转换为 Long
    val videoIdLong = videoId.toLongOrNull() ?: 0L
    val downloadIdLong = downloadId.toLongOrNull() ?: 0L
    
    // 收集状态
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val bufferedPosition by viewModel.bufferedPosition.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val playerError by viewModel.playerError.collectAsStateWithLifecycle()
    val brightness by viewModel.brightness.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val gestureIndicator by viewModel.showGestureIndicator.collectAsStateWithLifecycle()
    
    // 控制栏显示状态
    var showControls by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    
    // 初始化播放器
    LaunchedEffect(videoIdLong, downloadIdLong, isStreamPlayback) {
        if (isStreamPlayback && downloadIdLong > 0) {
            // 边下边播（暂时用 videoId 作为 downloadId）
            // TODO: 实际获取 DownloadTask
        } else if (videoIdLong > 0) {
            viewModel.initLocalPlayback(videoIdLong)
        }
    }
    
    // 自动隐藏控制栏
    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }
    
    // 设置窗口亮度
    LaunchedEffect(brightness) {
        setWindowBrightness(view, brightness)
    }
    
    // 处理播放器错误
    LaunchedEffect(playerError) {
        if (playerError != null) {
            // 显示错误提示
        }
    }
    
    // 退出时保存进度
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgress()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = {
                        viewModel.togglePlayPause()
                    }
                )
            }
    ) {
        // 视频播放器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.getPlayer()
                    useController = false  // 使用自定义控制器
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 加载指示器
        AnimatedVisibility(
            visible = playbackState == PlayerState.BUFFERING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
        
        // 水平滑动进度控制区域
        SeekGestureOverlay(
            currentPosition = currentPosition,
            duration = duration,
            onSeekRelative = { deltaMs ->
                viewModel.seekRelative(deltaMs)
            },
            onSeekStart = { showControls = false },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.5f)
        )
        
        // 手势指示器
        gestureIndicator?.let { indicator ->
            GestureIndicatorOverlay(
                indicator = indicator,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // 控制栏（点击显示/隐藏）
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControlsOverlay(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPosition = bufferedPosition,
                playbackState = playbackState,
                errorMessage = playerError ?: uiState.errorMessage,
                isStreamPlayback = isStreamPlayback,
                streamProgress = uiState.streamProgress,
                onBack = onBack,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) },
                onSeekRelative = { viewModel.seekRelative(it) },
                onVolumeChange = { viewModel.setVolume(it) },
                onBrightnessChange = { viewModel.setBrightness(it) },
                onClearError = { viewModel.clearError() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 边下边播状态指示
        if (isStreamPlayback && uiState.streamProgress > 0) {
            StreamPlaybackIndicator(
                progress = uiState.streamProgress,
                maxSeekPosition = uiState.maxSeekPosition,
                duration = duration,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
        
        // 错误显示
        if (playerError != null || uiState.errorMessage != null) {
            ErrorOverlay(
                message = playerError ?: uiState.errorMessage ?: "未知错误",
                onRetry = { viewModel.clearError() },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * 播放器控制覆盖层
 */
@Composable
fun PlayerControlsOverlay(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    playbackState: PlayerState,
    errorMessage: String?,
    isStreamPlayback: Boolean,
    streamProgress: Float,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 顶部栏
        TopBar(
            title = if (isStreamPlayback) "边下边播" else "播放器",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // 中间控制按钮
        CenterControls(
            isPlaying = isPlaying,
            playbackState = playbackState,
            onPlayPause = onPlayPause,
            onSeekBackward = { onSeekRelative(-10_000) },
            onSeekForward = { onSeekRelative(10_000) },
            modifier = Modifier.align(Alignment.Center)
        )
        
        // 底部进度条
        BottomProgressBar(
            currentPosition = currentPosition,
            duration = duration,
            bufferedPosition = bufferedPosition,
            isStreamPlayback = isStreamPlayback,
            streamProgress = streamProgress,
            onSeek = onSeek,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // 左侧亮度控制区域
        BrightnessGestureArea(
            onBrightnessChange = onBrightnessChange,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.6f)
                .width(100.dp)
        )
        
        // 右侧音量控制区域
        VolumeGestureArea(
            onVolumeChange = onVolumeChange,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.6f)
                .width(100.dp)
        )
    }
}

/**
 * 顶部栏
 */
@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * 中间控制按钮
 */
@Composable
fun CenterControls(
    isPlaying: Boolean,
    playbackState: PlayerState,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 快退 10 秒
        IconButton(
            onClick = onSeekBackward,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = "快退 10 秒",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        
        // 播放/暂停
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        
        // 快进 10 秒
        IconButton(
            onClick = onSeekForward,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = "快进 10 秒",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * 底部进度条
 */
@Composable
fun BottomProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isStreamPlayback: Boolean,
    streamProgress: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 进度条
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                val newPosition = (newProgress * duration).toLong()
                onSeek(newPosition)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
            )
        )
        
        // 时间显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTime(duration),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 亮度手势区域
 */
@Composable
fun BrightnessGestureArea(
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentBrightness by remember { mutableFloatStateOf(0.5f) }
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        onBrightnessChange(currentBrightness)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // 向上滑动增加亮度，向下减少
                        val delta = -dragAmount / 300f
                        currentBrightness = (currentBrightness + delta).coerceIn(0f, 1f)
                        onBrightnessChange(currentBrightness)
                    }
                )
            }
    )
}

/**
 * 音量手势区域
 */
@Composable
fun VolumeGestureArea(
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentVolume by remember { mutableFloatStateOf(1f) }
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        onVolumeChange(currentVolume)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // 向上滑动增加音量，向下减少
                        val delta = -dragAmount / 300f
                        currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
                        onVolumeChange(currentVolume)
                    }
                )
            }
    )
}

/**
 * 手势指示器覆盖层
 */
@Composable
fun GestureIndicatorOverlay(
    indicator: GestureIndicator,
    modifier: Modifier = Modifier
) {
    val (icon, value, label) = when (indicator) {
        is GestureIndicator.Volume -> {
            Triple(Icons.Filled.VolumeUp, indicator.value, "音量")
        }
        is GestureIndicator.Brightness -> {
            Triple(Icons.Filled.Brightness6, indicator.value, "亮度")
        }
        is GestureIndicator.Seek -> {
            val seekText = if (indicator.deltaMs > 0) "+${indicator.deltaMs / 1000}s" 
                           else "-${Math.abs(indicator.deltaMs) / 1000}s"
            Triple(Icons.Filled.FastForward, 1f, seekText)
        }
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(value * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * 边下边播状态指示器
 */
@Composable
fun StreamPlaybackIndicator(
    progress: Float,
    maxSeekPosition: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Downloading,
            contentDescription = "下载中",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "已下载 ${(progress * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 错误覆盖层
 */
@Composable
fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.8f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = "错误",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onBack) {
                    Text("返回")
                }
                Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}

/**
 * 格式化时间
 */
fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000 / 60) % 60
    val hours = ms / 1000 / 3600
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * 设置窗口亮度
 */
fun setWindowBrightness(view: View, brightness: Float) {
    // 在实际应用中需要 Activity 引用
    // 这里简化处理，实际实现需要使用 Window属性
}

/**
 * 水平滑动进度手势区域
 * 
 * 水平滑动控制视频进度：
 * - 向右滑动 → 快进
 * - 向左滑动 → 快退
 */
@Composable
fun SeekGestureOverlay(
    currentPosition: Long,
    duration: Long,
    onSeekRelative: (Long) -> Unit,
    onSeekStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    var seekIndicatorText by remember { mutableStateOf("") }
    
    // 显示进度跳转指示器
    var showSeekIndicator by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        accumulatedDelta = 0f
                        onSeekStart()
                    },
                    onDragEnd = {
                        if (isDragging && accumulatedDelta != 0f) {
                            // 根据滑动距离计算跳转时间
                            // 每 10dp 滑动 ≈ 1 秒
                            val seekDeltaMs = (accumulatedDelta * 1000).toLong()
                            onSeekRelative(seekDeltaMs)
                        }
                        isDragging = false
                        showSeekIndicator = false
                        accumulatedDelta = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDelta += dragAmount / 10f  // 10dp = 1秒
                        
                        // 显示跳转指示器
                        val seekSeconds = accumulatedDelta.toInt()
                        if (seekSeconds != 0) {
                            seekIndicatorText = if (seekSeconds > 0) {
                                "+${seekSeconds}s"
                            } else {
                                "${seekSeconds}s"
                            }
                            showSeekIndicator = true
                        }
                    }
                )
            }
    ) {
        // 跳转指示器
        if (showSeekIndicator && seekIndicatorText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (accumulatedDelta > 0) Icons.Filled.FastForward 
                                      else Icons.Filled.FastRewind,
                        contentDescription = "跳转",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = seekIndicatorText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}