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
        filePath = filePath,
        fileSize = fileSize,
        duration = duration,
        format = format,
        resolution = resolution,
        bitrate = bitrate,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
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
        filePath = filePath,
        fileSize = fileSize,
        duration = duration,
        format = format,
        resolution = resolution,
        bitrate = bitrate,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        isFavorite = isFavorite
    )
}

/**
 * DownloadTaskEntity -> DownloadTask
 */
fun DownloadTaskEntity.toDomainModel(): DownloadTask {
    return DownloadTask(
        id = id,
        url = url,
        title = name,
        fileName = name.substringAfterLast('/').substringAfterLast('\\'),
        savePath = downloadPath,
        totalSize = totalBytes,
        downloadedSize = downloadedBytes,
        status = parseDownloadStatus(status),
        progress = if (totalBytes > 0) (downloadedBytes * 100f / totalBytes) else 0f,
        speed = downloadSpeed,
        errorCode = errorMessage,
        createdAt = createdAt,
        updatedAt = completedAt ?: createdAt,
        completedAt = completedAt
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
        completedAt = completedAt
    )
}

/**
 * PlaybackHistoryEntity -> PlaybackHistory
 */
fun PlaybackHistoryEntity.toDomainModel(): PlaybackHistory {
    return PlaybackHistory(
        id = id,
        videoId = videoId,
        videoTitle = videoTitle,
        videoPath = videoPath,
        position = position,
        duration = duration,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        completed = completed
    )
}

/**
 * PlaybackHistory -> PlaybackHistoryEntity
 */
fun PlaybackHistory.toEntity(): PlaybackHistoryEntity {
    return PlaybackHistoryEntity(
        id = id,
        videoId = videoId,
        videoTitle = videoTitle,
        videoPath = videoPath,
        position = position,
        duration = duration,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        completed = completed
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
    }
}