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
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = "续播提示",
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
                    color = Color.White.copy(alpha = 0.8f),
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
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "上次观看至 ${formatTime(lastPosition)}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    // 已看完
                    Text(
                        text = "上次已看完此视频",
                        color = Color.White.copy(alpha = 0.8f),
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
                        contentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Text("从头播放")
                }
                
                // 续播（如果没看完）
                if (!isNearEnd) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "续播",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // 重播按钮
                    Button(
                        onClick = onPlayFromStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "重播",
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
@Composable
fun LastPlaybackInfo(
    lastPosition: Long,
    remainingTime: Long,
    progressPercent: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度条
        LinearProgressIndicator(
            progress = { progressPercent / 100f },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 进度百分比
        Text(
            text = "已观看 ${progressPercent}%",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        
        // 剩余时间
        Text(
            text = "剩余 ${formatTime(remainingTime)}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}