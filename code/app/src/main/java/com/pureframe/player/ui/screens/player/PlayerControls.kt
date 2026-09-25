package com.pureframe.player.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureframe.player.player.PlayerState

/**
 * 播放器控制栏 - 完整版
 * 
 * 功能：
 * - 顶部信息栏（标题、返回、更多选项）
 * - 中间控制区（播放/暂停、快进快退）
 * - 底部进度条（带缓存进度显示）
 * - 锁屏按钮
 * - 倍速显示和选择
 * - 画面比例控制
 * - 全屏控制
 */
@Suppress("UNUSED_PARAMETER")  // 部分参数未来使用
@Composable
fun EnhancedPlayerControls(
    title: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    playbackState: PlayerState,
    playbackSpeed: Float,
    aspectRatio: String,  // 未来使用
    isFullscreen: Boolean,
    isStreamPlayback: Boolean,
    streamProgress: Float,
    maxSeekPosition: Long,  // 边下边播最大跳转位置
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (String) -> Unit,  // 未来使用
    onFullscreenToggle: () -> Unit,
    onShowSpeedDialog: () -> Unit,
    onShowAspectRatioDialog: () -> Unit,
    onEnterPip: (() -> Unit)? = null,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 进度变量（计算用于子组件）
    @Suppress("UNUSED_VARIABLE")
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    @Suppress("UNUSED_VARIABLE")
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f

    // 拖动状态（未来实现）
    @Suppress("UNUSED_VARIABLE")
    var isDragging by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE")
    var dragProgress by remember { mutableFloatStateOf(0f) }
    @Suppress("UNUSED_VARIABLE")
    var dragPosition by remember { mutableLongStateOf(0L) }

    Box(modifier = modifier) {
        // 顶部栏（渐变背景）
        TopControlBar(
            title = title,
            isFullscreen = isFullscreen,
            playbackSpeed = playbackSpeed,
            onBack = onBack,
            onShowSpeedDialog = onShowSpeedDialog,
            onShowAspectRatioDialog = onShowAspectRatioDialog,
            onEnterPip = onEnterPip,
            onUserInteraction = onUserInteraction,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        )

        // 中间控制区
        CenterControlArea(
            isPlaying = isPlaying,
            playbackState = playbackState,
            onPlayPause = onPlayPause,
            onSeekBackward = { onSeekRelative(-10_000) },
            onSeekForward = { onSeekRelative(10_000) },
            onUserInteraction = onUserInteraction,
            modifier = Modifier.align(BiasAlignment(0f, 0.6f))
        )

        // 底部进度条（渐变背景）
        BottomControlBar(
            currentPosition = currentPosition,
            duration = duration,
            bufferedPosition = bufferedPosition,
            isStreamPlayback = isStreamPlayback,
            streamProgress = streamProgress,
            maxSeekPosition = maxSeekPosition,
            isFullscreen = isFullscreen,
            playbackSpeed = playbackSpeed,
            onSeek = onSeek,
            onFullscreenToggle = onFullscreenToggle,
            onSpeedChange = onSpeedChange,
            onUserInteraction = onUserInteraction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

/**
 * 顶部控制栏
 */
@Composable
fun TopControlBar(
    title: String,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    onBack: () -> Unit,
    onShowSpeedDialog: () -> Unit,
    onShowAspectRatioDialog: () -> Unit,
    onEnterPip: (() -> Unit)? = null,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
            // edge-to-edge 下内容画进状态栏后面，顶部控制栏需要避开状态栏
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            IconButton(
                onClick = {
                    onUserInteraction()
                    onBack()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.Close else Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 标题
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            // 画中画按钮（非全屏且支持时显示）
            if (onEnterPip != null && !isFullscreen) {
                IconButton(
                    onClick = {
                        onUserInteraction()
                        onEnterPip()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureInPictureAlt,
                        contentDescription = "画中画",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 更多选项
            IconButton(
                onClick = onShowAspectRatioDialog,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多选项",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 倍速指示器
 */
@Composable
fun SpeedIndicator(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.2f),
        modifier = modifier
    ) {
        Text(
            text = "${speed}x",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * 中间控制区域
 */
@Composable
fun CenterControlArea(
    isPlaying: Boolean,
    playbackState: PlayerState,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 加载状态显示
    if (playbackState == PlayerState.BUFFERING) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        }
        return
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 快退 10 秒
        SeekButton(
            icon = Icons.Filled.Replay10,
            contentDescription = "快退 10 秒",
            onClick = {
                onUserInteraction()
                onSeekBackward()
            }
        )

        // 播放/暂停（大按钮）
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = {
                onUserInteraction()
                onPlayPause()
            },
            size = 64.dp
        )

        // 快进 10 秒
        SeekButton(
            icon = Icons.Filled.Forward10,
            contentDescription = "快进 10 秒",
            onClick = {
                onUserInteraction()
                onSeekForward()
            }
        )
    }
}

/**
 * 播放/暂停按钮
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(isPlaying, label = "playPauseTransition")
    
    val iconSize by transition.animateDp(
        transitionSpec = { tween(200) },
        label = "iconSize"
    ) { playing -> if (playing) size * 0.6f else size * 0.7f }
    
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.3f))
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 跳转按钮
 */
@Composable
fun SeekButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}

/**
 * 底部控制栏（包含进度条）
 */
@Composable
fun BottomControlBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isStreamPlayback: Boolean,
    streamProgress: Float,
    maxSeekPosition: Long,
    isFullscreen: Boolean,
    playbackSpeed: Float = 1f,
    onSeek: (Long) -> Unit,
    onFullscreenToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 拖动状态
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f
    
    val displayProgress = if (isDragging) dragProgress else progress
    val displayPosition = if (isDragging) (dragProgress * duration).toLong() else currentPosition
    
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.3f),
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
            // 避开系统导航条/手势条，否则全屏按钮会被压在屏幕最底部无法点击
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 进度条
            EnhancedProgressSlider(
                progress = displayProgress,
                bufferedProgress = bufferedProgress,
                isStreamPlayback = isStreamPlayback,
                streamProgress = streamProgress,
                isDragging = isDragging,
                onProgressChange = { newProgress ->
                    dragProgress = newProgress
                },
                onDragStart = {
                    isDragging = true
                    dragProgress = progress
                },
                onDragEnd = {
                    isDragging = false
                    onSeek((dragProgress * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // 时间和全屏按钮行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前时间
                Text(
                    text = formatTime(displayPosition),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.widthIn(min = 52.dp)
                )

                // 边下边播进度指示
                if (isStreamPlayback && streamProgress > 0) {
                    StreamProgressBadge(
                        progress = streamProgress,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 倍速按钮
                SpeedSelectorButton(
                    currentSpeed = playbackSpeed,
                    onSpeedSelected = {
                        onUserInteraction()
                        onSpeedChange(it)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // 总时长
                Text(
                    text = formatTime(duration),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.widthIn(min = 52.dp)
                )

                // 全屏按钮
                IconButton(
                    onClick = {
                        onUserInteraction()
                        onFullscreenToggle()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit
                                      else Icons.Filled.Fullscreen,
                        contentDescription = "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        // 拖动时显示预览时间
        if (isDragging) {
            DragPreviewTime(
                position = displayPosition,
                duration = duration,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-32).dp)
            )
        }
    }
}

/**
 * 增强进度条（带缓存进度显示）
 */
@Composable
fun EnhancedProgressSlider(
    progress: Float,
    bufferedProgress: Float,
    isStreamPlayback: Boolean,
    streamProgress: Float,
    isDragging: Boolean,
    onProgressChange: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 使用 Box 实现进度条
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isDragging) 16.dp else 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onDragStart()
                        val xRatio = it.x / size.width
                        onProgressChange(xRatio.coerceIn(0f, 1f))
                        tryAwaitRelease()
                        onDragEnd()
                    }
                )
            }
    ) {
        // 缓存进度背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.2f))
        )
        
        // 已缓存区域（灰色）
        if (bufferedProgress > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedProgress)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }
        
        // 边下边播限制区域（半透明红色）
        if (isStreamPlayback && streamProgress > 0 && streamProgress < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f - streamProgress)
                    .height(4.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color.Red.copy(alpha = 0.1f))
            )
        }
        
        // 播放进度（白色）
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(if (isDragging) 6.dp else 4.dp)
                .align(Alignment.CenterStart)
                .background(Color.White)
        )
        
        // 拖动手柄
        if (isDragging) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (progress * 100).dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * 拖动预览时间
 */
@Composable
fun DragPreviewTime(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.8f),
        modifier = modifier
    ) {
        Text(
            text = formatTime(position),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 边下边播进度徽章
 */
@Composable
fun StreamProgressBadge(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Downloading,
                contentDescription = "下载中",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 锁屏按钮
 */
@Composable
fun LockButton(
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onLockToggle,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
            contentDescription = if (isLocked) "解锁" else "锁定",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 锁屏状态提示
 */
@Composable
fun LockedOverlayHint(
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        visible = false
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(500)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "已锁定",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "点击解锁按钮退出锁屏模式",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 倍速选择器按钮（点击切换下一种倍速）
 */
@Composable
fun SpeedSelectorButton(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speedLevels = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    TextButton(
        onClick = {
            val currentIndex = speedLevels.indexOf(currentSpeed)
            val nextIndex = (currentIndex + 1) % speedLevels.size
            onSpeedSelected(speedLevels[nextIndex])
        },
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = modifier
    ) {
        Text(
            text = "${currentSpeed}x",
            fontSize = 12.sp,
            color = Color.White
        )
    }
}

/**
 * 格式化时间（毫秒 → HH:MM:SS 或 MM:SS）
 */
fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"

    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}