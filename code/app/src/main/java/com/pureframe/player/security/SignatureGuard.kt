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
 * 注意：release 构建时由 CI/gradle 注入真实签名 hash；debug 构建跳过校验。
 *
 * 第三方分发兼容性（开源分发场景，如 F-Droid 生态用其他密钥重签）：
 * hash 由构建环境变量 PF_SIGNING_SHA256 / local.properties 注入——任何不持有
 * 本项目发布 keystore 的构建（F-Droid 构建机、社区 fork、第三方重打包者）
 * 都拿不到这个值，BuildConfig 里只会是 "skip"，校验天然关闭。
 * 换言之：只有「本项目官方用发布密钥签出的包」才启用自校验，无需构建期开关。
 * 唯一需要额外处理的场景是未来的 Google Play App Signing（Google 用自家密钥
 * 重签产物而构建时注入了我们的 hash）——届时叠加 Play Integrity 方案。
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
        if (actual != expected) {
            Timber.e("Signature mismatch! expected=%s actual=%s", expected, actual)
            // 篡改包：直接结束进程（不给逆向者调试空间）
            android.os.Process.killProcess(android.os.Process.myPid())
        }
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
