package com.pureframe.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 视频实体
 * 
 * 存储本地视频和下载视频的元数据
 */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 视频标题 */
    val title: String,
    
    /** 文件路径（本地视频）或下载路径 */
    val path: String,
    
    /** 文件大小（字节） */
    val size: Long,
    
    /** 播放时长（毫秒） */
    val duration: Long = 0,
    
    /** 视频来源：local(本地) | download(下载) */
    val source: String = "local",
    
    /** 缩略图路径 */
    val thumbnailPath: String? = null,
    
    /** 添加时间 */
    val addedAt: Date = Date(),
    
    /** 最后播放时间 */
    val lastPlayedAt: Date? = null,
    
    /** 播放次数 */
    val playCount: Int = 0,
    
    /** 是否收藏 */
    val isFavorite: Boolean = false,
    
    /** 视频格式 */
    val format: String? = null,
    
    /** 分辨率 */
    val resolution: String? = null,
    
    /** 关联的下载任务 ID */
    val downloadTaskId: Long? = null
)