package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.FlowUseCase
import com.pureframe.player.data.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取收藏视频 UseCase
 * 
 * 返回用户标记为收藏的视频列表
 */
@Singleton
class GetFavoriteVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : FlowUseCase<List<Video>>() {
    
    override fun invoke(): Flow<List<Video>> = videoRepository.getFavoriteVideos()
}