package com.pureframe.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.pureframe.player.data.preferences.ThemeMode

/**
 * 纯帧主题
 * 
 * 纯黑沉浸主题，无广告，极简设计
 * - 纯黑背景，降低视觉干扰
 * - 低饱和度强调色，保持沉浸感
 * - 高对比文字，清晰易读
 */
private val DarkColorScheme = darkColorScheme(
    // 主要颜色
    primary = MaterialPrimary,
    onPrimary = MaterialOnPrimary,
    primaryContainer = SurfaceVariant,
    onPrimaryContainer = Color.White,
    
    // 次要颜色
    secondary = MaterialSecondary,
    onSecondary = MaterialOnSecondary,
    secondaryContainer = Surface,
    onSecondaryContainer = OnSurfaceVariant,
    
    // 第三颜色
    tertiary = MaterialTertiary,
    onTertiary = MaterialOnTertiary,
    tertiaryContainer = SurfaceHigh,
    onTertiaryContainer = OnSurfaceVariant,
    
    // 错误颜色
    error = MaterialError,
    onError = MaterialOnError,
    errorContainer = Error.copy(alpha = 0.2f),
    onErrorContainer = Color.White,
    
    // 背景颜色
    background = MaterialBackground,
    onBackground = MaterialOnBackground,

    // 表面颜色
    surface = MaterialSurface,
    onSurface = MaterialOnSurface,
    surfaceVariant = MaterialSurfaceVariant,
    onSurfaceVariant = MaterialOnSurfaceVariant,
    surfaceTint = Color.White.copy(alpha = 0.1f),
    
    // 边框颜色
    outline = MaterialOutline,
    outlineVariant = MaterialOutlineVariant,
    
    // 其他
    inversePrimary = Color.Black,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    
    // 滚动条颜色（Compose 自动处理）
    scrim = Color.Black.copy(alpha = 0.8f)
)

/**
 * 浅色主题方案
 */
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = Color(0xFF1A1A1A),

    secondary = Color(0xFF5C5C5C),
    onSecondary = Color.White,
    secondaryContainer = LightSurface,
    onSecondaryContainer = LightOnSurface,

    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightSurfaceHigh,
    onTertiaryContainer = LightOnSurface,

    error = LightError,
    onError = Color.White,
    errorContainer = LightError.copy(alpha = 0.12f),
    onErrorContainer = LightError,

    background = LightBackground,
    onBackground = LightOnBackground,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color.Black.copy(alpha = 0.08f),

    outline = LightOutline,
    outlineVariant = LightDivider,

    inversePrimary = Color.White,
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color.White,

    scrim = Color.Black.copy(alpha = 0.5f)
)

/**
 * 主题感知的扩展状态色
 *
 * Material3 ColorScheme 不含 success/warning/info 等扩展语义色，
 * 通过 CompositionLocal 提供随主题切换的状态色，
 * 替代过去直接引用暗色常量（浅色模式下对比度不足）的写法。
 */
data class ExtendedColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val downloadActive: Color,
    val downloadPaused: Color,
    val downloadCompleted: Color,
    val downloadError: Color,
    val downloadWaiting: Color
)

val DarkExtendedColors = ExtendedColors(
    success = Success,
    warning = Warning,
    info = Info,
    downloadActive = DownloadActive,
    downloadPaused = DownloadPaused,
    downloadCompleted = DownloadCompleted,
    downloadError = DownloadError,
    downloadWaiting = DownloadWaiting
)

val LightExtendedColors = ExtendedColors(
    success = LightSuccess,
    warning = LightWarning,
    info = LightInfo,
    downloadActive = LightDownloadActive,
    downloadPaused = LightDownloadPaused,
    downloadCompleted = LightDownloadCompleted,
    downloadError = LightDownloadError,
    downloadWaiting = LightDownloadWaiting
)

val PlayerExtendedColors = DarkExtendedColors  // 播放器固定深色

private val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

