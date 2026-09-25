package com.pureframe.player.ui.screens.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import timber.log.Timber
import com.pureframe.player.data.preferences.DecoderType
import com.pureframe.player.data.preferences.ThemeMode

/**
 * 设置页面
 *
 * 功能：
 * - 下载路径设置
 * - 播放器设置（倍速、缓存大小）
 * - 边下边播阈值设置
 * - 主题设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val userPreferences by viewModel.userPreferences.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showFolderPickerDialog by remember { mutableStateOf(false) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // 文件夹选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // ACTION_OPEN_DOCUMENT_TREE 返回的是 content:// 的树 URI，
                // uri.path 形如 "/tree/primary:PureFrame"，不是真实文件路径，
                // 直接存下来会让下载引擎把目录建到一个不存在的路径上。
                // 这里持久化授予权限，并把可读的显示路径写进设置。
                val flags = result.data?.flags ?: 0
                val persistable = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                try {
                    context.contentResolver.takePersistableUriPermission(uri, persistable)
                } catch (e: Exception) {
                    Timber.w(e, "SettingsScreen: 持久化目录权限失败")
                }
                viewModel.setDownloadPath(resolveDisplayPath(context, uri))
            }
        }
    }

    fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        folderPickerLauncher.launch(intent)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 播放器设置
            item {
                SettingsSection(title = "播放器") {
                    // 循环播放
                    SwitchSettingsItem(
                        icon = Icons.Filled.Repeat,
                        title = "循环播放",
                        subtitle = "播放结束时自动重播",
                        checked = userPreferences.loopPlay,
                        onCheckedChange = { viewModel.setLoopPlay(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 字幕显示
                    SwitchSettingsItem(
                        icon = Icons.Filled.Subtitles,
                        title = "显示字幕",
                        subtitle = "自动显示已下载的字幕",
                        checked = userPreferences.showSubtitle,
                        onCheckedChange = { viewModel.setShowSubtitle(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 解码器类型
                    ClickableSettingsItem(
                        icon = Icons.Filled.Memory,
                        title = "解码方式",
                        subtitle = when (userPreferences.decoderType) {
                            DecoderType.HARDWARE -> "硬解（硬件加速）"
                            DecoderType.SOFTWARE -> "软解（软件解码）"
                            DecoderType.AUTO -> "自动（优先硬解）"
                        },
                        onClick = { showDecoderDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 保持屏幕常亮
                    SwitchSettingsItem(
                        icon = Icons.Filled.BrightnessHigh,
                        title = "播放时保持常亮",
                        subtitle = "播放视频时防止屏幕熄灭",
                        checked = userPreferences.keepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 手势控制
                    SwitchSettingsItem(
                        icon = Icons.Filled.TouchApp,
                        title = "亮度手势",
                        subtitle = "在屏幕左侧上下滑动调节亮度",
                        checked = userPreferences.brightnessGesture,
                        onCheckedChange = { viewModel.setBrightnessGesture(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    SwitchSettingsItem(
                        icon = Icons.Filled.TouchApp,
                        title = "音量手势",
                        subtitle = "在屏幕右侧上下滑动调节音量",
                        checked = userPreferences.volumeGesture,
                        onCheckedChange = { viewModel.setVolumeGesture(it) }
                    )
                }
            }

            // 下载设置
            item {
                SettingsSection(title = "下载") {
                    // 下载路径
                    ClickableSettingsItem(
                        icon = Icons.Filled.FolderOpen,
                        title = "下载保存位置",
                        subtitle = userPreferences.downloadPath.ifEmpty { "默认位置" },
                        onClick = { showFolderPickerDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 最大并行下载数
                    ClickableSettingsItem(
                        icon = Icons.Filled.Download,
                        title = "最大并行下载数",
                        subtitle = "${userPreferences.maxConcurrentDownloads} 个任务同时下载",
                        onClick = { showConcurrencyDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // WiFi 自动下载
                    SwitchSettingsItem(
                        icon = Icons.Filled.Wifi,
                        title = "仅 Wi-Fi 下载",
                        subtitle = "移动网络下暂停自动下载",
                        checked = userPreferences.autoDownloadOnWifi,
                        onCheckedChange = { viewModel.setAutoDownloadOnWifi(it) }
                    )

                    // 下载画质设置已移除：下载源（种子/直链文件）的画质由源文件本身决定，该设置无实际作用
                }
            }

            // 界面设置
            item {
                SettingsSection(title = "界面") {
                    // 主题模式
                    ClickableSettingsItem(
                        icon = Icons.Filled.DarkMode,
                        title = "主题模式",
                        subtitle = when (userPreferences.themeMode) {
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                            ThemeMode.SYSTEM -> "跟随系统"
                        },
                        onClick = { showThemeDialog = true }
                    )

                }
            }

            // 关于
            item {
                SettingsSection(title = "关于") {
                    // 版本信息（仅展示，无箭头）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "版本信息",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 底部留白
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 播放速度对话框
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = userPreferences.defaultPlaySpeed,
            onSpeedSelected = {
                viewModel.setDefaultPlaySpeed(it)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // 主题模式对话框
    if (showThemeDialog) {
        ThemeModeDialog(
            currentMode = userPreferences.themeMode,
            onModeSelected = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // 解码器类型对话框
    if (showDecoderDialog) {
        DecoderTypeDialog(
            currentType = userPreferences.decoderType,
            onTypeSelected = {
                viewModel.setDecoderType(it)
                showDecoderDialog = false
            },
            onDismiss = { showDecoderDialog = false }
        )
    }

    // 最大并行下载数对话框
    if (showConcurrencyDialog) {
        ConcurrencyDialog(
            currentCount = userPreferences.maxConcurrentDownloads,
            onCountSelected = {
                viewModel.setMaxConcurrentDownloads(it)
                showConcurrencyDialog = false
            },
            onDismiss = { showConcurrencyDialog = false }
        )
    }

    // 下载路径选择对话框
    if (showFolderPickerDialog) {
        FolderPickerDialog(
            currentPath = userPreferences.downloadPath,
            onPathSelected = { path ->
                viewModel.setDownloadPath(path)
                showFolderPickerDialog = false
            },
            onBrowseClick = {
                openFolderPicker()
            },
            onDismiss = { showFolderPickerDialog = false }
        )
    }
}

/**
 * 把 ACTION_OPEN_DOCUMENT_TREE 返回的树 URI 转成可读路径
 *
 * 例：content://.../tree/primary%3APureFrame -> /storage/emulated/0/PureFrame
 * 这个路径只用于展示与「是否可写」的判断，真正的写入由下载侧校验后决定。
 */
