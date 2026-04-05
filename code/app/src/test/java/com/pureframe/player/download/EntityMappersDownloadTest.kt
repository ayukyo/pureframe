package com.pureframe.player.download

import com.pureframe.player.data.entity.DownloadTaskEntity
import com.pureframe.player.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * DownloadTask Entity 映射单元测试
 * 验证 DownloadTaskEntity 与 DownloadTask 之间的转换逻辑
 */
class EntityMappersDownloadTest {

    @Test
    fun testEntityToDomainModel_basic() {
        val entity = DownloadTaskEntity(
            id = 1L,
            url = "magnet:?xt=urn:btih:29a07a299a99dfd4ae8aba12bd7aa7566573708a",
            name = "测试视频",
            downloadPath = "/storage/emulated/0/PureFrame/downloads",
            totalBytes = 1024L * 1024 * 500,
            downloadedBytes = 1024L * 1024 * 250,
            status = "downloading",
            downloadSpeed = 1024 * 1024 * 2,
            errorMessage = null,
            createdAt = Date(),
            completedAt = null,
            torrentHash = "29a07a299a99dfd4ae8aba12bd7aa7566573708a"
        )

        val domain = entity.toDomainModel()

        assertEquals(1L, domain.id)
        assertEquals(entity.url, domain.url)
        assertEquals("测试视频", domain.title)
        assertEquals("/storage/emulated/0/PureFrame/downloads", domain.savePath)
        assertEquals(1024L * 1024 * 500, domain.totalSize)
        assertEquals(1024L * 1024 * 250, domain.downloadedSize)
        assertEquals(DownloadStatus.DOWNLOADING, domain.status)
        assertEquals(50f, domain.progress, 0.01f) // 250/500 = 50%
        assertEquals(2 * 1024 * 1024L, domain.speed)
        assertEquals("29a07a299a99dfd4ae8aba12bd7aa7566573708a", domain.torrentHash)
    }