/**
 * 当前 App 主题是否为深色。
 *
 * 播放页使用 PlayerTheme（固定深色）以保证视频画面上的控制栏可读，
 * 但对话框属于「应用层 UI」，应当跟随 App 的主题偏好而不是播放器的固定深色。
 * 通过该 CompositionLocal 把外层 PureFrameTheme 的深浅标志透传进播放页内部，
 * 使对话框在浅色模式下呈现浅色、深色模式下呈现深色。
 */
val LocalAppDarkTheme = staticCompositionLocalOf { true }

/**
 * 播放页对话框配色（跟随 App 主题，而非播放器固定深色）
 */
data class AppDialogColors(
    val container: Color,          // 对话框底色
    val onContainer: Color,        // 主文字
    val onContainerMuted: Color,   // 次要文字
    val selectedContainer: Color,  // 选中项背景
    val primaryAction: Color,      // 主按钮底色
    val onPrimaryAction: Color,    // 主按钮文字
    val radioSelected: Color,      // 单选选中色
    val radioUnselected: Color     // 单选未选中色
)

@Composable
fun appDialogColors(): AppDialogColors {
    return if (LocalAppDarkTheme.current) {
        AppDialogColors(
            container = Color(0xFF1E1E1E),
            onContainer = Color.White,
            onContainerMuted = Color.White.copy(alpha = 0.6f),
            selectedContainer = Color.White.copy(alpha = 0.15f),
            primaryAction = Color.White,
            onPrimaryAction = Color.Black,
            radioSelected = Color.White,
            radioUnselected = Color.White.copy(alpha = 0.5f)
        )
    } else {
        AppDialogColors(
            container = Color.White,
            onContainer = Color(0xFF1A1A1A),
            onContainerMuted = Color(0xFF5C5C5C),
            selectedContainer = Color(0xFF1A1A1A).copy(alpha = 0.07f),
            primaryAction = Color(0xFF1A1A1A),
            onPrimaryAction = Color.White,
            radioSelected = Color(0xFF1A1A1A),
            radioUnselected = Color(0xFF1A1A1A).copy(alpha = 0.4f)
        )
    }
}

/**
 * 获取当前主题的扩展状态色
 */
object AppTheme {
    val extendedColors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

/**
 * MaterialTheme.colorScheme 的扩展属性：
 * - surfaceHigh：高亮表面（深色主题下比 surfaceVariant 更亮一级）
 * - onSurfaceMuted：辅助文字（弱于 onSurfaceVariant）
 */
val ColorScheme.surfaceHigh: Color
    @Composable get() = if (this == DarkColorScheme) SurfaceHigh else LightSurfaceHigh

val ColorScheme.onSurfaceMuted: Color
    @Composable get() = if (this == DarkColorScheme) OnSurfaceMuted else LightOnSurfaceMuted

/**
 * 纯帧主题包装器
 *
 * 根据 ThemeMode 偏好切换浅色/深色/跟随系统。
 */
@Composable
fun PureFrameTheme(
    // 传入 null 表示跟随系统深浅色设置
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 动态颜色禁用（保持自定义颜色）
    dynamicColor: Boolean = false,  // 固定禁用
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
        LocalAppDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PureFrameTypography,
            content = content
        )
    }
}

/**
 * 播放器专用主题
 * 
 * 用于播放器页面，使用更暗的背景和更高的对比度
 */
@Composable
fun PlayerTheme(
    content: @Composable () -> Unit
) {
    val playerColorScheme = darkColorScheme(
        primary = PlayerProgress,
        onPrimary = Color.Black,
        background = PlayerBackground,
        onBackground = Color.White,
        surface = PlayerControlBackground,
        onSurface = Color.White,
        surfaceVariant = OverlayDark,
        onSurfaceVariant = OnSurfaceVariant
    )

    CompositionLocalProvider(LocalExtendedColors provides PlayerExtendedColors) {
        MaterialTheme(
            colorScheme = playerColorScheme,
            typography = PureFrameTypography,
            content = content
        )
    }
}