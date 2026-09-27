package com.pureframe.player.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.pureframe.player.R
import com.pureframe.player.ui.MainActivity
import timber.log.Timber

/**
 * 悬浮窗小窗播放服务
 *
 * 替代系统画中画（系统 PiP 尺寸/位置不可编程，无法满足"横屏全宽置顶、竖屏半宽右上"）。
 * 用 TYPE_APPLICATION_OVERLAY 悬浮窗挂同一个单例 ExoPlayer 的 PlayerView，播放无缝续播。
 *
 * 布局规则（用户需求）：
 * - 横屏视频：宽度=屏幕宽，位于屏幕最上方
 * - 竖屏视频：宽度=屏幕宽一半，位于屏幕右上方
 * - 可拖动（跟手），单击切控制栏，双击播放/暂停
 */
@UnstableApi
class FloatingVideoService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_video"
        const val NOTIFICATION_ID = 2001
        const val ACTION_CLOSE = "com.pureframe.player.floating.CLOSE"
        const val ACTION_BACK_FULLSCREEN = "com.pureframe.player.floating.BACK_FULL"

        /** 是否正在悬浮窗播放 */
        @Volatile
        var isShowing: Boolean = false
            private set

        /** 当前是否竖屏视频（决定悬浮窗布局规则） */
        @Volatile
        var isPortraitVideo: Boolean = false
            private set

        fun start(context: Context, portrait: Boolean) {
            val intent = Intent(context, FloatingVideoService::class.java)
            intent.putExtra("portrait", portrait)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingVideoService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var playerView: PlayerView? = null
    private var floatingTexture: android.view.TextureView? = null
    private var rootView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private lateinit var navigationState: com.pureframe.player.ui.navigation.NavigationState
    private lateinit var playerManager: PlayerManager

    /** 当前窗口布局来源：视频是否竖屏 */
    private var layoutPortrait = false

    /** 是否处于"放大"状态：竖屏 半宽→全宽；横屏 全宽→半宽 */
    private var isExpanded = false

    // 拖动状态
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var paramDownX = 0
    private var paramDownY = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var singleTapPending: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val entry = dagger.hilt.android.EntryPointAccessors.fromApplication(
            application,
            FloatingPlayerEntryPoint::class.java
        )
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        playerManager = entry.playerManager()
        navigationState = entry.navigationState()
        Timber.i("FloatingVideoService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_BACK_FULLSCREEN -> {
                backToFullscreen()
                return START_NOT_STICKY
            }
        }

        val portrait = intent?.getBooleanExtra("portrait", false) ?: false
        isPortraitVideo = portrait

        startForeground(NOTIFICATION_ID, buildNotification())
        showFloatingWindow(portrait)
        return START_NOT_STICKY
    }

    /** 悬浮窗挂载：把 PlayerView 从 Activity 界面切到 WindowManager 窗口 */
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow(portrait: Boolean) {
        if (rootView != null) return // 已在显示
        layoutPortrait = portrait

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels

        // 视频真实宽高比
        val player = playerManager.getPlayer()
        val vs = player.videoSize
        val ratio = if (vs.width > 0 && vs.height > 0) {
            vs.width.toFloat() / vs.height
        } else 16f / 9f

        // 尺寸规则：横屏=屏幕宽；竖屏=屏幕宽一半。高按视频比例推。
        val width = if (portrait) screenWidth / 2 else screenWidth
        val height = (width / ratio).toInt()

        // 位置规则：横屏=屏幕最上方（含状态栏下）；竖屏=右上方
        val gravity = if (portrait) {
            Gravity.TOP or Gravity.END
        } else {
            Gravity.TOP or Gravity.START
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            // 横屏：贴顶；竖屏：贴顶+右缘
            x = 0
            y = 0
        }
        layoutParams = params

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        // 直接用 TextureView + player.setVideoTextureView：
        // TextureView 是普通视图层，无 SurfaceView 独占合成器的问题，切换窗口不黑屏；
        // PlayerView 的 surface_type 只能 XML 指定，代码无法改，故不用它。
        val texture = android.view.TextureView(this)
        texture.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                // surface 就绪后把视频输出切到悬浮窗
                playerManager.getPlayer().setVideoTextureView(texture)
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                // 悬浮窗销毁时把输出切回播放页的 surface 由播放页 PlayerView 重新接管
                return true
            }
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
        }
        container.addView(
            texture,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 顶部控制条：放大/缩小 + 关闭（参考常见视频 App 画中画交互）
        val controlBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setGravity(android.view.Gravity.END)
            setBackgroundColor(0x66000000)
            visibility = android.view.View.GONE // 默认隐藏，单击切换
            // 放大/缩小
            addView(makeControlButton("⤢") {
                toggleExpanded()
                hideControlBarDelayed()
            })
            // 关闭
            addView(makeControlButton("✕") {
                stopSelf()
            })
        }
        val barPx = (44 * resources.displayMetrics.density).toInt()
        container.addView(
            controlBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                barPx
            )
        )

        // 拖动 + 单击/双击手势（绝对坐标拖动，任何 gravity 下都不跳变）
        var moved = false
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    // 统一换算为窗口左上角的绝对屏幕坐标，后续拖动全程使用
                    val loc = IntArray(2)
                    container.getLocationOnScreen(loc)
                    paramDownX = loc[0]
                    paramDownY = loc[1]
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchDownX).toInt()
                    val dy = (event.rawY - touchDownY).toInt()
                    if (moved || dx * dx + dy * dy > 100) { // 10px 阈值才算拖动
                        moved = true
                        controlBar.visibility = android.view.View.GONE
                        params.x = paramDownX + dx
                        params.y = paramDownY + dy
                        windowManager.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (singleTapPending != null) {
                            // 已有等待中的单击 → 本次是双击：取消回全屏，切换播放/暂停
                            singleTapPending?.let { mainHandler.removeCallbacks(it) }
                            singleTapPending = null
                            if (player.isPlaying) player.pause() else player.play()
                        } else {
                            // 第一次 tap：延迟 300ms 等待双击判定，期间再 tap 则切换播放/暂停
                            val r = Runnable {
                                singleTapPending = null
                                // 单击：切换控制条显示；控制条已在显示则回全屏
                                if (controlBar.visibility == android.view.View.VISIBLE) {
                                    backToFullscreen()
                                } else {
                                    controlBar.visibility = android.view.View.VISIBLE
                                    hideControlBarDelayed()
                                }
                            }
                            singleTapPending = r
                            mainHandler.postDelayed(r, 300)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        rootView = container
        playerView = null // 不再使用 PlayerView，用 TextureView
        floatingTexture = texture
        isShowing = true
        // 通知播放页解除 player 绑定（surface 输出归悬浮窗 TextureView）
        navigationState.setFloatingMode(true)
        Timber.i("FloatingVideoService window shown: ${width}x${height} portrait=$portrait")
    }

    /** 构造控制条按钮（无皮肤依赖，纯文本按钮） */
    private fun makeControlButton(label: String, onClick: () -> Unit): android.view.View {
        val btn = android.widget.TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(24, 8, 24, 8)
            setOnClickListener { onClick() }
        }
        return btn
    }

    /** 4 秒无操作后自动隐藏控制条 */
    private fun hideControlBarDelayed() {
        mainHandler.removeCallbacks(hideControlBarRunnable)
        mainHandler.postDelayed(hideControlBarRunnable, 4000)
    }

    private val hideControlBarRunnable = Runnable {
        rootView?.let { root ->
            // 找到控制条（LinearLayout）并隐藏
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is android.widget.LinearLayout) child.visibility = android.view.View.GONE
            }
        }
    }

    /** 放大/缩小切换：竖屏 半宽↔全宽；横屏 全宽↔2/3宽。位置吸附对应角。 */
    private fun toggleExpanded() {
        val params = layoutParams ?: return
        val root = rootView ?: return
        val player = playerManager.getPlayer()
        val vs = player.videoSize
        val ratio = if (vs.width > 0 && vs.height > 0) {
            vs.width.toFloat() / vs.height
        } else 16f / 9f
        val screenWidth = resources.displayMetrics.widthPixels

        isExpanded = !isExpanded
        // 竖屏: 收起=半宽 放大=全宽; 横屏: 收起=全宽 放大=半宽
        // (isExpanded == layoutPortrait) 时为全宽：竖屏放大或横屏收起态
        val targetWidth = if (isExpanded == layoutPortrait) screenWidth else screenWidth / 2
        params.width = targetWidth
        params.height = (targetWidth / ratio).toInt()
        // 吸附角保持：竖屏右上 / 横屏左上
        params.gravity = if (layoutPortrait) Gravity.TOP or Gravity.END else Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        windowManager.updateViewLayout(root, params)
    }

    /** 回全屏：拉起 MainActivity 到播放页，销毁悬浮窗 */
    private fun backToFullscreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        stopSelf()
    }

    override fun onDestroy() {
        // 清理等待中的单击回全屏/隐藏控制条回调
        singleTapPending?.let { mainHandler.removeCallbacks(it) }
        singleTapPending = null
        mainHandler.removeCallbacks(hideControlBarRunnable)
        // 清空视频输出（播放页回前台后 PlayerView 会重新 setVideoSurfaceView 接管）
        runCatching { floatingTexture?.let { playerManager.getPlayer().clearVideoTextureView(it) } }
        floatingTexture = null
        rootView?.let {
            runCatching { windowManager.removeViewImmediate(it) }
        }
        rootView = null
        playerView = null
        layoutParams = null
        isShowing = false
        navigationState.setFloatingMode(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        Timber.i("FloatingVideoService destroyed")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val backFull = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val close = PendingIntent.getService(
            this, 2,
            Intent(this, FloatingVideoService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_playing))
            .setSmallIcon(R.drawable.ic_pip_play)
            .addAction(0, getString(R.string.floating_back_full), backFull)
            .addAction(0, getString(R.string.floating_close), close)
            .setOngoing(true)
            .build()
    }
}

/** Hilt 入口：服务里取 PlayerManager / NavigationState 单例 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface FloatingPlayerEntryPoint {
    fun playerManager(): PlayerManager
    fun navigationState(): com.pureframe.player.ui.navigation.NavigationState
}
