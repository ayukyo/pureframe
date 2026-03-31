package com.pureframe.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 纯帧 Typography
 * 
 * 基于 Material3 Typography，定制适合中文阅读的样式
 * - 略大的字号（中文阅读）
 * - 适中的行高（视觉舒适）
 * - 清晰的字重层次
 */

// ===== 字体家族 =====
// 使用系统默认字体（支持中文）
val DefaultFontFamily = FontFamily.Default

// ===== 主 Typography =====
val PureFrameTypography = Typography(
    // ===== Display - 大标题 =====
    displayLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    
    // ===== Headline - 标题 =====
    headlineLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    
    // ===== Title - 常用标题 =====
    titleLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    
    // ===== Body - 正文 =====
    bodyLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    
    // ===== Label - 标签/按钮文字 =====
    labelLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// ===== 特殊样式 =====

/**
 * 视频标题样式
 * 
 * 用于视频列表、播放器顶部的视频名称
 */
val VideoTitleStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

/**
 * 视频信息样式
 * 
 * 用于视频时长、大小、分辨率等次要信息
 */
val VideoInfoStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.3.sp
)

/**
 * 播放器时间样式
 * 
 * 用于播放器进度条下方的时间显示
 */
val PlayerTimeStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp
)

/**
 * 下载进度样式
 * 
 * 用于下载列表中的进度显示
 */
val DownloadProgressStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

/**
 * 设置项标题样式
 */
val SettingTitleStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp
)

/**
 * 设置项描述样式
 */
val SettingDescriptionStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp
)

/**
 * Dialog 标题样式
 */
val DialogTitleStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

/**
 * Dialog 内容样式
 */
val DialogContentStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.2.sp
)

/**
 * Dialog 按钮样式
 */
val DialogButtonStyle = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
)