package com.pureframe.player.di

import android.content.Context
import androidx.room.Room
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
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pureframe_database"
        )
            .fallbackToDestructiveMigration()
            .build()
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