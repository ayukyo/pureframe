package com.pureframe.player.domain.usecase.player

import com.pureframe.player.domain.model.DownloadTask
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.usecase.ParamSuspendUseCase
import com.pureframe.player.data.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 检查边下边播可用性 UseCase
 * 
 * 检查下载任务是否满足边下边播条件：
 * - 进度 >= 10%（可配置阈值）
 * - 已下载片段连续
 * 
 * @param params 下载任务 ID
 * @return CheckStreamableResult 包含是否可播放和最大播放位置
 */
@Singleton
class CheckStreamableUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ParamSuspendUseCase<Long, CheckStreamableUseCase.Result>() {
    
    data class Result(
        val isStreamable: Boolean,
        val progress: Float,
        val maxSeekPosition: Long = 0,
        val reason: String? = null
    )
    
    // 默认边下边播阈值：10%
    private val defaultThreshold = 0.1f
    
    override suspend fun invoke(params: Long): Result {
        val task = downloadRepository.getTaskById(params)
        
        if (task == null) {
            return Result(
                isStreamable = false,
                progress = 0f,
                reason = "下载任务不存在"
            )
        }
        
        // 检查状态
        if (task.status != DownloadStatus.DOWNLOADING && 
            task.status != DownloadStatus.PAUSED) {
            return Result(
                isStreamable = false,
                progress = task.progress,
                reason = "任务状态不允许播放（${task.status})"
            )
        }
        
        // 检查进度阈值
        val thresholdProgress = defaultThreshold * 100
        if (task.progress < thresholdProgress) {
            return Result(
                isStreamable = false,
                progress = task.progress,
                reason = "进度不足（需要 >= ${thresholdProgress.toInt()}%）"
            )
        }
        
        // 计算最大可播放位置
        val maxSeekPosition = (task.totalSize * task.progress / 100).toLong()
        
        return Result(
            isStreamable = true,
            progress = task.progress,
            maxSeekPosition = maxSeekPosition,
            reason = null
        )
    }
    
    /**
     * 使用自定义阈值检查
     */
    suspend fun checkWithThreshold(taskId: Long, threshold: Float): Result {
        val task = downloadRepository.getTaskById(taskId)
        
        if (task == null) {
            return Result(false, 0f, reason = "下载任务不存在")
        }
        
        val thresholdProgress = threshold * 100
        val isStreamable = task.progress >= thresholdProgress && 
                          (task.status == DownloadStatus.DOWNLOADING || 
                           task.status == DownloadStatus.PAUSED)
        
        val maxSeekPosition = if (isStreamable) {
            (task.totalSize * task.progress / 100).toLong()
        } else 0
        
        return Result(
            isStreamable = isStreamable,
            progress = task.progress,
            maxSeekPosition = maxSeekPosition,
            reason = if (!isStreamable) "进度不足或状态不允许" else null
        )
    }
}