    @Test
    fun testEntityToDomainModel_magnetLinkExtraction() {
        // 测试 magnet 链接被正确识别为磁力下载
        val magnetEntity = DownloadTaskEntity(
            id = 1L,
            url = "magnet:?xt=urn:btih:testhash&dn=TestVideo",
            name = "TestVideo",
            downloadPath = "/downloads",
            totalBytes = 0,
            downloadedBytes = 0,
            status = "pending",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = magnetEntity.toDomainModel()
        assertEquals("magnet:?xt=urn:btih:testhash&dn=TestVideo", domain.magnetLink)
        assertTrue(domain.isTorrentDownload)
    }

    @Test
    fun testEntityToDomainModel_nonMagnetUrl() {
        // 非磁力链接不应有 magnetLink
        val httpEntity = DownloadTaskEntity(
            id = 1L,
            url = "https://example.com/file.torrent",
            name = "File",
            downloadPath = "/downloads",
            totalBytes = 0,
            downloadedBytes = 0,
            status = "pending",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = httpEntity.toDomainModel()
        assertNull(domain.magnetLink)
        assertFalse(domain.isTorrentDownload)
    }

    @Test
    fun testEntityToDomainModel_fileNameExtraction_unixPath() {
        val entity = DownloadTaskEntity(
            id = 1L,
            url = "magnet:?xt=urn:btih:test",
            name = "/path/to/video.mp4",
            downloadPath = "/downloads",
            totalBytes = 0,
            downloadedBytes = 0,
            status = "pending",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = entity.toDomainModel()
        assertEquals("video.mp4", domain.fileName)
    }

    @Test
    fun testEntityToDomainModel_fileNameExtraction_windowsPath() {
        val entity = DownloadTaskEntity(
            id = 1L,
            url = "magnet:?xt=urn:btih:test",
            name = "C:\\Users\\Videos\\movie.mkv",
            downloadPath = "C:\\Users\\Videos",
            totalBytes = 0,
            downloadedBytes = 0,
            status = "pending",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = entity.toDomainModel()
        assertEquals("movie.mkv", domain.fileName)
    }

    @Test
    fun testEntityToDomainModel_fileNameExtraction_edgeCase_trailingSlash() {
        // 边界情况：路径以 \ 结尾
        val entity = DownloadTaskEntity(
            id = 1L,
            url = "magnet:?xt=urn:btih:test",
            name = "C:\\Downloads\\",
            downloadPath = "C:\\Downloads",
            totalBytes = 0,
            downloadedBytes = 0,
            status = "pending",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = entity.toDomainModel()
        // 应该回退到使用原始 name
        assertEquals("C:\\Downloads\\", domain.fileName)
    }

    @Test
    fun testEntityToDomainModel_progressCalculation() {
        // 测试进度计算：downloadedBytes * 100 / totalBytes
        val entity = DownloadTaskEntity(
            id = 1L,
            url = "magnet:?xt=urn:btih:test",
            name = "Test",
            downloadPath = "/downloads",
            totalBytes = 1000L,
            downloadedBytes = 500L,
            status = "downloading",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = entity.toDomainModel()
        assertEquals(50f, domain.progress, 0.01f)
    }

    @Test
    fun testEntityToDomainModel_progressCalculation_zeroTotal() {
        // totalBytes 为 0 时，进度应为 0
        val entity = DownloadTaskEntity(
            id = 1L,
            url = "magnet:?xt=urn:btih:test",
            name = "Test",
            downloadPath = "/downloads",
            totalBytes = 0,
            downloadedBytes = 0,
            status = "pending",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = entity.toDomainModel()
        assertEquals(0f, domain.progress, 0.01f)
    }

    @Test
    fun testDomainToEntity_conversion() {
        val domain = DownloadTask(
            url = "magnet:?xt=urn:btih:test",
            title = "测试标题",
            fileName = "test.mp4",
            savePath = "/downloads",
            totalSize = 1024 * 1024 * 100L,
            downloadedSize = 1024 * 1024 * 50L,
            status = DownloadStatus.DOWNLOADING,
            progress = 50f,
            speed = 1024 * 1024L
        )

        val entity = domain.toEntity()

        assertEquals("magnet:?xt=urn:btih:test", entity.url)
        assertEquals("测试标题", entity.name)
        assertEquals("/downloads", entity.downloadPath)
        assertEquals(100 * 1024 * 1024L, entity.totalBytes)
        assertEquals(50 * 1024 * 1024L, entity.downloadedBytes)
        assertEquals("downloading", entity.status)
        assertEquals(1024 * 1024L, entity.downloadSpeed)
    }

    @Test
    fun testDownloadTask_isActive() {
        val downloading = DownloadTask(
            url = "test1", title = "t1", fileName = "f1", savePath = "/p",
            status = DownloadStatus.DOWNLOADING
        )
        val paused = DownloadTask(
            url = "test2", title = "t2", fileName = "f2", savePath = "/p",
            status = DownloadStatus.PAUSED
        )
        val completed = DownloadTask(
            url = "test3", title = "t3", fileName = "f3", savePath = "/p",
            status = DownloadStatus.COMPLETED
        )

        assertTrue(downloading.isActive)
        assertTrue(paused.isActive)
        assertFalse(completed.isActive)
    }

    @Test
    fun testDownloadTask_isCompleted() {
        val downloading = DownloadTask(
            url = "test", title = "t", fileName = "f", savePath = "/p",
            status = DownloadStatus.DOWNLOADING
        )
        val completed = DownloadTask(
            url = "test", title = "t", fileName = "f", savePath = "/p",
            status = DownloadStatus.COMPLETED
        )

        assertFalse(downloading.isCompleted)
        assertTrue(completed.isCompleted)
    }

    @Test
    fun testDownloadTask_canStream() {
        val notStreamable = DownloadTask(
            url = "test", title = "t", fileName = "f", savePath = "/p",
            isStreamable = false,
            progress = 50f,
            streamableProgress = 10f
        )
        val lowProgress = DownloadTask(
            url = "test", title = "t", fileName = "f", savePath = "/p",
            isStreamable = true,
            progress = 5f, // 低于 10%
            streamableProgress = 10f
        )
        val readyToStream = DownloadTask(
            url = "test", title = "t", fileName = "f", savePath = "/p",
            isStreamable = true,
            progress = 50f, // 高于 10%
            streamableProgress = 10f
        )

        assertFalse(notStreamable.canStream)
        assertFalse(lowProgress.canStream)
        assertTrue(readyToStream.canStream)
    }

    @Test
    fun testDownloadStatus_toEntityStatus() {
        assertEquals("pending", DownloadStatus.PENDING.toEntityStatus())
        assertEquals("downloading", DownloadStatus.DOWNLOADING.toEntityStatus())
        assertEquals("paused", DownloadStatus.PAUSED.toEntityStatus())
        assertEquals("completed", DownloadStatus.COMPLETED.toEntityStatus())
        assertEquals("error", DownloadStatus.FAILED.toEntityStatus())
        assertEquals("error", DownloadStatus.ERROR.toEntityStatus())
        assertEquals("cancelled", DownloadStatus.CANCELLED.toEntityStatus())
        assertEquals("waiting", DownloadStatus.WAITING.toEntityStatus())
    }

    @Test
    fun testParseDownloadStatus() {
        val entity = DownloadTaskEntity(
            id = 1L,
            url = "test",
            name = "test",
            downloadPath = "/test",
            totalBytes = 0,
            downloadedBytes = 0,
            status = "downloading",
            downloadSpeed = 0,
            createdAt = Date()
        )

        val domain = entity.toDomainModel()
        assertEquals(DownloadStatus.DOWNLOADING, domain.status)
    }
}
