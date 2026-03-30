package com.pureframe.player.data.dao

import androidx.room.*
import com.pureframe.player.data.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 下载任务数据访问对象
 */
@Dao
interface DownloadTaskDao {
    
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<DownloadTaskEntity>>
    
    @Query("SELECT * FROM download_tasks WHERE status = :status ORDER BY createdAt DESC")
    fun getTasksByStatus(status: String): Flow<List<DownloadTaskEntity>>
    
    @Query("SELECT * FROM download_tasks WHERE status IN ('pending', 'downloading', 'paused') ORDER BY createdAt DESC")
    fun getActiveTasks(): Flow<List<DownloadTaskEntity>>
    
    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): DownloadTaskEntity?
    
    @Query("SELECT * FROM download_tasks WHERE torrentHash = :hash LIMIT 1")
    suspend fun getTaskByHash(hash: String): DownloadTaskEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity): Long
    
    @Update
    suspend fun updateTask(task: DownloadTaskEntity)
    
    @Query("UPDATE download_tasks SET status = :status, downloadedBytes = :bytes, downloadSpeed = :speed WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, bytes: Long, speed: Long)
    
    @Query("UPDATE download_tasks SET status = 'completed', completedAt = :time, totalBytes = :total WHERE id = :id")
    suspend fun markCompleted(id: Long, time: Date, total: Long)
    
    @Query("UPDATE download_tasks SET status = 'error', errorMessage = :message WHERE id = :id")
    suspend fun markError(id: Long, message: String)
    
    @Query("UPDATE download_tasks SET status = 'paused' WHERE id = :id")
    suspend fun pauseTask(id: Long)
    
    @Query("UPDATE download_tasks SET status = 'downloading', startedAt = :time WHERE id = :id AND status = 'pending'")
    suspend fun startTask(id: Long, time: Date)
    
    @Delete
    suspend fun deleteTask(task: DownloadTaskEntity)
    
    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
    
    @Query("DELETE FROM download_tasks WHERE status = 'completed'")
    suspend fun deleteCompletedTasks()
    
    @Query("DELETE FROM download_tasks WHERE status IN ('completed', 'error')")
    suspend fun deleteFinishedTasks()
    
    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = 'downloading'")
    suspend fun getActiveDownloadCount(): Int
}