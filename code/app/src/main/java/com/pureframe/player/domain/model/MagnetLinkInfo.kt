package com.pureframe.player.domain.model

/**
 * 磁力链接信息
 * 
 * 解析磁力链接后的元数据
 */
data class MagnetLinkInfo(
    val magnetLink: String,
    val infoHash: String,
    val displayName: String = "",
    val fileSize: Long = 0,
    val fileCount: Int = 0,
    val files: List<TorrentFile> = emptyList(),
    val trackers: List<String> = emptyList(),
    val isValid: Boolean = false
) {
    /**
     * 格式化文件大小
     */
    val formattedSize: String
        get() = formatFileSize(fileSize)
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * Torrent 文件信息
 */
data class TorrentFile(
    val path: String,
    val name: String,
    val size: Long,
    val isVideo: Boolean = false
) {
    /**
     * 文件扩展名
     */
    val extension: String
        get() = name.substringAfterLast('.', "")
    
    /**
     * 格式化大小
     */
    val formattedSize: String
        get() {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }
}