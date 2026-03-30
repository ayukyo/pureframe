package com.pureframe.player.data.repository

import com.pureframe.player.domain.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 播放历史仓库接口
 * 
 * 定义播放历史数据的操作契约，UI 层通过此接口访问数据
 */
interface PlaybackRepository {
    /**
     * 获取所有播放历史
     */
    fun getAllHistory(): Flow<List<PlaybackHistory>>
    
    /**
     * 根据视频 ID 获取播放历史
     */
    fun getHistoryByVideo(videoId: Long): Flow<List<PlaybackHistory>>
    
    /**
     * 获取最近的播放历史
     */
    fun getRecentHistory(limit: Int = 50): Flow<List<PlaybackHistory>>
    
    /**
     * 根据日期范围获取播放历史
     */
    fun getHistoryByDateRange(start: Date, end: Date): Flow<List<PlaybackHistory>>
    
    /**
     * 添加播放历史
     */
    suspend fun addHistory(history: PlaybackHistory): Long
    
    /**
     * 更新播放历史
     */
    suspend fun updateHistory(history: PlaybackHistory)
    
    /**
     * 更新播放结束信息
     */
    suspend fun updatePlaybackEnd(id: Long, endTime: Date, position: Long, duration: Long, completed: Boolean)
    
    /**
     * 获取视频的总播放时长
     */
    suspend fun getTotalPlayDuration(videoId: Long): Long?
    
    /**
     * 获取视频的最后播放位置
     */
    suspend fun getLastPosition(videoId: Long): Long?
    
    /**
     * 删除播放历史
     */
    suspend fun deleteHistory(history: PlaybackHistory)
    
    /**
     * 删除指定视频的播放历史
     */
    suspend fun deleteHistoryByVideo(videoId: Long)
    
    /**
     * 删除旧的播放历史
     */
    suspend fun deleteOldHistory(before: Date)
    
    /**
     * 删除所有播放历史
     */
    suspend fun deleteAllHistory()
    
    /**
     * 获取播放历史总数
     */
    suspend fun getTotalCount(): Int
    
    /**
     * 获取视频的播放次数
     */
    suspend fun getPlayCount(videoId: Long): Int
}