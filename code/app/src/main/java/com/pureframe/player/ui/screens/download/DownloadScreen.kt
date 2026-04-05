package com.pureframe.player.ui.screens.download

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.ui.navigation.NavigationState
import com.pureframe.player.ui.theme.Background
import timber.log.Timber
import com.pureframe.player.ui.theme.SurfaceVariant

/**
 * 下载页面
 * 
 * 功能：
 * - 显示下载任务列表（支持筛选）
 * - 支持添加新下载（磁链）
 * - 显示下载进度和速度
 * - 支持边下边播
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onPlayClick: (String) -> Unit,
    navigationState: NavigationState,
    viewModel: DownloadViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val allDownloads by viewModel.allDownloads.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val completedDownloads by viewModel.completedDownloads.collectAsState()
    val failedDownloads by viewModel.failedDownloads.collectAsState()
    val pendingMetadata by viewModel.pendingMetadata.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    // 列表滚动状态
    val listState = rememberLazyListState()

    // 监听导航状态，请求滚动到顶部
    LaunchedEffect(Unit) {
        navigationState.scrollToDownloadTop.collect {
            if (it) {
                listState.animateScrollToItem(0)
                navigationState.resetDownloadScrollFlag()
            }
        }
    }

    // Torrent 文件选择器
    val torrentFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // 获取文件路径
                val filePath = uri.path
                if (filePath != null) {
                    viewModel.addTorrentFileDownload(filePath)
                }
            }
        }
    }

    fun openTorrentFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/x-bittorrent",
                "application/octet-stream"
            ))
        }
        torrentFilePicker.launch(intent)
    }

    // 当前显示的列表
    val currentDownloads: List<DownloadTask> = when (uiState.listType) {
        DownloadViewModel.ListType.ALL -> allDownloads
        DownloadViewModel.ListType.ACTIVE -> activeDownloads
        DownloadViewModel.ListType.COMPLETED -> completedDownloads
        DownloadViewModel.ListType.FAILED -> failedDownloads
    }
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "下载任务",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                ),
                actions = {
                    // 篮选按钮
                    IconButton(onClick = { /* 显示筛选菜单 */ }) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "筛选",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Torrent 文件选择按钮
                    IconButton(onClick = { openTorrentFilePicker() }) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "选择 Torrent 文件",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 清除已完成按钮
                    if (completedDownloads.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearCompletedDownloads() }) {
                            Icon(
                                imageVector = Icons.Filled.ClearAll,
                                contentDescription = "清除已完成",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加下载",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 筛选标签行
            FilterTabs(
                currentType = uiState.listType,
                allCount = allDownloads.size,
                activeCount = activeDownloads.size,
                completedCount = completedDownloads.size,
                failedCount = failedDownloads.size,
                onTypeChange = { viewModel.setListType(it) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 下载列表
            if (currentDownloads.isEmpty()) {
                // 空状态
                EmptyDownloadsState(
                    listType = uiState.listType
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 80.dp // FAB 空间
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentDownloads, key = { it.id }) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPlayClick = {
                                // 播放边下边播或已完成文件
                                if (task.canStream || task.isCompleted) {
                                    // 使用 savePath + fileName 作为播放路径
                                    val playPath = "${task.savePath}/${task.fileName}"
                                    onPlayClick(playPath)
                                }
                            },
                            onPauseClick = { viewModel.pauseDownload(task.id) },
                            onResumeClick = { viewModel.startDownload(task.id) },
                            onDeleteClick = { deleteFiles -> 
                                viewModel.deleteDownload(task, deleteFiles)
                            }
                        )
                    }
                }
            }
        }
    }

    // 添加下载对话框
    if (showAddDialog) {
        AddDownloadDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { magnetLink, title ->
                Timber.d("DownloadScreen: onConfirm called - magnetLink=$magnetLink")
                viewModel.addDownloadTask(magnetLink, title)
                showAddDialog = false
            }
        )
    }

    // 加载对话框（获取 metadata 时显示）
    if (uiState.isAddingTask) {
        LoadingDialog(
            message = "正在获取文件列表...",
            onDismiss = {
                // 取消操作
                viewModel.cancelFileSelection()
            }
        )
    }

    // 文件选择对话框
    pendingMetadata?.let { metadata ->
        FileSelectionDialog(
            metadata = metadata,
            onConfirm = { selectedIndices ->
                viewModel.confirmFileSelection(selectedIndices)
            },
            onDismiss = {
                viewModel.cancelFileSelection()
            }
        )
    }
}

/**
 * 筛选标签行
 */
/**
 * 加载对话框
 * 用于显示获取 metadata 期间的加载状态
 */
@Composable
private fun LoadingDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Text(text = message)
            }
        }
    )
}

@Composable
private fun FilterTabs(
    currentType: DownloadViewModel.ListType,
    allCount: Int,
    activeCount: Int,
    completedCount: Int,
    failedCount: Int,
    onTypeChange: (DownloadViewModel.ListType) -> Unit,
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
            selected = currentType == DownloadViewModel.ListType.ALL,
            onClick = { onTypeChange(DownloadViewModel.ListType.ALL) }
        )
        FilterTab(
            text = "活跃 ($activeCount)",
            selected = currentType == DownloadViewModel.ListType.ACTIVE,
            onClick = { onTypeChange(DownloadViewModel.ListType.ACTIVE) }
        )
        FilterTab(
            text = "已完成 ($completedCount)",
            selected = currentType == DownloadViewModel.ListType.COMPLETED,
            onClick = { onTypeChange(DownloadViewModel.ListType.COMPLETED) }
        )
        if (failedCount > 0) {
            FilterTab(
                text = "失败 ($failedCount)",
                selected = currentType == DownloadViewModel.ListType.FAILED,
                onClick = { onTypeChange(DownloadViewModel.ListType.FAILED) }
            )
        }
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
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else SurfaceVariant,
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
 * 空状态显示
 */
@Composable
private fun EmptyDownloadsState(
    listType: DownloadViewModel.ListType,
    modifier: Modifier = Modifier
) {
    val message = when (listType) {
        DownloadViewModel.ListType.ALL -> "暂无下载任务"
        DownloadViewModel.ListType.ACTIVE -> "没有正在下载的任务"
        DownloadViewModel.ListType.COMPLETED -> "没有已完成的任务"
        DownloadViewModel.ListType.FAILED -> "没有失败的任务"
    }
    
    val subMessage = when (listType) {
        DownloadViewModel.ListType.ALL -> "点击右下角按钮添加下载"
        DownloadViewModel.ListType.ACTIVE -> "添加新任务开始下载"
        DownloadViewModel.ListType.COMPLETED -> "下载完成的任务会显示在这里"
        DownloadViewModel.ListType.FAILED -> "出错的任务会显示在这里"
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // 图标
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 主文字
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 次文字
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
        }
    }
}