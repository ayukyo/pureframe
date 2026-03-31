package com.pureframe.player

import com.pureframe.player.data.local.dao.VideoDao
import com.pureframe.player.data.local.database.AppDatabase
import com.pureframe.player.data.local.entity.VideoEntity
import com.pureframe.player.data.repository.VideoRepository
import com.pureframe.player.domain.model.Video
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 视频数据层测试
 */
@RunWith(RobolectricTestRunner::class)
class VideoDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var videoDao: VideoDao

    @Before
    fun setup() {
        database = AppDatabase.getAppDatabase(RuntimeEnvironment.getApplication())
        videoDao = database.videoDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertVideo() = runTest {
        val video = VideoEntity(
            id = 1L,
            path = "/sdcard/test.mp4",
            title = "测试视频",
            duration = 120000L,
            size = 1024 * 1024 * 100L,
            width = 1920,
            height = 1080,
            addedTime = System.currentTimeMillis(),
            modifiedTime = System.currentTimeMillis(),
            isFavorite = false,
            lastPlayPosition = 0L,
            playCount = 0
        )

        videoDao.insertVideo(video)

        val inserted = videoDao.getVideoById(1L)
        assertNotNull(inserted)
        assertEquals("测试视频", inserted?.title)
    }

    @Test
    fun testGetAllVideos() = runTest {
        val video1 = createTestVideo(1L, "视频 1")
        val video2 = createTestVideo(2L, "视频 2")

        videoDao.insertVideo(video1)
        videoDao.insertVideo(video2)

        val videos = videoDao.getAllVideos().first()
        assertEquals(2, videos.size)
    }

    @Test
    fun testToggleFavorite() = runTest {
        val video = createTestVideo(1L, "收藏测试")
        videoDao.insertVideo(video)

        videoDao.toggleFavorite(1L)

        val updated = videoDao.getVideoById(1L)
        assertTrue(updated?.isFavorite == true)
    }

    private fun createTestVideo(id: Long, title: String) = VideoEntity(
        id = id,
        path = "/sdcard/test$id.mp4",
        title = title,
        duration = 120000L,
        size = 1024 * 1024 * 100L,
        width = 1920,
        height = 1080,
        addedTime = System.currentTimeMillis(),
        modifiedTime = System.currentTimeMillis(),
        isFavorite = false,
        lastPlayPosition = 0L,
        playCount = 0
    )
}
