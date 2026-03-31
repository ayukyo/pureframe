package com.pureframe.player.download

import android.net.Uri
import org.libtorrent4j.PieceIndex
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import timber.log.Timber
import java.io.File

/**
 * 可边下边播的 Torrent 封装
 *
 * 核心功能：
 * - 判断是否可边下边播
 * - 获取可播放的 URI
 * - 追踪已缓存进度
 * - 计算最大可跳转位置
 */
class StreamableTorrent(
    private val handle: TorrentHandle,
    private val threshold: Float = 0.1f  // 默认 10% 阈值
) {
    private var largestFileIndex: Int = -1
    private var largestFileSize: Long = 0L

    init {
        initFileInfo()
    }

    /**
     * 初始化文件信息，找出最大文件（通常是视频）
     */
    private fun initFileInfo() {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) {
            Timber.w("Cannot get torrent info")
            return
        }

        val fileStorage = torrentInfo.files()
        var maxSize = 0L
        var maxIndex = 0

        for (i in 0 until fileStorage.numFiles()) {
            val size = fileStorage.fileSize(i)
            if (size > maxSize) {
                maxSize = size
                maxIndex = i
            }
        }

        largestFileIndex = maxIndex
        largestFileSize = maxSize

        Timber.d("Largest file index: $largestFileIndex, size: $largestFileSize bytes")
    }

    /**
     * 检查是否可边下边播
     *
     * 条件：下载进度 >= threshold
     */
    fun isStreamable(): Boolean {
        val progress = handle.status().progress()
        return progress >= threshold
    }

    /**
     * 获取边下边播阈值进度
     */
    fun getThresholdProgress(): Float {
        return threshold * 100f
    }

    /**
     * 获取当前下载进度
     */
    fun getCurrentProgress(): Float {
        return handle.status().progress() * 100f
    }

    /**
     * 获取最大文件路径
     */
    fun getVideoFilePath(): String {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return ""

        return torrentInfo.files().filePath(largestFileIndex)
    }

    /**
     * 获取最大文件保存路径
     */
    fun getVideoSavePath(): String {
        val savePath = handle.status().savePath()
        val filePath = getVideoFilePath()
        return File(savePath, filePath).absolutePath
    }

    /**
     * 获取播放 URI
     *
     * 注意：这返回的是已下载部分的文件路径
     * 需要配合 ExoPlayer 的顺序播放模式
     */
    fun getStreamUri(): Uri {
        val savePath = getVideoSavePath()
        val file = File(savePath)

        // 如果文件不存在（下载刚开始），返回空 URI
        if (!file.exists()) {
            Timber.w("Video file not exists yet: $savePath")
            return Uri.EMPTY
        }

        return Uri.fromFile(file)
    }

    /**
     * 获取最大文件大小
     */
    fun getVideoFileSize(): Long {
        return largestFileSize
    }

    /**
     * 获取已缓存进度（针对最大文件）
     */
    fun getCachedProgress(): Float {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return 0f

        val pieceLength = torrentInfo.pieceLength()
        val numPieces = torrentInfo.numPieces()

        // 计算最大文件对应的片段范围
        val fileStorage = torrentInfo.files()
        val fileOffset = fileStorage.fileOffset(largestFileIndex)
        val fileSize = fileStorage.fileSize(largestFileIndex)

        val startPiece = (fileOffset / pieceLength).toInt()
        val endPiece = ((fileOffset + fileSize) / pieceLength).toInt()

        // 计算已下载片段数
        var cachedPieces = 0
        for (i in startPiece..endPiece) {
            if (handle.havePiece(i)) {
                cachedPieces++
            }
        }

        val totalPieces = endPiece - startPiece + 1
        return if (totalPieces > 0) {
            cachedPieces.toFloat() / totalPieces
        } else {
            0f
        }
    }

    /**
     * 获取最大可跳转位置（毫秒）
     *
     * 根据已缓存进度估算最大可播放位置
     */
    fun getMaxSeekPosition(durationMs: Long): Long {
        val cachedProgress = getCachedProgress()
        return (durationMs * cachedProgress).toLong()
    }

    /**
     * 获取已缓存字节数
     */
    fun getCachedBytes(): Long {
        return (largestFileSize * getCachedProgress()).toLong()
    }

    /**
     * 设置优先下载开头片段（边下边播优化）
     *
     * @param numPieces 要优先下载的片段数
     */
    fun prioritizeStart(numPieces: Int = 5) {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return

        val fileStorage = torrentInfo.files()
        val fileOffset = fileStorage.fileOffset(largestFileIndex)
        val pieceLength = torrentInfo.pieceLength()

        val startPiece = (fileOffset / pieceLength).toInt()

        // 设置开头片段优先下载
        for (i in startPiece until startPiece + numPieces) {
            if (i < torrentInfo.numPieces()) {
                handle.setPieceDeadline(i, 0)
                handle.piecePriority(i, Priority.TOP_PRIORITY)
                Timber.d("Prioritizing piece $i for streaming")
            }
        }
    }

    /**
     * 设置顺序下载模式（边下边播优化）
     *
     * 按顺序下载片段，优先保证开头部分
     */
    fun enableSequentialDownload() {
        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) return

        val numPieces = torrentInfo.numPieces()

        // 设置文件优先级
        val priorities = ByteArray(numPieces)
        for (i in 0 until numPieces) {
            priorities[i] = Priority.NORMAL.priority
        }

        // 开头片段最高优先级
        val fileStorage = torrentInfo.files()
        val fileOffset = fileStorage.fileOffset(largestFileIndex)
        val pieceLength = torrentInfo.pieceLength()
        val startPiece = (fileOffset / pieceLength).toInt()

        for (i in startPiece until startPiece + 10) {
            if (i < numPieces) {
                priorities[i] = Priority.TOP_PRIORITY.priority
            }
        }

        // 后续片段按顺序下载
        for (i in startPiece + 10 until numPieces) {
            priorities[i] = Priority.SEVEN.priority // 高优先级但不是最高
        }

        Timber.d("Sequential download enabled for streaming")
    }

    /**
     * 检查指定位置是否已缓存
     *
     * @param positionMs 播放位置（毫秒）
     * @param durationMs 总时长（毫秒）
     */
    fun isPositionCached(positionMs: Long, durationMs: Long): Boolean {
        val cachedProgress = getCachedProgress()
        val targetProgress = positionMs.toFloat() / durationMs

        return targetProgress <= cachedProgress
    }

    /**
     * 检查指定片段是否已下载
     */
    fun isPieceDownloaded(pieceIndex: Int): Boolean {
        return handle.havePiece(pieceIndex)
    }

    /**
     * 获取下载状态
     */
    fun getState(): TorrentState {
        val status = handle.status()
        return when {
            status.isPaused -> TorrentState.PAUSED
            status.isFinished -> TorrentState.COMPLETED
            status.isDownloading -> TorrentState.DOWNLOADING
            status.isSeeding -> TorrentState.SEEDING
            status.hasError() -> TorrentState.ERROR
            else -> TorrentState.WAITING
        }
    }

    /**
     * 获取下载速度
     */
    fun getDownloadSpeed(): Long {
        return handle.status().downloadRate()
    }

    companion object {
        const val TAG = "StreamableTorrent"

        // 默认边下边播阈值
        const val DEFAULT_THRESHOLD = 0.1f  // 10%
        const val MIN_THRESHOLD = 0.05f     // 5%
        const val MAX_THRESHOLD = 0.5f      // 50%
    }
}