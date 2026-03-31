package com.pureframe.player.domain.usecase.playback

import com.pureframe.player.domain.model.PlaybackHistory
import com.pureframe.player.domain.usecase.ParamFlowUseCase
import com.pureframe.player.data.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据视频 ID 获取播放历史 UseCase
 * 
 * 获取指定视频的所有播放历史记录
 * 
 * @param params 视频 ID
 */
@Singleton
class GetPlaybackHistoryByVideoUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) : ParamFlowUseCase<Long, List<PlaybackHistory>>() {
    
    override fun invoke(params: Long): Flow<List<PlaybackHistory>> = 
        playbackRepository.getHistoryByVideo(params)
}