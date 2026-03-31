package com.pureframe.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 纯帧 - 颜色方案
 * 
 * 纯黑沉浸主题，无广告，极简设计
 */

// ===== 主色调 =====
val Background = Color(0xFF000000)        // 纯黑背景
val Surface = Color(0xFF121212)           // 深灰表面
val SurfaceVariant = Color(0xFF1E1E1E)    // 卡片背景
val SurfaceHigh = Color(0xFF2A2A2A)       // 高亮表面

// ===== 文字颜色 =====
val OnBackground = Color(0xFFFFFFFF)      // 白色主文字
val OnSurface = Color(0xFFFFFFFF)         // 白色
val OnSurfaceVariant = Color(0xFFAAAAAA)  // 浅灰次要文字
val OnSurfaceMuted = Color(0xFF666666)    // 深灰辅助文字

// ===== 强调色 =====
val Primary = Color(0xFFAAAAAA)           // 低饱和灰（主要交互）
val PrimaryVariant = Color(0xFF888888)    // 中灰
val Secondary = Color(0xFF666666)         // 深灰（次要交互）
val Accent = Color(0xFF888888)            // 中灰强调色

// ===== 状态色 =====
val Success = Color(0xFF4CAF50)           // 成功状态
val Warning = Color(0xFFFF9800)           // 警告状态
val Error = Color(0xFFF44336)             // 错误状态
val Info = Color(0xFF2196F3)              // 信息状态

// ===== 播放器专用色 =====
val PlayerBackground = Color(0xFF000000)  // 播放器背景（纯黑）
val PlayerControlBackground = Color(0xFF000000).copy(alpha = 0.5f)  // 控制栏半透明背景
val PlayerProgress = Color(0xFFFFFFFF)    // 进度条白色
val PlayerProgressTrack = Color(0xFF333333)  // 进度条轨道
val PlayerCached = Color(0xFF666666)      // 已缓存区域灰色
val PlayerBuffered = Color(0xFFFFFFFF).copy(alpha = 0.4f)  // 缓存进度
val PlayerSeekAvailable = Color(0xFF4CAF50).copy(alpha = 0.3f)  // 可跳转区域
val PlayerSeekUnavailable = Color(0xFFF44336).copy(alpha = 0.1f)  // 不可跳转区域

// ===== 下载状态色 =====
val DownloadActive = Color(0xFF4CAF50)    // 下载中（绿色）
val DownloadPaused = Color(0xFFFF9800)    // 已暂停（橙色）
val DownloadCompleted = Color(0xFF2196F3) // 已完成（蓝色）
val DownloadError = Color(0xFFF44336)     // 错误（红色）
val DownloadWaiting = Color(0xFF666666)   // 等待中（灰色）

// ===== 分隔线和边框 =====
val Divider = Color(0xFF333333)           // 分隔线
val Border = Color(0xFF444444)            // 边框
val BorderLight = Color(0xFF555555)       // 浅边框

// ===== 交互状态色 =====
val Ripple = Color(0xFFFFFFFF).copy(alpha = 0.1f)  // 水波纹效果
val Hover = Color(0xFFFFFFFF).copy(alpha = 0.05f)  // hover 状态
val Focus = Color(0xFFFFFFFF).copy(alpha = 0.2f)   // focus 状态
val Disabled = Color(0xFF666666)          // 禁用状态

// ===== 特殊效果色 =====
val GradientTop = Color(0xFF000000).copy(alpha = 0.7f)  // 渐变顶部
val GradientBottom = Color(0xFF000000).copy(alpha = 0.7f)  // 渐变底部
val OverlayDark = Color(0xFF000000).copy(alpha = 0.8f)  // 深色覆盖层
val OverlayMedium = Color(0xFF000000).copy(alpha = 0.6f)  // 中等覆盖层
val OverlayLight = Color(0xFF000000).copy(alpha = 0.4f)  // 浅色覆盖层

// ===== Material3 适配 =====
// 用于 Material3 组件的默认颜色映射
val MaterialPrimary = Color.White
val MaterialOnPrimary = Color.Black
val MaterialSecondary = Color.White.copy(alpha = 0.7f)
val MaterialOnSecondary = Color.Black
val MaterialTertiary = Color.White.copy(alpha = 0.5f)
val MaterialOnTertiary = Color.Black
val MaterialError = Error
val MaterialOnError = Color.White
val MaterialBackground = Background
val MaterialOnBackground = Color.White
val MaterialSurface = Surface
val MaterialOnSurface = Color.White
val MaterialSurfaceVariant = SurfaceVariant
val MaterialOnSurfaceVariant = OnSurfaceVariant
val MaterialOutline = Border
val MaterialOutlineVariant = Divider