package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.ParamFlowUseCase
import com.pureframe.player.data.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取最近播放视频 UseCase
 * 
 * 返回最近播放的视频列表，用于首页「最近播放」展示
 * 
 * @param limit 返回数量限制，默认 20
 */
@Singleton
class GetRecentVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : ParamFlowUseCase<Int, List<Video>>() {
    
    override fun invoke(params: Int): Flow<List<Video>> = 
        videoRepository.getRecentVideos(params)
    
    /**
     * 默认获取最近 20 个
     */
    fun getRecent(): Flow<List<Video>> = invoke(20)
}