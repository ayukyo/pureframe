package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.usecase.SuspendActionUseCase
import com.pureframe.player.data.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 清理已完成下载任务 UseCase
 * 
 * 删除所有已完成的下载任务记录（保留文件）
 */
@Singleton
class ClearCompletedDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : SuspendActionUseCase() {
    
    override suspend fun invoke() {
        downloadRepository.deleteCompletedTasks()
    }
}