package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.FlowUseCase
import com.pureframe.player.data.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取所有视频 UseCase
 * 
 * 返回所有视频列表（本地 + 已下载），供首页展示使用
 */
@Singleton
class GetAllVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : FlowUseCase<List<Video>>() {
    
    override fun invoke(): Flow<List<Video>> = videoRepository.getAllVideos()
}