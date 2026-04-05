package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.usecase.FlowUseCase
import com.pureframe.player.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取所有下载任务 UseCase
 *
 * 返回所有下载任务列表（包括进行中、已完成、失败等）
 * 注意：过滤掉 PENDING_SELECTION 状态的任务（这些是等待用户确认文件选择的临时任务）
 */
@Singleton
class GetAllDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : FlowUseCase<List<DownloadTask>>() {

    override fun invoke(): Flow<List<DownloadTask>> = downloadRepository.getAllTasks()
        .map { tasks -> tasks.filter { it.status != DownloadStatus.PENDING_SELECTION } }
}