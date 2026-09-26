package com.pureframe.player.domain.usecase.player

import com.pureframe.player.domain.usecase.ParamSuspendActionUseCase
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pureframe.player.R
import com.pureframe.player.i18n.LocaleManager

/**
 * 检查播放位置有效性 UseCase
 * 
 * 边下边播时，检查用户尝试跳转的位置是否在已缓存范围内
 * 
 * @param params CheckSeekPositionParams 包含目标位置和最大允许位置
 */
@Singleton
class CheckSeekPositionUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) : 
    ParamSuspendActionUseCase<CheckSeekPositionUseCase.Params>() {
    
    data class Params(
        val targetPosition: Long,    // 目标跳转位置（毫秒）
        val maxPosition: Long,       // 最大允许位置（毫秒）
        val totalDuration: Long      // 视频总时长（毫秒）
    )
    
    data class Result(
        val isValid: Boolean,
        val allowedPosition: Long,
        val message: String? = null
    )
    
    /**
     * 检查并返回结果（不是 ActionUseCase 的标准用法，但更实用）
     */
    fun check(params: Params): Result {
        if (params.targetPosition > params.maxPosition) {
            // 超出缓存范围，返回最大允许位置
            val percentage = (params.maxPosition * 100 / params.totalDuration)
            return Result(
                isValid = false,
                allowedPosition = params.maxPosition,
                message = LocaleManager.getString(context, R.string.error_seek_beyond_cached, percentage)
            )
        }
        
        return Result(
            isValid = true,
            allowedPosition = params.targetPosition
        )
    }
    
    override suspend fun invoke(params: Params) {
        // 空实现，实际使用 check() 方法
    }
}