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
     * 获取所有下载任务（Flow 方式）
     */
    fun getAllTasks(): Flow<List<DownloadTask>>
    
    /**
     * 获取所有下载任务（一次获取）
     */
    suspend fun getAllTasksOnce(): List<DownloadTask>
    
    /**
     * 根据状态获取任务
     */
    fun getTasksByStatus(status: DownloadStatus): Flow<List<DownloadTask>>
    
    /**
     * 根据状态获取任务（一次获取）
     */
    suspend fun getTasksByStatusOnce(status: DownloadStatus): List<DownloadTask>
    
    /**
     * 获取活跃任务（进行中、暂停、等待）
     */
    fun getActiveTasks(): Flow<List<DownloadTask>>
    
    /**
     * 根据 ID 获取任务（Long ID）
     */
    suspend fun getTaskById(id: Long): DownloadTask?
    
    /**
     * 根据 ID 获取任务（String ID - 用于 torrent）
     */
    suspend fun getTaskByStringId(id: String): DownloadTask?
    
    /**
     * 根据哈希获取任务
     */
    suspend fun getTaskByHash(hash: String): DownloadTask?
    
    /**
     * 添加下载任务
     */
    suspend fun addTask(task: DownloadTask): Long
    
    /**
     * 插入下载任务（返回是否成功）
     */
    suspend fun insertTask(task: DownloadTask): Boolean
    
    /**
     * 更新下载任务
     */
    suspend fun updateTask(task: DownloadTask)
    
    /**
     * 更新下载进度
     */
    suspend fun updateProgress(id: Long, status: DownloadStatus, bytes: Long, speed: Long)
    
    /**
     * 更新任务状态
     */
    suspend fun updateTaskStatus(id: String, status: DownloadStatus)
    
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
     * 校准任务总大小
     *
     * 磁力链接在确认文件选择前 totalBytes=0；若用户只勾选了部分文件，
     * 引擎上报的 totalDone/totalBytes 仍是整个种子的量，必须用"已选文件大小之和"
     * 覆盖，否则进度条会永远停在部分值。
     */
    suspend fun updateTotalBytes(id: Long, total: Long)
    
    /**
     * 开始任务
     */
    suspend fun startTask(id: Long, time: Date)
    
    /**
     * 删除任务（Long ID）
     */
    suspend fun deleteTask(task: DownloadTask)
    
    /**
     * 删除任务（String ID）
     */
    suspend fun deleteTaskById(id: String)
    
    /**
     * 根据 ID 删除任务（Long ID）
     */
    suspend fun deleteTaskByLongId(id: Long)
    
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