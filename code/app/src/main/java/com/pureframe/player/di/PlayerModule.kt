package com.pureframe.player.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.pureframe.player.player.PlayerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块 - 提供播放器相关依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    
    @Provides
    @Singleton
    @UnstableApi
    fun provideTrackSelector(
        @ApplicationContext context: Context
    ): DefaultTrackSelector {
        return DefaultTrackSelector(context)
    }
    
    @Provides
    @Singleton
    @UnstableApi
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        trackSelector: DefaultTrackSelector
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10_000)   // 快退 10 秒
            .setSeekForwardIncrementMs(10_000) // 快进 10 秒
            .build()
            .apply {
                // 默认配置
                playWhenReady = false
                volume = 1f
            }
    }
    
    @Provides
    @Singleton
    @UnstableApi
    fun providePlayerManager(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer
    ): PlayerManager {
        return PlayerManager(context, exoPlayer)
    }
}