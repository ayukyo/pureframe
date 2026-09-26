package com.pureframe.player.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureframe.player.ui.theme.AppDialogColors
import com.pureframe.player.ui.theme.appDialogColors

/**
 * 画面比例选择对话框
 *
 * 只提供两种选项：
 * - 自动 (AUTO) - 保持原始比例
 * - 填充 (FILL) - 填满屏幕
 */
@Composable
fun AspectRatioDialog(
    currentRatio: String,
    onRatioChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 对话框跟随 App 主题深浅色，而不是播放器固定的深色
    val dialogColors = appDialogColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColors.container,
        titleContentColor = dialogColors.onContainer,
        textContentColor = dialogColors.onContainer,
        title = {
            Text(
                text = "画面比例",
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RatioOption(
                    name = "自动",
                    description = "保持原始比例，不裁切",
                    isSelected = currentRatio == "AUTO",
                    colors = dialogColors,
                    onClick = {
                        onRatioChange("AUTO")
                        onDismiss()
                    }
                )
                RatioOption(
                    name = "填充",
                    description = "填满屏幕，可能裁切画面",
                    isSelected = currentRatio == "FILL",
                    colors = dialogColors,
                    onClick = {
                        onRatioChange("FILL")
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun RatioOption(
    name: String,
    description: String,
    isSelected: Boolean,
    colors: AppDialogColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.selectedContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.radioSelected,
                unselectedColor = colors.radioUnselected
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = name,
                color = colors.onContainer,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = description,
                color = colors.onContainerMuted,
                fontSize = 12.sp
            )
        }
    }
}