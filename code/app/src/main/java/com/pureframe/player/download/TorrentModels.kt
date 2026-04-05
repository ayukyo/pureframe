package com.pureframe.player.download

/**
 * 下载进度信息
 */
data class DownloadProgressInfo(
    val taskId: String,
    val progress: Float,
    val downloadSpeed: Long,
    val state: TorrentState,
    val downloadedBytes: Long,
    val totalBytes: Long
)

/**
 * Torrent 添加成功信息
 */
data class TorrentAddedInfo(
    val taskId: String,
    val infoHash: String,
    val torrentName: String?
)

/**
 * Torrent 文件信息
 */
data class TorrentFileInfo(
    val index: Int,
    val path: String,
    val name: String,
    val size: Long,
    val isVideo: Boolean,
    val resolution: String? = null
) {
    val formattedSize: String
        get() = formatFileSize(size)

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
 * Torrent 元数据信息（包含文件列表）
 */
data class TorrentMetadataInfo(
    val taskId: String,
    val infoHash: String,
    val name: String,
    val totalSize: Long,
    val files: List<TorrentFileInfo>
) {
    val videoFiles: List<TorrentFileInfo>
        get() = files.filter { it.isVideo }

    val formattedTotalSize: String
        get() = formatFileSize(totalSize)

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
 * 边下边播信息
 */
data class StreamableInfo(
    val taskId: String,
    val isStreamable: Boolean,
    val largestFileIndex: Int,
    val largestFilePath: String,
    val cachedProgress: Float,
    val maxSeekPosition: Long,
    val totalSize: Long
)

/**
 * 下载状态枚举
 */
enum class TorrentState {
    PAUSED,
    DOWNLOADING,
    COMPLETED,
    ERROR,
    WAITING,
    SEEDING
}
