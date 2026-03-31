package com.pureframe.player.domain.usecase.playback

import com.pureframe.player.domain.model.PlaybackHistory
import com.pureframe.player.domain.usecase.ParamSuspendUseCase
import com.pureframe.player.data.repository.PlaybackRepository
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保存播放进度 UseCase
 * 
 * 在用户播放视频时保存进度，支持断点续播
 * 
 * @param params SavePlaybackProgressParams 包含播放信息
 * @return 新历史记录的 ID（或更新现有记录）
 */
@Singleton
class SavePlaybackProgressUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) : ParamSuspendUseCase<SavePlaybackProgressUseCase.Params, Long>() {
    
    data class Params(
        val videoId: Long,
        val videoTitle: String,
        val videoPath: String,
        val position: Long,        // 当前播放位置（毫秒）
        val duration: Long,        // 视频总时长（毫秒）
        val completed: Boolean = false  // 是否看完
    )
    
    override suspend fun invoke(params: Params): Long {
        val history = PlaybackHistory(
            videoId = params.videoId,
            videoTitle = params.videoTitle,
            videoPath = params.videoPath,
            position = params.position,
            duration = params.duration,
            lastPlayedAt = Date(),
            completed = params.completed
        )
        return playbackRepository.addHistory(history)
    }
    
    /**
     * 快捷保存：仅更新位置和时长
     */
    suspend fun savePosition(
        videoId: Long,
        position: Long,
        duration: Long
    ): Long {
        // 如果已有历史记录，只更新位置
        val lastPosition = playbackRepository.getLastPosition(videoId)
        if (lastPosition != null) {
            playbackRepository.updatePlaybackEnd(
                id = 0, // 需要从历史记录获取 ID
                endTime = Date(),
                position = position,
                duration = duration,
                completed = position >= duration * 0.95 // 95% 视为看完
            )
            return videoId
        }
        
        // 无历史记录时创建新记录（需要从 VideoRepository 获取标题和路径）
        return invoke(Params(
            videoId = videoId,
            videoTitle = "", // 由调用方填充
            videoPath = "",
            position = position,
            duration = duration
        ))
    }
}