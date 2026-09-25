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
     * 根据播放器当前视频尺寸构建 PiP 参数
     */
    fun buildParams(context: Context, player: Player): PictureInPictureParams {
        val videoSize = player.videoSize
        val w = if (videoSize.width > 0) videoSize.width else 16
        val h = if (videoSize.height > 0) videoSize.height else 9

        // 计算真实比例并夹到系统允许区间
        val rawRatio = w.toFloat() / h.toFloat()
        val clamped = rawRatio.coerceIn(MIN_RATIO, MAX_RATIO)
        // Rational 要求整数比：用 100 份近似即可（Rational 内部会约分）
        val ratio = if (clamped >= 1f) {
            Rational(100, (100 / clamped).toInt().coerceAtLeast(1))
        } else {
            Rational((100 * clamped).toInt().coerceAtLeast(1), 100)
        }

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            // 关闭无缝缩放，避免非标准比例视频进小窗时被拉伸闪烁
            .setSeamlessResizeEnabled(false)
            .setActions(buildActions(context, player.isPlaying))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                // 用户展开小窗时最多放大到方形（竖屏视频体验更好）
                .setExpandedAspectRatio(Rational(1, 1))
        }

        return builder.build()
    }

    /**
     * 小窗内系统控制按钮：快退 10s / 播放暂停 / 快进 10s
     */
    private fun buildActions(context: Context, isPlaying: Boolean): List<android.app.RemoteAction> {
        val playPause = android.app.RemoteAction(
            Icon.createWithResource(context, if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play),
            if (isPlaying) "暂停" else "播放",
            if (isPlaying) "暂停" else "播放",
            pendingIntent(context, PiPActionReceiver.ACTION_PLAY_PAUSE, 1)
        )
        val rewind = android.app.RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_pip_rewind),
            "快退 10 秒",
            "快退 10 秒",
            pendingIntent(context, PiPActionReceiver.ACTION_REWIND, 2)
        )
        val forward = android.app.RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_pip_forward),
            "快进 10 秒",
            "快进 10 秒",
            pendingIntent(context, PiPActionReceiver.ACTION_FORWARD, 3)
        )
        return listOf(rewind, playPause, forward)
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
