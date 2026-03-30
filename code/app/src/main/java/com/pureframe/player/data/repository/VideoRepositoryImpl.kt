package com.pureframe.player.data.repository

import com.pureframe.player.data.dao.VideoDao
import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.model.toDomainModel
import com.pureframe.player.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频仓库实现
 * 
 * 负责视频数据的读写操作，将 Entity 转换为 Domain Model
 */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val videoDao: VideoDao
) : VideoRepository {
    
    override fun getAllVideos(): Flow<List<Video>> {
        return videoDao.getAllVideos().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getLocalVideos(): Flow<List<Video>> {
        return videoDao.getLocalVideos().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getDownloadedVideos(): Flow<List<Video>> {
        return videoDao.getDownloadedVideos().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getFavoriteVideos(): Flow<List<Video>> {
        return videoDao.getFavoriteVideos().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getRecentVideos(limit: Int): Flow<List<Video>> {
        return videoDao.getRecentVideos(limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun searchVideos(keyword: String): Flow<List<Video>> {
        return videoDao.searchVideos(keyword).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getVideoById(id: Long): Video? {
        return videoDao.getVideoById(id)?.toDomainModel()
    }
    
    override suspend fun getVideoByPath(path: String): Video? {
        return videoDao.getVideoByPath(path)?.toDomainModel()
    }
    
    override suspend fun addVideo(video: Video): Long {
        return videoDao.insertVideo(video.toEntity())
    }
    
    override suspend fun addVideos(videos: List<Video>) {
        videoDao.insertVideos(videos.map { it.toEntity() })
    }
    
    override suspend fun updateVideo(video: Video) {
        videoDao.updateVideo(video.toEntity())
    }
    
    override suspend fun updatePlayInfo(id: Long, time: Date) {
        videoDao.updatePlayInfo(id, time)
    }
    
    override suspend fun updateFavorite(id: Long, favorite: Boolean) {
        videoDao.updateFavorite(id, favorite)
    }
    
    override suspend fun deleteVideo(video: Video) {
        videoDao.deleteVideo(video.toEntity())
    }
    
    override suspend fun deleteVideoById(id: Long) {
        videoDao.deleteVideoById(id)
    }
    
    override suspend fun deleteAllLocalVideos() {
        videoDao.deleteAllLocalVideos()
    }
    
    override suspend fun getTotalCount(): Int {
        return videoDao.getTotalCount()
    }
}