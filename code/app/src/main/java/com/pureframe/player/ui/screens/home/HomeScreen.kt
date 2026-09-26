package com.pureframe.player.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.pureframe.player.data.preferences.SortBy
import com.pureframe.player.domain.model.Video
import com.pureframe.player.ui.navigation.NavigationState

/**
 * 本地视频页面
 *
 * 功能：
 * - 显示本地视频列表
 * - 支持文件夹分类
 * - 点击视频跳转播放器
 * - 扫描本地视频
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    navigationState: NavigationState,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val favoriteVideos by viewModel.favoriteVideos.collectAsState()
    val recentVideos by viewModel.recentVideos.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val currentSort by viewModel.currentSort.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current

    // 列表滚动状态
    val listState = rememberLazyStaggeredGridState()

    // 扫描时滚动到顶部
    LaunchedEffect(scanState) {
        if (scanState is HomeViewModel.ScanState.Scanning) {
            listState.animateScrollToItem(0)
        }
    }

    // 排序变化时滚动到顶部
    LaunchedEffect(currentSort) {
        listState.animateScrollToItem(0)
    }

    // 是否需要"所有文件访问"（Android 11+ 分区存储下扫描全盘视频必需）
    val needManageStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    // 权限状态：API 30+ 用"所有文件访问"判断；低版本用 READ_EXTERNAL_STORAGE
    var hasStoragePermission by remember {
        mutableStateOf(
            if (needManageStorage) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // 普通运行时权限 launcher（API < 30）
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        hasStoragePermission = allGranted
        if (allGranted) {
            viewModel.scanVideos()
        }
    }

    // "所有文件访问"设置页返回 launcher（API 30+）
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = !needManageStorage || Environment.isExternalStorageManager()
        hasStoragePermission = granted
        if (granted) {
            viewModel.scanVideos()
        }
    }

    // 首次进入时自动扫描（清理已删除的视频）
    LaunchedEffect(Unit) {
        if (hasStoragePermission) {
            kotlinx.coroutines.delay(500) // 等待页面渲染完成
            viewModel.scanVideos()
        }
    }

    // 当前显示的列表
    val currentVideos: List<Video> = when {
        showSearch && searchQuery.isNotEmpty() -> searchResults
        else -> when (uiState.listType) {
            HomeViewModel.ListType.ALL -> allVideos
            HomeViewModel.ListType.FAVORITE -> favoriteVideos
            HomeViewModel.ListType.RECENT -> recentVideos
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchVideos(it)
                            },
                            placeholder = { Text("搜索视频...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "本地视频",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    // 搜索按钮
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (showSearch) "关闭搜索" else "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 排序按钮（56.dp，与刷新按钮一样大，上图标下标签）
                val sortIcon = when (currentSort) {
                    SortBy.DATE_ASC, SortBy.DATE_DESC -> Icons.Filled.Event
                    SortBy.SIZE_ASC, SortBy.SIZE_DESC -> Icons.Filled.Storage
                    SortBy.DURATION_ASC, SortBy.DURATION_DESC -> Icons.Filled.Schedule
                    else -> Icons.Filled.Event
                }
                val sortLabel = when (currentSort) {
                    SortBy.DATE_ASC, SortBy.DATE_DESC -> "日期"
                    SortBy.SIZE_ASC, SortBy.SIZE_DESC -> "大小"
                    SortBy.DURATION_ASC, SortBy.DURATION_DESC -> "时长"
                    else -> "日期"
                }

                FloatingActionButton(
                    onClick = { viewModel.toggleSort() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = sortIcon,
                            contentDescription = "排序",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = sortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }

                // 扫描状态提示
                if (scanState is HomeViewModel.ScanState.Scanning) {
                    FloatingActionButton(
                        onClick = { },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            if (hasStoragePermission) {
                                viewModel.scanVideos()
                            } else if (needManageStorage) {
                                // API 30+：引导开启"所有文件访问"
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        "package:${context.packageName}".toUri()
                                    )
                                    manageStorageLauncher.launch(intent)
                                } catch (e: Exception) {
                                    // 部分机型无此页面，回退到通用应用设置
                                    manageStorageLauncher.launch(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            "package:${context.packageName}".toUri())
                                    )
                                }
                            } else {
                                // API < 30：请求运行时存储权限
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新扫描",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 筛选标签行
            if (!showSearch) {
                FilterTabs(
                    currentType = uiState.listType,
                    allCount = allVideos.size,
                    favoriteCount = favoriteVideos.size,
                    recentCount = recentVideos.size,
                    onTypeChange = { viewModel.setListType(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 视频列表
            if (currentVideos.isEmpty()) {
                EmptyVideosState(
                    listType = uiState.listType,
                    isSearching = showSearch && searchQuery.isNotEmpty(),
                    onGrantPermission = {
                        // 空状态里的"去授权"按钮：与刷新 FAB 相同的权限引导流程
                        if (needManageStorage) {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    "package:${context.packageName}".toUri()
                                )
                                manageStorageLauncher.launch(intent)
                            } catch (e: Exception) {
                                manageStorageLauncher.launch(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        "package:${context.packageName}".toUri())
                                )
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    }
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    items(currentVideos, key = { it.id }) { video ->
                        VideoGridItem(
                            video = video,
                            onClick = { onVideoClick(video.id.toString()) },
                            onFavoriteClick = {
                                viewModel.toggleFavorite(video.id, video.isFavorite)
                            },
                            onDeleteClick = { deleteFile ->
                                viewModel.deleteVideo(video.id, deleteFile)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 筛选标签行
 */
