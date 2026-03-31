package com.pureframe.player.domain.usecase.playback

import com.pureframe.player.domain.usecase.ParamSuspendUseCase
import com.pureframe.player.data.repository.PlaybackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取最后播放位置 UseCase
 * 
 * 获取视频上次播放的位置，用于续播功能
 * 
 * @param params 视频 ID
 * @return 播放位置（毫秒），如果没有记录则返回 null
 */
@Singleton
class GetLastPlaybackPositionUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) : ParamSuspendUseCase<Long, Long?>() {
    
    override suspend fun invoke(params: Long): Long? = 
        playbackRepository.getLastPosition(params)
}