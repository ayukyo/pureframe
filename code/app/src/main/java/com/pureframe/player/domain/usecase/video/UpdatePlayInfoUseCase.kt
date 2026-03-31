package com.pureframe.player.domain.usecase.video

import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import com.pureframe.player.data.repository.VideoRepository
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新播放信息 UseCase
 * 
 * 更新视频的播放信息（播放时间、播放次数等）
 * 在用户开始/结束播放时调用
 * 
 * @param params UpdatePlayInfoParams 包含视频 ID 和播放时间
 */
@Singleton
class UpdatePlayInfoUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) : ParamSuspendActionUseCase<UpdatePlayInfoUseCase.Params>() {
    
    data class Params(
        val videoId: Long,
        val playTime: Date = Date()
    )
    
    override suspend fun invoke(params: Params) {
        videoRepository.updatePlayInfo(params.videoId, params.playTime)
    }
    
    /**
     * 使用当前时间更新播放信息
     */
    suspend fun updateNow(videoId: Long) {
        invoke(Params(videoId, Date()))
    }
}