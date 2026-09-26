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
import androidx.compose.ui.res.stringResource
import com.pureframe.player.R

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
    navigationState: com.pureframe.player.ui.navigation.NavigationState? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val view = LocalView.current  // 未来使用
    @Suppress("UNUSED_VARIABLE")
    val scope = rememberCoroutineScope()  // 未来使用
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
    @Suppress("UNUSED_VARIABLE")
    val volume by viewModel.volume.collectAsStateWithLifecycle()  // 音量控制
    val gestureIndicator by viewModel.showGestureIndicator.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val showSpeedDialog by viewModel.showSpeedDialog.collectAsStateWithLifecycle()
    val showAspectRatioDialog by viewModel.showAspectRatioDialog.collectAsStateWithLifecycle()
    val showResumeDialog by viewModel.showResumeDialog.collectAsStateWithLifecycle()
    val lastPosition by viewModel.lastPosition.collectAsStateWithLifecycle()
    
    // 控制栏显示状态
    var showControls by remember { mutableStateOf(true) }
    // 控制栏显示触发器（用于重置自动隐藏计时器）
    var controlsTrigger by remember { mutableStateOf(0L) }

    // 播放中保持屏幕常亮（来自设置项，默认开启）
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    if (activity != null) {
        DisposableEffect(keepScreenOn) {
            if (keepScreenOn) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    
    // 初始化播放器
    LaunchedEffect(videoIdLong, downloadIdLong, isStreamPlayback) {
        if (isStreamPlayback && downloadIdLong > 0) {
            // 边下边播：通过下载任务 ID 初始化
            viewModel.initStreamPlaybackById(downloadIdLong)
        } else if (videoIdLong > 0) {
            viewModel.initLocalPlayback(videoIdLong)
        }
    }
    
    // 自动隐藏控制栏（controlsTrigger 变化时重置计时器）
    LaunchedEffect(isPlaying, showControls, controlsTrigger) {
        if (isPlaying && showControls) {
            kotlinx.coroutines.delay(4000)
            showControls = false
        }
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
            activity?.let {
                setFullscreenMode(it, false)
                // 归还系统亮度与屏幕方向，避免影响其他页面
                it.resetWindowBrightness()
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // ---- 画中画（PiP）支持 ----
    // 监听 PiP 模式变化：小窗模式下隐藏全部控制栏，只留画面
    // activity 1.8.1 没有 PictureInPictureModeChangedInfo API，
    // 通过 NavigationState 的共享流从 MainActivity 覆写回调桥接过来
    val isInPipMode by (navigationState?.isInPipMode
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    // 进入画中画：按视频真实宽高比自适应小窗形状 + 小窗内控制按钮
    val enterPip = {
        activity?.let { act ->
            runCatching {
                android.util.Log.i("PureFramePip", "enterPictureInPictureMode requested")
                act.enterPictureInPictureMode(
                    com.pureframe.player.player.PiPHelper.buildParams(
                        act,
                        viewModel.getPlayer()
                    )
                )
            }.onFailure {
                android.util.Log.e("PureFramePip", "enterPictureInPictureMode failed", it)
            }
        }
        Unit
    }
    
    // PiP 小窗模式下控制栏强制隐藏，禁用手势
    val controlsVisible = showControls && !isInPipMode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isInPipMode) Modifier
                else Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            if (showControls) {
                                // 重置自动隐藏计时器
                                controlsTrigger++
                            }
                        },
                        onDoubleTap = {
                            viewModel.togglePlayPause()
                        }
                    )
                }
            )
    ) {
        // 视频播放器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.getPlayer()
                    useController = false  // 使用自定义控制器
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // 字幕样式：半透明淡底色，避免纯黑底完全遮盖视频画面
                    subtitleView?.apply {
                        setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                /* foregroundColor = */ android.graphics.Color.WHITE,
                                /* backgroundColor = */ (0x66000000).toInt(), // 40% 透明黑底
                                /* windowColor = */ android.graphics.Color.TRANSPARENT,
                                /* edgeType = */ androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                /* edgeColor = */ (0x99000000).toInt(), // 60% 黑色描边增强可读性
                                /* typeface = */ null
                            )
                        )
                    }
                }
            },
            update = { playerView ->
                // 更新画面比例模式
                playerView.resizeMode = when (aspectRatio) {
                    "FILL" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 水平滑动进度控制区域（中间 40% 宽）—— PiP 模式下不启用手势
        if (!isInPipMode) {
            SeekGestureOverlay(
                currentPosition = currentPosition,
                duration = duration,
                onSeekRelative = { deltaMs ->
                    viewModel.seekRelative(deltaMs)
                },
                onSeekStart = { showControls = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight(0.6f)
            )

            // 左侧亮度控制区域（左 30% 宽，扩大热区，避免划不到）
            BrightnessGestureArea(
                onBrightnessChange = { value ->
                    viewModel.setBrightness(value)
                    // 真正写入系统窗口亮度，否则手势只改了状态、屏幕不会变
                    activity?.applyWindowBrightness(value)
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(0.8f)
                    .fillMaxWidth(0.3f)
            )

            // 右侧音量控制区域（右 30% 宽，扩大热区，避免划不到）
            VolumeGestureArea(
                onVolumeChange = { viewModel.setVolume(it) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.8f)
                    .fillMaxWidth(0.3f)
            )

            // 手势指示器：靠边显示，避免遮挡画面中心
            gestureIndicator?.let { indicator ->
                val alignment = when (indicator) {
                    is GestureIndicator.Brightness -> Alignment.CenterStart
                    is GestureIndicator.Volume -> Alignment.CenterEnd
                    is GestureIndicator.Seek -> Alignment.TopCenter
                }
                GestureIndicatorOverlay(
                    indicator = indicator,
                    modifier = Modifier.align(alignment)
                )
            }
        }

        // 控制栏（点击显示/隐藏）—— PiP 模式下隐藏
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            EnhancedPlayerControls(
                title = uiState.video?.title
                    ?: uiState.title.ifEmpty { stringResource(R.string.player_title) },
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPosition = bufferedPosition,
                playbackState = playbackState,
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
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onAspectRatioChange = { viewModel.setAspectRatio(it) },
                onFullscreenToggle = { viewModel.toggleFullscreen() },
                onShowSpeedDialog = { viewModel.showSpeedDialog() },
                onShowAspectRatioDialog = { viewModel.showAspectRatioDialog() },
                onEnterPip = { enterPip() },
                onUserInteraction = {
                    controlsTrigger++
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 边下边播状态指示（PiP 模式下隐藏）
        if (isStreamPlayback && uiState.streamProgress > 0 && !isInPipMode) {
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
                message = playerError ?: uiState.errorMessage ?: stringResource(R.string.player_error_unknown),
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
        is GestureIndicator.Volume -> Triple(Icons.Filled.VolumeUp, indicator.value, stringResource(R.string.gesture_volume))
        is GestureIndicator.Brightness -> Triple(Icons.Filled.Brightness6, indicator.value, stringResource(R.string.gesture_brightness))
        is GestureIndicator.Seek -> {
            val seekText = if (indicator.deltaMs > 0) "+${indicator.deltaMs / 1000}s" 
                           else "-${Math.abs(indicator.deltaMs) / 1000}s"
            Triple(Icons.Filled.FastForward, 1f, seekText)
        }
    }

    val isSeek = indicator is GestureIndicator.Seek
    // 侧边 24dp / 顶部 48dp 内边距：贴边显示且避开系统栏
    val outerPadding = if (isSeek) PaddingValues(top = 48.dp, start = 24.dp, end = 24.dp)
                       else PaddingValues(horizontal = 24.dp)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier.padding(outerPadding)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSeek) label else "${(value * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 水平滑动进度手势区域
 */
@Suppress("UNUSED_PARAMETER")  // 参数未来用于精确跳转显示
@Composable
fun SeekGestureOverlay(
    currentPosition: Long,  // 当前位置
    duration: Long,  // 总时长
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
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (accumulatedDelta > 0) Icons.Filled.FastForward 
                                      else Icons.Filled.FastRewind,
                        contentDescription = stringResource(R.string.player_jump),
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
@Suppress("UNUSED_PARAMETER")  // 参数未来用于详细状态显示
@Composable
fun StreamPlaybackIndicator(
    progress: Float,
    maxSeekPosition: Long,  // 最大可跳转位置
    duration: Long,  // 总时长
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
                contentDescription = stringResource(R.string.player_downloading),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.player_downloaded_percent, (progress * 100).toInt()),
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
                contentDescription = stringResource(R.string.player_error),
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
                Button(onClick = onBack) { Text(stringResource(R.string.action_go_back)) }
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

/**
 * 将亮度值写入当前窗口（0f~1f，-1f 表示跟随系统）
 */
private fun Activity.applyWindowBrightness(value: Float) {
    val lp = window.attributes
    lp.screenBrightness = value.coerceIn(0.01f, 1f)
    window.attributes = lp
}

/**
 * 退出播放页时恢复系统亮度，避免把用户在播放器里调暗的亮度带回其他页面
 */
private fun Activity.resetWindowBrightness() {
    val lp = window.attributes
    lp.screenBrightness = -1f
    window.attributes = lp
}

/**
 * 设置全屏模式
 */
fun setFullscreenMode(activity: Activity, fullscreen: Boolean) {
    val window = activity.window
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    
    if (fullscreen) {
        // 全屏模式：隐藏系统栏，跟随传感器横屏（不再硬锁死 LANDSCAPE）
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // 全屏下让内容延伸到系统栏后面
        WindowCompat.setDecorFitsSystemWindows(window, false)
    } else {
        // 正常模式：显示系统栏，把方向交还给系统/用户
        controller.show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        // 注意：这里不能调用 setDecorFitsSystemWindows(window, true)，
        // 否则会覆盖 MainActivity 的 enableEdgeToEdge()，导致退出播放页后
        // 全 App 顶部多出一块状态栏高度的空白（系统栏 inset 被二次叠加）。
        // 全局始终保持 edge-to-edge，由各页面的 TopAppBar/statusBarsPadding 自行消费 inset。
    }
}