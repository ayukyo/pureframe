package com.pureframe.player.data.repository

import com.pureframe.player.domain.model.Video
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 视频仓库接口
 * 
 * 定义视频数据的操作契约，UI 层通过此接口访问数据
 */
interface VideoRepository {
    /**
     * 获取所有视频
     */
    fun getAllVideos(): Flow<List<Video>>
    
    /**
     * 获取本地视频
     */
    fun getLocalVideos(): Flow<List<Video>>
    
    /**
     * 获取已下载视频
     */
    fun getDownloadedVideos(): Flow<List<Video>>
    
    /**
     * 获取收藏视频
     */
    fun getFavoriteVideos(): Flow<List<Video>>
    
    /**
     * 获取最近播放视频
     */
    fun getRecentVideos(limit: Int = 20): Flow<List<Video>>
    
    /**
     * 搜索视频
     */
    fun searchVideos(keyword: String): Flow<List<Video>>
    
    /**
     * 根据 ID 获取视频
     */
    suspend fun getVideoById(id: Long): Video?
    
    /**
     * 根据路径获取视频
     */
    suspend fun getVideoByPath(path: String): Video?
    
    /**
     * 添加视频
     */
    suspend fun addVideo(video: Video): Long
    
    /**
     * 批量添加视频
     */
    suspend fun addVideos(videos: List<Video>)
    
    /**
     * 更新视频
     */
    suspend fun updateVideo(video: Video)
    
    /**
     * 更新播放信息
     */
    suspend fun updatePlayInfo(id: Long, time: Date)
    
    /**
     * 更新收藏状态
     */
    suspend fun updateFavorite(id: Long, favorite: Boolean)
    
    /**
     * 删除视频
     */
    suspend fun deleteVideo(video: Video)
    
    /**
     * 根据 ID 删除视频
     */
    suspend fun deleteVideoById(id: Long)
    
    /**
     * 删除所有本地视频记录
     */
    suspend fun deleteAllLocalVideos()
    
    /**
     * 获取视频总数
     */
    suspend fun getTotalCount(): Int
}