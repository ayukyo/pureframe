package com.pureframe.player.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 播放速度选择对话框
 * 
 * 提供常用的播放速度选项：
 * - 0.5x (慢速)
 * - 0.75x (较慢)
 * - 1.0x (正常) ← 默认
 * - 1.25x (较快)
 * - 1.5x (快速)
 * - 2.0x (超快)
 * - 3.0x (极速)
 */
@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedOptions = listOf(
        0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = "播放速度",
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 当前速度显示
                SpeedDisplay(
                    currentSpeed = currentSpeed,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
                
                // 速度选项网格
                SpeedOptionsGrid(
                    options = speedOptions,
                    currentSpeed = currentSpeed,
                    onSpeedChange = onSpeedChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "确定",
                    color = Color.White
                )
            }
        }
    )
}

/**
 * 当前速度显示
 */
@Composable
fun SpeedDisplay(
    currentSpeed: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.2f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${currentSpeed}x",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = getSpeedDescription(currentSpeed),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 速度选项网格
 */
@Composable
fun SpeedOptionsGrid(
    options: List<Float>,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { speed ->
            SpeedOptionButton(
                speed = speed,
                isSelected = speed == currentSpeed,
                onClick = { onSpeedChange(speed) }
            )
        }
    }
}

/**
 * 速度选项按钮
 */
@Composable
fun SpeedOptionButton(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.15f)
    }
    
    val textColor = if (isSelected) {
        Color.Black
    } else {
        Color.White
    }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = modifier.size(60.dp, 50.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "${speed}x",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * 获取速度描述
 */
fun getSpeedDescription(speed: Float): String {
    return when (speed) {
        0.5f -> "慢速播放"
        0.75f -> "较慢播放"
        1.0f -> "正常速度"
        1.25f -> "较快播放"
        1.5f -> "快速播放"
        2.0f -> "超快播放"
        3.0f -> "极速播放"
        else -> "自定义速度"
    }
}