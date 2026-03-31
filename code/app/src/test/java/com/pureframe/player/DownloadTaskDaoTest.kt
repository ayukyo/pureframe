package com.pureframe.player

import com.pureframe.player.data.local.dao.DownloadTaskDao
import com.pureframe.player.data.local.database.AppDatabase
import com.pureframe.player.data.local.entity.DownloadTaskEntity
import com.pureframe.player.domain.model.DownloadStatus
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
 * 下载任务数据层测试
 */
@RunWith(RobolectricTestRunner::class)
class DownloadTaskDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var downloadTaskDao: DownloadTaskDao

    @Before
    fun setup() {
        database = AppDatabase.getAppDatabase(RuntimeEnvironment.getApplication())
        downloadTaskDao = database.downloadTaskDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testCreateDownloadTask() = runTest {
        val task = DownloadTaskEntity(
            id = 1L,
            magnetUri = "magnet:?xt=urn:btih:test",
            title = "测试下载",
            savePath = "/sdcard/Download",
            status = DownloadStatus.DOWNLOADING.name,
            progress = 0,
            downloadedSize = 0L,
            totalSize = 1024 * 1024 * 500L,
            speed = 0L,
            createTime = System.currentTimeMillis(),
            startTime = null,
            completeTime = null
        )

        downloadTaskDao.insertTask(task)

        val inserted = downloadTaskDao.getTaskById(1L)
        assertNotNull(inserted)
        assertEquals("测试下载", inserted?.title)
        assertEquals(DownloadStatus.DOWNLOADING.name, inserted?.status)
    }

    @Test
    fun testPauseDownload() = runTest {
        val task = createTestTask(1L, DownloadStatus.DOWNLOADING)
        downloadTaskDao.insertTask(task)

        downloadTaskDao.updateStatus(1L, DownloadStatus.PAUSED)

        val updated = downloadTaskDao.getTaskById(1L)
        assertEquals(DownloadStatus.PAUSED.name, updated?.status)
    }

    @Test
    fun testGetActiveDownloads() = runTest {
        val task1 = createTestTask(1L, DownloadStatus.DOWNLOADING)
        val task2 = createTestTask(2L, DownloadStatus.PAUSED)
        val task3 = createTestTask(3L, DownloadStatus.COMPLETED)

        downloadTaskDao.insertTask(task1)
        downloadTaskDao.insertTask(task2)
        downloadTaskDao.insertTask(task3)

        val activeTasks = downloadTaskDao.getActiveDownloads().first()
        assertEquals(2, activeTasks.size)
    }

    @Test
    fun testDeleteCompletedDownloads() = runTest {
        val task1 = createTestTask(1L, DownloadStatus.COMPLETED)
        val task2 = createTestTask(2L, DownloadStatus.DOWNLOADING)

        downloadTaskDao.insertTask(task1)
        downloadTaskDao.insertTask(task2)

        downloadTaskDao.deleteCompletedDownloads()

        val allTasks = downloadTaskDao.getAllTasks().first()
        assertEquals(1, allTasks.size)
        assertEquals(DownloadStatus.DOWNLOADING.name, allTasks[0].status)
    }

    private fun createTestTask(id: Long, status: DownloadStatus) = DownloadTaskEntity(
        id = id,
        magnetUri = "magnet:?xt=urn:btih:test$id",
        title = "测试下载$id",
        savePath = "/sdcard/Download",
        status = status.name,
        progress = if (status == DownloadStatus.COMPLETED) 100 else 50,
        downloadedSize = if (status == DownloadStatus.COMPLETED) 1024 * 1024 * 500L else 250L * 1024 * 1024,
        totalSize = 1024 * 1024 * 500L,
        speed = if (status == DownloadStatus.DOWNLOADING) 1024 * 1024 else 0L,
        createTime = System.currentTimeMillis(),
        startTime = System.currentTimeMillis(),
        completeTime = if (status == DownloadStatus.COMPLETED) System.currentTimeMillis() else null
    )
}
