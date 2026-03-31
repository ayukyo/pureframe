package com.pureframe.player.domain.usecase.settings

import com.pureframe.player.data.preferences.UserPreferences
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新用户偏好设置 UseCase
 * 
 * 批量更新用户偏好设置
 * 
 * @param params 新的偏好设置对象
 */
@Singleton
class UpdateUserPreferencesUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ParamSuspendActionUseCase<UserPreferences>() {
    
    override suspend fun invoke(params: UserPreferences) {
        preferencesRepository.updateUserPreferences(params)
    }
}