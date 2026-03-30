package com.pureframe.player.data.dao

import androidx.room.*
import com.pureframe.player.data.entity.VideoEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 视频数据访问对象
 */
@Dao
interface VideoDao {
    
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>
    
    @Query("SELECT * FROM videos WHERE source = 'local' ORDER BY addedAt DESC")
    fun getLocalVideos(): Flow<List<VideoEntity>>
    
    @Query("SELECT * FROM videos WHERE source = 'download' ORDER BY addedAt DESC")
    fun getDownloadedVideos(): Flow<List<VideoEntity>>
    
    @Query("SELECT * FROM videos WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>
    
    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoEntity?
    
    @Query("SELECT * FROM videos WHERE path = :path LIMIT 1")
    suspend fun getVideoByPath(path: String): VideoEntity?
    
    @Query("SELECT * FROM videos ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentVideos(limit: Int = 20): Flow<List<VideoEntity>>
    
    @Query("SELECT * FROM videos WHERE title LIKE '%' || :keyword || '%' ORDER BY addedAt DESC")
    fun searchVideos(keyword: String): Flow<List<VideoEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)
    
    @Update
    suspend fun updateVideo(video: VideoEntity)
    
    @Query("UPDATE videos SET lastPlayedAt = :time, playCount = playCount + 1 WHERE id = :id")
    suspend fun updatePlayInfo(id: Long, time: Date)
    
    @Query("UPDATE videos SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)
    
    @Delete
    suspend fun deleteVideo(video: VideoEntity)
    
    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)
    
    @Query("DELETE FROM videos WHERE source = 'local'")
    suspend fun deleteAllLocalVideos()
    
    @Query("SELECT COUNT(*) FROM videos")
    suspend fun getTotalCount(): Int
}