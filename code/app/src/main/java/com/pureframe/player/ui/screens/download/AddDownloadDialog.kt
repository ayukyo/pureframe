package com.pureframe.player.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * 添加下载对话框
 * 
 * 用于输入磁力链接创建下载任务
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (magnetLink: String, title: String?) -> Unit,
    initialMagnetLink: String = "",
    modifier: Modifier = Modifier
) {
    var magnetLink by remember { mutableStateOf(TextFieldValue(initialMagnetLink)) }
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 验证磁力链接格式
    fun isValidMagnetLink(link: String): Boolean {
        return link.startsWith("magnet:?xt=urn:btih:") && link.length > 20
    }
    
    // 从磁力链接提取标题
    fun extractTitle(link: String): String? {
        // magnet:?xt=urn:btih:xxx&dn=标题
        val dnIndex = link.indexOf("&dn=")
        if (dnIndex >= 0) {
            val dnStart = dnIndex + 4
            val dnEnd = link.indexOf("&", dnStart)
            return if (dnEnd >= 0) {
                link.substring(dnStart, dnEnd)
            } else {
                link.substring(dnStart)
            }
        }
        return null
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            // 标题
            Text(
                text = "添加磁力链接",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 磁力链接输入
            OutlinedTextField(
                value = magnetLink,
                onValueChange = { 
                    magnetLink = it
                    errorMessage = null
                    // 自动提取标题
                    val extracted = extractTitle(it.text)
                    if (extracted != null && title.text.isEmpty()) {
                        title = TextFieldValue(extracted)
                    }
                },
                label = { Text("磁力链接") },
                placeholder = { Text("magnet:?xt=urn:btih:...") },
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it) } },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 标题输入（可选）
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("任务标题（可选）") },
                placeholder = { Text("将自动从磁力链接提取") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 提示文字
            Text(
                text = "提示：支持磁力链接下载，下载后可边下边播",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 操作按钮
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 添加按钮
                Button(
                    onClick = {
                        val link = magnetLink.text.trim()
                        if (link.isEmpty()) {
                            errorMessage = "请输入磁力链接"
                            return@Button
                        }
                        if (!isValidMagnetLink(link)) {
                            errorMessage = "磁力链接格式不正确"
                            return@Button
                        }
                        onConfirm(link, title.text.trim().takeIf { it.isNotEmpty() })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始下载")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 取消按钮
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("取消")
                }
            }
        }
    }
}