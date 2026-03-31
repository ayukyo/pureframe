package com.pureframe.player.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.pureframe.player.player.PlayerState
import kotlinx.coroutines.launch

/**
 * 播放器页面 - 增强版
 * 
 * 功能：
 * - 视频播放控制
 * - 手势控制（亮度/音量/进度）
 * - 边下边播状态显示
 * - 播放进度保存
 * - 锁屏功能
 * - 倍速选择
 * - 画面比例控制
 * - 全屏控制
 * - 续播提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
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
    val _view = LocalView.current  // 未来使用
    val _scope = rememberCoroutineScope()  // 未来使用
    val activity = context as? Activity
    
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
    val _volume by viewModel.volume.collectAsStateWithLifecycle()  // 音量控制，未来使用
    val gestureIndicator by viewModel.showGestureIndicator.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val showSpeedDialog by viewModel.showSpeedDialog.collectAsStateWithLifecycle()
    val showAspectRatioDialog by viewModel.showAspectRatioDialog.collectAsStateWithLifecycle()
    val showResumeDialog by viewModel.showResumeDialog.collectAsStateWithLifecycle()
    val lastPosition by viewModel.lastPosition.collectAsStateWithLifecycle()
    
    // 控制栏显示状态
    var showControls by remember { mutableStateOf(true) }
    
    // 初始化播放器
    LaunchedEffect(videoIdLong, downloadIdLong, isStreamPlayback) {
        if (isStreamPlayback && downloadIdLong > 0) {
            // 边下边播：通过下载任务 ID 初始化
            viewModel.initStreamPlaybackById(downloadIdLong)
        } else if (videoIdLong > 0) {
            viewModel.initLocalPlayback(videoIdLong)
        }
    }
    
    // 自动隐藏控制栏
    LaunchedEffect(isPlaying, showControls, isLocked) {
        if (isPlaying && showControls && !isLocked) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }
    
    // 设置窗口亮度
    LaunchedEffect(brightness) {
        activity?.let { setWindowBrightness(it.window, brightness) }
    }
    
    // 设置全屏状态
    LaunchedEffect(isFullscreen) {
        activity?.let { setFullscreenMode(it, isFullscreen) }
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
            // 恢复正常屏幕模式
            activity?.let { setFullscreenMode(it, false) }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = {
                            viewModel.togglePlayPause()
                        }
                    )
                } else {
                    // 锁屏状态下点击无响应
                }
            }
    ) {
        // 视频播放器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.getPlayer()
                    useController = false  // 使用自定义控制器
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // 设置画面比例模式
                    resizeMode = when (aspectRatio) {
                        "FILL" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "16:9" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        "ORIGINAL" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
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
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        }
        
        // 水平滑动进度控制区域（仅在非锁屏状态）
        if (!isLocked) {
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
            
            // 左侧亮度控制区域
            BrightnessGestureArea(
                onBrightnessChange = { viewModel.setBrightness(it) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(0.6f)
                    .width(100.dp)
            )
            
            // 右侧音量控制区域
            VolumeGestureArea(
                onVolumeChange = { viewModel.setVolume(it) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.6f)
                    .width(100.dp)
            )
        }
        
        // 手势指示器
        gestureIndicator?.let { indicator ->
            GestureIndicatorOverlay(
                indicator = indicator,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // 控制栏（点击显示/隐藏，非锁屏状态）
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            EnhancedPlayerControls(
                title = uiState.video?.title ?: uiState.title,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPosition = bufferedPosition,
                playbackState = playbackState,
                isLocked = isLocked,
                playbackSpeed = playbackSpeed,
                aspectRatio = aspectRatio,
                isFullscreen = isFullscreen,
                isStreamPlayback = isStreamPlayback,
                streamProgress = uiState.streamProgress,
                maxSeekPosition = uiState.maxSeekPosition,
                onBack = {
                    if (isFullscreen) {
                        viewModel.setFullscreen(false)
                    } else {
                        onBack()
                    }
                },
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) },
                onSeekRelative = { viewModel.seekRelative(it) },
                onLockToggle = { viewModel.toggleLock() },
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onAspectRatioChange = { viewModel.setAspectRatio(it) },
                onFullscreenToggle = { viewModel.toggleFullscreen() },
                onShowSpeedDialog = { viewModel.showSpeedDialog() },
                onShowAspectRatioDialog = { viewModel.showAspectRatioDialog() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 锁屏按钮（始终显示，位置根据锁屏状态变化）
        LockButton(
            isLocked = isLocked,
            onLockToggle = { viewModel.toggleLock() },
            modifier = Modifier
                .align(
                    if (isLocked) Alignment.Center 
                    else Alignment.CenterStart
                )
                .padding(16.dp)
        )
        
        // 锁屏状态提示
        if (isLocked) {
            LockedOverlayHint(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 100.dp)
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
        
        // 倍速选择对话框
        if (showSpeedDialog) {
            PlaybackSpeedDialog(
                currentSpeed = playbackSpeed,
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onDismiss = { viewModel.dismissSpeedDialog() }
            )
        }
        
        // 画面比例选择对话框
        if (showAspectRatioDialog) {
            AspectRatioDialog(
                currentRatio = aspectRatio,
                onRatioChange = { viewModel.setAspectRatio(it) },
                onDismiss = { viewModel.dismissAspectRatioDialog() }
            )
        }
        
        // 续播提示对话框
        if (showResumeDialog && uiState.video != null) {
            ResumePlaybackDialog(
                lastPosition = lastPosition,
                duration = duration,
                videoTitle = uiState.video!!.title,
                onResume = { viewModel.resumeFromLastPosition() },
                onPlayFromStart = { viewModel.playFromStart() },
                onDismiss = { viewModel.dismissResumeDialog() }
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
        is GestureIndicator.Volume -> Triple(Icons.Filled.VolumeUp, indicator.value, "音量")
        is GestureIndicator.Brightness -> Triple(Icons.Filled.Brightness6, indicator.value, "亮度")
        is GestureIndicator.Seek -> {
            val seekText = if (indicator.deltaMs > 0) "+${indicator.deltaMs / 1000}s" 
                           else "-${Math.abs(indicator.deltaMs) / 1000}s"
            Triple(Icons.Filled.FastForward, 1f, seekText)
        }
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        modifier = modifier.padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
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
 * 水平滑动进度手势区域
 */
