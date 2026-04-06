package com.pureframe.player.download

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地视频扫描器
 *
 * 使用 MediaStore API 扫描设备上的本地视频文件
 * 支持实时监听媒体库变化
 */
@Singleton
class LocalVideoScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    // 媒体库变化观察器
    private var mediaStoreObserver: ContentObserver? = null

    /**
     * 监听媒体库变化
     * 当有视频添加/删除时自动触发
     */
    fun observeMediaStoreChanges(): Flow<Boolean> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Timber.d("LocalVideoScanner: MediaStore 发生变化")
                trySend(true)
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        mediaStoreObserver = observer

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
            mediaStoreObserver = null
        }
    }

    /**
     * 扫描本地视频
     *
     * @param forceRefresh 是否强制刷新（跳过缓存）
     * @return 扫描到的视频路径列表
     */
    suspend fun scanLocalVideos(forceRefresh: Boolean = false): List<LocalVideoInfo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideoInfo>()

        try {
            // 扫描外部存储上的视频
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )

            val selection: String? = null
            val selectionArgs: Array<String>? = null
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            val cursor: Cursor? = contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: "未知视频"
                    val path = it.getString(pathColumn) ?: continue
                    val size = it.getLong(sizeColumn)
                    val duration = it.getLong(durationColumn)
                    val dateAdded = it.getLong(dateAddedColumn)
                    val mimeType = it.getString(mimeTypeColumn) ?: ""
                    val width = it.getInt(widthColumn)
                    val height = it.getInt(heightColumn)

                    // 检查文件是否存在
                    val file = File(path)
                    if (!file.exists()) {
                        continue
                    }

                    // 过滤不支持的格式
                    if (!isVideoFormatSupported(path)) {
                        continue
                    }

                    // 获取文件 URI
                    val uri = Uri.withAppendedPath(collection, id.toString())

                    videos.add(LocalVideoInfo(
                        id = id,
                        name = name,
                        path = path,
                        uri = uri.toString(),
                        size = size,
                        duration = duration,
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        width = width,
                        height = height
                    ))
                }
            }

            Timber.i("LocalVideoScanner: 扫描到 ${videos.size} 个本地视频")

        } catch (e: Exception) {
            Timber.e(e, "LocalVideoScanner: 扫描失败")
        }

        videos
    }

    /**
     * 检查视频格式是否支持
     */
    private fun isVideoFormatSupported(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_FORMATS
    }

    /**
     * 根据目录扫描视频
     *
     * @param directory 要扫描的目录
     * @return 该目录下的视频列表
     */
    suspend fun scanDirectory(directory: File): List<LocalVideoInfo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideoInfo>()

        if (!directory.exists() || !directory.isDirectory) {
            return@withContext videos
        }

        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && isVideoFormatSupported(file.name)) {
                    videos.add(file.toLocalVideoInfo())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "LocalVideoScanner: 扫描目录失败 - ${directory.absolutePath}")
        }

        videos
    }

    /**
     * File 转换为 LocalVideoInfo
     */
    private fun File.toLocalVideoInfo(): LocalVideoInfo {
        return LocalVideoInfo(
            id = 0,
            name = this.name,
            path = this.absolutePath,
            uri = Uri.fromFile(this).toString(),
            size = this.length(),
            duration = 0, // 本地文件需要通过 MediaMetadataRetriever 获取
            dateAdded = this.lastModified() / 1000,
            mimeType = getMimeType(this.name),
            width = 0,
            height = 0
        )
    }

    /**
     * 根据扩展名获取 MIME 类型
     */
    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "flv" -> "video/x-flv"
            "ts" -> "video/mp2ts"
            "wmv" -> "video/x-ms-wmv"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "m4v" -> "video/x-m4v"
            else -> "video/*"
        }
    }

    companion object {
        /**
         * 支持的视频格式
         */
        val SUPPORTED_FORMATS = setOf(
            "mp4", "mkv", "avi", "mov", "flv", "ts", "wmv", "webm", "3gp", "m4v"
        )
    }
}

/**
 * 本地视频信息
 */
data class LocalVideoInfo(
    val id: Long,
    val name: String,
    val path: String,
    val uri: String,
    val size: Long,
    val duration: Long, // 毫秒
    val dateAdded: Long, // Unix 时间戳（秒）
    val mimeType: String,
    val width: Int,
    val height: Int
) {
    /**
     * 格式化时长
     */
    val formattedDuration: String
        get() {
            if (duration <= 0) return "00:00"
            val seconds = (duration / 1000) % 60
            val minutes = (duration / (1000 * 60)) % 60
            val hours = duration / (1000 * 60 * 60)
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    /**
     * 格式化大小
     */
    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }

    /**
     * 获取分辨率
     */
    val resolution: String?
        get() = if (width > 0 && height > 0) "${width}x${height}" else null
}
