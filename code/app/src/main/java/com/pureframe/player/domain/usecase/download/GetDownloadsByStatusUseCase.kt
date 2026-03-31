package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.usecase.ParamFlowUseCase
import com.pureframe.player.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据状态获取下载任务 UseCase
 * 
 * 筛选指定状态的下载任务
 * 
 * @param params DownloadStatus（状态枚举）
 */
@Singleton
class GetDownloadsByStatusUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ParamFlowUseCase<DownloadStatus, List<DownloadTask>>() {
    
    override fun invoke(params: DownloadStatus): Flow<List<DownloadTask>> = 
        downloadRepository.getTasksByStatus(params)
    
    /**
     * 获取下载中的任务
     */
    fun getDownloading(): Flow<List<DownloadTask>> = invoke(DownloadStatus.DOWNLOADING)
    
    /**
     * 获取已完成的任务
     */
    fun getCompleted(): Flow<List<DownloadTask>> = invoke(DownloadStatus.COMPLETED)
    
    /**
     * 获取失败的任务
     */
    fun getFailed(): Flow<List<DownloadTask>> = invoke(DownloadStatus.FAILED)
    
    /**
     * 获取暂停的任务
     */
    fun getPaused(): Flow<List<DownloadTask>> = invoke(DownloadStatus.PAUSED)
}