@Composable
fun SeekGestureOverlay(
    _currentPosition: Long,  // 当前位置，未来用于精确跳转
    _duration: Long,  // 总时长，未来使用
    onSeekRelative: (Long) -> Unit,
    onSeekStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    var seekIndicatorText by remember { mutableStateOf("") }
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
                            val seekDeltaMs = (accumulatedDelta * 1000).toLong()
                            onSeekRelative(seekDeltaMs)
                        }
                        isDragging = false
                        showSeekIndicator = false
                        accumulatedDelta = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDelta += dragAmount / 10f
                        
                        val seekSeconds = accumulatedDelta.toInt()
                        if (seekSeconds != 0) {
                            seekIndicatorText = if (seekSeconds > 0) "+${seekSeconds}s" else "${seekSeconds}s"
                            showSeekIndicator = true
                        }
                    }
                )
            }
    ) {
        if (showSeekIndicator && seekIndicatorText.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
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

/**
 * 边下边播状态指示器
 */
@Composable
fun StreamPlaybackIndicator(
    progress: Float,
    _maxSeekPosition: Long,  // 最大可跳转位置，未来使用
    _duration: Long,  // 总时长，未来使用
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth(0.8f)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
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
                Button(onClick = onBack) { Text("返回") }
                Button(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

/**
 * 设置窗口亮度
 */
fun setWindowBrightness(window: Window, brightness: Float) {
    val lp = window.attributes
    lp.screenBrightness = brightness.coerceIn(0f, 1f)
    window.attributes = lp
}

/**
 * 设置全屏模式
 */
fun setFullscreenMode(activity: Activity, fullscreen: Boolean) {
    val window = activity.window
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    
    if (fullscreen) {
        // 全屏模式：隐藏系统栏，锁定横屏
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        // 正常模式：显示系统栏，恢复竖屏
        controller.show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    
    // 设置沉浸式模式
    WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
}