package com.pureframe.player.domain.usecase.settings

import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新播放速度 UseCase
 * 
 * 更改默认播放速度
 * 
 * @param params 播放速度（如 1.0, 1.5, 2.0）
 */
@Singleton
class UpdatePlaySpeedUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ParamSuspendActionUseCase<Float>() {
    
    override suspend fun invoke(params: Float) {
        preferencesRepository.updateDefaultPlaySpeed(params)
    }
}