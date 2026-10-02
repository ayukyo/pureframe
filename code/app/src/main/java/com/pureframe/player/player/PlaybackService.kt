package com.pureframe.player.player

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pureframe.player.R
import com.pureframe.player.data.preferences.UserPreferencesRepository
import com.pureframe.player.data.repository.VideoRepository
import com.pureframe.player.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * ## 通知栏 / 锁屏 上一首 / 下一首按钮（PR7）
 * - 通知栏 / 锁屏由 [androidx.media3.session.DefaultMediaNotificationProvider] 渲染。
 * - ExoPlayer 持有 ≥2 个 MediaItem 的 playlist 时，Player.AvailableCommands 自动启用
 *   [Player.COMMAND_SEEK_TO_NEXT] / [Player.COMMAND_SEEK_TO_PREVIOUS]，provider 据此渲染 prev/next 按钮。
 *
 * ## 通知自定义按钮（PR8）
 * - [setCustomLayout] 挂 3 个 [CommandButton]：字幕开关 / 循环模式 / 收藏。
 * - 点击走 [onCustomCommand]，操作同一 [PlayerManager] 单例（ExoPlayer 天然共享），
 *   状态经 Player.Listener 回灌 → [buildCustomLayout] 重建 → [MediaSession.setCustomLayout]
 *   推送新图标。播放页 UI 侧的开关变化同样经 listener 回灌刷新通知图标（双向同步）。
 * - 收藏按钮按 currentMediaItem.uri 反查 VideoRepository（无需跨层同步 currentVideoId）。
 *
 * ## 媒体通知总开关（PR8 语义 A）
 * - 设置关闭 → release MediaSession + stopSelf：通知栏 / 锁屏媒体控件全部消失。
 * - 代价（已在设置文案告知）：前台服务保活同时停用，后台播放可能被系统（MIUI）杀。
 * - 重新开启：由 PlayerViewModel 监听同一开关后重建 MediaController 触发 onCreate 重建。
 *
 * ## 锁屏通知可见性开关（PR8 语义 B）
 * - 自定义 [MediaNotification.Provider] 包装 DefaultMediaNotificationProvider，
 *   设置关闭时把通知 visibility 置为 VISIBILITY_SECRET（锁屏不显示通知本体）。
 * - 系统锁屏媒体卡片（Android 11+ keyguard media controls）由 SystemUI 基于 MediaSession
 *   渲染，不经过通知，因此部分机型上锁屏卡片仍会显示 —— 设置文案已声明该限制。
 */
