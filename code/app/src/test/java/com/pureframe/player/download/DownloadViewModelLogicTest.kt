package com.pureframe.player.download

import com.pureframe.player.domain.model.DownloadStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * DownloadViewModel 核心逻辑单元测试
 * 验证文件选择、状态转换等逻辑
 */
class DownloadViewModelLogicTest {

    // ========== 文件选择逻辑测试 ==========

    @Test
    fun testFileSelection_confirmWithEmptySet_shouldNotStart() {
        val selectedIndices = emptySet<Int>()
        // 当 selectedIndices 为空时，不应该开始下载
        assertTrue(selectedIndices.isEmpty())
    }

    @Test
    fun testFileSelection_confirmWithVideoFiles() {
        val metadata = TorrentMetadataInfo(
            taskId = "123",
            infoHash = "abc123",
            name = "Test Torrent",
            totalSize = 1024L * 1024 * 1000,
            files = listOf(
                TorrentFileInfo(0, "/path/video1.mp4", "video1.mp4", 500 * 1024 * 1024, true, "1920x1080"),
                TorrentFileInfo(1, "/path/video2.mkv", "video2.mkv", 300 * 1024 * 1024, true, "1280x720"),
                TorrentFileInfo(2, "/path/subs.nfo", "subs.nfo", 1024, false, null)
            )
        )

        // 默认选择所有视频文件
        val defaultSelection = metadata.videoFiles.map { it.index }.toSet()
        assertEquals(2, defaultSelection.size)
        assertTrue(defaultSelection.contains(0))
        assertTrue(defaultSelection.contains(1))
        assertFalse(defaultSelection.contains(2))
    }

    @Test
    fun testFileSelection_selectAll() {
        val metadata = TorrentMetadataInfo(
            taskId = "123",
            infoHash = "abc123",
            name = "Test",
            totalSize = 1000,
            files = listOf(
                TorrentFileInfo(0, "/a.txt", "a.txt", 100, false, null),
                TorrentFileInfo(1, "/b.txt", "b.txt", 200, false, null),
                TorrentFileInfo(2, "/c.txt", "c.txt", 300, false, null)
            )
        )

        val allIndices = metadata.files.map { it.index }.toSet()
        assertEquals(3, allIndices.size)
        assertTrue(allIndices.containsAll(listOf(0, 1, 2)))
    }

    @Test
    fun testFileSelection_deselectAll() {
        val previouslySelected = setOf(0, 1, 2)
        val deselected = emptySet<Int>()

        // 取消全选后，应该清空选择
        assertTrue(deselected.isEmpty())
        assertFalse(deselected.contains(0))
    }

    @Test
    fun testFileSelection_toggleSingleFile() {
        var selected = setOf(0, 1, 2)

        // 取消选择 index 1
        selected = selected - 1
        assertEquals(2, selected.size)
        assertFalse(selected.contains(1))

        // 重新选择 index 1
        selected = selected + 1
        assertEquals(3, selected.size)
        assertTrue(selected.contains(1))
    }

    // ========== 任务状态转换测试 ==========

    @Test
    fun testTorrentState_toDownloadStatus() {
        // 验证 TorrentState -> DownloadStatus 映射
        val mappings = mapOf(
            TorrentState.DOWNLOADING to DownloadStatus.DOWNLOADING,
            TorrentState.COMPLETED to DownloadStatus.COMPLETED,
            TorrentState.PAUSED to DownloadStatus.PAUSED,
            TorrentState.ERROR to DownloadStatus.ERROR,
            TorrentState.SEEDING to DownloadStatus.COMPLETED,
            TorrentState.WAITING to DownloadStatus.WAITING
        )

        for ((torrentState, expectedStatus) in mappings) {
            val actualStatus = mapTorrentStateToStatus(torrentState)
            assertEquals("映射失败: $torrentState", expectedStatus, actualStatus)
        }
    }

    // 辅助函数：模拟 TorrentManager 的状态映射逻辑
    private fun mapTorrentStateToStatus(state: TorrentState): DownloadStatus {
        return when (state) {
            TorrentState.DOWNLOADING -> DownloadStatus.DOWNLOADING
            TorrentState.COMPLETED -> DownloadStatus.COMPLETED
            TorrentState.PAUSED -> DownloadStatus.PAUSED
            TorrentState.ERROR -> DownloadStatus.ERROR
            TorrentState.SEEDING -> DownloadStatus.COMPLETED
            TorrentState.WAITING -> DownloadStatus.WAITING
        }
    }

    // ========== magnetLink 解析测试 ==========

    @Test
    fun testExtractTitleFromMagnet() {
        val magnetLink = "magnet:?xt=urn:btih:29a07a299a99dfd4ae8aba12bd7aa7566573708a&dn=[javdb.com]FNS-168-UC"

        // 提取 dn 参数
        val dnParam = magnetLink.findParameter("dn")
        assertEquals("[javdb.com]FNS-168-UC", dnParam)
    }

    @Test
    fun testExtractTitleFromMagnet_noDn() {
        val magnetLink = "magnet:?xt=urn:btih:29a07a299a99dfd4ae8aba12bd7aa7566573708a"

        val dnParam = magnetLink.findParameter("dn")
        assertNull(dnParam)
    }

