package com.pureframe.player.domain.usecase.settings

import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新下载路径 UseCase
 * 
 * 更改磁链下载的默认保存路径
 * 
 * @param params 新的下载路径
 */
@Singleton
class UpdateDownloadPathUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ParamSuspendActionUseCase<String>() {
    
    override suspend fun invoke(params: String) {
        preferencesRepository.updateDownloadPath(params)
    }
}