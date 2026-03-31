package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import com.pureframe.player.data.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 暂停下载任务 UseCase
 * 
 * 暂停正在进行的下载任务
 * 
 * @param params 任务 ID
 */
@Singleton
class PauseDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ParamSuspendActionUseCase<Long>() {
    
    override suspend fun invoke(params: Long) {
        downloadRepository.pauseTask(params)
    }
}