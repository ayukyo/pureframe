package com.pureframe.player.domain.usecase.download

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.usecase.ParamSuspendUseCase
import com.pureframe.player.data.repository.DownloadRepository
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 创建下载任务 UseCase
 * 
 * 从磁力链接或 URL 创建新的下载任务
 * 
 * @param params CreateDownloadParams 包含下载信息
 * @return 新任务的 ID
 */
@Singleton
class CreateDownloadTaskUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ParamSuspendUseCase<CreateDownloadTaskUseCase.Params, Long>() {
    
    data class Params(
        val url: String,           // magnet 链接或下载 URL
        val title: String,         // 任务标题
        val fileName: String,      // 目标文件名
        val savePath: String,      // 保存路径
        val totalSize: Long = 0    // 预估总大小（如果已知）
    )
    
    override suspend fun invoke(params: Params): Long {
        val task = DownloadTask(
            url = params.url,
            title = params.title,
            fileName = params.fileName,
            savePath = params.savePath,
            totalSize = params.totalSize,
            status = DownloadStatus.PENDING,
            createdAt = Date(),
            updatedAt = Date()
        )
        return downloadRepository.addTask(task)
    }
}