package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import com.pureframe.player.data.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 删除下载任务 UseCase
 * 
 * 删除下载任务记录
 * 
 * @param params DownloadTask 对象（用于删除）
 */
@Singleton
class DeleteDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ParamSuspendActionUseCase<DeleteDownloadUseCase.Params>() {
    
    data class Params(
        val task: DownloadTask,
        val deleteFiles: Boolean = false  // 是否同时删除已下载的文件
    )
    
    override suspend fun invoke(params: Params) {
        downloadRepository.deleteTask(params.task)
        // 如果需要删除文件，由 DownloadService 处理
    }
    
    /**
     * 仅删除任务记录
     */
    suspend fun deleteTaskOnly(task: DownloadTask) {
        invoke(Params(task, false))
    }
    
    /**
     * 删除任务和文件
     */
    suspend fun deleteTaskAndFiles(task: DownloadTask) {
        invoke(Params(task, true))
    }
}