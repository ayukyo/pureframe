package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import com.pureframe.player.data.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 切换收藏状态 UseCase
 * 
 * 切换视频的收藏状态（收藏/取消收藏）
 * 
 * @param params ToggleFavoriteParams 包含视频 ID 和目标状态
 */
@Singleton
class ToggleFavoriteUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : ParamSuspendActionUseCase<ToggleFavoriteUseCase.Params>() {
    
    data class Params(
        val videoId: Long,
        val favorite: Boolean
    )
    
    override suspend fun invoke(params: Params) {
        videoRepository.updateFavorite(params.videoId, params.favorite)
    }
    
    /**
     * 切换收藏（toggle）
     */
    suspend fun toggle(videoId: Long, currentFavorite: Boolean) {
        invoke(Params(videoId, !currentFavorite))
    }
}