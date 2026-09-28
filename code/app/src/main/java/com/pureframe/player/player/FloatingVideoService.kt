package com.pureframe.player.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private var playPauseIcon: PlayPauseIconView? = null
    private var shrinkButton: android.view.View? = null
    private var enlargeButton: View? = null
    private var closeButton: View? = null
    private var backButton: View? = null // 左上角"回到播放页"按钮

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
        //
        // 播放/暂停按钮单独放在浮窗中央（不在控制条里），与控制条同一视觉风格但稍大（48dp）。
        val barPx = (56 * density).toInt() // 控制条高度（含上下内边距）
        val btnSizePx = (40 * density).toInt() // 40dp 圆形按钮
        val btnSizeLargePx = (48 * density).toInt() // ⏸ 稍大一档（48dp）
        val btnMargin = (6 * density).toInt() // 按钮间距
        val btnRightPx = (12 * density).toInt() // 按钮距控制条右缘

        // ⏸/▶ 用矢量 View（不能用 TextView + Unicode：⏸ 会被渲染成橙色 emoji，setTextColor 无效）
        val btnPlayPause = PlayPauseIconView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6000000.toInt())
            }
            elevation = 4 * density
        }
        val btnShrink = makeControlLabel("－", btnSizePx)
        val btnEnlarge = makeControlLabel("＋", btnSizePx)
        val btnClose = makeControlLabel("✕", btnSizePx)
        // 左上角"回到播放页"按钮：与右侧控制条对称，iOS PiP 风格圆形黑底。
        // 点击 → backToFullscreen() 拉起播放页并关闭浮窗。图标用 Canvas 矢量
        // （避免 Unicode 字符被渲染成 emoji 的坑，同 ⏸ 的教训）。
        val btnBack = ExpandIconView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6000000.toInt())
            }
            elevation = 4 * density
        }
        val controlBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setGravity(android.view.Gravity.END) // 子 view 靠右排列（－ ＋ ✕ 在右侧）
            setBackgroundColor(0x00000000) // 透明背景，避免遮挡视频；按钮自带圆底
            visibility = android.view.View.GONE // 默认隐藏，单击切换
            // addView 顺序：缩小、放大、关闭；gravity=END → 从左到右：－ ＋ ✕
            addView(btnShrink)
            addView(btnEnlarge)
            addView(btnClose)
            setPadding(0, (8 * density).toInt(), btnRightPx, 0)
        }
        playPauseButton = btnPlayPause
        playPauseIcon = btnPlayPause
        shrinkButton = btnShrink
        enlargeButton = btnEnlarge
        closeButton = btnClose
        backButton = btnBack
        // 给前两个子 view 加右边距（间距 6dp，让三个按钮不要太近；✕ 是最右的，不加 marginEnd）
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
        // 左上角"回到播放页"按钮：单独挂在 container 上（与控制条不同侧，
        // 不参与 －/＋/✕ 的 hit-test），显示/隐藏与控制条同步。
        btnBack.visibility = android.view.View.GONE
        container.addView(
            btnBack,
            FrameLayout.LayoutParams(
                btnSizePx,
                btnSizePx,
                Gravity.TOP or Gravity.START
            ).apply {
                marginStart = btnRightPx
                topMargin = (8 * density).toInt()
            }
        )
        // 播放/暂停按钮单独居中放在浮窗中央（与控制条风格一致但稍大一档 48dp）。
        // 默认隐藏，与控制条（－/＋/✕）一起在单击视频时同步出现，4s 后同步隐藏。
        // 注意：level 0（半宽小窗）时浮窗高度较小，控制条（barPx 高）会与正中央重叠，
        // updatePlayPausePosition() 会把按钮下移到控制条之下，避免视觉叠压。
        btnPlayPause.visibility = android.view.View.GONE
        container.addView(
            btnPlayPause,
            FrameLayout.LayoutParams(
                btnSizeLargePx,
                btnSizeLargePx,
                Gravity.CENTER
            )
        )
        container.post { updatePlayPausePosition() }
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
                        showPlayPause(false)
                        backButton?.visibility = android.view.View.GONE
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
                    if (moved) {
                        // 拖动结束：水平吸附到最近的左/右缘（iOS PiP 行为），y 保持不变
                        val screenW = resources.displayMetrics.widthPixels
                        val margin = (12 * density).toInt()
                        val left = params.x
                        val rightX = screenW - container.width - margin
                        params.x = if (kotlin.math.abs(left - margin) < kotlin.math.abs(left - rightX)) {
                            margin // 吸附左缘
                        } else {
                            rightX // 吸附右缘（x 为距左缘距离，右缘 = 屏宽 - 窗宽 - margin）
                        }
                        windowManager.updateViewLayout(container, params)
                    }
                    if (!moved) {
                        if (singleTapPending != null) {
                            // 已有等待中的单击 → 本次是双击：取消回全屏，切换播放/暂停
                            singleTapPending?.let { mainHandler.removeCallbacks(it) }
                            singleTapPending = null
                            if (player.isPlaying) player.pause() else player.play()
                        } else {
                            // 第一次 tap：延迟 300ms 等待双击判定，期间再 tap 则切换播放/暂停
                            val r = Runnable {
                                singleTapPending = null
                                val barVisible = controlBar.visibility == android.view.View.VISIBLE
                                val hitPP = hitTestPlayPauseButton(touchDownX, touchDownY)
                                // ⏸/▶ 优先级最高：命中就切播放/暂停（⏸ 默认隐藏也能命中，按距离判断）
                                if (hitPP) {
                                    if (player.isPlaying) player.pause() else player.play()
                                    // 同时显示控制条（让用户看到 ⏸↔▶ 切换 + 可继续操作 －/＋/✕）
                                    if (!barVisible) {
                                        controlBar.visibility = android.view.View.VISIBLE
                                        showPlayPause(true)
                                    }
                                    hideControlBarDelayed()
                                } else if (barVisible) {
                                    // 控制条已显示 → 按 hit-test 决定触发哪个按钮
                                    val hitBack = hitTestButton(backButton, touchDownX, touchDownY)
                                    if (hitBack) {
                                        // 左上角"回到播放页"：拉起 MainActivity 播放页并关闭浮窗
                                        backToFullscreen()
                                    } else {
                                        val btnHit = hitTestControlButton(touchDownX, touchDownY)
                                        when (btnHit) {
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
                                    }
                                } else {
                                    // 控制条隐藏 → 显示（控制条与 ⏸、回播放页按钮一起出现）
                                    controlBar.visibility = android.view.View.VISIBLE
                                    showPlayPause(true)
                                    backButton?.visibility = android.view.View.VISIBLE
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

    /** 构造控制按钮：圆形深黑底 + 白字 + 阴影（iOS PiP 风）。仅标签，无 onClickListener。 */
    private fun makeControlLabel(label: String, sizePx: Int): android.view.View {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = label
            textSize = if (label == "✕") 18f else 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            // 圆形深黑底（不透明度 0xE6≈90%）：
            // 之前用 0x99(60%) 太透，视频亮色/暖色会透出来，
            // 中央 ⏸ 压在画面暖色区时会明显发橙，与顶部按钮观感不一致。
            // 提到 90% 后任意画面底色下都呈统一的深灰黑。
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6000000.toInt())
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
     * 在控制条内对 (x, y) 做 hit-test，返回按钮索引（1=缩小, 2=放大, 3=关闭）或 0（未命中）。
     * 按钮是固定 40dp 圆形，gravity=END 靠右排列。依次从右向左为 ✕、＋、－。
     * 按按钮圆心距离判定：圆心半径 distance < btnSize/2 视为命中。
     */
    private fun hitTestControlButton(rawX: Float, rawY: Float): Int {
        val root = rootView ?: return 0
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
            // 子顺序：－ ＋ ✕，gravity=END → 从左到右排列：－ ＋ ✕
            // 即子 idx 0(-/缩小) 在最左、idx 2(✕/关闭) 在最右
            // 取每个子 view 的中心点，看 (rawX, rawY) 离哪个中心最近且 distance < btnSize/2
            val n = child.childCount
            if (n == 0) return 0
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
            if (bestIdx < 0 || bestDist > halfBtn) return 0
            // 子 idx → 按钮编号：0=-/缩小(1), 1=+/放大(2), 2=✕/关闭(3)
            return when (bestIdx) {
                0 -> 1
                1 -> 2
                2 -> 3
                else -> 0
            }
        }
        return 0
    }

    /**
     * 中央 ⏸/▶ 按钮的 hit-test：返回 true 表示点击落在 ⏸ 上。
     * ⏸ 是 container 的单独子 view（FrameLayout Gravity.CENTER），按圆心距离判定。
     */
    private fun hitTestPlayPauseButton(rawX: Float, rawY: Float): Boolean {
        val btn = playPauseButton ?: return false
        val btnLoc = IntArray(2)
        btn.getLocationOnScreen(btnLoc)
        val cx = btnLoc[0] + btn.width / 2f
        val cy = btnLoc[1] + btn.height / 2f
        val d = kotlin.math.hypot((rawX - cx).toDouble(), (rawY - cy).toDouble()).toFloat()
        return d < btn.width / 2f
    }

    /**
     * 通用单按钮 hit-test（左上角"回播放页"等 container 直接子 view）：
     * 点击点距按钮圆心 < 半径即命中。未布局（width=0）时返回 false。
     */
    private fun hitTestButton(btn: View?, rawX: Float, rawY: Float): Boolean {
        if (btn == null || btn.width == 0) return false
        val loc = IntArray(2)
        btn.getLocationOnScreen(loc)
        val cx = loc[0] + btn.width / 2f
        val cy = loc[1] + btn.height / 2f
        val d = kotlin.math.hypot((rawX - cx).toDouble(), (rawY - cy).toDouble()).toFloat()
        return d < btn.width / 2f
    }

    private val hideControlBarRunnable = Runnable {
        rootView?.let { root ->
            // 找到控制条（LinearLayout）并隐藏，同时隐藏中央 ⏸/▶ 与左上角回播放页按钮
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is android.widget.LinearLayout) child.visibility = android.view.View.GONE
            }
            playPauseButton?.visibility = android.view.View.GONE
            backButton?.visibility = android.view.View.GONE
        }
    }

    /** ⏸/▶ 显示开关：true=与控制条一起显示，false=一起隐藏 */
    private fun showPlayPause(visible: Boolean) {
        playPauseButton?.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    /**
     * 重算中央 ⏸/▶ 的垂直位置，避免与控制条重叠：
     * - 理想位置 = 浮窗垂直居中
     * - 但若"居中位置 - 按钮半径" 会侵入控制条（barPx），则下移到控制条之下 8dp
     * 在浮窗创建、changeScale 后调用（浮窗高度随档位变化）。
     */
    private fun updatePlayPausePosition() {
        val btn = playPauseButton ?: return
        val root = rootView ?: return
        val density = resources.displayMetrics.density
        val barPx = (56 * density).toInt()
        val btnSize = btn.layoutParams.height
        val containerH = root.height
        if (containerH <= 0) return // 首帧布局前跳过，addView 后会有 layout 回调
        val idealTop = (containerH - btnSize) / 2
        val minTop = barPx + (8 * density).toInt()
        btn.translationY = (if (idealTop < minTop) minTop - idealTop else 0).toFloat()
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
        // 浮窗高度变了，重算中央 ⏸/▶ 位置避免与控制条重叠
        root.post { updatePlayPausePosition() }
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
        // 0.45：既能明显区分"不可用"，又不至于在亮色画面上完全隐形（0.3 太淡）
        shrinkButton?.alpha = if (scaleLevel == min) 0.45f else 1.0f
        enlargeButton?.alpha = if (scaleLevel == max) 0.45f else 1.0f
        // ⏸ / ✕ 永远可点
        playPauseButton?.alpha = 1.0f
        closeButton?.alpha = 1.0f
    }

    /**
     * 同步播放/暂停图标：播放中画「⏸ 双竖线」，暂停/未播放画「▶ 三角」。
     * 由 Player.Listener.onIsPlayingChanged 自动触发，也可在创建时手动调一次保证初始状态正确。
     * 注意：这里是**重绘矢量图形**，不是改 TextView 文字。
     */
    private fun updatePlayPauseIcon() {
        val icon = playPauseIcon ?: return
        runCatching {
            icon.isPlaying = playerManager.getPlayer().isPlaying
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
        playPauseIcon = null
        shrinkButton = null
        enlargeButton = null
        closeButton = null
        backButton = null
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

/**
 * 纯矢量绘制的播放/暂停图标（**不用 Unicode 字符**）。
 *
 * 原因：⏸ 是 U+23F8「PAUSE BUTTON」，属于 emoji 字符集。Android/Noto Color Emoji 会把它
 * 渲染成**彩色 emoji（橙红圆角方块 + 白色双竖线）**，此时 TextView 的 setTextColor 对它
 * 完全无效，图标看起来就是一坨橙色。▶(U+25B6) 虽是文本符号（白色），但为了两态观感一致，
 * 这里两个图形都用 Canvas 画成纯白，彻底摆脱字体/emoji 依赖。
 *
 * - isPlaying = true  → 画「暂停」两条圆角竖线
 * - isPlaying = false → 画「播放」圆角三角形
 */
private class PlayPauseIconView(context: Context) : View(context) {

    var isPlaying: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val u = minOf(width, height) * 0.34f // 图标主体基准尺寸

        if (isPlaying) {
            // 暂停：两条对称的圆角竖线
            val barW = u * 0.34f
            val barH = u * 1.05f
            val halfGap = u * 0.15f
            val r = barW / 2f
            canvas.drawRoundRect(
                cx - halfGap - barW, cy - barH / 2f,
                cx - halfGap, cy + barH / 2f,
                r, r, paint
            )
            canvas.drawRoundRect(
                cx + halfGap, cy - barH / 2f,
                cx + halfGap + barW, cy + barH / 2f,
                r, r, paint
            )
        } else {
            // 播放：白色三角形（顶点略偏右，使视觉重心居中）
            val half = u * 0.62f
            val left = cx - u * 0.30f
            val right = cx + u * 0.40f
            path.reset()
            path.moveTo(left, cy - half)
            path.lineTo(right, cy)
            path.lineTo(left, cy + half)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}

/**
 * 纯矢量绘制的"回到播放页"图标（四个向外的角箭头 = 展开到全屏）。
 * 同 PlayPauseIconView：不用 Unicode 字符（⤢/⛶ 等字符在不同字体下渲染不一致或变 emoji），
 * Canvas 画四个 L 形圆角折线，观感与系统全屏图标一致。
 */
private class ExpandIconView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val u = minOf(width, height) * 0.5f  // 图标主体外接半径基准
        val inset = u * 0.28f                // 距中心的起始偏移
        val arm = u * 0.42f                  // 每条边的臂长
        paint.strokeWidth = u * 0.16f

        val cx = width / 2f
        val cy = height / 2f

        // 四角：左上 / 右上 / 左下 / 右下，每角一条 L 形折线
        // 左上：从 (cx-inset-arm, cy-inset) 横向到角再竖直向下
        canvas.drawLine(cx - inset - arm, cy - inset, cx - inset, cy - inset, paint)
        canvas.drawLine(cx - inset, cy - inset, cx - inset, cy - inset + arm, paint)
        // 右上
        canvas.drawLine(cx + inset + arm, cy - inset, cx + inset, cy - inset, paint)
        canvas.drawLine(cx + inset, cy - inset, cx + inset, cy - inset + arm, paint)
        // 左下
        canvas.drawLine(cx - inset - arm, cy + inset, cx - inset, cy + inset, paint)
        canvas.drawLine(cx - inset, cy + inset, cx - inset, cy + inset - arm, paint)
        // 右下
        canvas.drawLine(cx + inset + arm, cy + inset, cx + inset, cy + inset, paint)
        canvas.drawLine(cx + inset, cy + inset, cx + inset, cy + inset - arm, paint)
    }
}

/** Hilt 入口：服务里取 PlayerManager / NavigationState 单例 */@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface FloatingPlayerEntryPoint {
    fun playerManager(): PlayerManager
    fun navigationState(): com.pureframe.player.ui.navigation.NavigationState
}
