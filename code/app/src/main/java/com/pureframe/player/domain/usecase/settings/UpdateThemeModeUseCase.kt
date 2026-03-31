package com.pureframe.player.domain.usecase.settings

import com.pureframe.player.data.preferences.ThemeMode
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新主题模式 UseCase
 * 
 * 更换应用主题（浅色/深色/跟随系统）
 * 
 * @param params ThemeMode 枚举值
 */
@Singleton
class UpdateThemeModeUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ParamSuspendActionUseCase<ThemeMode>() {
    
    override suspend fun invoke(params: ThemeMode) {
        preferencesRepository.updateThemeMode(params)
    }
}