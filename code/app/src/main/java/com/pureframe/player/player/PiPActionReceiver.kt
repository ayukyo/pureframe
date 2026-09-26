package com.pureframe.player.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 画中画小窗内控制按钮的广播接收器
 *
 * 处理 PiPHelper 中 RemoteAction 发出的 播放暂停 / 快退 / 快进 指令
 */
@AndroidEntryPoint
class PiPActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var playerManager: PlayerManager

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.i("PureFramePip", "onReceive action=${intent.action} playing=${playerManager.isPlaying.value}")
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (playerManager.isPlaying.value) {
                    playerManager.pause()
                    android.util.Log.i("PureFramePip", "paused")
                } else {
                    playerManager.play()
                    android.util.Log.i("PureFramePip", "played")
                }
            }
            ACTION_REWIND -> playerManager.seekRelative(-10_000L)
            ACTION_FORWARD -> playerManager.seekRelative(10_000L)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.pureframe.player.pip.PLAY_PAUSE"
        const val ACTION_REWIND = "com.pureframe.player.pip.REWIND"
        const val ACTION_FORWARD = "com.pureframe.player.pip.FORWARD"
    }
}
