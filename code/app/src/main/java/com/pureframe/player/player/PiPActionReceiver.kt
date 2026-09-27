package com.pureframe.player.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 画中画小窗内控制按钮的广播接收器
 *
 * 仅处理播放/暂停（主动小窗播放已改用悬浮窗方案 FloatingVideoService）。
 */
@AndroidEntryPoint
class PiPActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var playerManager: PlayerManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (playerManager.isPlaying.value) {
                    playerManager.pause()
                } else {
                    playerManager.play()
                }
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.pureframe.player.pip.PLAY_PAUSE"
    }
}
