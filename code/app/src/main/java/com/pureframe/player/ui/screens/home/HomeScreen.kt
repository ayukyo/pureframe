package com.pureframe.player.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.pureframe.player.data.preferences.SortBy
import com.pureframe.player.domain.model.Video
import com.pureframe.player.ui.navigation.NavigationState
import androidx.compose.ui.res.stringResource
import com.pureframe.player.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

/**
 * 首页滚动位置持有者
 *
 * 进程内存级单例：切 Tab 离开首页时记录列表位置，
 * 切回时恢复。懒加载网格在导航 restoreState 下无法
 * 自动恢复异步数据的滚动位置，需要手动桥接。
 */
object HomeScrollIndexHolder {
    var index: Int = 0
}

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

    // 排序变化时滚动到顶部（跳过每次重新组合后的首次执行：
    // 切 Tab 返回首页时是全新组合，不应被重置到顶部；
    // 注意不能用 rememberSaveable——切 Tab 返回时标志残留 true 会误触发滚动）
    var sortEffectInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(currentSort) {
        if (sortEffectInitialized) {
            listState.animateScrollToItem(0)
        } else {
            sortEffectInitialized = true
        }
    }

    // 底部导航"本地"按钮：已在首页时再次点击 → 滚动到列表顶部
    LaunchedEffect(Unit) {
        navigationState.scrollToHomeTop.collect {
            if (it) {
                listState.animateScrollToItem(0)
                navigationState.resetHomeScrollFlag()
            }
        }
    }

    // 滚动位置跨 Tab 持久化：切走时记录位置（恢复逻辑在 currentVideos 定义后）
    DisposableEffect(Unit) {
        onDispose {
            HomeScrollIndexHolder.index = listState.firstVisibleItemIndex
        }
    }

    // 运行时媒体读取权限：API 33+ 用 READ_MEDIA_VIDEO，30~32 用 READ_EXTERNAL_STORAGE。
    // 扫描走 MediaStore，分区存储下不需要"所有文件访问"。
    val storagePermissions: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else ->
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
    }

    fun hasMediaPermission(): Boolean = storagePermissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // 权限状态
    var hasStoragePermission by remember { mutableStateOf(hasMediaPermission()) }

    // 运行时权限 launcher（所有 API 级别统一走这里）
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.any { it.value }
        hasStoragePermission = allGranted
        if (allGranted) {
            viewModel.scanVideos()
        }
    }

    // 用户可能绕过 App 直接在系统设置里开/关权限（或从授权页按返回键回来），
    // 所以每次回到前台都重新读一次真实权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = hasMediaPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 首次进入时自动扫描（清理已删除的视频）
    // 用 rememberSaveable 标记：仅本次进程会话扫描一次。
    // 切 Tab 返回首页（restoreState 恢复组合）时不重复扫描——
    // 否则每次切回都会触发 Scanning 状态，导致列表被强制滚回顶部。
    var hasAutoScanned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasStoragePermission && !hasAutoScanned) {
            hasAutoScanned = true
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

    // 滚动位置恢复：返回首页且数据已就绪时，恢复到离开前的位置
    LaunchedEffect(currentVideos.size) {
        val saved = HomeScrollIndexHolder.index
        if (saved > 0 && currentVideos.size > saved && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(saved)
            HomeScrollIndexHolder.index = 0
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
                            placeholder = { Text(stringResource(R.string.home_search_hint)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.home_title),
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
                            contentDescription = if (showSearch) stringResource(R.string.home_close_search) else stringResource(R.string.home_search),
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
                    SortBy.DATE_ASC, SortBy.DATE_DESC -> stringResource(R.string.sort_date)
                    SortBy.SIZE_ASC, SortBy.SIZE_DESC -> stringResource(R.string.sort_size)
                    SortBy.DURATION_ASC, SortBy.DURATION_DESC -> stringResource(R.string.sort_duration)
                    else -> stringResource(R.string.sort_date)
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
                            contentDescription = stringResource(R.string.home_sort),
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
                            } else {
                                // 请求运行时媒体权限（READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE）
                                permissionLauncher.launch(storagePermissions)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.home_rescan),
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
                    hasStoragePermission = hasStoragePermission,
                    onGrantPermission = if (hasStoragePermission) {
                        // 已有权限却没视频，引导按钮没有意义，不应再显示"去授权"
                        null
                    } else {
                        {
                            // 空状态里的"去授权"按钮：与刷新 FAB 相同的权限请求流程
                            permissionLauncher.launch(storagePermissions)
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterTab(
            text = stringResource(R.string.home_filter_all, allCount),
            selected = currentType == HomeViewModel.ListType.ALL,
            onClick = { onTypeChange(HomeViewModel.ListType.ALL) }
        )
        FilterTab(
            text = stringResource(R.string.home_filter_favorite, favoriteCount),
            selected = currentType == HomeViewModel.ListType.FAVORITE,
            onClick = { onTypeChange(HomeViewModel.ListType.FAVORITE) }
        )
        FilterTab(
            text = stringResource(R.string.home_filter_recent, recentCount),
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
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
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

    // DropdownMenu 锚定到它的父布局。之前菜单放在 Card 外面，
    // 锚到了整个网格容器，导致菜单位置与长按的卡片完全无关。
    // 包一层 Box 让菜单锚定到卡片本身。
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
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
                            contentDescription = if (video.isFavorite) stringResource(R.string.home_favorite_remove) else stringResource(R.string.home_favorite_add),
                            tint = if (video.isFavorite) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 长按操作菜单：在 Box 内声明，锚定到卡片左上角，
        // 菜单会出现在被长按的卡片旁边而不是固定在列表底部
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_play)) },
                onClick = {
                    showMenu = false
                    onClick()
                },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp)) }
            )
            DropdownMenuItem(
                text = { Text(if (video.isFavorite) stringResource(R.string.home_favorite_remove) else stringResource(R.string.home_favorite_add)) },
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
                text = { Text(stringResource(R.string.home_menu_detail)) },
                onClick = {
                    showMenu = false
                    showDetailDialog = true
                },
                leadingIcon = { Icon(Icons.Filled.Info, null, modifier = Modifier.size(20.dp)) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_delete), color = MaterialTheme.colorScheme.error) },
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
                    DetailRow(stringResource(R.string.detail_size), video.formattedSize)
                    DetailRow(stringResource(R.string.detail_duration), video.formattedDuration)
                    if (video.resolution.isNotEmpty()) DetailRow(stringResource(R.string.detail_resolution), video.resolution)
                    if (video.format.isNotEmpty()) DetailRow(stringResource(R.string.detail_format), video.format)
                    if (video.playCount > 0) DetailRow(stringResource(R.string.detail_play_count), pluralStringResource(R.plurals.play_count_times, video.playCount, video.playCount))
                    video.lastPlayedAt?.let {
                        DetailRow(stringResource(R.string.detail_last_played), java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it))
                    }
                    DetailRow(stringResource(R.string.detail_path), video.filePath)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.home_delete_title)) },
            text = { Text(stringResource(R.string.home_delete_message, video.title)) },
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
                    Text(stringResource(R.string.home_delete_file))
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
                        Text(stringResource(R.string.home_remove_record_only))
                    }
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
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
    hasStoragePermission: Boolean = false,
    onGrantPermission: (() -> Unit)? = null
) {
    val (message, subMessage, icon) = when {
        isSearching -> Triple(
            stringResource(R.string.home_empty_search_title),
            stringResource(R.string.home_empty_search_desc),
            Icons.Filled.Search
        )
        listType == HomeViewModel.ListType.ALL && !hasStoragePermission -> Triple(
            stringResource(R.string.home_empty_local_title),
            stringResource(R.string.home_empty_local_desc),
            Icons.Filled.VideoLibrary
        )
        listType == HomeViewModel.ListType.ALL -> Triple(
            stringResource(R.string.home_empty_local_title),
            stringResource(R.string.home_empty_local_desc_ok),
            Icons.Filled.VideoLibrary
        )
        listType == HomeViewModel.ListType.FAVORITE -> Triple(
            stringResource(R.string.home_empty_favorite_title),
            stringResource(R.string.home_empty_favorite_desc),
            Icons.Filled.FavoriteBorder
        )
        listType == HomeViewModel.ListType.RECENT -> Triple(
            stringResource(R.string.home_empty_recent_title),
            stringResource(R.string.home_empty_recent_desc),
            Icons.Filled.History
        )
        else -> Triple(stringResource(R.string.home_empty_generic_title), "", Icons.Filled.VideoLibrary)
    }

    // 只有真正缺权限时才显示"去授权"；已授权但单纯没视频时不显示
    val showGrantButton = !isSearching &&
        listType == HomeViewModel.ListType.ALL &&
        !hasStoragePermission &&
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
                    Text(stringResource(R.string.home_grant_permission))
                }
            }
        }
    }
}
