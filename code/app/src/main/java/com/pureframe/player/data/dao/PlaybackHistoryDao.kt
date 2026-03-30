package com.pureframe.player.data.dao

import androidx.room.*
import com.pureframe.player.data.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 播放历史数据访问对象
 */
@Dao
interface PlaybackHistoryDao {
    
    @Query("SELECT * FROM playback_history ORDER BY startTime DESC")
    fun getAllHistory(): Flow<List<PlaybackHistoryEntity>>
    
    @Query("SELECT * FROM playback_history WHERE videoId = :videoId ORDER BY startTime DESC")
    fun getHistoryByVideo(videoId: Long): Flow<List<PlaybackHistoryEntity>>
    
    @Query("SELECT * FROM playback_history ORDER BY startTime DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<PlaybackHistoryEntity>>
    
    @Query("SELECT * FROM playback_history WHERE startTime BETWEEN :start AND :end ORDER BY startTime DESC")
    fun getHistoryByDateRange(start: Date, end: Date): Flow<List<PlaybackHistoryEntity>>
    
    @Query("SELECT SUM(playDuration) FROM playback_history WHERE videoId = :videoId")
    suspend fun getTotalPlayDuration(videoId: Long): Long?
    
    @Query("SELECT MAX(lastPosition) FROM playback_history WHERE videoId = :videoId")
    suspend fun getLastPosition(videoId: Long): Long?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity): Long
    
    @Update
    suspend fun updateHistory(history: PlaybackHistoryEntity)
    
    @Query("UPDATE playback_history SET endTime = :endTime, lastPosition = :position, playDuration = :duration, isCompleted = :completed WHERE id = :id")
    suspend fun updatePlaybackEnd(id: Long, endTime: Date, position: Long, duration: Long, completed: Boolean)
    
    @Delete
    suspend fun deleteHistory(history: PlaybackHistoryEntity)
    
    @Query("DELETE FROM playback_history WHERE videoId = :videoId")
    suspend fun deleteHistoryByVideo(videoId: Long)
    
    @Query("DELETE FROM playback_history WHERE startTime < :before")
    suspend fun deleteOldHistory(before: Date)
    
    @Query("DELETE FROM playback_history")
    suspend fun deleteAllHistory()
    
    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM playback_history WHERE videoId = :videoId")
    suspend fun getPlayCount(videoId: Long): Int
}