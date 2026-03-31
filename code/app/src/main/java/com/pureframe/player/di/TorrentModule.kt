package com.pureframe.player.di

import android.content.Context
import com.pureframe.player.download.TorrentEngine
import com.pureframe.player.download.TorrentManager
import com.pureframe.player.data.repository.DownloadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Torrent 下载引擎的 Hilt DI 模块
 */
@Module
@InstallIn(SingletonComponent::class)
object TorrentModule {

    @Provides
    @Singleton
    fun provideTorrentEngine(
        @ApplicationContext context: Context
    ): TorrentEngine {
        return TorrentEngine(context)
    }

    @Provides
    @Singleton
    fun provideTorrentManager(
        @ApplicationContext context: Context,
        torrentEngine: TorrentEngine,
        downloadRepository: DownloadRepository
    ): TorrentManager {
        return TorrentManager(context, torrentEngine, downloadRepository)
    }
}