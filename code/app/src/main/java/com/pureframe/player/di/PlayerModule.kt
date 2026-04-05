package com.pureframe.player.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.pureframe.player.data.preferences.DecoderType
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.player.PlayerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        trackSelector: DefaultTrackSelector,
        userPreferencesRepository: UserPreferencesRepository
    ): ExoPlayer {
        // 获取解码器偏好
        val decoderType = runBlocking {
            userPreferencesRepository.userPreferencesFlow.first().decoderType
        }

        // 配置渲染器工厂
        // EXTENSION_RENDERER_MODE_PREFER: 优先使用扩展渲染器（通常是硬件加速）
        // EXTENSION_RENDERER_MODE_ON: 启用所有扩展渲染器（软件解码优先）
        val extensionRendererMode = when (decoderType) {
            DecoderType.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            DecoderType.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderType.AUTO -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(extensionRendererMode)

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10_000)   // 快退 10 秒
            .setSeekForwardIncrementMs(10_000) // 快进 10 秒
            .build()
            .also { player ->
                player.playWhenReady = false
                player.volume = 1f
            }
    }

    @Provides
    @Singleton
    @UnstableApi
    fun providePlayerManager(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        userPreferencesRepository: UserPreferencesRepository
    ): PlayerManager {
        return PlayerManager(context, exoPlayer, userPreferencesRepository)
    }
}