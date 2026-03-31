package com.pureframe.player.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 设置页面
 * 
 * 功能：
 * - 下载路径设置
 * - 播放器设置（倍速、缓存大小）
 * - 边下边播阈值设置
 * - 主题设置
 */
@Suppress("UNUSED_PARAMETER")  // 参数未来使用
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),  // 获取设置值
    modifier: Modifier = Modifier
) {
    // TODO: 从 ViewModel 获取设置值
    // val settings by viewModel.settings.collectAsState()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 页面标题
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        // 设置项（暂显示占位）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // TODO: 实现设置项
            // - 下载路径选择
            // - 默认播放倍速
            // - 边下边播阈值
            // - 缓存大小限制
            // - 主题模式
            
            Text(
                text = "⏳ 设置功能开发中...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}