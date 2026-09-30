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
import androidx.compose.ui.res.stringResource
import com.pureframe.player.R

/**
 * 画面比例选择弹层（底部弹出样式，与投屏设备弹层一致）
 *
 * 只提供两种选项：
 * - 自动 (AUTO) - 保持原始比例
 * - 填充 (FILL) - 填满屏幕
 *
 * 配色跟随 App 主题（appDialogColors 经 LocalAppDarkTheme 透传），
 * 而非播放页的固定深色——浅色模式下弹层为浅色底深色字。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioDialog(
    currentRatio: String,
    onRatioChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColors = appDialogColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = dialogColors.container
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题
            Text(
                text = stringResource(R.string.aspect_title),
                color = dialogColors.onContainer,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            RatioOption(
                name = stringResource(R.string.aspect_auto),
                description = stringResource(R.string.aspect_auto_desc),
                isSelected = currentRatio == "AUTO",
                colors = dialogColors,
                onClick = {
                    onRatioChange("AUTO")
                    onDismiss()
                }
            )
            Spacer(Modifier.height(4.dp))
            RatioOption(
                name = stringResource(R.string.aspect_fill),
                description = stringResource(R.string.aspect_fill_desc),
                isSelected = currentRatio == "FILL",
                colors = dialogColors,
                onClick = {
                    onRatioChange("FILL")
                    onDismiss()
                }
            )
        }
    }
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.selectedContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = description,
                color = colors.onContainerMuted,
                fontSize = 12.sp
            )
        }
    }
}