@Composable
private fun FilterTabs(
    currentType: HomeViewModel.ListType,
    allCount: Int,
    favoriteCount: Int,
    recentCount: Int,
    onTypeChange: (HomeViewModel.ListType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterTab(
            text = "全部 ($allCount)",
            selected = currentType == HomeViewModel.ListType.ALL,
            onClick = { onTypeChange(HomeViewModel.ListType.ALL) }
        )
        FilterTab(
            text = "收藏 ($favoriteCount)",
            selected = currentType == HomeViewModel.ListType.FAVORITE,
            onClick = { onTypeChange(HomeViewModel.ListType.FAVORITE) }
        )
        FilterTab(
            text = "最近 ($recentCount)",
            selected = currentType == HomeViewModel.ListType.RECENT,
            onClick = { onTypeChange(HomeViewModel.ListType.RECENT) }
        )
    }
}

/**
 * 筛选标签项
 */
@Composable
private fun FilterTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 视频网格项（瀑布流两列布局）
 *
 * 单击播放；长按弹出操作菜单（播放/收藏/详情/删除）
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: Video,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDeleteClick: (deleteFile: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 根据分辨率决定显示方式
    val isPortrait = video.resolution.isNotEmpty() &&
        video.resolution.contains("x") &&
        (video.resolution.split("x").getOrNull(0)?.toIntOrNull() ?: 0) <
        (video.resolution.split("x").getOrNull(1)?.toIntOrNull() ?: 0)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // 视频缩略图（自适应横竖屏）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isPortrait) 9f / 16f else 16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(video.filePath.toUri())
                        .decoderFactory { result, options, _ ->
                            VideoFrameDecoder(result.source, options)
                        }
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 左下角时长
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            // 视频信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = video.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 收藏按钮
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (video.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (video.isFavorite) "取消收藏" else "收藏",
                        tint = if (video.isFavorite) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // 长按操作菜单（锚定在卡片上）
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("播放") },
            onClick = {
                showMenu = false
                onClick()
            },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text(if (video.isFavorite) "取消收藏" else "收藏") },
            onClick = {
                showMenu = false
                onFavoriteClick()
            },
            leadingIcon = {
                Icon(
                    if (video.isFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                    null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        DropdownMenuItem(
            text = { Text("详情") },
            onClick = {
                showMenu = false
                showDetailDialog = true
            },
            leadingIcon = { Icon(Icons.Filled.Info, null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
            onClick = {
                showMenu = false
                showDeleteDialog = true
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Delete,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }

    // 视频详情对话框
    if (showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow("大小", video.formattedSize)
                    DetailRow("时长", video.formattedDuration)
                    if (video.resolution.isNotEmpty()) DetailRow("分辨率", video.resolution)
                    if (video.format.isNotEmpty()) DetailRow("格式", video.format)
                    if (video.playCount > 0) DetailRow("播放次数", "${video.playCount} 次")
                    video.lastPlayedAt?.let {
                        DetailRow("最近播放", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it))
                    }
                    DetailRow("路径", video.filePath)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除视频") },
            text = { Text("确定要删除「${video.title}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteClick(true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteClick(false)
                        }
                    ) {
                        Text("仅移除记录")
                    }
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}

/**
 * 详情对话框内的键值对行
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 空状态显示
 */
@Composable
private fun EmptyVideosState(
    listType: HomeViewModel.ListType,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
    onGrantPermission: (() -> Unit)? = null
) {
    val (message, subMessage, icon) = when {
        isSearching -> Triple("未找到视频", "尝试其他关键词搜索", Icons.Filled.Search)
        listType == HomeViewModel.ListType.ALL -> Triple("暂无本地视频", "授权后自动扫描全盘视频", Icons.Filled.VideoLibrary)
        listType == HomeViewModel.ListType.FAVORITE -> Triple("暂无收藏", "点击视频右侧的心形图标收藏", Icons.Filled.FavoriteBorder)
        listType == HomeViewModel.ListType.RECENT -> Triple("暂无最近播放", "播放过的视频会显示在这里", Icons.Filled.History)
        else -> Triple("暂无视频", "", Icons.Filled.VideoLibrary)
    }

    // 全部视频为空且未在搜索时，显示"去授权"引导按钮
    val showGrantButton = !isSearching &&
        listType == HomeViewModel.ListType.ALL &&
        onGrantPermission != null

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (subMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            if (showGrantButton && onGrantPermission != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onGrantPermission) {
                    Text("去授权")
                }
            }
        }
    }
}
