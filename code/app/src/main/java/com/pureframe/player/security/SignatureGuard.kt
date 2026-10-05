package com.pureframe.player.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pureframe.player.BuildConfig
import timber.log.Timber

/**
 * 签名自校验：防"打包党"二次签名盗用。
 *
 * 原理：APK 的签名证书在安装后不可替换。盗用者反编译 → 改代码 → 用自己的
 * key 重签名上架，此时运行时读到的签名 hash 与原始发布签名不一致，直接
 * 拒绝运行（闪退前给出提示）。
 *
 * 原始签名的 SHA-256 在编译期由 CI 注入（BuildConfig.ORIGINAL_SIGNING_SHA256），
 * 不以明文字符串存在于代码中——混淆后即使被反编译，也只是一串常量，
 * 且盗用者无法伪造与自己签名匹配的 hash。
 *
 * 双通道判定（2026-10 Play Integrity 改造）：
 * 通道 1：ORIGINAL_SIGNING_SHA256 —— 直装/F-Droid/GitHub 渠道的发布签名。
 *         匹配 → 放行。
 * 通道 2：EXPECTED_PLAY_SIGNING_SHA256 —— Play App Signing 分发密钥指纹
 *         （仅 Play 渠道构建注入；其他构建为 "skip"）。通道 1 不匹配且通道 2
 *         已配置时，交给 IntegrityFallback 二轮判定（Play 指纹匹配 → 放行；
 *         都不匹配 → Integrity 探针观察，当前 fail-open）。
 *
 * 第三方分发兼容性（开源分发场景，如 F-Droid 生态用其他密钥重签）：
 * 两个 hash 都由构建环境变量 PF_SIGNING_SHA256 / PF_EXPECTED_PLAY_SHA256 /
 * local.properties 注入——任何不持有本项目发布 keystore 的构建（F-Droid 构建
 * 机、社区 fork、第三方重打包者）都拿不到这些值，BuildConfig 里只会是 "skip"，
 * 校验天然关闭。换言之：只有「本项目官方用发布密钥签出的包」才启用自校验。
 */
object SignatureGuard {

    /**
     * release 启动时调用。签名不匹配时直接 kill 进程。
     * debug 构建或 CI 未注入 hash 时跳过（不阻塞开发）。
     */
    fun checkAndKillIfTampered(context: Context) {
        // 直接引用编译期常量（非反射）：R8 会把 BuildConfig 字段值内联到调用点，
        // 反射方式在混淆后会导致 BuildConfig 类被删、getField 失败而静默跳过校验。
        val expected: String? = BuildConfig.ORIGINAL_SIGNING_SHA256

        // 未注入（debug 构建 / 配置缺失）→ 跳过
        if (expected.isNullOrBlank() || expected == "skip") return

        val actual = currentSignatureSha256(context) ?: return
        if (actual == expected) return

        // 通道 1 不匹配 → 若是 Play 渠道构建，走二轮判定（Play 指纹 / Integrity）
        if (IntegrityFallback.isPlayChannelBuild()) {
            if (IntegrityFallback.verifyAndAllow(context, actual)) return
            // verifyAndAllow 返回 false 才 kill（当前实现恒 true，见 fail-open 决策）
        }

        Timber.e("Signature mismatch! expected=%s actual=%s", expected, actual)
        // 篡改包：直接结束进程（不给逆向者调试空间）
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** 当前安装包签名证书的 SHA-256 十六进制（小写，无冒号） */
    fun currentSignatureSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo ?: return null
            signingInfo.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: return null
        val md = java.security.MessageDigest.getInstance("SHA-256")
        signatures.firstOrNull()?.toByteArray()?.let { bytes ->
            md.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()
}
