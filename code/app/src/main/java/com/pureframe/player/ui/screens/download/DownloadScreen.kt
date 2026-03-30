package com.pureframe.player.ui.screens.download

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 下载页面
 * 
 * 功能：
 * - 显示下载任务列表
 * - 支持添加新下载（磁链）
 * - 显示下载进度
 * - 支持边下边播
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onDownloadClick: (String) -> Unit,
    viewModel: DownloadViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // TODO: 从 ViewModel 获取下载任务列表
    // val downloadTasks by viewModel.downloadTasks.collectAsState()
    
    Scaffold(
        floatingActionButton = {
            // 添加下载按钮
            FloatingActionButton(
                onClick = {
                    // TODO: 显示添加下载对话框
                    // viewModel.showAddDownloadDialog()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加下载"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 页面标题
            Text(
                text = "下载任务",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            
            // 下载列表（暂显示占位）
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "📥",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "添加磁力链接开始下载",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角按钮添加任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            // TODO: 实现下载列表
            // LazyColumn {
            //     items(downloadTasks) { task ->
            //         DownloadListItem(
            //             task = task,
            //             onPlayClick = { if (task.isStreamable) onDownloadClick(task.id) },
            //             onPauseClick = { viewModel.pauseTask(task.id) },
            //             onDeleteClick = { viewModel.deleteTask(task.id) }
            //         )
            //     }
            // }
        }
    }
    
    // TODO: 显示添加下载对话框
    // if (viewModel.showDialog) {
    //     AddDownloadDialog(
    //         onDismiss = { viewModel.hideAddDownloadDialog() },
    //         onConfirm = { magnetLink ->
    //             viewModel.addDownloadTask(magnetLink)
    //             viewModel.hideAddDownloadDialog()
    //         }
    //     )
    // }
}