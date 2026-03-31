package com.pureframe.player.di

import com.pureframe.player.domain.usecase.video.*
import com.pureframe.player.domain.usecase.download.*
import com.pureframe.player.domain.usecase.playback.*
import com.pureframe.player.domain.usecase.settings.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * UseCase 依赖注入模块
 * 
 * 提供所有 UseCase 的实例，ViewModel 通过此模块获取 UseCase
 * 
 * 所有 UseCase 都是 Singleton，确保应用内只有一个实例
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    
    // ============ Video UseCases ============
    
    @Provides
    @Singleton
    fun provideGetAllVideosUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): GetAllVideosUseCase = GetAllVideosUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideGetLocalVideosUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): GetLocalVideosUseCase = GetLocalVideosUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideGetDownloadedVideosUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): GetDownloadedVideosUseCase = GetDownloadedVideosUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideGetRecentVideosUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): GetRecentVideosUseCase = GetRecentVideosUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideGetFavoriteVideosUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): GetFavoriteVideosUseCase = GetFavoriteVideosUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideGetVideoByIdUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): GetVideoByIdUseCase = GetVideoByIdUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideSearchVideosUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): SearchVideosUseCase = SearchVideosUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideToggleFavoriteUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): ToggleFavoriteUseCase = ToggleFavoriteUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideUpdatePlayInfoUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): UpdatePlayInfoUseCase = UpdatePlayInfoUseCase(videoRepository)
    
    @Provides
    @Singleton
    fun provideDeleteVideoUseCase(
        videoRepository: com.pureframe.player.data.repository.VideoRepository
    ): DeleteVideoUseCase = DeleteVideoUseCase(videoRepository)
    
    // ============ Download UseCases ============
    
    @Provides
    @Singleton
    fun provideGetAllDownloadsUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): GetAllDownloadsUseCase = GetAllDownloadsUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideGetActiveDownloadsUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): GetActiveDownloadsUseCase = GetActiveDownloadsUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideGetDownloadsByStatusUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): GetDownloadsByStatusUseCase = GetDownloadsByStatusUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideGetDownloadByIdUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): GetDownloadByIdUseCase = GetDownloadByIdUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideCreateDownloadTaskUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): CreateDownloadTaskUseCase = CreateDownloadTaskUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun providePauseDownloadUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): PauseDownloadUseCase = PauseDownloadUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideStartDownloadUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): StartDownloadUseCase = StartDownloadUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideDeleteDownloadUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): DeleteDownloadUseCase = DeleteDownloadUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideGetActiveDownloadCountUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): GetActiveDownloadCountUseCase = GetActiveDownloadCountUseCase(downloadRepository)
    
    @Provides
    @Singleton
    fun provideClearCompletedDownloadsUseCase(
        downloadRepository: com.pureframe.player.data.repository.DownloadRepository
    ): ClearCompletedDownloadsUseCase = ClearCompletedDownloadsUseCase(downloadRepository)
    
    // ============ Playback UseCases ============
    
    @Provides
    @Singleton
    fun provideGetPlaybackHistoryUseCase(
        playbackRepository: com.pureframe.player.data.repository.PlaybackRepository
    ): GetPlaybackHistoryUseCase = GetPlaybackHistoryUseCase(playbackRepository)
    
    @Provides
    @Singleton
    fun provideGetPlaybackHistoryByVideoUseCase(
        playbackRepository: com.pureframe.player.data.repository.PlaybackRepository
    ): GetPlaybackHistoryByVideoUseCase = GetPlaybackHistoryByVideoUseCase(playbackRepository)
    
    @Provides
    @Singleton
    fun provideGetLastPlaybackPositionUseCase(
        playbackRepository: com.pureframe.player.data.repository.PlaybackRepository
    ): GetLastPlaybackPositionUseCase = GetLastPlaybackPositionUseCase(playbackRepository)
    
    @Provides
    @Singleton
    fun provideSavePlaybackProgressUseCase(
        playbackRepository: com.pureframe.player.data.repository.PlaybackRepository
    ): SavePlaybackProgressUseCase = SavePlaybackProgressUseCase(playbackRepository)
    
    @Provides
    @Singleton
    fun provideGetPlayCountUseCase(
        playbackRepository: com.pureframe.player.data.repository.PlaybackRepository
    ): GetPlayCountUseCase = GetPlayCountUseCase(playbackRepository)
    
    @Provides
    @Singleton
    fun provideClearPlaybackHistoryUseCase(
        playbackRepository: com.pureframe.player.data.repository.PlaybackRepository
    ): ClearPlaybackHistoryUseCase = ClearPlaybackHistoryUseCase(playbackRepository)
    
    // ============ Settings UseCases ============
    
    @Provides
    @Singleton
    fun provideGetUserPreferencesUseCase(
        preferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
    ): GetUserPreferencesUseCase = GetUserPreferencesUseCase(preferencesRepository)
    
    @Provides
    @Singleton
    fun provideUpdateUserPreferencesUseCase(
        preferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
    ): UpdateUserPreferencesUseCase = UpdateUserPreferencesUseCase(preferencesRepository)
    
    @Provides
    @Singleton
    fun provideUpdateThemeModeUseCase(
        preferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
    ): UpdateThemeModeUseCase = UpdateThemeModeUseCase(preferencesRepository)
    
    @Provides
    @Singleton
    fun provideUpdateSortByUseCase(
        preferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
    ): UpdateSortByUseCase = UpdateSortByUseCase(preferencesRepository)
    
    @Provides
    @Singleton
    fun provideUpdatePlaySpeedUseCase(
        preferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
    ): UpdatePlaySpeedUseCase = UpdatePlaySpeedUseCase(preferencesRepository)
    
    @Provides
    @Singleton
    fun provideUpdateDownloadPathUseCase(
        preferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
    ): UpdateDownloadPathUseCase = UpdateDownloadPathUseCase(preferencesRepository)
    
    @Provides
    @Singleton
    fun provideUpdateGestureSettingsUseCase(
        preferencesRepository: com.pureframe.player.data.preferences.UserPreferencesRepository
    ): UpdateGestureSettingsUseCase = UpdateGestureSettingsUseCase(preferencesRepository)
}