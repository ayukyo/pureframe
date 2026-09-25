package com.pureframe.player.domain.model

import com.pureframe.player.data.entity.VideoEntity
import com.pureframe.player.data.entity.DownloadTaskEntity
import com.pureframe.player.data.entity.PlaybackHistoryEntity

/**
 * 数据层 Entity 到 Domain 层 Model 的映射扩展函数
 */

/**
 * VideoEntity -> Video
 */
fun VideoEntity.toDomainModel(): Video {
    return Video(
        id = id,
        title = title,
        filePath = path,
        fileSize = size,
        duration = duration,
        format = format ?: "",
        resolution = resolution ?: "",
        bitrate = 0, // Entity 中没有这个字段
        thumbnailPath = thumbnailPath,
        createdAt = addedAt,
        updatedAt = addedAt,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        isFavorite = isFavorite
    )
}

/**
 * Video -> VideoEntity
 */
fun Video.toEntity(): VideoEntity {
    return VideoEntity(
        id = id,
        title = title,
        path = filePath,
        size = fileSize,
        duration = duration,
        format = format,
        resolution = resolution,
        thumbnailPath = thumbnailPath,
        addedAt = createdAt,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        isFavorite = isFavorite
    )
}

/**
 * DownloadTaskEntity -> DownloadTask
 */
fun DownloadTaskEntity.toDomainModel(): DownloadTask {
    val rawFileName = name.substringAfterLast('/').substringAfterLast('\\')
    val fileName = if (rawFileName.isEmpty()) name else rawFileName
    // 从 URL 推断下载类型
    val downloadType = if (url.startsWith("magnet:")) DownloadType.BT else DownloadType.HTTP
    return DownloadTask(
        id = id,
        url = url,
        title = name,
        fileName = fileName,
        savePath = downloadPath,
        totalSize = totalBytes,
        downloadedSize = downloadedBytes,
        status = parseDownloadStatus(status),
        // 注意：必须先除后乘。downloadedBytes * 100f 会在 Float32 下丢失精度
        //（如 991017 * 100f = 99101696），导致已完成任务显示 99% 而不是 100%。
        progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100f) else 0f,
        speed = downloadSpeed,
        errorCode = errorMessage,
        createdAt = createdAt,
        updatedAt = completedAt ?: createdAt,
        completedAt = completedAt,
        downloadType = downloadType,
        magnetLink = if (url.startsWith("magnet:")) url else null,
        torrentHash = torrentHash
    )
}

/**
 * DownloadTask -> DownloadTaskEntity
 */
fun DownloadTask.toEntity(): DownloadTaskEntity {
    return DownloadTaskEntity(
        id = id,
        url = url,
        name = title,
        downloadPath = savePath,
        totalBytes = totalSize,
        downloadedBytes = downloadedSize,
        status = status.toEntityStatus(),
        downloadSpeed = speed,
        errorMessage = errorCode,
        createdAt = createdAt,
        completedAt = completedAt,
        torrentHash = torrentHash
    )
}

/**
 * PlaybackHistoryEntity -> PlaybackHistory
 * 
 * 注意：PlaybackHistoryEntity 没有 videoTitle/videoPath 字段，
 * 需要通过 videoId 关联查询 VideoEntity 获取
 */
fun PlaybackHistoryEntity.toDomainModel(videoTitle: String = "", videoPath: String = "", videoDuration: Long = 0): PlaybackHistory {
    return PlaybackHistory(
        id = id,
        videoId = videoId,
        videoTitle = videoTitle,
        videoPath = videoPath,
        position = lastPosition,
        duration = videoDuration,
        lastPlayedAt = startTime,
        playCount = 1, // Entity 中没有累计次数，每个记录是单独的播放事件
        completed = isCompleted
    )
}

/**
 * PlaybackHistory -> PlaybackHistoryEntity
 * 
 * 注意：videoTitle/videoPath 不存储在 PlaybackHistoryEntity 中
 */
fun PlaybackHistory.toEntity(): PlaybackHistoryEntity {
    return PlaybackHistoryEntity(
        id = id,
        videoId = videoId,
        startTime = lastPlayedAt,
        endTime = null,
        lastPosition = position,
        playDuration = duration,
        isCompleted = completed
    )
}

/**
 * 解析下载状态字符串
 */
private fun parseDownloadStatus(status: String): DownloadStatus {
    return when (status.lowercase()) {
        "pending" -> DownloadStatus.PENDING
        "downloading" -> DownloadStatus.DOWNLOADING
        "paused" -> DownloadStatus.PAUSED
        "completed" -> DownloadStatus.COMPLETED
        "error", "failed" -> DownloadStatus.FAILED
        "cancelled" -> DownloadStatus.CANCELLED
        "pending_selection" -> DownloadStatus.PENDING_SELECTION
        else -> DownloadStatus.PENDING
    }
}

/**
 * DownloadStatus 转换为 Entity 状态字符串
 */
fun DownloadStatus.toEntityStatus(): String {
    return when (this) {
        DownloadStatus.PENDING -> "pending"
        DownloadStatus.DOWNLOADING -> "downloading"
        DownloadStatus.PAUSED -> "paused"
        DownloadStatus.COMPLETED -> "completed"
        DownloadStatus.FAILED -> "error"
        DownloadStatus.CANCELLED -> "cancelled"
        DownloadStatus.WAITING -> "waiting"
        DownloadStatus.ERROR -> "error"
        DownloadStatus.PENDING_SELECTION -> "pending_selection"
    }
}