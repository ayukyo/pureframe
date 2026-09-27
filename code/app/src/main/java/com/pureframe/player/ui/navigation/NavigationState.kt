package com.pureframe.player.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导航状态管理器
 * 用于协调主页/下载页面与底部导航栏之间的滚动行为
 */
@Singleton
class NavigationState @Inject constructor() {
    // 是否应该滚动到主页顶部
    private val _scrollToHomeTop = MutableStateFlow(false)
    val scrollToHomeTop: StateFlow<Boolean> = _scrollToHomeTop.asStateFlow()

    // 是否应该滚动到下载页面顶部
    private val _scrollToDownloadTop = MutableStateFlow(false)
    val scrollToDownloadTop: StateFlow<Boolean> = _scrollToDownloadTop.asStateFlow()

    // 请求滚动到主页顶部
    fun requestScrollToHomeTop() {
        _scrollToHomeTop.value = true
    }

    // 请求滚动到下载页面顶部
    fun requestScrollToDownloadTop() {
        _scrollToDownloadTop.value = true
    }

    // 重置主页滚动标志
    fun resetHomeScrollFlag() {
        _scrollToHomeTop.value = false
    }

    // 重置下载页面滚动标志
    fun resetDownloadScrollFlag() {
        _scrollToDownloadTop.value = false
    }

    // ---- 画中画（PiP）模式状态 ----
    // MainActivity.onPictureInPictureModeChanged 覆写回调写入，
    // PlayerScreen 订阅以在小窗模式下隐藏控制栏与手势
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    // ---- 悬浮窗小窗播放状态 ----
    // FloatingVideoService 显示/销毁时写入。
    // PlayerScreen 订阅：悬浮窗显示时解除播放页 PlayerView 的 player 绑定
    // （SurfaceView 不能同时被两个窗口消费，否则其中一边黑屏），回全屏时重新绑定。
    private val _isFloatingMode = MutableStateFlow(false)
    val isFloatingMode: StateFlow<Boolean> = _isFloatingMode.asStateFlow()

    fun setFloatingMode(inFloating: Boolean) {
        _isFloatingMode.value = inFloating
    }
}