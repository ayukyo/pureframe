package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.usecase.ParamSuspendUseCase
import com.pureframe.player.data.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据 ID 获取下载任务 UseCase
 * 
 * 获取单个下载任务详情
 * 
 * @param params 任务 ID
 * @return 下载任务，如果不存在则返回 null
 */
@Singleton
class GetDownloadByIdUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ParamSuspendUseCase<Long, DownloadTask?>() {
    
    override suspend fun invoke(params: Long): DownloadTask? = 
        downloadRepository.getTaskById(params)
}