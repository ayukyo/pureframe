package com.pureframe.player.domain.model

import java.util.Date

/**
 * 播放历史领域模型
 * 
 * 记录用户的播放行为，用于统计分析
 */
data class PlaybackHistory(
    val id: Long = 0,
    val videoId: Long,
    val videoTitle: String,
    val videoPath: String,
    val position: Long = 0, // 播放位置（毫秒）
    val duration: Long = 0, // 视频总时长（毫秒）
    val lastPlayedAt: Date = Date(),
    val playCount: Int = 1,
    val completed: Boolean = false // 是否看完
) {
    /**
     * 播放进度百分比
     */
    val progressPercent: Int
        get() {
            if (duration <= 0) return 0
            return ((position * 100) / duration).toInt().coerceIn(0, 100)
        }
    
    /**
     * 格式化播放位置
     */
    val formattedPosition: String
        get() = formatTime(position)
    
    /**
     * 格式化总时长
     */
    val formattedDuration: String
        get() = formatTime(duration)
    
    /**
     * 是否需要继续播放（进度 > 5% 且 < 95%）
     */
    val needsResume: Boolean
        get() = progressPercent > 5 && progressPercent < 95
    
    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> "${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
            else -> "${minutes}:${secs.toString().padStart(2, '0')}"
        }
    }
}