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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
 * ## 通知栏 / 锁屏 上一首 / 下一首按钮（PR7）
 * - 通知栏 / 锁屏由 [androidx.media3.session.DefaultMediaNotificationProvider] 渲染。
 * - ExoPlayer 持有 ≥2 个 MediaItem 的 playlist 时，Player.AvailableCommands 自动启用
 *   [Player.COMMAND_SEEK_TO_NEXT] / [Player.COMMAND_SEEK_TO_PREVIOUS]，provider 据此渲染 prev/next 按钮。
 * - 用户点通知栏「下一首」→ MediaController.sendCustomCommand("next"?) → 内部转发到 ExoPlayer.seekToNextMediaItem()，
 *   playlist 自动切到下一个 sibling（无需 service 介入 onCustomCommand）。
 *
 * ## 单 Activity 模式下客户端进程保活由谁负责？
 * - 播放期间：MediaSessionService 自身在前台服务里 → 进程不会被杀。
 * - 退出播放页 → client unbind MediaController → service 默认会转为 background，
 *   10min 后销毁；但进程本身仍可能因 ExoPlayer 单例（@Singleton）持有资源而保留。
 *
 * ## 自定义 command 扩展点
 * - PR8 起允许挂自定义 session command（如「跳过 30s」「收藏」「循环目录」等）。
 * - 当前 PR7 不启用任何自定义 command，预留 [CustomCommandHandler] 抽象供后续 PR 接入。
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
        //
        // PR7：只要 ExoPlayer playlist 含 ≥2 个 item，Player.AvailableCommands 自动启用
        // COMMAND_SEEK_TO_NEXT/PREVIOUS，DefaultMediaNotificationProvider 据此渲染 prev/next 按钮，
        // 锁屏控件同理。本 service 不需要写 onCustomCommand / setCustomLayout —— 全部交给 Media3 默认行为。
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildLaunchActivityPendingIntent())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // 默认 ConnectionResult 已包含 PREPARE/PLAY_PAUSE/SEEK 等，
                    // 且 ExoPlayer playlist ≥2 项时 SEEK_TO_NEXT/PREVIOUS 自动启用，
                    // 通知栏/锁屏天然显示上下首按钮。
                    return super.onConnect(session, controller)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle
                ): ListenableFuture<SessionResult> {
                    // 预留供 PR8 等后续扩展用。当前 PR7 直接走 ExoPlayer 内置 command 路径，
                    // 此处只处理"已注册但当前不响应"的未知自定义 command。
                    Timber.d("PlaybackService 收到自定义 command（未处理）: %s", customCommand.customAction)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
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
     * 点击通知/媒体控件时拉起的 PendingIntent：
     * - ACTION_MAIN + NEW_TASK + REORDER_TO_FRONT：把已在后台的 MainActivity 拉到前台
     * - 再触发 CATEGORY_LAUNCHER：系统级 fallback（如果 app 被回收，重启后回到首页）
     * - 用户体验上：玩家在桌面 / 锁屏 / 系统多任务里点通知 → app 回到前台
     *   → PlayerViewModel 仍持有 mediaController → 播放继续，画面回归播放页
     */
    private fun buildLaunchActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
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