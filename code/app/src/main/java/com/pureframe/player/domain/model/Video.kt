package com.pureframe.player.domain.model

import java.util.Date

/**
 * 视频领域模型
 * 
 * 表示视频文件的核心业务信息，与数据库实体分离
 */
data class Video(
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val fileSize: Long,
    val duration: Long = 0, // 毫秒
    val format: String = "",
    val resolution: String = "",
    val bitrate: Long = 0,
    val thumbnailPath: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val lastPlayedAt: Date? = null,
    val playCount: Int = 0,
    val isFavorite: Boolean = false
) {
    /**
     * 文件大小格式化显示
     */
    val formattedSize: String
        get() = formatFileSize(fileSize)
    
    /**
     * 播放时长格式化显示
     */
    val formattedDuration: String
        get() = formatDuration(duration)
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> "${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
            minutes > 0 -> "${minutes}:${secs.toString().padStart(2, '0')}"
            else -> "0:${secs.toString().padStart(2, '0')}"
        }
    }
}