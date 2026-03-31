package com.pureframe.player.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 下载任务实体
 * 
 * 管理磁力链接下载任务
 */
@Entity(
    tableName = "download_tasks",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["videoId"])
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 关联的视频 ID */
    val videoId: Long? = null,
    
    /** 磁力链接或 HTTP URL */
    val url: String,
    
    /** 任务名称 */
    val name: String,
    
    /** 下载状态：pending | downloading | paused | completed | error */
    val status: String = "pending",
    
    /** 已下载大小（字节） */
    val downloadedBytes: Long = 0,
    
    /** 总大小（字节） */
    val totalBytes: Long = 0,
    
    /** 下载速度（字节/秒） */
    val downloadSpeed: Long = 0,
    
    /** 下载路径 */
    val downloadPath: String,
    
    /** 创建时间 */
    val createdAt: Date = Date(),
    
    /** 开始时间 */
    val startedAt: Date? = null,
    
    /** 完成时间 */
    val completedAt: Date? = null,
    
    /** 错误信息 */
    val errorMessage: String? = null,
    
    /** 种子信息哈希 */
    val torrentHash: String? = null,
    
    /** 文件列表（JSON 数组） */
    val fileList: String? = null,
    
    /** 选择的文件索引列表（JSON 数组） */
    val selectedFiles: String? = null
)