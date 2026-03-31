package com.pureframe.player.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * 下载服务
 * 
 * 支持磁力链接下载、边下边播
 * 使用前台服务确保下载不被中断
 */
@AndroidEntryPoint
class DownloadService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    @Inject
    lateinit var torrentManager: TorrentManager
    
    // 当前下载任务 ID
    private var currentTaskId: Long? = null
    private var currentTitle: String = ""
    
    // 通知管理器
    private lateinit var notificationManager: NotificationManager
    
    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_STOP_DOWNLOAD = "action_stop_download"
        const val ACTION_PAUSE_DOWNLOAD = "action_pause_download"
        const val ACTION_RESUME_DOWNLOAD = "action_resume_download"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_SAVE_PATH = "extra_save_path"
        
        fun createIntent(context: Context, url: String, title: String, savePath: String): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SAVE_PATH, savePath)
            }
        }
        
        fun createStopIntent(context: Context, taskId: Long): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP_DOWNLOAD
                putExtra(EXTRA_TASK_ID, taskId)
            }
        }
        
        fun createPauseIntent(context: Context, taskId: Long): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_TASK_ID, taskId)
            }
        }
        
        fun createResumeIntent(context: Context, taskId: Long): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_TASK_ID, taskId)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(NotificationManager::class.java)
        
        // 监听下载进度更新
        observeDownloadProgress()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "下载中"
                val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) 
                    ?: File(filesDir, "downloads").absolutePath
                
                currentTitle = title
                startForeground(NOTIFICATION_ID, createNotification(title, 0))
                startDownload(url, title, savePath)
            }
            ACTION_STOP_DOWNLOAD -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId > 0) {
                    stopDownload(taskId)
                } else {
                    stopDownload()
                }
                stopSelf()
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId > 0) {
                    pauseDownload(taskId)
                }
            }
            ACTION_RESUME_DOWNLOAD -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId > 0) {
                    resumeDownload(taskId)
                }
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "下载服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频下载进度通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, progress: Int, speed: Long = 0): Notification {
        val speedText = if (speed > 0) {
            val speedKB = speed / 1024
            if (speedKB > 1024) {
                "${speedKB / 1024} MB/s"
            } else {
                "$speedKB KB/s"
            }
        } else ""
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (progress > 0) "下载进度: $progress% $speedText" else "准备下载...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    /**
     * 监听下载进度更新
     */
    private fun observeDownloadProgress() {
        torrentManager.downloadProgress
            .onEach { progress ->
                // 更新通知
                updateNotification(progress.progress.toInt(), progress.downloadSpeed)
                
                // 检查是否完成
                if (progress.state == TorrentState.COMPLETED) {
                    Timber.i("DownloadService: 下载完成 - ${progress.taskId}")
                    onDownloadComplete(progress.taskId)
                }
            }
            .catch { e ->
                Timber.e(e, "DownloadService: 进度监听错误")
            }
            .launchIn(serviceScope)
    }
    
    /**
     * 开始下载
     */
    private fun startDownload(url: String, title: String, savePath: String) {
        serviceScope.launch {
            try {
                // 判断是磁力链接还是 torrent 文件
                val result = if (url.startsWith("magnet:")) {
                    torrentManager.createDownloadTask(url, savePath, title)
                } else if (url.endsWith(".torrent")) {
                    val torrentFile = File(url)
                    torrentManager.createDownloadTaskFromFile(torrentFile, savePath, title)
                } else {
                    // 不支持的链接类型
                    Result.failure(IllegalArgumentException("不支持的链接类型"))
                }
                
                result.fold(
                    onSuccess = { taskId ->
                        currentTaskId = taskId
                        Timber.i("DownloadService: 下载任务创建成功 - $taskId")
                    },
                    onFailure = { e ->
                        Timber.e(e, "DownloadService: 下载任务创建失败")
                        stopSelf()
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "DownloadService: 开始下载异常")
                stopSelf()
            }
        }
    }
    
    /**
     * 停止下载（当前任务）
     */
    private fun stopDownload() {
        currentTaskId?.let { taskId ->
            stopDownload(taskId)
        }
    }
    
    /**
     * 停止下载（指定任务）
     */
    private fun stopDownload(taskId: Long) {
        serviceScope.launch {
            torrentManager.deleteDownload(taskId, deleteFiles = false)
            Timber.d("DownloadService: 停止下载 - $taskId")
        }
    }
    
    /**
     * 暂停下载
     */
    private fun pauseDownload(taskId: Long) {
        serviceScope.launch {
            torrentManager.pauseDownload(taskId)
            updateNotification(0, 0, "已暂停")
            Timber.d("DownloadService: 暂停下载 - $taskId")
        }
    }
    
    /**
     * 恢复下载
     */
    private fun resumeDownload(taskId: Long) {
        serviceScope.launch {
            torrentManager.resumeDownload(taskId)
            updateNotification(0, 0, "恢复中...")
            Timber.d("DownloadService: 恢复下载 - $taskId")
        }
    }
    
    /**
     * 更新通知
     * 
     * 前台服务通知不需要 POST_NOTIFICATIONS 权限检查
     */
    @SuppressLint("NotificationPermission")
    private fun updateNotification(progress: Int, speed: Long, statusText: String? = null) {
        val notification = if (statusText != null) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentTitle)
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
        } else {
            createNotification(currentTitle, progress, speed)
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 下载完成处理
     * 
     * 前台服务通知不需要 POST_NOTIFICATIONS 权限检查
     */
    @SuppressLint("NotificationPermission")
    private fun onDownloadComplete(taskId: String) {
        serviceScope.launch {
            // 发送完成通知
            val completeNotification = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                .setContentTitle(currentTitle)
                .setContentText("下载完成")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, completeNotification)
            
            // 延迟停止服务
            kotlinx.coroutines.delay(3000)
            stopSelf()
        }
    }
}