package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.ParamFlowUseCase
import com.pureframe.player.data.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索视频 UseCase
 * 
 * 根据关键词搜索视频（标题、文件名等）
 * 
 * @param params 搜索关键词
 */
@Singleton
class SearchVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : ParamFlowUseCase<String, List<Video>>() {
    
    override fun invoke(params: String): Flow<List<Video>> = 
        videoRepository.searchVideos(params)
}