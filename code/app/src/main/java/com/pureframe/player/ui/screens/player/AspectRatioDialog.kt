package com.pureframe.player.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
 * 画面比例选择对话框
 * 
 * 提供常用的画面比例选项：
 * - 自动 (AUTO) ← 默认，保持原始比例
 * - 16:9 (标准宽屏)
 * - 4:3 (传统比例)
 * - 填充 (FILL) 填满屏幕，可能裁切
 * - 原始 (ORIGINAL) 强制原始大小
 */
@Composable
fun AspectRatioDialog(
    currentRatio: String,
    onRatioChange: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ratioOptions = listOf(
        AspectRatioOption("AUTO", "自动", "保持原始比例，不裁切"),
        AspectRatioOption("16:9", "16:9", "标准宽屏比例"),
        AspectRatioOption("4:3", "4:3", "传统电视比例"),
        AspectRatioOption("FILL", "填充", "填满屏幕，可能裁切画面"),
        AspectRatioOption("ORIGINAL", "原始", "强制使用原始大小")
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = "画面比例",
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 选项列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ratioOptions) { option ->
                        RatioOptionItem(
                            option = option,
                            isSelected = option.code == currentRatio,
                            onClick = { onRatioChange(option.code) }
                        )
                    }
                }
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
 * 画面比例选项数据类
 */
data class AspectRatioOption(
    val code: String,
    val name: String,
    val description: String
)

/**
 * 比例选项项
 */
@Composable
fun RatioOptionItem(
    option: AspectRatioOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        Color.White.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 单选按钮
        RadioButton(
            selected = isSelected,
            onClick = null,  // null 因为整个 row 是可选择的
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.White,
                unselectedColor = Color.White.copy(alpha = 0.5f)
            )
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 选项名称和描述
        Column {
            Text(
                text = option.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = option.description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        
        // 选中时显示比例预览图标
        if (isSelected) {
            Spacer(modifier = Modifier.weight(1f))
            AspectRatioPreview(
                ratioCode = option.code,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/**
 * 比例预览图标
 */
@Composable
fun AspectRatioPreview(
    ratioCode: String,
    modifier: Modifier = Modifier
) {
    val (widthRatio, heightRatio) = when (ratioCode) {
        "16:9" -> 16f to 9f
        "4:3" -> 4f to 3f
        "FILL" -> 1f to 1f
        "ORIGINAL" -> 2f to 1.5f
        "AUTO" -> 4f to 3f
        else -> 4f to 3f
    }
    
    val aspectRatio = widthRatio / heightRatio
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width((40.dp * aspectRatio).coerceAtMost(40.dp))
                .height(40.dp / aspectRatio.coerceAtLeast(1f))
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
    }
}