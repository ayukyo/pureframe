package com.pureframe.player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 纯帧应用
 * 
 * 纯粹观影，只留帧影
 */
@HiltAndroidApp
class PureFrameApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化应用
    }
}
