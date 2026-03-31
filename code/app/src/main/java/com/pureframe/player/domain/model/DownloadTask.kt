package com.pureframe.player.domain.model

import java.util.Date

/**
 * 下载任务领域模型
 * 
 * 表示下载任务的核心业务状态，与数据库实体分离
 */
data class DownloadTask(
    val id: Long = 0,
    val url: String,
    val title: String,
    val fileName: String,
    val savePath: String,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f, // 0-100
    val speed: Long = 0, // bytes per second
    val errorCode: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val completedAt: Date? = null,
    
    // 磁力链接下载扩展字段
    val magnetLink: String? = null,
    val torrentPath: String? = null,
    val torrentHash: String? = null,
    val isStreamable: Boolean = false,
    val streamableProgress: Float = 0.1f
) {
    /**
     * 进度百分比
     */
    val progressPercent: Int
        get() = progress.toInt().coerceIn(0, 100)
    
    /**
     * 格式化下载速度
     */
    val formattedSpeed: String
        get() = formatSpeed(speed)
    
    /**
     * 格式化文件大小
     */
    val formattedSize: String
        get() = "${formatFileSize(downloadedSize)} / ${formatFileSize(totalSize)}"
    
    /**
     * 是否正在下载
     */
    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED
    
    /**
     * 是否已完成
     */
    val isCompleted: Boolean
        get() = status == DownloadStatus.COMPLETED
    
    /**
     * 是否失败
     */
    val isFailed: Boolean
        get() = status == DownloadStatus.FAILED
    
    /**
     * 是否是磁力链接下载
     */
    val isTorrentDownload: Boolean
        get() = magnetLink != null || torrentPath != null
    
    /**
     * 是否可边下边播
     */
    val canStream: Boolean
        get() = isStreamable && progress >= streamableProgress
    
    /**
     * 剩余时间估算（秒）
     */
    val estimatedTimeRemaining: Long
        get() {
            if (speed <= 0 || totalSize <= downloadedSize) return 0
            val remainingBytes = totalSize - downloadedSize
            return remainingBytes / speed
        }
    
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "${bytesPerSecond} B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> "${bytesPerSecond / (1024 * 1024)} MB/s"
        }
    }
    
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
 * 下载状态
 */
enum class DownloadStatus {
    PENDING,      // 等待开始
    DOWNLOADING,  // 正在下载
    PAUSED,       // 已暂停
    COMPLETED,    // 已完成
    FAILED,       // 失败
    CANCELLED,    // 已取消
    WAITING,      // 等待资源（DHT 查找）
    ERROR         // 错误
}