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
import androidx.compose.ui.layout.onSizeChanged
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

    // 投屏状态
    val isCasting by viewModel.isCasting.collectAsStateWithLifecycle()
    val castState by viewModel.castState.collectAsStateWithLifecycle()
    val castEndedEvent by viewModel.castEnded.collectAsStateWithLifecycle()
    // 播完提示条显示状态（收到事件时显示，6s 后自动消失）
    var showCastEndedToast by remember { mutableStateOf(false) }
    LaunchedEffect(castEndedEvent) {
        castEndedEvent?.let {
            showCastEndedToast = true
            kotlinx.coroutines.delay(6_000)
            showCastEndedToast = false
        }
    }
    val showCastDialog by viewModel.showCastDialog.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isScanningDevices by viewModel.isScanningDevices.collectAsStateWithLifecycle()
    val connectingDeviceId by viewModel.connectingDeviceId.collectAsStateWithLifecycle()
    val castError by viewModel.castError.collectAsStateWithLifecycle()
    // 投屏时播放控制走远程，显示的进度/播放态取远程值
    val effIsPlaying = if (isCasting) (castState?.isPlaying ?: false) else isPlaying
    val effPosition = if (isCasting) (castState?.positionMs ?: 0L) else currentPosition
    val effDuration = if (isCasting) (castState?.durationMs?.takeIf { it > 0 } ?: duration) else duration
    val effPlaybackState = if (isCasting) {
        when {
            castState?.phase == com.pureframe.player.cast.RoutePhase.CONNECTING -> PlayerState.BUFFERING
            castState?.isPlaying == true -> PlayerState.READY
            else -> PlayerState.READY
        }
    } else playbackState

    // ---- feed 模式（上下滑切换视频，抖音式）----
    // 开关来自设置；投屏中 / 边下边播不启用（滑动切条只对本机本地视频有意义）
    val verticalSwipeSetting by viewModel.verticalSwipeSwitch.collectAsStateWithLifecycle()
    val feedMode = verticalSwipeSetting && !isCasting && !isStreamPlayback
    var feedSwipeToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedSwipeToast) {
        if (feedSwipeToast != null) {
            kotlinx.coroutines.delay(1500)
            feedSwipeToast = null
        }
    }
    
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

    // ---- 悬浮窗小窗播放 ----
    // 主动小窗改用悬浮窗方案（系统 PiP 尺寸/位置不可编程：横屏到不了全宽、竖屏只有 30% 屏宽、位置固定吸底）
    // 注意：不能用 remember{} 缓存权限值——用户从设置页授权返回后不会刷新，导致提示反复弹出（需重启进程）。
    // 改为每次进入 RESUMED 状态时重读（onResume 是授权返回的必经路径）。
    var canDrawOverlays by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = android.provider.Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showFloatingPermissionTip by remember { mutableStateOf(false) }
    // 悬浮窗显示时播放页 PlayerView 必须解除 player 绑定（SurfaceView 不能被两个窗口同时消费）
    val isFloatingMode by (navigationState?.isFloatingMode
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    fun enterFloatingWindow() {
        val player = viewModel.getPlayer()
        val vs = player.videoSize
        val portrait = vs.width > 0 && vs.height > 0 && vs.height > vs.width
        if (canDrawOverlays) {
            com.pureframe.player.player.FloatingVideoService.start(context, portrait)
            // 播放页自身退出前台即可（服务挂同一个 ExoPlayer，播放无缝继续）
            activity?.moveTaskToBack(true)
        } else {
            showFloatingPermissionTip = true
        }
    }
    
    // PiP 小窗模式下控制栏强制隐藏，禁用手势
    val controlsVisible = showControls

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
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
                // 悬浮窗显示时解除绑定（surface 归悬浮窗所有），回全屏时重新绑定
                playerView.player = if (isFloatingMode) null else viewModel.getPlayer()
                // 更新画面比例模式
                playerView.resizeMode = when (aspectRatio) {
                    "FILL" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 手势区域二选一：
        // - feed 模式（设置「上下滑切换视频」开启且本机播放非投屏）：全屏 VerticalPager
        //   跟手上下滑切上/下一条视频，亮度/音量手势禁用（互斥）
        // - 传统模式：中间水平 seek + 左亮度 + 右音量（现状）
        if (feedMode) {
            FeedGesturePager(
                onSwipeUp = {
                    if (viewModel.canSwipeToNext()) {
                        viewModel.playNext()
                    } else {
                        feedSwipeToast = context.getString(R.string.feed_no_next)
                    }
                },
                onSwipeDown = {
                    if (viewModel.canSwipeToPrevious()) {
                        viewModel.playPrevious()
                    } else {
                        feedSwipeToast = context.getString(R.string.feed_no_previous)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 水平滑动进度控制区域（中间 40% 宽）—— 悬浮窗方案：播放页手势常开
            run {
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
                // initialVolume 用系统媒体音量归一值，手势起点与当前音量一致
                VolumeGestureArea(
                    onVolumeChange = { viewModel.setVolume(it) },
                    initialVolume = volume,
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
        }

        // feed 模式滑动边界提示（滑到队列尽头时显示，自动消失）
        androidx.compose.animation.AnimatedVisibility(
            visible = feedSwipeToast != null,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = feedSwipeToast ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        // feed 模式底部常显迷你进度条（控制栏隐藏时可见，可拖动 seek）
        if (feedMode && !controlsVisible) {
            FeedMiniProgressBar(
                currentPosition = effPosition,
                duration = effDuration,
                onSeek = {
                    if (isCasting) viewModel.castSeekTo(it) else viewModel.seekTo(it)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }

        // 投屏结束提示条（远端播完自动断开后显示，自动消失）
        androidx.compose.animation.AnimatedVisibility(
            visible = showCastEndedToast,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(                initialOffsetY = { -it },
                animationSpec = tween(200)
            ),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 64.dp, start = 24.dp, end = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = castEndedEvent?.let {
                            stringResource(
                                R.string.cast_ended_on_device,
                                it.deviceName
                            )
                        } ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 控制栏（点击显示/隐藏）
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            EnhancedPlayerControls(
                title = uiState.video?.title
                    ?: uiState.title.ifEmpty { stringResource(R.string.player_title) },
                isPlaying = effIsPlaying,
                currentPosition = effPosition,
                duration = effDuration,
                bufferedPosition = bufferedPosition,
                playbackState = effPlaybackState,
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
                onPlayPause = {
                    if (isCasting) viewModel.castPlayPause() else viewModel.togglePlayPause()
                },
                onSeek = {
                    if (isCasting) viewModel.castSeekTo(it) else viewModel.seekTo(it)
                },
                onSeekRelative = {
                    if (isCasting) viewModel.castSeekRelative(it) else viewModel.seekRelative(it)
                },
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onAspectRatioChange = { viewModel.setAspectRatio(it) },
                onFullscreenToggle = { viewModel.toggleFullscreen() },
                onShowSpeedDialog = { viewModel.showSpeedDialog() },
                onShowAspectRatioDialog = { viewModel.showAspectRatioDialog() },
                onShowCastDialog = { viewModel.openCastDialog() },
                isCasting = isCasting,
                onEnterPip = { enterFloatingWindow() },
                onUserInteraction = {
                    controlsTrigger++
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 边下边播状态指示（PiP 模式下隐藏）
        if (isStreamPlayback && uiState.streamProgress > 0 ) {
            StreamPlaybackIndicator(
                progress = uiState.streamProgress,
                maxSeekPosition = uiState.maxSeekPosition,
                duration = duration,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
        
        // 错误显示（投屏中本机播放器静默，其错误态不遮挡投屏会话）
        if (!isCasting && (playerError != null || uiState.errorMessage != null)) {
            ErrorOverlay(
                message = playerError ?: uiState.errorMessage ?: stringResource(R.string.player_error_unknown),
                onRetry = { viewModel.retryPlayback() },
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

        // 投屏设备选择弹层
        if (showCastDialog) {
            CastDevicePicker(
                devices = discoveredDevices,
                isScanning = isScanningDevices,
                isCasting = isCasting,
                currentDeviceName = castState?.deviceName,
                connectingDeviceId = connectingDeviceId,
                castError = castError,
                onDeviceClick = { viewModel.castToDevice(it) },
                onDisconnect = {
                    viewModel.disconnectCast()
                },
                onRefresh = { viewModel.refreshCastDevices() },
                onDismiss = { viewModel.dismissCastDialog() }
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

        // 悬浮窗权限引导提示条
        if (showFloatingPermissionTip) {
            FloatingPermissionTip(
                onGrant = {
                    showFloatingPermissionTip = false
                    // 跳转系统悬浮窗授权页
                    runCatching {
                        activity?.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                },
                onDismiss = { showFloatingPermissionTip = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            )
        }
    }
}

/**
 * 悬浮窗权限引导提示
 */
@Composable
private fun FloatingPermissionTip(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.floating_permission_needed),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.End)
                    .padding(top = 8.dp)
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                androidx.compose.material3.TextButton(onClick = onGrant) {
                    Text(
                        stringResource(R.string.floating_permission_grant),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            }
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
    modifier: Modifier = Modifier,
    initialVolume: Float = 1f
) {
    var currentVolume by remember { mutableFloatStateOf(initialVolume.coerceIn(0f, 1f)) }
    
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
/**
 * feed 模式上下滑手势层（抖音式）。
 *
 * 设计：单 PlayerView 架构下不做真·多页视频预渲染（Surface 归属冲突 + 内存开销），
 * 手势层用固定 3 页（上/当前/下）VerticalPager 承担跟手位移与 snap：
 * - 拖动中相邻页可见（黑底占位，与抖音上滑露出的「下一页黑底」一致）
 * - settle 到相邻页后触发 onSwipeUp/onSwipeDown（内部 canSwipeToNext/Previous
 *   边界检查，失败提示 toast），切换完成后 snap 回中页等待下一次手势
 *
 * 与传统手势互斥：feed 模式下不挂载亮度/音量手势区域（见 PlayerScreen 分支）。
 */
@Composable
fun FeedGesturePager(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 1, pageCount = { 3 }
    )
    val scope = rememberCoroutineScope()

    androidx.compose.foundation.pager.VerticalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = true,
        beyondViewportPageCount = 0
    ) { page ->
        // 中页（page==1）透明：露出底下真正的 PlayerView（视频画面 + 点击/双击手势）。
        // 相邻页黑底：拖动时露出，形成「黑幕滑入盖住当前视频」的转场——
        // settle 后 playNext/Previous 已把底下视频切好，snap 回中页即露出新视频
        if (page != 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }

    // settle 到非中页 → 触发切换并 snap 回中页（等待下一次手势）
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled != 1) {
            if (settled < 1) onSwipeDown() else onSwipeUp()
            // 等切换动画起播后再回中页，避免 snap 抢在切换前闪烁
            kotlinx.coroutines.delay(150)
            scope.launch { pagerState.scrollToPage(1) }
        }
    }
}

/**
 * feed 模式底部常显迷你进度条（抖音样式：屏幕最底部细线，控制栏隐藏时可见）。
 *
 * 可拖动：拖动中显示预览位置，松手执行 onSeek；不做拖动中实时 seek（避免频繁转码点）。
 */
@Composable
fun FeedMiniProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f
    val displayProgress = dragFraction ?: progress

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)   // 触控热区；视觉线只有 3dp
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (duration > 0) {
                                dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            dragFraction?.let { f -> onSeek((f * duration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
        ) {
            // 背景轨道
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            // 播放进度
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(displayProgress)
                    .height(3.dp)
                    .background(Color.White)
            )
        }
    }
}
