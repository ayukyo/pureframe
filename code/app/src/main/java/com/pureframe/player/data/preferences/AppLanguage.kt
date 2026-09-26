package com.pureframe.player.data.preferences

import java.util.Locale

/**
 * 应用语言
 *
 * - [SYSTEM] 跟随手机系统语言（未覆盖的语言回落到英文 resources）
 * - [CHINESE] 简体中文
 * - [ENGLISH] 英文
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    CHINESE("zh-CN"),
    ENGLISH("en");

    /**
     * 该语言对应的 [Locale]；[SYSTEM] 返回 null 表示交由系统决定。
     */
    val locale: Locale?
        get() = tag?.let { Locale.forLanguageTag(it) }

    companion object {
        /**
         * 从持久化的名字安全还原，遇到未知值回落到 [SYSTEM]。
         */
        fun fromName(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
