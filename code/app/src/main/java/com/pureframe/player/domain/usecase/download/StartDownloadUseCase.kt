package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import com.pureframe.player.data.repository.DownloadRepository
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 开始/恢复下载任务 UseCase
 * 
 * 开始等待中的任务或恢复暂停的任务
 * 
 * @param params 任务 ID
 */
@Singleton
class StartDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ParamSuspendActionUseCase<Long>() {
    
    override suspend fun invoke(params: Long) {
        downloadRepository.startTask(params, Date())
    }
}