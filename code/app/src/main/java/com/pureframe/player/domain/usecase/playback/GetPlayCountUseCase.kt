package com.pureframe.player.domain.usecase.playback

import com.pureframe.player.domain.usecase.ParamSuspendUseCase
import com.pureframe.player.data.repository.PlaybackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取视频播放次数 UseCase
 * 
 * 返回指定视频的总播放次数，用于统计展示
 * 
 * @param params 视频 ID
 * @return 播放次数
 */
@Singleton
class GetPlayCountUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) : ParamSuspendUseCase<Long, Int>() {
    
    override suspend fun invoke(params: Long): Int = 
        playbackRepository.getPlayCount(params)
}