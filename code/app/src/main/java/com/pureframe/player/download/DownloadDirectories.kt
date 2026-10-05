package com.pureframe.player.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 下载目录解析的统一入口
 *
 * 优先级：
 * 1. 用户在设置里指定的目录（前提确实可写）
 * 2. 公共媒体目录 /sdcard/Movies/PureFrame —— 需要存储权限；
 *    位于 MediaStore 收录范围，文件管理器和「本地」页都能看到
 * 3. 回退到应用专属外部目录（Android/data/<pkg>/files/...）——
 *    无需任何权限、一定可写，但用户在文件管理器里看不到
 *
 * [hasPublicStorage] 与 [isAppPrivate] 同时给设置页做「路径是否用户可见」的判断。
 */
object DownloadDirectories {

    /**
     * 是否具备写公共外部存储的能力（决定下载默认落点是否用户可见）。
     *
     * MEES（所有文件访问）已随 manifest 移除，此判断不再依赖它：
     * API 29- 写公共目录看 WRITE_EXTERNAL_STORAGE；API 30+ 分区存储下，
     * 应用对公共媒体目录（Movies 等）的媒体文件写入不需要全盘权限
     * （libtorrent native File API 写新建媒体文件同样豁免），
     * 其余路径靠 File.canWrite() 实测兜底——写不了会自然回退应用私有目录。
     */
    fun hasPublicStorage(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 分区存储：Movies/PureFrame 属媒体集合目录，创建/写入媒体文件无需额外权限。
            // 真值无本地判定 API，交给 resolve() 的 canWrite() 实测（失败回退私有目录）。
            true
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** 路径是否落在应用私有目录（用户在文件管理器里看不到） */
    fun isAppPrivate(path: String): Boolean {
        return path.contains("${File.separator}Android${File.separator}data${File.separator}")
    }

    /** 公共媒体目录下的默认保存位置（/sdcard/Movies/PureFrame） */
    fun publicDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "PureFrame"
    )

    /** 应用专属外部目录（无权限时的兜底） */
    fun appPrivateDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(base, "PureFrame/downloads")
    }

    /**
     * 解析实际生效的下载保存目录。
     *
     * @param configured 用户在设置里指定的目录（可为空）
     */
    fun resolve(context: Context, configured: String?): String {
        // 1) 用户配置的目录优先（前提确实可写）
        if (!configured.isNullOrBlank()) {
            val dir = File(configured)
            val usable = (dir.exists() && dir.isDirectory && dir.canWrite()) || dir.mkdirs()
            if (usable) return dir.absolutePath
        }

        // 2) 有存储权限 → 用公共媒体目录，用户能在文件管理器/「本地」页看到
        if (hasPublicStorage(context)) {
            val dir = publicDir()
            if (dir.exists() || dir.mkdirs()) return dir.absolutePath
        }

        // 3) 兜底：应用专属目录，无需权限、一定可写
        val dir = appPrivateDir(context)
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }
}