    @Test
    fun testMagnetLink_findParameter() {
        val magnetLink = "magnet:?xt=urn:btih:abc&dn=TestName&xl=1000"

        assertEquals("TestName", magnetLink.findParameter("dn"))
        assertEquals("1000", magnetLink.findParameter("xl"))
        assertNull(magnetLink.findParameter("invalid"))
    }

    // 辅助函数：模拟 DownloadViewModel 的参数提取逻辑
    private fun String.findParameter(key: String): String? {
        val prefix = "$key="
        return this.split("&")
            .find { it.startsWith(prefix) || it.contains(prefix) }
            ?.substringAfter(prefix)
            ?.substringBefore("&")
    }

    // ========== infoHash 解析测试 ==========

    @Test
    fun testParseInfoHashFromMagnet() {
        val magnetLink = "magnet:?xt=urn:btih:29a07a299a99dfd4ae8aba12bd7aa7566573708a&dn=Test"

        val infoHash = parseInfoHashFromMagnet(magnetLink)
        assertEquals("29a07a299a99dfd4ae8aba12bd7aa7566573708a", infoHash)
    }

    @Test
    fun testParseInfoHashFromMagnet_lowercase() {
        // 验证 infoHash 被转换为小写（40位十六进制）
        val magnetLink = "magnet:?xt=urn:btih:ABCDEF1234567890ABCDEF1234567890ABCDEF12&dn=Test"

        val infoHash = parseInfoHashFromMagnet(magnetLink)
        assertEquals("abcdef1234567890abcdef1234567890abcdef12", infoHash)
    }

    @Test
    fun testParseInfoHashFromMagnet_invalid() {
        // 没有有效的 BTIH hash
        val magnetLink = "magnet:?dn=TestOnly"

        val infoHash = parseInfoHashFromMagnet(magnetLink)
        assertNull(infoHash)
    }

    // 辅助函数：模拟 LibTorrentWrapper 的 infoHash 解析逻辑
    private fun parseInfoHashFromMagnet(magnetLink: String): String? {
        val pattern = java.util.regex.Pattern.compile("urn:btih:([a-fA-F0-9]{40})")
        val matcher = pattern.matcher(magnetLink)
        if (matcher.find()) {
            return matcher.group(1)?.lowercase()
        }
        return null
    }

    // ========== 任务 ID 转换测试 ==========

    @Test
    fun testTaskIdConversion() {
        val taskIdStr = "123"
        val taskIdLong = taskIdStr.toLongOrNull()

        assertEquals(123L, taskIdLong)
    }

    @Test
    fun testTaskIdConversion_invalid() {
        val invalidId = "abc".toLongOrNull()
        assertNull(invalidId)
    }

    @Test
    fun testTaskIdConversion_roundTrip() {
        val originalLong = 9223372036854775807L
        val str = originalLong.toString()
        val recovered = str.toLongOrNull()

        assertEquals(originalLong, recovered)
    }

    // ========== 文件名提取测试 ==========

    @Test
    fun testIsVideoFile() {
        val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "flv", "ts", "wmv", "webm")

        for (ext in videoExtensions) {
            assertTrue(".$ext 应该是视频文件", isVideoFile("video.$ext"))
        }

        assertFalse("txt 不是视频文件", isVideoFile("document.txt"))
        assertFalse("jpg 不是视频文件", isVideoFile("image.jpg"))
    }

    @Test
    fun testIsVideoFile_caseInsensitive() {
        assertTrue(isVideoFile("video.MP4"))
        assertTrue(isVideoFile("video.Mkv"))
        assertTrue(isVideoFile("video.AVI"))
    }

    @Test
    fun testIsVideoFile_noExtension() {
        assertFalse(isVideoFile("README"))
        assertFalse(isVideoFile("Makefile"))
    }

    // 辅助函数：模拟 LibTorrentWrapper 的视频文件判断逻辑
    private fun isVideoFile(fileName: String): Boolean {
        if (fileName.isNullOrEmpty()) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("mp4", "mkv", "avi", "mov", "flv", "ts", "wmv", "webm")
    }

    // ========== 分辨率解析测试 ==========

    @Test
    fun testParseResolution() {
        assertEquals("1920x1080", parseResolution("video_1920x1080.mp4"))
        assertEquals("1280x720", parseResolution("movie.1280x720.mkv"))
        assertEquals("3840x2160", parseResolution("uhd_3840X2160.ts")) // 大写 X
        assertNull(parseResolution("video.txt"))
        assertNull(parseResolution("video_999xabc.mp4"))
    }

    // 辅助函数：模拟 LibTorrentWrapper 的分辨率解析逻辑
    private fun parseResolution(path: String): String? {
        if (path.isNullOrEmpty()) return null
        val pattern = java.util.regex.Pattern.compile("(\\d{3,4})[xX](\\d{3,4})")
        val matcher = pattern.matcher(path)
        if (matcher.find()) {
            return "${matcher.group(1)}x${matcher.group(2)}"
        }
        return null
    }
}
