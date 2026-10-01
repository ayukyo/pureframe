package com.pureframe.player.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.pureframe.player.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * 播放服务
 *
 * 使用 Media3 MediaSessionService 管理播放。
 *
 * 进程保活（PR5）：
 * - MediaSessionService 是 bound service，必须有 [androidx.media3.session.MediaController] 连着才不被销毁。
 * - 客户端（PlayerViewModel）构造 MediaController 连接到它 → service 升为前台服务，通知栏 media 控件可见。
 * - 客户端断开连接 → 播放停止 10 分钟后（Media3 默认）service 转 background 可被回收。
 *
 * 这里不主动 [android.content.Context.startForegroundService]，也不接收任何 bind intent 之外的
 * startService 路径。客户端只需要通过 [getSessionToken] 拿到 token 即可用 MediaController 连接。
 *
 * ## 单 Activity 模式下客户端进程保活由谁负责？
 * - 播放期间：MediaSessionService 自身在前台服务里 → 进程不会被杀。
 * - 退出播放页 → client unbind MediaController → service 默认会转为 background，
 *   10min 后销毁；但进程本身仍可能因 ExoPlayer 单例（@Singleton）持有资源而保留。
 */
@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @Inject
    lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        // 创建 MediaSession。Media3 默认通过 DefaultMediaNotificationProvider
        // 在通知栏展示 media 控件（标题/暂停/上下一首），无需自定义。
        // 注意：通知标题取自 MediaItem.mediaMetadata.title，即 PlayerManager 加载时的视频标题。
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildLaunchActivityPendingIntent())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // 允许控制器连接
                    return super.onConnect(session, controller)
                }
            })
            .build()
        Timber.i("PlaybackService onCreate, MediaSession 已建立")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        Timber.i("PlaybackService onDestroy, MediaSession 已释放")
        super.onDestroy()
    }

    /**
     * 点击通知/媒体控件时拉起的 PendingIntent：直接进 MainActivity，
     * 由 NavController 切到播放页（如果当前视频仍激活）。
     */
    private fun buildLaunchActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    companion object {
        /**
         * 同步构造 [SessionToken]，无需 ListenableFuture。
         *
         * 用构造器路径（1.9.0 没有 public static createSessionToken API）。
         * 内部走 SessionTokenImplBase/TYPE_SESSION_SERVICE，本应用内 binder 已知 host，
         * 不会像 [androidx.media3.session.SessionToken.createSessionToken] 走 Legacy
         * CompatToken 反序列化路径（该路径在 Android 13+ Parcel 严格校验下会抛
         * ClassNotFoundException）。
         */
        @JvmStatic
        fun newSessionToken(appContext: Context): SessionToken =
            SessionToken(
                appContext.applicationContext,
                ComponentName(appContext.applicationContext, PlaybackService::class.java)
            )
    }
}