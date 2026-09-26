package com.pureframe.player.ui.screens.download

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pureframe.player.R
import com.pureframe.player.domain.model.DownloadError

/**
 * 把持久化的失败原因翻译成当前语言的文案。
 *
 * 数据库里存的是语言无关的错误码（见 [DownloadError]），所以在这里按当前语言解析 —— 
 * 用户切换语言后，历史任务的失败原因会跟着一起变。
 *
 * 兼容历史数据：老版本直接把翻译结果落库，那种内容不匹配错误码格式，
 * 此时原样返回，至少不会显示成空白。
 */
@Composable
fun downloadErrorText(stored: String?): String {
    val decoded = DownloadError.decode(stored)
    val code = decoded.code ?: return decoded.raw.orEmpty()
    val detail = decoded.detail
    return when (code) {
        DownloadError.CONNECT_TIMEOUT -> stringResource(R.string.error_connect_timeout)
        DownloadError.RESPONSE_TIMEOUT -> stringResource(R.string.error_response_timeout)
        DownloadError.UNKNOWN_HOST -> stringResource(R.string.error_unknown_host)
        DownloadError.CONNECT_FAILED -> stringResource(R.string.error_connect_failed)
        DownloadError.NO_SPACE -> stringResource(R.string.error_no_space)
        DownloadError.NO_PERMISSION -> stringResource(R.string.error_no_permission)
        DownloadError.HTTP_STATUS ->
            stringResource(R.string.error_http_status, detail.orEmpty())
        DownloadError.NETWORK_DETAIL ->
            stringResource(R.string.error_network_detail, detail.orEmpty())
        DownloadError.DOWNLOAD_FAILED_DETAIL ->
            stringResource(R.string.error_download_failed_detail, detail.orEmpty())
        DownloadError.DOWNLOAD_FAILED -> stringResource(R.string.error_download_failed)
        else -> stringResource(R.string.error_unknown)
    }
}
