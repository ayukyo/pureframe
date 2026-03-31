package com.pureframe.player.domain.usecase.playback

import com.pureframe.player.domain.model.PlaybackHistory
import com.pureframe.player.domain.usecase.ParamFlowUseCase
import com.pureframe.player.data.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取播放历史 UseCase
 * 
 * 返回用户的播放历史记录，用于首页「最近播放」展示
 * 
 * @param params 返回数量限制，默认 50
 */
@Singleton
class GetPlaybackHistoryUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) : ParamFlowUseCase<Int, List<PlaybackHistory>>() {
    
    override fun invoke(params: Int): Flow<List<PlaybackHistory>> = 
        playbackRepository.getRecentHistory(params)
    
    /**
     * 获取最近 50 条历史
     */
    fun getRecent(): Flow<List<PlaybackHistory>> = invoke(50)
    
    /**
     * 获取最近 20 条历史
     */
    fun getRecentShort(): Flow<List<PlaybackHistory>> = invoke(20)
}