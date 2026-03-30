package com.pureframe.player.data.repository

import com.pureframe.player.data.dao.PlaybackHistoryDao
import com.pureframe.player.data.dao.VideoDao
import com.pureframe.player.domain.model.PlaybackHistory
import com.pureframe.player.domain.model.toDomainModel
import com.pureframe.player.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放历史仓库实现
 * 
 * 负责播放历史数据的读写操作，将 Entity 转换为 Domain Model
 * 由于 PlaybackHistoryEntity 不包含视频标题和路径，需要关联 VideoEntity 获取
 */
@Singleton
class PlaybackRepositoryImpl @Inject constructor(
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val videoDao: VideoDao
) : PlaybackRepository {
    
    override fun getAllHistory(): Flow<List<PlaybackHistory>> {
        return playbackHistoryDao.getAllHistory().map { entities ->
            entities.map { entity ->
                val video = videoDao.getVideoById(entity.videoId)
                entity.toDomainModel(
                    videoTitle = video?.title ?: "",
                    videoPath = video?.path ?: "",
                    videoDuration = video?.duration ?: 0
                )
            }
        }
    }
    
    override fun getHistoryByVideo(videoId: Long): Flow<List<PlaybackHistory>> {
        return playbackHistoryDao.getHistoryByVideo(videoId).map { entities ->
            entities.map { entity ->
                val video = videoDao.getVideoById(entity.videoId)
                entity.toDomainModel(
                    videoTitle = video?.title ?: "",
                    videoPath = video?.path ?: "",
                    videoDuration = video?.duration ?: 0
                )
            }
        }
    }
    
    override fun getRecentHistory(limit: Int): Flow<List<PlaybackHistory>> {
        return playbackHistoryDao.getRecentHistory(limit).map { entities ->
            entities.map { entity ->
                val video = videoDao.getVideoById(entity.videoId)
                entity.toDomainModel(
                    videoTitle = video?.title ?: "",
                    videoPath = video?.path ?: "",
                    videoDuration = video?.duration ?: 0
                )
            }
        }
    }
    
    override fun getHistoryByDateRange(start: Date, end: Date): Flow<List<PlaybackHistory>> {
        return playbackHistoryDao.getHistoryByDateRange(start, end).map { entities ->
            entities.map { entity ->
                val video = videoDao.getVideoById(entity.videoId)
                entity.toDomainModel(
                    videoTitle = video?.title ?: "",
                    videoPath = video?.path ?: "",
                    videoDuration = video?.duration ?: 0
                )
            }
        }
    }
    
    override suspend fun addHistory(history: PlaybackHistory): Long {
        return playbackHistoryDao.insertHistory(history.toEntity())
    }
    
    override suspend fun updateHistory(history: PlaybackHistory) {
        playbackHistoryDao.updateHistory(history.toEntity())
    }
    
    override suspend fun updatePlaybackEnd(id: Long, endTime: Date, position: Long, duration: Long, completed: Boolean) {
        playbackHistoryDao.updatePlaybackEnd(id, endTime, position, duration, completed)
    }
    
    override suspend fun getTotalPlayDuration(videoId: Long): Long? {
        return playbackHistoryDao.getTotalPlayDuration(videoId)
    }
    
    override suspend fun getLastPosition(videoId: Long): Long? {
        return playbackHistoryDao.getLastPosition(videoId)
    }
    
    override suspend fun deleteHistory(history: PlaybackHistory) {
        playbackHistoryDao.deleteHistory(history.toEntity())
    }
    
    override suspend fun deleteHistoryByVideo(videoId: Long) {
        playbackHistoryDao.deleteHistoryByVideo(videoId)
    }
    
    override suspend fun deleteOldHistory(before: Date) {
        playbackHistoryDao.deleteOldHistory(before)
    }
    
    override suspend fun deleteAllHistory() {
        playbackHistoryDao.deleteAllHistory()
    }
    
    override suspend fun getTotalCount(): Int {
        return playbackHistoryDao.getTotalCount()
    }
    
    override suspend fun getPlayCount(videoId: Long): Int {
        return playbackHistoryDao.getPlayCount(videoId)
    }
}