private fun resolveDisplayPath(context: android.content.Context, uri: android.net.Uri): String {
    val raw = uri.path ?: return uri.toString()
    val relative = raw.substringAfter(':', "").trim()
    if (relative.isEmpty()) return uri.toString()
    val primary = android.os.Environment.getExternalStorageDirectory()
    return "$primary/$relative"
}

/**
 * 设置区块
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * 开关设置项
 */
@Composable
private fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * 点击设置项
 */
@Composable
private fun ClickableSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 播放速度对话框
 */
@Composable
private fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放速度") },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = speed == currentSpeed,
                            onClick = { onSpeedSelected(speed) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${speed}x")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 主题模式对话框
 */
@Composable
private fun ThemeModeDialog(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题模式") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (mode) {
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                                ThemeMode.SYSTEM -> "跟随系统"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 最大并行下载数对话框
 */
@Composable
private fun ConcurrencyDialog(
    currentCount: Int,
    onCountSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 1~5 个并发：过多会争抢带宽反而变慢
    val options = listOf(1, 2, 3, 4, 5)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最大并行下载数") },
        text = {
            Column {
                options.forEach { count ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCountSelected(count) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = count == currentCount,
                            onClick = { onCountSelected(count) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$count 个")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 解码器类型对话框
 */
@Composable
private fun DecoderTypeDialog(
    currentType: DecoderType,
    onTypeSelected: (DecoderType) -> Unit,
    onDismiss: () -> Unit
) {
    // 按自动、硬解、软解排序
    val sortedTypes = listOf(DecoderType.AUTO, DecoderType.HARDWARE, DecoderType.SOFTWARE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解码方式") },
        text = {
            Column {
                sortedTypes.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTypeSelected(type)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = type == currentType,
                            onClick = {
                                onTypeSelected(type)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when (type) {
                                    DecoderType.HARDWARE -> "硬解"
                                    DecoderType.SOFTWARE -> "软解"
                                    DecoderType.AUTO -> "自动"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when (type) {
                                    DecoderType.HARDWARE -> "硬件加速，省电省资源"
                                    DecoderType.SOFTWARE -> "软件解码，兼容性更好"
                                    DecoderType.AUTO -> "优先硬解，失败后自动切换软解"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * 下载路径选择对话框
 */
@Composable
private fun FolderPickerDialog(
    currentPath: String,
    onPathSelected: (String) -> Unit,
    onBrowseClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var manualPath by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载保存位置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 当前路径
                if (currentPath.isNotEmpty()) {
                    Text(
                        text = "当前: $currentPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 浏览文件夹按钮
                OutlinedButton(
                    onClick = onBrowseClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("浏览文件夹")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                // 手动输入路径
                Text(
                    text = "或手动输入路径",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualPath,
                    onValueChange = { manualPath = it },
                    label = { Text("路径") },
                    placeholder = { Text("/storage/emulated/0/PureFrame/downloads") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPathSelected(manualPath) },
                enabled = manualPath.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
