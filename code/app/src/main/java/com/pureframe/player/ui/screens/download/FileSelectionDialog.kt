package com.pureframe.player.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pureframe.player.download.TorrentFileInfo
import com.pureframe.player.download.TorrentMetadataInfo
import com.pureframe.player.ui.theme.AppTheme
import com.pureframe.player.ui.theme.onSurfaceMuted
import com.pureframe.player.ui.theme.surfaceHigh
import androidx.compose.ui.res.stringResource
import com.pureframe.player.R

/**
 * 文件选择对话框
 *
 * 用于在开始下载前选择要下载的文件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSelectionDialog(
    metadata: TorrentMetadataInfo,
    onConfirm: (selectedIndices: Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndices by remember { mutableStateOf(
        // 默认选择所有视频文件
        metadata.videoFiles.map { it.index }.toSet()
    ) }

    val selectedFiles = metadata.files.filter { it.index in selectedIndices }
    val totalSelectedSize = selectedFiles.sumOf { it.size }
    val ext = AppTheme.extendedColors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.file_select_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = metadata.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                // 统计信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.file_selected_count, selectedIndices.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatSize(totalSelectedSize) + " / " + metadata.formattedTotalSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        // 全选
                        TextButton(
                            onClick = {
                                selectedIndices = metadata.files.map { it.index }.toSet()
                            }
                        ) {
                            Text(stringResource(R.string.action_select_all))
                        }
                        // 取消全选
                        TextButton(
                            onClick = {
                                selectedIndices = emptySet()
                            }
                        ) {
                            Text(stringResource(R.string.action_deselect_all))
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // 文件列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(metadata.files) { file ->
                        FileSelectionItem(
                            file = file,
                            isSelected = file.index in selectedIndices,
                            onToggle = {
                                selectedIndices = if (file.index in selectedIndices) {
                                    selectedIndices - file.index
                                } else {
                                    selectedIndices + file.index
                                }
                            }
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }

                    Button(
                        onClick = { onConfirm(selectedIndices) },
                        modifier = Modifier.weight(1f),
                        enabled = selectedIndices.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_start_download))
                    }
                }
            }
        }
    }
}

/**
 * 文件选择项
 */
@Composable
private fun FileSelectionItem(
    file: TorrentFileInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = AppTheme.extendedColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 选择框
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 文件图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (file.isVideo) MaterialTheme.colorScheme.surfaceHigh else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (file.isVideo) Icons.Filled.VideoFile else Icons.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isVideo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 文件信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 文件大小
                Text(
                    text = file.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 分辨率（如果有）
                if (file.resolution != null) {
                    Text(
                        text = file.resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.success
                    )
                }

                // 文件类型
                if (!file.isVideo) {
                    val extension = file.name.substringAfterLast('.', "").uppercase()
                    if (extension.isNotEmpty()) {
                        Text(
                            text = extension,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.info
                        )
                    }
                }
            }
        }
    }
}

/**
 * 格式化文件大小
 */
private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
