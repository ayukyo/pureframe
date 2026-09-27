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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
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

    /** 尺寸档位宽度系数：0=半宽 1=中(70%) 2=全宽 */
    private val scaleFactors = floatArrayOf(0.5f, 0.7f, 1.0f)

    /** 当前尺寸档位：竖屏初始最小档(0)，横屏初始全宽档(2) */
    private var scaleLevel = 0

    /** 控制条按钮的引用（用于边界变灰、图标切换；hit-test 按坐标判定） */
    private var playPauseButton: android.view.View? = null
    private var playPauseLabel: TextView? = null
    private var shrinkButton: android.view.View? = null
    private var enlargeButton: android.view.View? = null
    private var closeButton: android.view.View? = null

    /** Player 监听：自动同步 ⏸ ↔ ▶ 图标（外部 play()/pause() 调用后也能刷新） */
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon()
        }
    }

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

        // 尺寸规则：竖屏初始=半宽（最小档），横屏初始=全宽（最大档）。高按视频比例推。
        scaleLevel = if (portrait) 0 else scaleFactors.size - 1
        val width = (screenWidth * scaleFactors[scaleLevel]).toInt()
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

        val density = resources.displayMetrics.density
        val cornerRadiusPx = (28 * density).toInt() // iOS PiP 风圆角

        val container = FrameLayout(this).apply {
            // 圆角背景：GradientDrawable + setClipToOutline 让视频也按圆角裁剪
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = cornerRadiusPx.toFloat()
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx.toFloat())
                }
            }
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

        // 顶部控制条：缩小 / 放大 / 关闭（参考 iOS PiP 视觉，胶囊半透黑底 + 圆形按钮）
        // 不在按钮上挂 onClickListener：父 setOnTouchListener 必须 return true 才能持续接收
        // MOVE/UP 实现拖动 + 单/双击，但子 view onClick 会消化 DOWN 让外层失去 MOVE/UP。
        // 改为：父 onTouchListener 单击时按 hit-test 决定触发哪个按钮
        val barPx = (56 * density).toInt() // 控制条高度（含上下内边距）
        val btnSizePx = (40 * density).toInt() // 40dp 圆形按钮
        val btnMargin = (6 * density).toInt() // 按钮间距
        val btnRightPx = (12 * density).toInt() // 按钮距控制条右缘

        val btnPlayPause = makeControlLabel("⏸", btnSizePx)
        val btnShrink = makeControlLabel("－", btnSizePx)
        val btnEnlarge = makeControlLabel("＋", btnSizePx)
        val btnClose = makeControlLabel("✕", btnSizePx)
        val controlBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setGravity(android.view.Gravity.END) // 子 view 靠右排列（⏸ － ＋ ✕ 在右侧）
            setBackgroundColor(0x00000000) // 透明背景，避免遮挡视频；按钮自带圆底
            visibility = android.view.View.GONE // 默认隐藏，单击切换
            // addView 顺序：⏸ 缩小 放大 关闭；gravity=END → 从左到右：⏸ － ＋ ✕
            addView(btnPlayPause)
            addView(btnShrink)
            addView(btnEnlarge)
            addView(btnClose)
            setPadding(0, (8 * density).toInt(), btnRightPx, 0)
        }
        playPauseButton = btnPlayPause
        playPauseLabel = btnPlayPause as TextView
        shrinkButton = btnShrink
        enlargeButton = btnEnlarge
        closeButton = btnClose
        // 给前三个子 view 加右边距（间距 6dp，让四个按钮不要太近；✕ 是最右的，不加 marginEnd）
        for (i in 0 until controlBar.childCount - 1) {
            val child = controlBar.getChildAt(i)
            (child.layoutParams as android.widget.LinearLayout.LayoutParams).marginEnd = btnMargin
        }
        // 绑定 Player 监听：自动同步播放/暂停图标
        runCatching { playerManager.getPlayer().removeListener(playerListener) }
        playerManager.getPlayer().addListener(playerListener)
        // 初始图标（按当前真实状态）
        updatePlayPauseIcon()
        container.addView(
            controlBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                barPx,
                Gravity.TOP or Gravity.END
            )
        )
        // 初始档位后刷新边界按钮状态（缩放在最小档时 － 灰，最大档时 ＋ 灰）
        updateBoundaryButtons()

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
                    // DOWN 始终 return true：保证后续 MOVE/UP 都被外层收到（拖动 + 单/双击）；
                    // 控制条按钮的 hit-test 在 UP 单击 Runnable 内做（不依赖子 view onClick）。
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchDownX).toInt()
                    val dy = (event.rawY - touchDownY).toInt()
                    if (moved || dx * dx + dy * dy > 100) { // 10px 阈值才算拖动
                        moved = true
                        controlBar.visibility = android.view.View.GONE
                        // 目标窗口左上角绝对坐标
                        val targetLeft = paramDownX + dx
                        val targetTop = paramDownY + dy
                        // 统一转为 TOP|START 语义（x=距左缘），无论当前 gravity 是什么
                        params.gravity = Gravity.TOP or Gravity.START
                        params.x = targetLeft
                        params.y = targetTop
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
                                if (controlBar.visibility == android.view.View.VISIBLE) {
                                    // 控制条已显示 → 按 hit-test 决定触发哪个按钮
                                    val btnHit = hitTestControlButton(touchDownX, touchDownY)
                                    when (btnHit) {
                                        0 -> { // ⏸ / ▶ 切换播放暂停
                                            if (player.isPlaying) player.pause() else player.play()
                                            // 不立即 hide：让用户看到图标切换，4s 自动隐藏
                                            hideControlBarDelayed()
                                        }
                                        1 -> {
                                            changeScale(-1)
                                            hideControlBarDelayed()
                                        }
                                        2 -> {
                                            changeScale(+1)
                                            hideControlBarDelayed()
                                        }
                                        3 -> {
                                            stopSelf()
                                        }
                                        else -> {
                                            // 控制条已显示但点击落在视频区（控制条外）→ 保持控制条可见，
                                            // 仅重置 4s 自动隐藏计时（避免点击按钮前控制条消失）
                                            hideControlBarDelayed()
                                        }
                                    }
                                } else {
                                    // 控制条隐藏 → 显示
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

    /** 构造控制条按钮：40dp 圆形半透明白底 + 阴影（iOS PiP 风）。仅标签，无 onClickListener。 */
    private fun makeControlLabel(label: String, sizePx: Int): android.view.View {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = label
            textSize = if (label == "✕") 18f else 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            // 圆形半透明白底（iOS PiP 风）
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x99000000.toInt())
            }
            // 阴影（elevation 在小圆上效果有限，主要是 Outline）
            elevation = 4 * density
        }
        // 强制宽高 = sizePx（圆形按钮需要精确尺寸，不能 wrap）
        tv.layoutParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx)
        return tv
    }

    /** 4 秒无操作后自动隐藏控制条 */
    private fun hideControlBarDelayed() {
        mainHandler.removeCallbacks(hideControlBarRunnable)
        mainHandler.postDelayed(hideControlBarRunnable, 4000)
    }

    /**
     * 在控制条内对 (x, y) 做 hit-test，返回按钮索引（0=⏸, 1=缩小, 2=放大, 3=关闭）或 -1（未命中）。
     * 按钮是固定 40dp 圆形，gravity=END 靠右排列。依次从右向左为 ✕、＋、－、⏸。
     * 按按钮圆心距离判定：圆心半径 distance < btnSize/2 视为命中。
     */
    private fun hitTestControlButton(rawX: Float, rawY: Float): Int {
        val root = rootView ?: return -1
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child !is android.widget.LinearLayout) continue
            val loc = IntArray(2)
            child.getLocationOnScreen(loc)
            val barLeft = loc[0]
            val barRight = barLeft + child.width
            val barTop = loc[1]
            val barBottom = barTop + child.height
            if (rawY < barTop || rawY > barBottom) continue
            if (rawX < barLeft || rawX > barRight) continue
            // 子顺序：⏸ － ＋ ✕，gravity=END → 从左到右排列：⏸ － ＋ ✕
            // 即子 idx 0(⏸) 在最左、idx 3(✕) 在最右
            val n = child.childCount
            if (n == 0) return -1
            val halfBtn = (child.getChildAt(0).width.toFloat() / 2f)
            var bestIdx = -1
            var bestDist = Float.MAX_VALUE
            for (j in 0 until n) {
                val btn = child.getChildAt(j)
                val btnLoc = IntArray(2)
                btn.getLocationOnScreen(btnLoc)
                val cx = btnLoc[0] + btn.width / 2f
                val cy = btnLoc[1] + btn.height / 2f
                val d = kotlin.math.hypot((rawX - cx).toDouble(), (rawY - cy).toDouble()).toFloat()
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = j
                }
            }
            if (bestIdx < 0 || bestDist > halfBtn) return -1
            // 子 idx → 按钮编号：0=⏸, 1=-/缩小, 2=+/放大, 3=✕/关闭
            return bestIdx
        }
        return -1
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

    /** 多档缩放：dir=+1 放大 / -1 缩小，到边界不动。缩放后吸附到水平边缘。 */
    private fun changeScale(dir: Int) {
        val params = layoutParams ?: return
        val root = rootView ?: return
        val newLevel = (scaleLevel + dir).coerceIn(0, scaleFactors.size - 1)
        if (newLevel == scaleLevel) return
        scaleLevel = newLevel

        val player = playerManager.getPlayer()
        val vs = player.videoSize
        val ratio = if (vs.width > 0 && vs.height > 0) {
            vs.width.toFloat() / vs.height
        } else 16f / 9f
        val screenWidth = resources.displayMetrics.widthPixels

        val targetWidth = (screenWidth * scaleFactors[scaleLevel]).toInt()
        params.width = targetWidth
        params.height = (targetWidth / ratio).toInt()
        // 吸附：竖屏吸右缘、横屏吸左缘（保持 y 不变，只水平吸附）
        params.gravity = if (layoutPortrait) Gravity.TOP or Gravity.END else Gravity.TOP or Gravity.START
        params.x = 0
        windowManager.updateViewLayout(root, params)
        // 同步刷新边界按钮状态（缩到最小档时 － 灰，缩到最大档时 ＋ 灰）
        updateBoundaryButtons()
    }

    /**
     * 根据当前 scaleLevel 刷新边界按钮 alpha：
     * - 已在最小档(0)：shrinkButton 灰掉 (alpha=0.3)
     * - 已在最大档(size-1)：enlargeButton 灰掉
     * - ⏸ 永远可点（不受档位影响）
     * 视觉上让用户知道"已经无法再缩/放了"，点击落到已灰按钮由 hit-test 判定不触发 changeScale。
     */
    private fun updateBoundaryButtons() {
        val min = 0
        val max = scaleFactors.size - 1
        shrinkButton?.alpha = if (scaleLevel == min) 0.3f else 1.0f
        enlargeButton?.alpha = if (scaleLevel == max) 0.3f else 1.0f
        // ⏸ / ✕ 永远可点
        playPauseButton?.alpha = 1.0f
        closeButton?.alpha = 1.0f
    }

    /**
     * 同步播放/暂停按钮图标：播放中显示 ⏸，暂停/未播放显示 ▶。
     * 由 Player.Listener.onIsPlayingChanged 自动触发，也可在创建时手动调一次保证初始状态正确。
     */
    private fun updatePlayPauseIcon() {
        val label = playPauseLabel ?: return
        runCatching {
            label.text = if (playerManager.getPlayer().isPlaying) "⏸" else "▶"
        }
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
        // 解绑 Player 监听
        runCatching { playerManager.getPlayer().removeListener(playerListener) }
        // 清空视频输出（播放页回前台后 PlayerView 会重新 setVideoSurfaceView 接管）
        runCatching { floatingTexture?.let { playerManager.getPlayer().clearVideoTextureView(it) } }
        floatingTexture = null
        rootView?.let {
            runCatching { windowManager.removeViewImmediate(it) }
        }
        rootView = null
        playerView = null
        layoutParams = null
        playPauseButton = null
        playPauseLabel = null
        shrinkButton = null
        enlargeButton = null
        closeButton = null
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
