package com.pureframe.player.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pureframe.player.data.converter.DateTypeConverter
import com.pureframe.player.data.dao.DownloadTaskDao
import com.pureframe.player.data.dao.PlaybackHistoryDao
import com.pureframe.player.data.dao.VideoDao
import com.pureframe.player.data.entity.DownloadTaskEntity
import com.pureframe.player.data.entity.PlaybackHistoryEntity
import com.pureframe.player.data.entity.VideoEntity

/**
 * 应用数据库
 * 
 * Room 数据库配置
 */
@Database(
    entities = [
        VideoEntity::class,
        DownloadTaskEntity::class,
        PlaybackHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun videoDao(): VideoDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
}