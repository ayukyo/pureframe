package com.pureframe.player.data.preferences

/**
 * 用户偏好设置数据类
 * 
 * 存储用户的个性化设置，包括：
 * - 播放器设置（自动播放、循环播放、播放速度）
 * - 下载设置（并行下载数、下载路径）
 * - 界面设置（主题、全屏模式）
 */
data class UserPreferences(
    // 播放器设置
    val autoPlay: Boolean = false,
    val loopPlay: Boolean = false,
    val defaultPlaySpeed: Float = 1.0f,
    val rememberPlaySpeed: Boolean = true,
    val showSubtitle: Boolean = true,
    val decoderType: DecoderType = DecoderType.AUTO,

    // 下载设置
    val maxConcurrentDownloads: Int = 3,
    val downloadPath: String = "",
    val autoDownloadOnWifi: Boolean = true,
    val downloadQuality: DownloadQuality = DownloadQuality.HIGH,

    // 界面设置
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fullScreenMode: Boolean = false,
    val showThumbnail: Boolean = true,
    val sortBy: SortBy = SortBy.DATE_DESC,

    // 其他设置
    val keepScreenOn: Boolean = true,
    val brightnessGesture: Boolean = true,
    val volumeGesture: Boolean = true
)

/**
 * 下载质量选项
 */
enum class DownloadQuality {
    LOW,    // 480P
    MEDIUM, // 720P
    HIGH,   // 1080P
    ORIGINAL // 原画
}

/**
 * 主题模式
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * 排序方式
 */
enum class SortBy {
    NAME_ASC,      // 名称升序
    NAME_DESC,     // 名称降序
    DATE_ASC,      // 日期升序
    DATE_DESC,     // 日期降序
    SIZE_ASC,      // 大小升序
    SIZE_DESC,     // 大小降序
    DURATION_ASC,  // 时长升序
    DURATION_DESC  // 时长降序
}

/**
 * 解码器类型
 */
enum class DecoderType {
    HARDWARE,  // 硬解（硬件加速）
    SOFTWARE,  // 软解（软件解码）
    AUTO      // 自动（优先硬解，失败后软解）
}