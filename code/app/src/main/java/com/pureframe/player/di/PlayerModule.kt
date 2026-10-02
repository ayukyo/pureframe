package com.pureframe.player.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
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
        // NextRenderersFactory（nextlib-media3ext）：DefaultRenderersFactory 的一比一兼容
        // 替代，额外注册 ffmpeg 软解渲染器（DTS/AC3/EAC3 音轨 + H.264/HEVC/VP9 软解兜底）。
        // 之前用 DefaultRenderersFactory 时 SOFTWARE 模式实际无任何软解器可开（未引入扩展包）。
        // EXTENSION_RENDERER_MODE_PREFER: 扩展渲染器排前（软解优先于硬解）
        // EXTENSION_RENDERER_MODE_ON:      扩展渲染器启用但排在硬解之后（硬解优先）
        // EXTENSION_RENDERER_MODE_OFF:     不用扩展渲染器（纯硬解）
        val extensionRendererMode = when (decoderType) {
            DecoderType.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderType.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            DecoderType.AUTO -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }

        val renderersFactory = NextRenderersFactory(context)
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