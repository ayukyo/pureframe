package com.pureframe.player.domain.usecase.playback

import com.pureframe.player.domain.usecase.SuspendActionUseCase
import com.pureframe.player.data.repository.PlaybackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 清除所有播放历史 UseCase
 * 
 * 删除所有播放历史记录
 */
@Singleton
class ClearPlaybackHistoryUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) : SuspendActionUseCase() {
    
    override suspend fun invoke() {
        playbackRepository.deleteAllHistory()
    }
}