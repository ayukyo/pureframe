package com.pureframe.player.player

import android.app.Activity

/**
 * 当前前台 Activity 的弱引用持有者
 *
 * 用途：PlayerManager（单例）在播放状态变化时需要更新画中画小窗按钮，
 * 但它拿不到 Activity 引用。MainActivity 在 onStart/onStart 中注册自己，
 * 便于 PiP 参数更新找到当前 Activity；onStop 时清除引用避免泄漏。
 */
object PipCurrentActivityHolder {

    private var ref: java.lang.ref.WeakReference<Activity>? = null

    @JvmStatic
    var currentActivity: Activity?
        get() = ref?.get()
        set(value) {
            ref = value?.let { java.lang.ref.WeakReference(it) }
        }
}
