package com.pureframe.player.data.repository

import com.pureframe.player.data.dao.DownloadTaskDao
import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.model.toDomainModel
import com.pureframe.player.domain.model.toEntity
import com.pureframe.player.domain.model.toEntityStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载仓库实现
 * 
 * 负责下载任务数据的读写操作，将 Entity 转换为 Domain Model
 */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadTaskDao: DownloadTaskDao
) : DownloadRepository {
    
    override fun getAllTasks(): Flow<List<DownloadTask>> {
        return downloadTaskDao.getAllTasks().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getAllTasksOnce(): List<DownloadTask> {
        return getAllTasks().first()
    }
    
    override fun getTasksByStatus(status: DownloadStatus): Flow<List<DownloadTask>> {
        return downloadTaskDao.getTasksByStatus(status.toEntityStatus()).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getTasksByStatusOnce(status: DownloadStatus): List<DownloadTask> {
        return getTasksByStatus(status).first()
    }
    
    override fun getActiveTasks(): Flow<List<DownloadTask>> {
        return downloadTaskDao.getActiveTasks().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getTaskById(id: Long): DownloadTask? {
        return downloadTaskDao.getTaskById(id)?.toDomainModel()
    }
    
    override suspend fun getTaskByStringId(id: String): DownloadTask? {
        // 尝试解析为 Long ID
        return try {
            val longId = id.toLongOrNull()
            if (longId != null) {
                getTaskById(longId)
            } else {
                // 如果不是数字，尝试按 hash 查找
                getTaskByHash(id)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getTaskByHash(hash: String): DownloadTask? {
        return downloadTaskDao.getTaskByHash(hash)?.toDomainModel()
    }
    
    override suspend fun addTask(task: DownloadTask): Long {
        return downloadTaskDao.insertTask(task.toEntity())
    }
    
    override suspend fun insertTask(task: DownloadTask): Boolean {
        try {
            downloadTaskDao.insertTask(task.toEntity())
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    override suspend fun updateTask(task: DownloadTask) {
        downloadTaskDao.updateTask(task.toEntity())
    }
    
    override suspend fun updateProgress(id: Long, status: DownloadStatus, bytes: Long, speed: Long) {
        downloadTaskDao.updateProgress(id, status.toEntityStatus(), bytes, speed)
    }
    
    override suspend fun updateTaskStatus(id: String, status: DownloadStatus) {
        val task = getTaskByStringId(id)
        if (task != null) {
            val updatedTask = task.copy(status = status)
            downloadTaskDao.updateTask(updatedTask.toEntity())
        }
    }
    
    override suspend fun markCompleted(id: Long, time: Date, total: Long) {
        downloadTaskDao.markCompleted(id, time, total)
    }
    
    override suspend fun markError(id: Long, message: String) {
        downloadTaskDao.markError(id, message)
    }
    
    override suspend fun pauseTask(id: Long) {
        downloadTaskDao.pauseTask(id)
    }
    
    override suspend fun startTask(id: Long, time: Date) {
        downloadTaskDao.startTask(id, time)
    }
    
    override suspend fun deleteTask(task: DownloadTask) {
        downloadTaskDao.deleteTask(task.toEntity())
    }
    
    override suspend fun deleteTaskById(id: String) {
        val task = getTaskByStringId(id)
        if (task != null) {
            downloadTaskDao.deleteTask(task.toEntity())
        }
    }
    
    override suspend fun deleteTaskByLongId(id: Long) {
        downloadTaskDao.deleteTaskById(id)
    }
    
    override suspend fun deleteCompletedTasks() {
        downloadTaskDao.deleteCompletedTasks()
    }
    
    override suspend fun deleteFinishedTasks() {
        downloadTaskDao.deleteFinishedTasks()
    }
    
    override suspend fun getActiveDownloadCount(): Int {
        return downloadTaskDao.getActiveDownloadCount()
    }
}