package com.pureframe.player.data.repository

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 下载仓库接口
 * 
 * 定义下载任务数据的操作契约，UI 层通过此接口访问数据
 */
interface DownloadRepository {
    /**
     * 获取所有下载任务
     */
    fun getAllTasks(): Flow<List<DownloadTask>>
    
    /**
     * 根据状态获取任务
     */
    fun getTasksByStatus(status: DownloadStatus): Flow<List<DownloadTask>>
    
    /**
     * 获取活跃任务（进行中、暂停、等待）
     */
    fun getActiveTasks(): Flow<List<DownloadTask>>
    
    /**
     * 根据 ID 获取任务
     */
    suspend fun getTaskById(id: Long): DownloadTask?
    
    /**
     * 根据哈希获取任务
     */
    suspend fun getTaskByHash(hash: String): DownloadTask?
    
    /**
     * 添加下载任务
     */
    suspend fun addTask(task: DownloadTask): Long
    
    /**
     * 更新下载任务
     */
    suspend fun updateTask(task: DownloadTask)
    
    /**
     * 更新下载进度
     */
    suspend fun updateProgress(id: Long, status: DownloadStatus, bytes: Long, speed: Long)
    
    /**
     * 标记任务完成
     */
    suspend fun markCompleted(id: Long, time: Date, total: Long)
    
    /**
     * 标记任务失败
     */
    suspend fun markError(id: Long, message: String)
    
    /**
     * 暂停任务
     */
    suspend fun pauseTask(id: Long)
    
    /**
     * 开始任务
     */
    suspend fun startTask(id: Long, time: Date)
    
    /**
     * 删除任务
     */
    suspend fun deleteTask(task: DownloadTask)
    
    /**
     * 根据 ID 删除任务
     */
    suspend fun deleteTaskById(id: Long)
    
    /**
     * 删除已完成的任务
     */
    suspend fun deleteCompletedTasks()
    
    /**
     * 删除已完成或失败的任务
     */
    suspend fun deleteFinishedTasks()
    
    /**
     * 获取正在下载的任务数量
     */
    suspend fun getActiveDownloadCount(): Int
}