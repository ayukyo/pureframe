package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.ParamSuspendUseCase
import com.pureframe.player.data.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据 ID 获取视频 UseCase
 * 
 * 获取单个视频详情，用于播放页面或详情页
 * 
 * @param params 视频 ID
 * @return 视频对象，如果不存在则返回 null
 */
@Singleton
class GetVideoByIdUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : ParamSuspendUseCase<Long, Video?>() {
    
    override suspend fun invoke(params: Long): Video? = 
        videoRepository.getVideoById(params)
}