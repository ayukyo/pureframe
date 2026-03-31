package com.pureframe.player.domain.usecase.settings

import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新手势控制设置 UseCase
 * 
 * 启用/禁用播放器的手势控制（亮度、音量、进度）
 * 
 * @param params GestureSettingsParams
 */
@Singleton
class UpdateGestureSettingsUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ParamSuspendActionUseCase<UpdateGestureSettingsUseCase.Params>() {
    
    data class Params(
        val brightnessGesture: Boolean,
        val volumeGesture: Boolean
    )
    
    override suspend fun invoke(params: Params) {
        preferencesRepository.updateBrightnessGesture(params.brightnessGesture)
        preferencesRepository.updateVolumeGesture(params.volumeGesture)
    }
    
    /**
     * 全部启用
     */
    suspend fun enableAll() {
        invoke(Params(true, true))
    }
    
    /**
     * 全部禁用
     */
    suspend fun disableAll() {
        invoke(Params(false, false))
    }
}