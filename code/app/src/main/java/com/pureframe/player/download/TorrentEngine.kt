package com.pureframe.player.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BitTorrent 下载引擎
 *
 * 使用 libtorrent4j 实现完整的 BitTorrent 下载功能
 * 通过 Java 封装层 LibTorrentWrapper 解决 Kotlin 无法直接调用 libtorrent4j 枚举的问题
 */
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Java 封装层
    private val wrapper = LibTorrentWrapper(context)

    // 代理 Flow
    val downloadProgress: SharedFlow<DownloadProgressInfo> = wrapper.downloadProgress
    val torrentAdded: SharedFlow<TorrentAddedInfo> = wrapper.torrentAdded
    val streamableStatus: StateFlow<Map<String, StreamableInfo>> = wrapper.streamableStatus
    val metadataReceived: SharedFlow<TorrentMetadataInfo> = wrapper.metadataReceived

    /**
     * 添加磁力链接下载任务
     */
    fun addMagnetLink(magnetLink: String, savePath: String, taskId: String): Boolean {
        return wrapper.addMagnetLink(magnetLink, savePath, taskId)
    }

    /**
     * 添加 Torrent 文件下载任务
     */
    fun addTorrentFile(torrentFile: File, savePath: String, taskId: String): Boolean {
        return wrapper.addTorrentFile(torrentFile, savePath, taskId)
    }

    /**
     * 设置要下载的文件列表
     */
    fun setDownloadFiles(taskId: String, fileIndices: Set<Int>) {
        wrapper.setDownloadFiles(taskId, fileIndices)
    }

    /**
     * 开始下载
     */
    fun startDownload(taskId: String) {
        wrapper.startDownload(taskId)
    }

    /**
     * 暂停下载
     */
    fun pause(taskId: String) {
        wrapper.pause(taskId)
    }

    /**
     * 恢复下载
     */
    fun resume(taskId: String) {
        wrapper.resume(taskId)
    }

    /**
     * 移除下载任务
     */
    fun remove(taskId: String, deleteFiles: Boolean = false) {
        wrapper.remove(taskId, deleteFiles)
    }

    /**
     * 获取 torrent 元数据
     */
    fun getTorrentMetadata(taskId: String): TorrentMetadataInfo? {
        return wrapper.getTorrentMetadata(taskId)
    }

    /**
     * 检查是否可边下边播
     */
    fun isStreamable(taskId: String, threshold: Float = DEFAULT_STREAMABLE_THRESHOLD): Boolean {
        return wrapper.isStreamable(taskId, threshold)
    }

    /**
     * 获取边下边播信息
     */
    fun getStreamableInfo(taskId: String): StreamableInfo? {
        return wrapper.getStreamableInfo(taskId)
    }

    /**
     * 获取文件路径
     */
    fun getFilePath(taskId: String, fileIndex: Int): String? {
        return wrapper.getFilePath(taskId, fileIndex)
    }

    /**
     * 读取数据块
     */
    fun readDataBlock(taskId: String, fileIndex: Int, offset: Long, length: Int): ByteArray? {
        return wrapper.readDataBlock(taskId, fileIndex, offset, length)
    }

    /**
     * 获取下载进度信息
     */
    fun getProgressInfo(taskId: String): DownloadProgressInfo? {
        return wrapper.getProgressInfo(taskId)
    }

    /**
     * 关闭引擎
     */
    fun shutdown() {
        wrapper.shutdown()
    }

    companion object {
        const val TAG = "TorrentEngine"
        const val DEFAULT_STREAMABLE_THRESHOLD = 10f  // 10%
    }
}
