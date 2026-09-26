package com.pureframe.player.domain.model

/**
 * 下载失败原因 —— 语言无关的稳定错误码
 *
 * 为什么不能直接把「翻译好的文案」落库：
 *  失败原因会持久化到 Room（`download_tasks.errorMessage`）。若写入的是翻译结果，
 *  用户之后切换语言时，历史记录里仍是当时那门语言 —— 表现就是「英文界面里
 *  一条旧任务冒出中文的失败原因」。
 *  所以这里只落「错误码 [+ 少量原始细节]」，真正的文案留到渲染时按当前语言解析。
 *
 * 存储格式：`code`，或 `code\u0001detail`。
 *  用不可见控制符 `\u0001` 作分隔，避免和 URL、异常信息中可能出现的 `:` `|` `-` 冲突。
 */
object DownloadError {

    /** SocketTimeoutException：连接阶段超时 */
    const val CONNECT_TIMEOUT = "connect_timeout"

    /** SocketTimeoutException：读响应/数据超时 */
    const val RESPONSE_TIMEOUT = "response_timeout"

    /** UnknownHostException：域名解析失败 */
    const val UNKNOWN_HOST = "unknown_host"

    /** ConnectException：连接被拒绝 */
    const val CONNECT_FAILED = "connect_failed"

    /** 存储空间不足 */
    const val NO_SPACE = "no_space"

    /** 无写权限 */
    const val NO_PERMISSION = "no_permission"

    /** HTTP 状态码非 2xx，detail 为状态码 */
    const val HTTP_STATUS = "http_status"

    /** 其他 IO 异常，detail 为原始信息 */
    const val NETWORK_DETAIL = "network_detail"

    /** 非 IO 异常，detail 为原始信息 */
    const val DOWNLOAD_FAILED_DETAIL = "download_failed_detail"

    /** 下载失败（无更多信息） */
    const val DOWNLOAD_FAILED = "download_failed"

    /** 未知错误 */
    const val UNKNOWN = "unknown"

    /** 码与细节之间的分隔符（不可见控制符） */
    private const val SEP = '\u0001'

    /** 全部已知错误码，用于区分「新格式码」与「旧版本直存的已翻译文案」 */
    private val KNOWN_CODES = setOf(
        CONNECT_TIMEOUT, RESPONSE_TIMEOUT, UNKNOWN_HOST, CONNECT_FAILED,
        NO_SPACE, NO_PERMISSION, HTTP_STATUS, NETWORK_DETAIL,
        DOWNLOAD_FAILED_DETAIL, DOWNLOAD_FAILED, UNKNOWN
    )

    /**
     * 编码为可落库的字符串。
     *
     * @param code 错误码（本对象的常量）
     * @param detail 可选细节（如 HTTP 状态码、原始异常信息），为空则不附带
     */
    fun encode(code: String, detail: String? = null): String =
        if (detail.isNullOrBlank()) code else "$code$SEP$detail"

    /**
     * 解码 [encode] 的结果。
     *
     * @return code 为 null 表示这串内容不是本格式（多半是旧版本直存的已翻译文案），
     *         调用方应原样展示以兼容历史数据；此时 [raw] 即原始内容。
     */
    fun decode(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded(null, null, raw)
        val idx = raw.indexOf(SEP)
        val head = if (idx >= 0) raw.substring(0, idx) else raw
        return if (head in KNOWN_CODES) {
            Decoded(head, if (idx >= 0) raw.substring(idx + 1) else null, raw)
        } else {
            Decoded(null, null, raw)
        }
    }

    /** [decode] 的结果 */
    data class Decoded(
        /** 识别出的错误码；null 表示非本格式（历史数据） */
        val code: String?,
        /** 随码附带的细节，可能为 null */
        val detail: String?,
        /** 原始字符串，供历史数据兜底展示 */
        val raw: String?
    )

    /** 从 "server returned HTTP 404 ..." 中提取状态码 */
    private val HTTP_STATUS_REGEX = Regex("""server returned HTTP (\d{3})""")

    /**
     * 把下载异常归类成语言无关的错误码（可能附带原始细节）。
     *
     * 原始异常 message 对用户无意义（如 "failed to connect to /1.2.3.4 (port 80)"），
     * 所以只有无法归类时才把精简后的 message 当作 detail 附带。
     */
    fun fromException(e: Throwable): String {
        val msg = e.message
        return when (e) {
            is java.net.SocketTimeoutException ->
                if (msg?.contains("connect", ignoreCase = true) == true) CONNECT_TIMEOUT
                else RESPONSE_TIMEOUT
            is java.net.UnknownHostException -> UNKNOWN_HOST
            is java.net.ConnectException -> CONNECT_FAILED
            is java.io.IOException -> when {
                msg == null -> DOWNLOAD_FAILED
                msg.contains("ENOSPC", ignoreCase = true) -> NO_SPACE
                msg.contains("EACCES", ignoreCase = true) ||
                    msg.contains("Permission denied", ignoreCase = true) -> NO_PERMISSION
                msg.contains("No space", ignoreCase = true) -> NO_SPACE
                else -> {
                    // "server returned HTTP 404 (cannot download this URL)" → 只保留状态码
                    val http = HTTP_STATUS_REGEX.find(msg)
                    if (http != null) encode(HTTP_STATUS, http.groupValues[1])
                    else encode(NETWORK_DETAIL, msg)
                }
            }
            else -> if (msg == null) DOWNLOAD_FAILED
            else encode(DOWNLOAD_FAILED_DETAIL, msg.take(120))
        }
    }
}