@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 锁屏是否显示通知内容（PR8 语义 B，由设置驱动） */
    private val lockscreenVisible = MutableStateFlow(true)

    /** 当前 MediaItem 是否已收藏（PR8 收藏按钮图标状态） */
    private var isCurrentFavorite = false

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var videoRepository: VideoRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val defaultNotificationProvider by lazy {
        DefaultMediaNotificationProvider(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 同步读一次总开关：关闭则不建 session、不起前台（避免通知一闪而过）
        val notificationEnabled = runBlocking {
            runCatching {
                userPreferencesRepository.userPreferencesFlow.first().mediaNotificationEnabled
            }.getOrDefault(true)
        }
        if (!notificationEnabled) {
            Timber.i("PlaybackService: 媒体通知总开关为关，不建立 MediaSession")
            stopSelf()
            return
        }

        createMediaSession()
        observePreferences()
    }

    private fun createMediaSession() {
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildLaunchActivityPendingIntent())
            // PR8：全局 custom layout。通知由 MediaSessionService 用 session 级 layout 渲染，
            // 只在 onConnect 里给单个 controller 设置对通知无效（系统通知 controller 是匿名的）。
            .setCustomLayout(buildCustomLayout())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // PR8：自定义按钮生效前提 —— controller 的 available session commands
                    // 必须包含按钮对应的 SessionCommand，否则通知渲染时按 available commands
                    // 过滤掉这些按钮（DefaultMediaNotificationProvider.getMediaButtons 行为）。
                    // 默认 AcceptedResultBuilder 只含标准 commands，必须显式 add。
                    val sessionCommands =
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand(COMMAND_TOGGLE_REPEAT, Bundle.EMPTY))
                            .add(SessionCommand(COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
                            .build()
                    // PR7 自动启用的 SEEK_TO_NEXT/PREVIOUS 在默认 player commands 里，
                    // 这里只需扩展 session commands + per-controller custom layout。
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .setCustomLayout(buildCustomLayout())
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    return handleCustomCommand(session, customCommand)
                }
            })
            .build()

        // PR8：player 状态变化 → 通知按钮图标刷新。
        // 覆盖两个方向：通知按钮点击（onCustomCommand 内已同步刷新）+ 播放页 UI 开关变化。
        player.addListener(servicePlayerListener)
        refreshFavoriteState()

        // PR8 语义 B：锁屏可见性 provider（visibility=SECRET）
        setMediaNotificationProvider(LockscreenAwareProvider())

        Timber.i("PlaybackService onCreate, MediaSession 已建立")
    }

    /** PR8：设置变化观察（总开关动态关闭方向 + 锁屏可见性） */
    private fun observePreferences() {
        serviceScope.launch {
            userPreferencesRepository.userPreferencesFlow
                .map { it.mediaNotificationEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (!enabled && mediaSession != null) {
                        Timber.i("PlaybackService: 总开关关闭 → 释放 MediaSession + stopSelf")
                        player.removeListener(servicePlayerListener)
                        runCatching { mediaSession?.release() }
                        mediaSession = null
                        stopSelf()
                    }
                    // 开启方向由 PlayerViewModel 重建 MediaController → onCreate 重建 session
                }
        }
        serviceScope.launch {
            userPreferencesRepository.userPreferencesFlow
                .map { it.lockscreenMediaVisible }
                .distinctUntilChanged()
                .collect { visible ->
                    lockscreenVisible.value = visible
                    // visibility 只在 createNotification 时应用：开关变化后主动触发
                    // 一次通知重建，否则要等下一次 player 状态变化才生效
                    if (mediaSession != null) {
                        runCatching { triggerNotificationUpdate() }
                    }
                }
        }
    }

    // ---------- PR8 自定义按钮 ----------

    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        val repeatIcon = when (playerManager.repeatMode.value) {
            Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat_all
            Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
            else -> R.drawable.ic_notif_repeat_off
        }
        val repeatButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.notif_button_repeat))
            .setIconResId(repeatIcon)
            .setSessionCommand(SessionCommand(COMMAND_TOGGLE_REPEAT, Bundle.EMPTY))
            .build()

        val favoriteButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.notif_button_favorite))
            .setIconResId(
                if (isCurrentFavorite) R.drawable.ic_notif_favorite_on
                else R.drawable.ic_notif_favorite_off
            )
            .setSessionCommand(SessionCommand(COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()

        // 字幕按钮已移除（PR8 验收后调整）：MIUI 通知 action 上限 5，去掉字幕让收藏
        // 稳定进大视图 actions 行；字幕开关保留在播放页 UI 内
        return ImmutableList.of(repeatButton, favoriteButton)
    }

    private fun refreshCustomLayout() {
        val session = mediaSession ?: return
        runCatching { session.setCustomLayout(buildCustomLayout()) }
            .onFailure { Timber.w(it, "setCustomLayout 失败（session 可能已释放）") }
    }

    /**
     * 切歌后异步反查当前视频收藏态 → 刷新收藏按钮图标。
     * 队列里非库内视频（file 系统 scan 兜底项）查不到时按未收藏处理。
     */
    private fun refreshFavoriteState() {
        serviceScope.launch {
            isCurrentFavorite = queryCurrentFavorite()
            refreshCustomLayout()
        }
    }

    private suspend fun queryCurrentFavorite(): Boolean {
        val path = player.currentMediaItem?.localConfiguration?.uri?.toString()
            ?.removePrefix("file://") ?: return false
        return runCatching { videoRepository.getVideoByPath(path)?.isFavorite ?: false }
            .getOrDefault(false)
    }

    private fun handleCustomCommand(
        session: MediaSession,
        customCommand: SessionCommand
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            COMMAND_TOGGLE_REPEAT -> {
                playerManager.toggleRepeat()
                refreshCustomLayout()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            COMMAND_TOGGLE_FAVORITE -> {
                toggleFavorite()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            else -> {
                Timber.d("PlaybackService 收到未知自定义 command: %s", customCommand.customAction)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }
    }

    private fun toggleFavorite() {
        serviceScope.launch {
            val path = player.currentMediaItem?.localConfiguration?.uri?.toString()
                ?.removePrefix("file://") ?: return@launch
            runCatching {
                val video = videoRepository.getVideoByPath(path) ?: return@launch
                val newFavorite = !video.isFavorite
                videoRepository.updateFavorite(video.id, newFavorite)
                isCurrentFavorite = newFavorite
                refreshCustomLayout()
                Timber.i("PlaybackService: 收藏 toggle → %s (%s)", newFavorite, video.title)
            }.onFailure { Timber.w(it, "收藏 toggle 失败") }
        }
    }

    /** PR8：player 状态 → 通知按钮图标（UI 侧开关变化也走这里） */
    private val servicePlayerListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            refreshCustomLayout()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshFavoriteState()
        }
    }

    // ---------- PR8 锁屏可见性 provider（语义 B） ----------

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * 包装 DefaultMediaNotificationProvider：锁屏开关关闭时把通知 visibility
     * 置为 VISIBILITY_SECRET（安全锁屏上不显示通知本体）。系统媒体卡片是否
     * 消失取决于 ROM（Android 11+ keyguard media controls 由 session 驱动）。
     */
    private inner class LockscreenAwareProvider : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            callback: MediaNotification.Provider.Callback
        ): MediaNotification {
            val mediaNotification = defaultNotificationProvider.createNotification(
                mediaSession, customLayout, actionFactory, callback
            )
            if (lockscreenVisible.value) return mediaNotification

            val rebuilt: Notification = Notification.Builder.recoverBuilder(
                this@PlaybackService, mediaNotification.notification
            )
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build()
            return MediaNotification(mediaNotification.notificationId, rebuilt)
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean = defaultNotificationProvider.handleCustomCommand(session, action, extras)
    }

    override fun onDestroy() {
        player.removeListener(servicePlayerListener)
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        Timber.i("PlaybackService onDestroy, MediaSession 已释放")
        super.onDestroy()
    }

    /**
     * 点击通知/媒体控件时拉起的 PendingIntent：
     * - ACTION_MAIN + NEW_TASK + REORDER_TO_FRONT：把已在后台的 MainActivity 拉到前台
     * - 再触发 CATEGORY_LAUNCHER：系统级 fallback（如果 app 被回收，重启后回到首页）
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
        const val COMMAND_TOGGLE_REPEAT = "com.pureframe.player.command.TOGGLE_REPEAT"
        const val COMMAND_TOGGLE_FAVORITE = "com.pureframe.player.command.TOGGLE_FAVORITE"

        /**
         * 同步构造 [SessionToken]，无需 ListenableFuture。
         *
         * 用构造器路径（1.9.0 没有 public static createSessionToken API）。
         * 内部走 SessionTokenImplBase/TYPE_SESSION_SERVICE，本应用内 binder 已知 host，
         * 不会走 Legacy CompatToken 反序列化路径（该路径在 Android 13+ Parcel 严格校验下会抛
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
