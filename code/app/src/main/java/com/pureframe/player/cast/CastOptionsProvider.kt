package com.pureframe.player.cast

import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Google Cast 初始化选项
 *
 * manifest 的 OPTIONS_PROVIDER_CLASS_NAME 指向此类，CastContext 首次获取时反射创建。
 * 使用默认 receiver（CC1AD845）：可播 mp4/mkv/webm（h264/vp9 + aac/opus），
 * 无需注册自定义 Cast Receiver 应用。
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: android.content.Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: android.content.Context): List<SessionProvider>? =
        null
}
