package com.pureframe.player.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 播放历史实体
 * 
 * 记录每次播放的详细信息
 */
@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 关联的视频 ID */
    val videoId: Long,
    
    /** 播放开始时间 */
    val startTime: Date,
    
    /** 播放结束时间 */
    val endTime: Date? = null,
    
    /** 最后播放位置（毫秒） */
    val lastPosition: Long = 0,
    
    /** 播放时长（毫秒） */
    val playDuration: Long = 0,
    
    /** 是否完整播放 */
    val isCompleted: Boolean = false
)