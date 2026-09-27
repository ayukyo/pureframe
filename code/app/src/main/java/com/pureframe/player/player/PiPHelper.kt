package com.pureframe.player.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.media3.common.Player
import com.pureframe.player.R
import com.pureframe.player.i18n.LocaleManager

/**
 * 画中画（PiP）参数构建工具
 *
 * - 按视频真实宽高比自适应小窗形状（横屏视频不压黑边、竖屏视频不再被 16:9 强制变形）
 * - Android 12+ 允许用户把小窗放大到接近方形
 * - 小窗内提供 快退10s / 播放暂停 / 快进10s 三个系统按钮
 */
object PiPHelper {

    // 系统允许的宽高比范围（超出会被 IllegalArgumentException 拒绝）
    private const val MAX_RATIO = 2.39f
    private const val MIN_RATIO = 0.41841f

    /**
     * 小窗缩放档位（Android 12 以下系统不支持拖拽边缘缩放，
     * 用调整宽高比的方式实现多档大小切换）：
     * 0 = 正常（视频原始比例）；1 = 放大；2 = 更大。
     * 档位存这里（进程级），RemoteAction 触发时循环 +1。
     */
    @Volatile
    var scaleLevel: Int = 0
        private set

    /** 每档的放大系数：宽高比越大，小窗整体越大（系统按比例分配宽度） */
    private val scaleFactors = floatArrayOf(1.0f, 1.25f, 1.5f)

    val maxScaleLevel: Int
        get() = scaleFactors.size - 1

    /** 切到下一档，返回新档位（到顶后回到 0） */
    fun cycleScaleLevel(): Int {
        scaleLevel = (scaleLevel + 1) % scaleFactors.size
        return scaleLevel
    }

    /**
     * 根据播放器当前视频尺寸构建 PiP 参数
     */
    fun buildParams(context: Context, player: Player): PictureInPictureParams {
        val videoSize = player.videoSize
        val w = if (videoSize.width > 0) videoSize.width else 16
        val h = if (videoSize.height > 0) videoSize.height else 9

        // 计算真实比例并夹到系统允许区间
        val rawRatio = w.toFloat() / h.toFloat()
        // 缩放档位：放大比例（仅对横屏视频有意义；竖屏视频放大反而变小，
        // 因为 PiP 窗口高度被屏幕约束，比例变"高"窗口反而更窄）
        val scaled = if (rawRatio >= 1f) rawRatio * scaleFactors[scaleLevel] else rawRatio
        val clamped = scaled.coerceIn(MIN_RATIO, MAX_RATIO)
        // Rational 要求整数比：用 100 份近似。注意取整方向：
        // 分子向下取整、分母向上取整，保证近似比例不超过 clamped（clamp 才真正有效）
        val ratio = if (clamped >= 1f) {
            Rational(100, (100 / clamped + 0.999).toInt().coerceAtLeast(1))
        } else {
            Rational((100 * clamped).toInt().coerceAtLeast(1), 100)
        }

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            // 注意：不要调用 setSeamlessResizeEnabled —— 部分 MIUI ROM（Android 11）上
            // 该方法在运行时不存在（NoSuchMethodError），会导致画中画点击静默失败。
            // 去掉后仅影响非标准比例视频进小窗的缩放平滑度，功能不受影响。
            .setActions(buildActions(context, player.isPlaying))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                // 用户展开小窗时最多放大到方形（竖屏视频体验更好）
                .setExpandedAspectRatio(Rational(1, 1))
        }

        return builder.build()
    }

    /**
     * 小窗内系统控制按钮：播放暂停 / 调整大小（最右＝右下角）
     * 注意：MIUI（Android 11）小窗菜单最多渲染 3 个 RemoteAction，
     * 用户选择只保留 播放暂停 + 调整大小 两个按钮。
     */
    private fun buildActions(context: Context, isPlaying: Boolean): List<android.app.RemoteAction> {
        val playPause = android.app.RemoteAction(
            Icon.createWithResource(context, if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play),
            if (isPlaying) LocaleManager.getString(context, R.string.pip_pause)
            else LocaleManager.getString(context, R.string.pip_play),
            if (isPlaying) LocaleManager.getString(context, R.string.pip_pause)
            else LocaleManager.getString(context, R.string.pip_play),
            pendingIntent(context, PiPActionReceiver.ACTION_PLAY_PAUSE, 1)
        )
        val resize = android.app.RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_pip_resize),
            LocaleManager.getString(context, R.string.pip_resize),
            LocaleManager.getString(context, R.string.pip_resize),
            pendingIntent(context, PiPActionReceiver.ACTION_RESIZE, 2)
        )
        // 顺序：播放暂停 | 调整大小（右下角）
        return listOf(playPause, resize)
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PiPActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
