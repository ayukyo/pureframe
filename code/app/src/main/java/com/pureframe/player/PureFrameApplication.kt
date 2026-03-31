package com.pureframe.player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * 纯帧应用
 * 
 * 纯粹观影，只留帧影
 */
@HiltAndroidApp
class PureFrameApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Timber 日志
        Timber.plant(Timber.DebugTree())
        
        Timber.d("PureFrame Application initialized")
    }
}
