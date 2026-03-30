package com.pureframe.player.download

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

/**
 * 下载服务
 * 
 * 支持磁力链接下载、边下边播
 * 使用前台服务确保下载不被中断
 */
@AndroidEntryPoint
class DownloadService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_STOP_DOWNLOAD = "action_stop_download"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        
        fun createIntent(context: Context, url: String, title: String): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "下载中"
                
                startForeground(NOTIFICATION_ID, createNotification(title, 0))
                startDownload(url, title)
            }
            ACTION_STOP_DOWNLOAD -> {
                stopDownload()
                stopSelf()
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
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (progress > 0) "下载进度: $progress%" else "准备下载...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
    }
    
    private fun startDownload(url: String, title: String) {
        // TODO: 实现下载逻辑
        // 1. 解析磁力链接
        // 2. 创建下载任务
        // 3. 更新进度通知
        // 4. 完成后停止服务
    }
    
    private fun stopDownload() {
        // TODO: 取消下载任务
    }
}