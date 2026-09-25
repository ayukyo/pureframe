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

    // 下载类型
    val downloadType: DownloadType = DownloadType.HTTP,

    // BT下载扩展字段
    val magnetLink: String? = null,
    val torrentPath: String? = null,
    val torrentHash: String? = null,
    val isStreamable: Boolean = false,
    val streamableProgress: Float = 10f  // 默认需要下载 10% 才能播放
) {
    /**
     * 进度百分比
     *
     * - 已完成（或字节数已补齐）直接返回 100，避免 99.999% 被截断成 99%
     * - 四舍五入而非截断，进度显示更贴近真实值
     */
    val progressPercent: Int
        get() {
            if (status == DownloadStatus.COMPLETED) return 100
            if (totalSize > 0 && downloadedSize >= totalSize) return 100
            return kotlin.math.round(progress).toInt().coerceIn(0, 100)
        }
    
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
     * 是否是 BT 下载
     */
    val isBtDownload: Boolean
        get() = downloadType == DownloadType.BT

    /**
     * 是否是磁力链接下载
     */
    val isTorrentDownload: Boolean
        get() = magnetLink != null || torrentPath != null

    /**
     * 是否可边下边播
     * streamableProgress 是百分比（0-100），例如 10 表示 10%
     * 默认需要下载 10% 才能播放
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
            bytesPerSecond < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024))
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}

/**
 * 下载类型
 */
enum class DownloadType {
    HTTP,    // HTTP/直链下载
    BT       // BitTorrent 下载
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
    ERROR,        // 错误
    PENDING_SELECTION  // 等待文件选择（磁力链接添加后，未确认文件前）
}