package com.pureframe.player.security

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityServiceException
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.model.IntegrityErrorCode
import com.pureframe.player.BuildConfig
import timber.log.Timber

/**
 * Play Integrity 探针（Play 渠道专用兜底）。
 *
 * 背景：Google Play App Signing 会用 Google 的分发密钥重签 APK，官方 Play 包的
 * 运行时签名 hash ≠ 我们注入的 ORIGINAL_SIGNING_SHA256。SignatureGuard 遇到
 * 这种"hash 不匹配"时，改由本探针做第二轮判定。
 *
 * 设计决策（fail-open，仅记日志不 kill）：
 * 1. Play Integrity 的判定 token 只能在服务端解密（Google 推荐 Google 服务器
 *    解密；本地解密需托管响应加密密钥）。无后端的客户端无法读到
 *    appRecognitionVerdict，token 请求成功本身只能证明"设备有 GMS 且环境
 *    正常"，不能证明 PLAY_RECOGNIZED。
 * 2. 本项目的威胁模型是"打包党盗用重签"，而 Play 渠道本身由 Google 管控
 *    证书——篡改包根本无法上架 Play。客户端侧 Integrity 对该威胁模型
 *    增益有限；无 GMS 设备上的重签包来自 F-Droid/第三方构建（构建时拿不到
 *    hash，校验本来就是 skip），不在本探针的判定路径上。
 * 3. 因此本探针当前策略：请求结果仅写日志（release 无 Timber plant，
 *    实际无输出，但保留结构便于未来接后端收紧），一律放行。
 *    上架 Play 后若观察到滥用，再把"token 请求失败 + hash 不匹配"
 *    组合收紧为 kill。
 *
 * 触发条件：hash 不匹配 且 构建期注入了 PLAY 渠道指纹（PF_EXPECTED_PLAY_SHA256）。
 * F-Droid/直装构建不注入该值 → 探针永不启动，零额外依赖开销。
 */
object IntegrityFallback {

    /** Play 渠道分发密钥指纹（构建期注入），空 = 非 Play 渠道构建，探针禁用 */
    val expectedPlaySha256: String? = BuildConfig.EXPECTED_PLAY_SIGNING_SHA256
        .takeIf { it.isNotBlank() && it != "skip" }

    fun isPlayChannelBuild(): Boolean = expectedPlaySha256 != null

    /**
     * 第二轮判定入口（SignatureGuard 在 hash 不匹配且本函数可用时调用）。
     * 返回 true = 放行。当前实现恒放行，Integrity 请求结果仅用于日志观察。
     */
    fun verifyAndAllow(context: Context, actualSha256: String): Boolean {
        val expected = expectedPlaySha256 ?: return true

        // hash 与 Play 渠道指纹一致 → Google 重签的官方包，直接放行
        if (actualSha256 == expected) {
            Timber.i("Signature: Play App Signing match (actual=%s)", actualSha256)
            return true
        }

        // 与两个官方指纹都不匹配：发 Integrity 探针（观察用，不阻断）
        Timber.e("Signature mismatch vs both official hashes. actual=%s", actualSha256)
        requestIntegrityProbe(context)
        // fail-open：见类注释。篡改包在 Play 上不了架；侧载场景 kill 只会误伤
        return true
    }

    /**
     * 发起 classic Integrity 请求。结果仅记日志。
     * classic 每分钟限 5 个 token、每天 1 万次，只在启动时 hash 不匹配才触发，
     * 远低于配额。
     */
    private fun requestIntegrityProbe(context: Context) {
        runCatching {
            val nonce = java.security.MessageDigest.getInstance("SHA-256")
                .digest("pf-sig-${System.currentTimeMillis()}".toByteArray())
                .joinToString("") { "%02x".format(it) }
                // classic 要求 URL-safe base64 ≥16 字符；hex 串天然满足
                .let { it + it } // 拉长到 64 字符冗余

            val manager = IntegrityManagerFactory.create(context)
            manager.requestIntegrityToken(
                IntegrityTokenRequest.builder().setNonce(nonce).build()
            )
                .addOnSuccessListener { response ->
                    Timber.i("Integrity probe: token acquired (len=%d, verify server-side)", response.token().length)
                    // 无后端，无法本地解密判定；token 只落日志长度作为环境健康信号
                }
                .addOnFailureListener { e ->
                    // 无 GMS 设备典型：API_NOT_AVAILABLE / PLAY_SERVICES_NOT_FOUND
                    val code = (e as? IntegrityServiceException)?.errorCode
                    Timber.w("Integrity probe failed: code=%s %s", code?.let(::describeError), e.message)
                }
        }.onFailure { Timber.w("Integrity probe crashed: %s", it.message) }
    }

    /** 供测试/诊断用：Error 常量转可读名（避免 R8 裁剪隐性依赖） */
    private fun describeError(code: Int): String = when (code) {
        IntegrityErrorCode.API_NOT_AVAILABLE -> "API_NOT_AVAILABLE"
        IntegrityErrorCode.PLAY_SERVICES_NOT_FOUND -> "PLAY_SERVICES_NOT_FOUND"
        IntegrityErrorCode.PLAY_STORE_NOT_FOUND -> "PLAY_STORE_NOT_FOUND"
        IntegrityErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
        IntegrityErrorCode.TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS"
        else -> "code=$code"
    }
}
