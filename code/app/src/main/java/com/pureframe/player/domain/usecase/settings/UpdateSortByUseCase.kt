package com.pureframe.player.domain.usecase.settings

import com.pureframe.player.data.preferences.SortBy
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新排序方式 UseCase
 * 
 * 更改视频列表的排序方式
 * 
 * @param params SortBy 枚举值
 */
@Singleton
class UpdateSortByUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ParamSuspendActionUseCase<SortBy>() {
    
    override suspend fun invoke(params: SortBy) {
        preferencesRepository.updateSortBy(params)
    }
}