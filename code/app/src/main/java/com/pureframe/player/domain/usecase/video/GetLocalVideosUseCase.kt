package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.model.Video
import com.pureframe.player.domain.usecase.FlowUseCase
import com.pureframe.player.data.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取本地视频 UseCase
 * 
 * 返回本地存储的视频列表（非下载视频）
 */
@Singleton
class GetLocalVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : FlowUseCase<List<Video>>() {
    
    override fun invoke(): Flow<List<Video>> = videoRepository.getLocalVideos()
}