package com.pureframe.player.i18n

import android.content.Context
import android.content.res.Configuration
import com.pureframe.player.data.preferences.AppLanguage
import java.util.Locale

/**
 * 语言运行时（进程级单例）
 *
 * 作用：让 [Context] 带上用户选择的语言，从而正确解析 strings 资源。
 *
 * 为什么需要它：
 *  本项目是纯 Compose 架构（没有依赖 appcompat），若使用官方的
 *  `AppCompatDelegate.setApplicationLocales()` 就必须把 MainActivity 改成
 *  AppCompatActivity 并迁移主题；且在低版本上切语言依赖 Activity 重建，
 *  播放中的画面会被打断。因此这里用「包装 Context」的方式自行实现：
 *
 *  - Compose 层：在 [com.pureframe.player.ui.MainActivity] 里用 [wrapContext]
 *    包一层 Context 并通过 CompositionLocal 下发给 `stringResource()`。
 *  - 非 Compose 层（Service 通知、PiP 小窗 RemoteAction 标题）：这些地方没有
 *    Compose 作用域，直接调用 [localizedContext] 取一个带语言的 Context。
 *
 * 切换语言时无需重建 Activity —— 只需要让 Compose 重新组合即可（读取
 * [AppLanguage] 的 StateFlow 会自然触发）。
 */
object LocaleManager {

    @Volatile
    private var current: AppLanguage = AppLanguage.SYSTEM

    /**
     * 当前生效的语言。由 MainActivity 观察偏好并同步过来，
     * 保证非 Compose 代码拿到的是最新值。
     */
    val currentLanguage: AppLanguage
        get() = current

    /** 由 UI 层在语言偏好变化时调用。 */
    fun update(language: AppLanguage) {
        current = language
    }

    /** 当前语言对应的 Locale；跟随系统时返回系统 Locale。 */
    private fun targetLocale(base: Context): Locale {
        val tag = current.tag
        return if (tag != null) {
            Locale.forLanguageTag(tag)
        } else {
            // 跟随系统：取系统配置里的首选语言
            base.resources.configuration.locales[0] ?: Locale.getDefault()
        }
    }

    /**
     * 为 [base] 包一层带目标语言的 Context。
     *
     * 传入的 [base] 通常是带主题的 Activity Context，因此被包装后的 Context
     * 依然是 Activity Context（主题、窗口等特性不丢失）。
     *
     * 注意：[AppLanguage.SYSTEM] 时**原样返回** base，不做覆盖。这样系统层面的
     * 语言决定（含 Android 13+ 的「应用语言」per-app locale）能自然生效。
     */
    fun wrapContext(base: Context): Context {
        if (current == AppLanguage.SYSTEM) return base

        val locale = targetLocale(base)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /**
     * 取一个已应用当前语言的 Context，用于没有 Compose 作用域的场景
     * （Service 通知、RemoteAction 文案等）。
     */
    fun localizedContext(base: Context): Context = wrapContext(base)

    /**
     * 便捷方法：按当前语言解析字符串。
     */
    fun getString(base: Context, resId: Int): String =
        wrapContext(base).getString(resId)

    /**
     * 便捷方法：按当前语言解析带格式参数的字符串。
     */
    fun getString(base: Context, resId: Int, vararg args: Any): String =
        wrapContext(base).getString(resId, *args)
}
