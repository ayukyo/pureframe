package com.pureframe.player.download

import org.junit.Assert.*
import org.junit.Test

/**
 * TorrentModels 单元测试
 * 验证 TorrentMetadataInfo, TorrentFileInfo, DownloadProgressInfo 等数据类的逻辑
 */
class TorrentModelsTest {

    @Test
    fun testTorrentMetadataInfo_videoFiles() {
        val files = listOf(
            TorrentFileInfo(0, "/path/video1.mp4", "video1.mp4", 1024 * 1024 * 500, true, "1920x1080"),
            TorrentFileInfo(1, "/path/video2.mkv", "video2.mkv", 1024 * 1024 * 800, true, "1280x720"),
            TorrentFileInfo(2, "/path/subs.nfo", "subs.nfo", 1024, false, null),
            TorrentFileInfo(3, "/path/sample.txt", "sample.txt", 2048, false, null)
        )

        val metadata = TorrentMetadataInfo(
            taskId = "123",
            infoHash = "abc123def456",
            name = "Test Torrent",
            totalSize = 1024L * 1024 * 1300 + 3072,
            files = files
        )

        // 验证 videoFiles 过滤
        assertEquals(2, metadata.videoFiles.size)
        assertEquals("video1.mp4", metadata.videoFiles[0].name)
        assertEquals("video2.mkv", metadata.videoFiles[1].name)
    }

    @Test
    fun testTorrentFileInfo_formattedSize() {
        val smallFile = TorrentFileInfo(0, "/path/small.txt", "small.txt", 500, false, null)
        val kbFile = TorrentFileInfo(1, "/path/kb.txt", "kb.txt", 1024 * 50, false, null)
        val mbFile = TorrentFileInfo(2, "/path/mb.txt", "mb.txt", 1024 * 1024 * 100, false, null)
        val gbFile = TorrentFileInfo(3, "/path/gb.txt", "gb.txt", 1024L * 1024 * 1024 * 5, false, null)

        assertEquals("500 B", smallFile.formattedSize)
        assertEquals("50 KB", kbFile.formattedSize)
        assertEquals("100 MB", mbFile.formattedSize)
        assertEquals("5 GB", gbFile.formattedSize)
    }

    @Test
    fun testTorrentMetadataInfo_formattedTotalSize() {
        val metadata = TorrentMetadataInfo(
            taskId = "123",
            infoHash = "abc123",
            name = "Test",
            totalSize = 1024L * 1024 * 1024 * 2 + 1024 * 1024 * 500, // 2.5 GB
            files = emptyList()
        )

        assertEquals("2 GB", metadata.formattedTotalSize)
    }

    @Test
    fun testDownloadProgressInfo_creation() {
        val progress = DownloadProgressInfo(
            taskId = "123",
            progress = 50.5f,
            downloadSpeed = 1024 * 1024 * 2, // 2 MB/s
            state = TorrentState.DOWNLOADING,
            downloadedBytes = 1024L * 1024 * 500,
            totalBytes = 1024L * 1024 * 1000
        )

        assertEquals("123", progress.taskId)
        assertEquals(50.5f, progress.progress, 0.01f)
        assertEquals(2 * 1024 * 1024L, progress.downloadSpeed)
        assertEquals(TorrentState.DOWNLOADING, progress.state)
        assertEquals(500 * 1024 * 1024L, progress.downloadedBytes)
        assertEquals(1000 * 1024 * 1024L, progress.totalBytes)
    }

    @Test
    fun testTorrentState_values() {
        // 验证所有 TorrentState 枚举值
        assertEquals(6, TorrentState.values().size)
        assertNotNull(TorrentState.valueOf("PAUSED"))
        assertNotNull(TorrentState.valueOf("DOWNLOADING"))
        assertNotNull(TorrentState.valueOf("COMPLETED"))
        assertNotNull(TorrentState.valueOf("ERROR"))
        assertNotNull(TorrentState.valueOf("WAITING"))
        assertNotNull(TorrentState.valueOf("SEEDING"))
    }

    @Test
    fun testStreamableInfo_creation() {
        val info = StreamableInfo(
            taskId = "456",
            isStreamable = true,
            largestFileIndex = 2,
            largestFilePath = "/path/largest.mp4",
            cachedProgress = 0.25f,
            maxSeekPosition = 1024L * 1024 * 250,
            totalSize = 1024L * 1024 * 1000
        )

        assertEquals("456", info.taskId)
        assertTrue(info.isStreamable)
        assertEquals(2, info.largestFileIndex)
        assertEquals(0.25f, info.cachedProgress, 0.001f)
    }
}
