package com.pureframe.player.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureframe.player.ui.theme.AppDialogColors
import com.pureframe.player.ui.theme.appDialogColors
import androidx.compose.ui.res.stringResource
import com.pureframe.player.R

/**
 * 续播提示对话框
 * 
 * 当检测到视频有上次播放记录时，显示此对话框：
 * - 显示上次播放位置
 * - 提供"从头播放"或"续播"选项
 * - 显示上次观看进度百分比
 */
@Composable
fun ResumePlaybackDialog(
    lastPosition: Long,
    duration: Long,
    videoTitle: String,
    onResume: () -> Unit,
    onPlayFromStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressPercent = if (duration > 0) {
        ((lastPosition.toFloat() / duration) * 100).toInt()
    } else 0
    
    val remainingTime = duration - lastPosition
    val isNearEnd = progressPercent >= 95

    // 对话框跟随 App 主题深浅色，而不是播放器固定的深色
    val dialogColors = appDialogColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = dialogColors.container,
        titleContentColor = dialogColors.onContainer,
        textContentColor = dialogColors.onContainer,
        title = {
            Text(
                text = stringResource(R.string.resume_title),
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 视频标题
                Text(
                    text = videoTitle,
                    color = dialogColors.onContainerMuted,
                    fontSize = 14.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 进度指示
                if (!isNearEnd) {
                    LastPlaybackInfo(
                        lastPosition = lastPosition,
                        remainingTime = remainingTime,
                        progressPercent = progressPercent,
                        colors = dialogColors,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = stringResource(R.string.resume_last_at, formatTime(lastPosition)),
                        color = dialogColors.onContainer,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    // 已看完
                    Text(
                        text = stringResource(R.string.resume_finished),
                        color = dialogColors.onContainerMuted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 从头播放
                TextButton(
                    onClick = onPlayFromStart,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = dialogColors.onContainerMuted
                    )
                ) {
                    Text(stringResource(R.string.resume_from_start))
                }
                
                // 续播（如果没看完）
                if (!isNearEnd) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dialogColors.primaryAction,
                            contentColor = dialogColors.onPrimaryAction
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.resume_continue),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // 重播按钮
                    Button(
                        onClick = onPlayFromStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dialogColors.primaryAction,
                            contentColor = dialogColors.onPrimaryAction
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.resume_replay),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}

/**
 * 上次播放信息卡片
 */
@Suppress("UNUSED_PARAMETER")  // 参数未来用于显示上次位置
@Composable
fun LastPlaybackInfo(
    lastPosition: Long,  // 上次播放位置
    remainingTime: Long,
    progressPercent: Int,
    colors: AppDialogColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度条
        LinearProgressIndicator(
            progress = progressPercent / 100f,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(4.dp),
            color = colors.primaryAction,
            trackColor = colors.selectedContainer
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 进度百分比
        Text(
            text = stringResource(R.string.resume_watched, progressPercent),
            color = colors.onContainerMuted,
            fontSize = 12.sp
        )
        
        // 剩余时间
        Text(
            text = stringResource(R.string.resume_remaining, formatTime(remainingTime)),
            color = colors.onContainerMuted,
            fontSize = 12.sp
        )
    }
}