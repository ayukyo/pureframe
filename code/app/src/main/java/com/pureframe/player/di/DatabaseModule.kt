package com.pureframe.player.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pureframe.player.data.database.AppDatabase
import com.pureframe.player.data.dao.VideoDao
import com.pureframe.player.data.dao.DownloadTaskDao
import com.pureframe.player.data.dao.PlaybackHistoryDao
import com.pureframe.player.data.repository.VideoRepository
import com.pureframe.player.data.repository.VideoRepositoryImpl
import com.pureframe.player.data.repository.DownloadRepository
import com.pureframe.player.data.repository.DownloadRepositoryImpl
import com.pureframe.player.data.repository.PlaybackRepository
import com.pureframe.player.data.repository.PlaybackRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块 - 提供数据库相关依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val dbName = "pureframe_database"
        ensureDatabaseUsable(context, dbName)
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
        // 关键：不用默认 WAL 模式。WAL 在进程被 SIGKILL（如用户从最近任务
        // 划掉应用、force-stop）后依赖 checkpoint 恢复，一旦 WAL 损坏，
        // Room 会静默重建空库导致用户数据全部丢失（真机已复现）。
        // TRUNCATE 模式每次写入原子落盘，无 WAL 恢复环节，更抗崩溃。
        return builder
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 打开数据库前做完整性检查：若库文件损坏（如 SIGKILL 打断写入），
     * 先把损坏文件备份到 name.corrupt.<时间戳> 再删除，让 Room 重建空库。
     * 比静默丢数据多一层可恢复的现场，便于事后排查。
     */
    private fun ensureDatabaseUsable(context: Context, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return
        val intact = runCatching {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                // integrity_check 返回 "ok" 表示库结构完好
                db.compileStatement("PRAGMA integrity_check").simpleQueryForString() == "ok"
            }
        }.getOrDefault(false)
        if (!intact) {
            Log.e("DatabaseModule", "数据库损坏，备份后重建: ${dbFile.name}")
            val ts = System.currentTimeMillis()
            runCatching {
                dbFile.renameTo(java.io.File(dbFile.parentFile, "$dbName.corrupt.$ts"))
                java.io.File(dbFile.parentFile, "$dbName-wal").delete()
                java.io.File(dbFile.parentFile, "$dbName-shm").delete()
                java.io.File(dbFile.parentFile, "$dbName-journal").delete()
            }
        }
    }
    
    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }
    
    @Provides
    fun provideDownloadTaskDao(database: AppDatabase): DownloadTaskDao {
        return database.downloadTaskDao()
    }
    
    @Provides
    fun providePlaybackHistoryDao(database: AppDatabase): PlaybackHistoryDao {
        return database.playbackHistoryDao()
    }
}

/**
 * Hilt 模块 - 绑定 Repository 接口到实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindVideoRepository(
        impl: VideoRepositoryImpl
    ): VideoRepository
    
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository
    
    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        impl: PlaybackRepositoryImpl
    ): PlaybackRepository
}