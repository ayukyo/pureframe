package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.usecase.SuspendUseCase
import com.pureframe.player.data.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取活跃下载数量 UseCase
 * 
 * 返回当前正在下载的任务数量，用于控制并发下载数
 */
@Singleton
class GetActiveDownloadCountUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : SuspendUseCase<Int>() {
    
    override suspend fun invoke(): Int = downloadRepository.getActiveDownloadCount()
}