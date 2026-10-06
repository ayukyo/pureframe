package com.pureframe.player.ui.screens.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.pureframe.player.data.preferences.AppLanguage
import com.pureframe.player.data.preferences.DecoderType
import com.pureframe.player.data.preferences.ThemeMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pureframe.player.R
import com.pureframe.player.BuildConfig
import com.pureframe.player.download.DownloadDirectories

/**
 * 用系统浏览器打开链接；无浏览器时静默忽略（Tangle 阻止异常上抛）
 */
internal fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

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
    onNavigateToAcknowledgements: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userPreferences by viewModel.userPreferences.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showFolderPickerDialog by remember { mutableStateOf(false) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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

    // 自定义扫描目录选择器（只读权限即可，持久化授权供后续扫描）
    val scanDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    Timber.w(e, "SettingsScreen: 持久化扫描目录权限失败")
                }
                viewModel.addCustomScanDir(uri.toString())
            }
        }
    }

    fun openScanDirPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        scanDirPickerLauncher.launch(intent)
    }

    /**
     * 扫描目录显示名：SAF tree URI 解析成可读路径，解析失败回退 URI 本身
     */
    fun scanDirDisplayName(ctx: android.content.Context, dir: String): String {
        return if (dir.startsWith("content://")) {
            runCatching {
                android.net.Uri.parse(dir).let { uri ->
                    // tree URI 的 path 形如 /tree/primary:Movies，取冒号后的段拼成可读形式
                    val treePath = uri.path?.substringAfter("/tree/") ?: ""
                    val storage = treePath.substringBefore(':')
                    val sub = treePath.substringAfter(':', "")
                    if (sub.isEmpty()) "/$storage" else "/$storage/$sub"
                }
            }.getOrDefault(dir)
        } else dir
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
                SettingsSection(title = stringResource(R.string.settings_section_player)) {
                    // 循环播放
                    SwitchSettingsItem(
                        icon = Icons.Filled.Repeat,
                        title = stringResource(R.string.settings_loop_play),
                        subtitle = stringResource(R.string.settings_loop_play_desc),
                        checked = userPreferences.loopPlay,
                        onCheckedChange = { viewModel.setLoopPlay(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 字幕显示
                    SwitchSettingsItem(
                        icon = Icons.Filled.Subtitles,
                        title = stringResource(R.string.settings_show_subtitle),
                        subtitle = stringResource(R.string.settings_show_subtitle_desc),
                        checked = userPreferences.showSubtitle,
                        onCheckedChange = { viewModel.setShowSubtitle(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 解码器类型
                    ClickableSettingsItem(
                        icon = Icons.Filled.Memory,
                        title = stringResource(R.string.settings_decoder),
                        subtitle = when (userPreferences.decoderType) {
                            DecoderType.HARDWARE -> stringResource(R.string.settings_decoder_hardware_full)
                            DecoderType.SOFTWARE -> stringResource(R.string.settings_decoder_software_full)
                            DecoderType.AUTO -> stringResource(R.string.settings_decoder_auto_full)
                        },
                        onClick = { showDecoderDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 保持屏幕常亮
                    SwitchSettingsItem(
                        icon = Icons.Filled.BrightnessHigh,
                        title = stringResource(R.string.settings_keep_screen_on),
                        subtitle = stringResource(R.string.settings_keep_screen_on_desc),
                        checked = userPreferences.keepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 手势控制
                    SwitchSettingsItem(
                        icon = Icons.Filled.TouchApp,
                        title = stringResource(R.string.settings_brightness_gesture),
                        subtitle = stringResource(R.string.settings_brightness_gesture_desc),
                        checked = userPreferences.brightnessGesture,
                        onCheckedChange = { viewModel.setBrightnessGesture(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    SwitchSettingsItem(
                        icon = Icons.Filled.TouchApp,
                        title = stringResource(R.string.settings_volume_gesture),
                        subtitle = stringResource(R.string.settings_volume_gesture_desc),
                        checked = userPreferences.volumeGesture,
                        onCheckedChange = { viewModel.setVolumeGesture(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    SwitchSettingsItem(
                        icon = Icons.Filled.PictureInPicture,
                        title = stringResource(R.string.settings_system_pip_on_home),
                        subtitle = stringResource(R.string.settings_system_pip_on_home_desc),
                        checked = userPreferences.systemPipOnHome,
                        onCheckedChange = { viewModel.setSystemPipOnHome(it) }
                    )
                }
            }

            // 下载设置
            item {
                SettingsSection(title = stringResource(R.string.settings_section_download)) {
                    // 下载路径
                    ClickableSettingsItem(
                        icon = Icons.Filled.FolderOpen,
                        title = stringResource(R.string.settings_download_path),
                        // 未配置时直接展示实际生效的目录，而不是含糊的"默认位置"
                        subtitle = userPreferences.downloadPath.ifEmpty {
                            DownloadDirectories.resolve(context, null)
                        },
                        onClick = { showFolderPickerDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 最大并行下载数
                    ClickableSettingsItem(
                        icon = Icons.Filled.Download,
                        title = stringResource(R.string.settings_max_concurrent),
                        subtitle = stringResource(
                            R.string.settings_concurrent_desc,
                            userPreferences.maxConcurrentDownloads
                        ),
                        onClick = { showConcurrencyDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // WiFi 自动下载
                    SwitchSettingsItem(
                        icon = Icons.Filled.Wifi,
                        title = stringResource(R.string.settings_wifi_only),
                        subtitle = stringResource(R.string.settings_wifi_only_desc),
                        checked = userPreferences.autoDownloadOnWifi,
                        onCheckedChange = { viewModel.setAutoDownloadOnWifi(it) }
                    )

                    // 下载画质设置已移除：下载源（种子/直链文件）的画质由源文件本身决定，该设置无实际作用
                }
            }

            // 视频库设置：自定义扫描目录
            item {
                SettingsSection(title = stringResource(R.string.settings_section_video_library)) {
                    // 添加扫描目录（SAF）
                    ClickableSettingsItem(
                        icon = Icons.Filled.LibraryAdd,
                        title = stringResource(R.string.settings_scan_dir_add),
                        subtitle = stringResource(R.string.settings_scan_dir_add_desc),
                        onClick = { openScanDirPicker() }
                    )

                    // 已添加的目录列表
                    userPreferences.customScanDirs.forEach { dir ->
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                        ClickableSettingsItem(
                            icon = Icons.Filled.Folder,
                            title = scanDirDisplayName(context, dir),
                            subtitle = stringResource(R.string.settings_scan_dir_remove_hint),
                            onClick = { viewModel.removeCustomScanDir(dir) }
                        )
                    }
                }
            }

            // 界面设置
            item {
                SettingsSection(title = stringResource(R.string.settings_section_interface)) {
                    // 主题模式
                    ClickableSettingsItem(
                        icon = Icons.Filled.DarkMode,
                        title = stringResource(R.string.settings_theme),
                        subtitle = themeModeLabel(userPreferences.themeMode),
                        onClick = { showThemeDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 应用语言
                    ClickableSettingsItem(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.settings_language),
                        subtitle = languageLabel(userPreferences.appLanguage),
                        onClick = { showLanguageDialog = true }
                    )

                }
            }

            // 投屏设置
            item {
                SettingsSection(title = stringResource(R.string.settings_section_cast)) {
                    // 自动连接上次投屏设备
                    SwitchSettingsItem(
                        icon = Icons.Filled.Cast,
                        title = stringResource(R.string.settings_cast_auto_connect),
                        subtitle = stringResource(R.string.settings_cast_auto_connect_desc),
                        checked = userPreferences.castAutoConnect,
                        onCheckedChange = { viewModel.setCastAutoConnect(it) }
                    )
                }
            }

            // 通知与锁屏（PR8）
            item {
                SettingsSection(title = stringResource(R.string.settings_section_notification)) {
                    // 媒体通知总开关：关闭 = 不建 MediaSession/不起前台服务
                    SwitchSettingsItem(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.settings_media_notification),
                        subtitle = stringResource(R.string.settings_media_notification_desc),
                        checked = userPreferences.mediaNotificationEnabled,
                        onCheckedChange = { viewModel.setMediaNotificationEnabled(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    // 锁屏通知内容可见性：visibility=SECRET（部分机型锁屏媒体卡片仍可能显示）
                    SwitchSettingsItem(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.settings_lockscreen_media),
                        subtitle = stringResource(R.string.settings_lockscreen_media_desc),
                        checked = userPreferences.lockscreenMediaVisible,
                        onCheckedChange = { viewModel.setLockscreenMediaVisible(it) }
                    )
                }
            }

            // 关于
            item {
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
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
                                text = stringResource(R.string.settings_version),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Divider()

                    // 作者（点击复制邮箱，方便反馈）
                    val contactEmail = stringResource(R.string.settings_contact_email_value)
                    val copiedMsg = stringResource(R.string.settings_copied_to_clipboard)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("email", contactEmail))
                                android.widget.Toast.makeText(
                                    context,
                                    copiedMsg,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_author),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_author_name) +
                                        " · " + stringResource(R.string.settings_contact_email_value),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Divider()

                    // 项目源码
                    val sourceUrl = stringResource(R.string.settings_source_code_url)
                    ClickableSettingsItem(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.settings_source_code),
                        subtitle = sourceUrl,
                        onClick = { openUrl(context, sourceUrl) }
                    )

                    Divider()

                    // 去 GitHub 点个 Star
                    ClickableSettingsItem(
                        icon = Icons.Filled.Star,
                        title = stringResource(R.string.settings_rate_star),
                        subtitle = stringResource(R.string.settings_rate_star_desc),
                        onClick = { openUrl(context, sourceUrl) }
                    )

                    Divider()

                    // 开源许可（GPL-3.0，点击查看全文）
                    ClickableSettingsItem(
                        icon = Icons.Filled.Gavel,
                        title = stringResource(R.string.settings_license),
                        subtitle = stringResource(R.string.settings_license_value),
                        onClick = { onNavigateToLicenses() }
                    )

                    Divider()

                    // 隐私政策
                    val privacyUrl = stringResource(R.string.settings_privacy_policy_url)
                    ClickableSettingsItem(
                        icon = Icons.Filled.PrivacyTip,
                        title = stringResource(R.string.settings_privacy_policy),
                        subtitle = privacyUrl,
                        onClick = { openUrl(context, privacyUrl) }
                    )

                    Divider()

                    // 致谢（第三方开源库）
                    ClickableSettingsItem(
                        icon = Icons.Filled.Favorite,
                        title = stringResource(R.string.settings_oss_acknowledgements),
                        subtitle = stringResource(R.string.settings_oss_acknowledgements_desc),
                        onClick = { onNavigateToAcknowledgements() }
                    )
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

    // 语言选择对话框
    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = userPreferences.appLanguage,
            onLanguageSelected = {
                viewModel.setAppLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
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
            // 未配置时展示实际生效的目录，让用户知道文件到底存到了哪
            currentPath = userPreferences.downloadPath.ifEmpty {
                DownloadDirectories.resolve(context, null)
            },
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
 * 主题模式的展示文案
 */
@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.SYSTEM -> R.string.settings_theme_system
    }
)

/**
 * 应用语言的展示文案
 */
@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.CHINESE -> R.string.settings_language_chinese
        AppLanguage.ENGLISH -> R.string.settings_language_english
    }
)

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
        title = { Text(stringResource(R.string.speed_title)) },
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
                Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                // 顺序：跟随系统 / 浅色 / 深色
                listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
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
                        Text(themeModeLabel(mode))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * 语言选择对话框
 */
@Composable
private fun LanguageDialog(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    // 顺序：跟随系统 / 简体中文 / English
    val options = listOf(AppLanguage.SYSTEM, AppLanguage.CHINESE, AppLanguage.ENGLISH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                options.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = { onLanguageSelected(language) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(languageLabel(language))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.settings_max_concurrent)) },
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
                        Text(pluralStringResource(R.plurals.settings_concurrent_count, count, count))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.settings_decoder)) },
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
                                text = stringResource(
                                    when (type) {
                                        DecoderType.HARDWARE -> R.string.settings_decoder_hardware
                                        DecoderType.SOFTWARE -> R.string.settings_decoder_software
                                        DecoderType.AUTO -> R.string.settings_decoder_auto
                                    }
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(
                                    when (type) {
                                        DecoderType.HARDWARE -> R.string.settings_decoder_hardware_desc
                                        DecoderType.SOFTWARE -> R.string.settings_decoder_software_desc
                                        DecoderType.AUTO -> R.string.settings_decoder_auto_desc
                                    }
                                ),
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
        title = { Text(stringResource(R.string.settings_download_path)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 当前路径
                if (currentPath.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_current_path, currentPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 私有目录用户在文件管理器里看不到，必须明确告知
                    if (DownloadDirectories.isAppPrivate(currentPath)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_private_dir_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
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
                    Text(stringResource(R.string.settings_browse_folder))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                // 手动输入路径
                Text(
                    text = stringResource(R.string.settings_manual_path),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualPath,
                    onValueChange = { manualPath = it },
                    label = { Text(stringResource(R.string.settings_path_label)) },
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
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
