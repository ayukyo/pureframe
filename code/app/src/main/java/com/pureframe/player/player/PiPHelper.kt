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
 * 仅用于系统 PiP 入窗（Home 键自动小窗）。宽高比按视频真实比例自适应。
 * 注意：系统 PiP 只在「进入那一刻」采样比例，入窗后改参数不会重算窗口尺寸；
 * 主动的"小窗播放"已改用悬浮窗方案（FloatingVideoService）。
 */
object PiPHelper {

    // 系统允许的宽高比范围（超出会被 IllegalArgumentException 拒绝）
    private const val MAX_RATIO = 2.39f
    private const val MIN_RATIO = 0.41841f

    /**
     * 根据播放器当前视频尺寸构建 PiP 参数
     */
    fun buildParams(context: Context, player: Player): PictureInPictureParams {
        val videoSize = player.videoSize
        val w = if (videoSize.width > 0) videoSize.width else 16
        val h = if (videoSize.height > 0) videoSize.height else 9

        // 计算真实比例并夹到系统允许区间
        val rawRatio = w.toFloat() / h.toFloat()
        val clamped = rawRatio.coerceIn(MIN_RATIO, MAX_RATIO)
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
            .setActions(buildActions(context, player.isPlaying))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                // 用户展开小窗时最多放大到方形（竖屏视频体验更好）
                .setExpandedAspectRatio(Rational(1, 1))
        }

        return builder.build()
    }

    /**
     * 小窗内系统控制按钮：播放暂停（仅保留必要项）
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
        return listOf(playPause)
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
