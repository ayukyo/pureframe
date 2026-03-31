package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import com.pureframe.player.data.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 删除视频 UseCase
 * 
 * 从数据库中删除视频记录（不删除实际文件）
 * 
 * @param params 视频 ID
 */
@Singleton
class DeleteVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : ParamSuspendActionUseCase<Long>() {
    
    override suspend fun invoke(params: Long) {
        videoRepository.deleteVideoById(params)
    }
}