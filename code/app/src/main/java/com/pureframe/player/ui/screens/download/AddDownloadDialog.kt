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
 * 链接类型
 */
enum class LinkType {
    MAGNET,    // 磁力链接
    HTTP,      // HTTP/直链
    UNKNOWN    // 未知类型
}

/**
 * 添加下载对话框
 *
 * 用于输入磁力链接或直链创建下载任务
 * 自动检测链接类型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, title: String?, linkType: LinkType) -> Unit,
    initialUrl: String = "",
    modifier: Modifier = Modifier
) {
    var urlInput by remember(initialUrl) { mutableStateOf(TextFieldValue(initialUrl)) }
    var title by remember(initialUrl) { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 标题是否为自动填充（用户未手动修改过）。用于在继续输入 URL 时持续刷新标题，
    // 否则会停在首个能被解析的中间态（如 ...1MB.），导致扩展名被吞。
    var titleAutoFilled by remember { mutableStateOf(false) }

    // 检测链接类型
    fun detectLinkType(link: String): LinkType {
        return when {
            link.startsWith("magnet:?xt=urn:btih:") -> LinkType.MAGNET
            link.startsWith("http://") || link.startsWith("https://") -> LinkType.HTTP
            else -> LinkType.UNKNOWN
        }
    }

    // 验证磁力链接格式
    fun isValidMagnetLink(link: String): Boolean {
        return link.startsWith("magnet:?xt=urn:btih:") && link.length > 20
    }

    // 验证 HTTP 链接格式
    fun isValidHttpUrl(link: String): Boolean {
        return (link.startsWith("http://") || link.startsWith("https://")) && link.length > 10
    }

    // 从磁力链接提取标题
    fun extractTitleFromMagnet(link: String): String? {
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

    // 从 URL 提取文件名作为标题
    fun extractTitleFromUrl(url: String): String? {
        return try {
            val path = java.net.URL(url).path
            val fileName = path.substringAfterLast("/").substringBefore("?")
            if (fileName.isNotEmpty() && (fileName.contains(".") || fileName.contains("%"))) {
                java.net.URLDecoder.decode(fileName, "UTF-8")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val currentLinkType = detectLinkType(urlInput.text)
    val inputLabel = if (currentLinkType == LinkType.MAGNET) "磁力链接" else "下载链接"
    val inputPlaceholder = if (currentLinkType == LinkType.MAGNET) "magnet:?xt=urn:btih:..." else "https://example.com/file.mp4"
    val dialogTitle = if (currentLinkType == LinkType.MAGNET) "添加磁力链接" else "添加下载链接"

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
                text = dialogTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 链接输入
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    errorMessage = null
                    // 自动提取标题
                    val linkType = detectLinkType(it.text)
                    val extracted = when (linkType) {
                        LinkType.MAGNET -> extractTitleFromMagnet(it.text)
                        LinkType.HTTP -> extractTitleFromUrl(it.text)
                        else -> null
                    }
                    // 仅在用户没有手动改过标题时自动跟随，持续刷新为完整文件名
                    if (extracted != null && (title.text.isEmpty() || titleAutoFilled)) {
                        title = TextFieldValue(extracted)
                        titleAutoFilled = true
                    }
                },
                label = { Text(inputLabel) },
                placeholder = { Text(inputPlaceholder) },
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
                onValueChange = {
                    title = it
                    // 用户手动编辑过标题，之后不再自动覆盖
                    titleAutoFilled = false
                },
                label = { Text("任务标题（可选）") },
                placeholder = { Text("将自动提取") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 提示文字
            val hintText = when (currentLinkType) {
                LinkType.MAGNET -> "提示：支持磁力链接下载，下载后可边下边播"
                LinkType.HTTP -> "提示：直链下载，速度更快"
                else -> "提示：支持磁力链接和直链下载"
            }
            Text(
                text = hintText,
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
                        val link = urlInput.text.trim()
                        val linkType = detectLinkType(link)
                        when {
                            link.isEmpty() -> {
                                errorMessage = "请输入下载链接"
                                return@Button
                            }
                            linkType == LinkType.UNKNOWN -> {
                                errorMessage = "不支持的链接类型"
                                return@Button
                            }
                            linkType == LinkType.MAGNET && !isValidMagnetLink(link) -> {
                                errorMessage = "磁力链接格式不正确"
                                return@Button
                            }
                            linkType == LinkType.HTTP && !isValidHttpUrl(link) -> {
                                errorMessage = "直链格式不正确"
                                return@Button
                            }
                        }
                        onConfirm(link, title.text.trim().takeIf { it.isNotEmpty() }, linkType)
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