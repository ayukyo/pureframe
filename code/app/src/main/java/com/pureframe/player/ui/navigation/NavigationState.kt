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
}