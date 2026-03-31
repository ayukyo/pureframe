package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.usecase.FlowUseCase
import com.pureframe.player.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取活跃下载任务 UseCase
 * 
 * 返回当前正在进行的下载任务（包括下载中、暂停、等待）
 */
@Singleton
class GetActiveDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : FlowUseCase<List<DownloadTask>>() {
    
    override fun invoke(): Flow<List<DownloadTask>> = downloadRepository.getActiveTasks()
}