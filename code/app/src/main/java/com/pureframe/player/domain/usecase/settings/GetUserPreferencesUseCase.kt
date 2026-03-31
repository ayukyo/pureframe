package com.pureframe.player.domain.usecase.settings

import com.pureframe.player.data.preferences.UserPreferences
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 获取用户偏好设置 UseCase
 * 
 * 返回用户偏好设置 Flow，监听设置变化自动更新 UI
 */
@Singleton
class GetUserPreferencesUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : FlowUseCase<UserPreferences>() {
    
    override fun invoke(): Flow<UserPreferences> = 
        preferencesRepository.userPreferencesFlow
}