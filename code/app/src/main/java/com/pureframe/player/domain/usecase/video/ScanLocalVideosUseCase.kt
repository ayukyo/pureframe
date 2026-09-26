package com.pureframe.player.domain.usecase.video

import com.pureframe.player.data.repository.VideoRepository
import com.pureframe.player.domain.model.Video
import com.pureframe.player.download.LocalVideoInfo
import com.pureframe.player.download.LocalVideoScanner
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pureframe.player.R
import com.pureframe.player.i18n.LocaleManager

/**
 * 扫描本地视频 UseCase
 *
 * 负责：
 * - 扫描设备上的本地视频
 * - 将新视频添加到数据库
 * - 更新已有视频的信息
 */
class ScanLocalVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val localVideoScanner: LocalVideoScanner,
    @ApplicationContext private val context: Context
) {
    /**
     * 执行扫描
     *
     * @param forceRefresh 是否强制刷新
     * @return 新增的视频数量
     */
    suspend operator fun invoke(forceRefresh: Boolean = false): Result<Int> {
        return try {
            Timber.d("ScanLocalVideosUseCase: 开始扫描本地视频")

            // 扫描本地视频
            val localVideos = localVideoScanner.scanLocalVideos(forceRefresh)

            // 获取数据库中已有的视频
            val existingVideos = videoRepository.getAllVideos().first()
            val existingPaths = existingVideos.map { it.filePath }.toSet()

            // 获取当前扫描到的视频路径
            val currentPaths = localVideos.map { it.path }.toSet()

            // 找出已删除的视频（数据库中有但文件已不存在）
            val deletedVideos = existingVideos.filter { it.filePath !in currentPaths }

            // 删除已不存在的视频
            if (deletedVideos.isNotEmpty()) {
                Timber.d("ScanLocalVideosUseCase: 发现 ${deletedVideos.size} 个已删除视频")
                deletedVideos.forEach { video ->
                    try {
                        videoRepository.deleteVideoById(video.id)
                        Timber.d("ScanLocalVideosUseCase: 删除已不存在视频: ${video.filePath}")
                    } catch (e: Exception) {
                        Timber.e(e, "ScanLocalVideosUseCase: 删除视频失败: ${video.filePath}")
                    }
                }
            }

            if (localVideos.isEmpty()) {
                Timber.d("ScanLocalVideosUseCase: 未扫描到本地视频")
                return Result.success(0)
            }

            // 找出新视频
            val newVideos = localVideos.filter { it.path !in existingPaths }

            if (newVideos.isEmpty()) {
                Timber.d("ScanLocalVideosUseCase: 没有新视频需要添加")
                return Result.success(0)
            }

            // 转换为 Video 实体并保存
            val videosToAdd = newVideos.map { localVideoInfo ->
                Video(
                    title = localVideoInfo.name,
                    filePath = localVideoInfo.path,
                    fileSize = localVideoInfo.size,
                    duration = localVideoInfo.duration,
                    format = localVideoInfo.mimeType,
                    resolution = localVideoInfo.resolution ?: "",
                    thumbnailPath = null,
                    createdAt = Date(localVideoInfo.dateAdded * 1000),
                    updatedAt = Date()
                )
            }

            // 批量添加
            videoRepository.addVideos(videosToAdd)

            Timber.i("ScanLocalVideosUseCase: 扫描完成，新增 ${videosToAdd.size} 个视频，删除 ${deletedVideos.size} 个已不存在视频")
            Result.success(videosToAdd.size)

        } catch (e: Exception) {
            Timber.e(e, "ScanLocalVideosUseCase: 扫描失败")
            Result.failure(e)
        }
    }

    /**
     * 扫描指定目录
     */
    suspend fun scanDirectory(directoryPath: String): Result<Int> {
        return try {
            val directory = java.io.File(directoryPath)
            if (!directory.exists() || !directory.isDirectory) {
                return Result.failure(
                    IllegalArgumentException(
                        LocaleManager.getString(context, R.string.error_directory_not_found, directoryPath)
                    )
                )
            }

            val localVideos = localVideoScanner.scanDirectory(directory)

            if (localVideos.isEmpty()) {
                return Result.success(0)
            }

            // 获取已有视频
            val existingVideos = videoRepository.getAllVideos().first()
            val existingPaths = existingVideos.map { it.filePath }.toSet()

            // 找出新视频
            val newVideos = localVideos.filter { it.path !in existingPaths }

            if (newVideos.isEmpty()) {
                return Result.success(0)
            }

            // 转换并保存
            val videosToAdd = newVideos.map { localVideoInfo ->
                Video(
                    title = localVideoInfo.name,
                    filePath = localVideoInfo.path,
                    fileSize = localVideoInfo.size,
                    duration = localVideoInfo.duration,
                    format = localVideoInfo.mimeType,
                    resolution = localVideoInfo.resolution ?: "",
                    thumbnailPath = null,
                    createdAt = Date(),
                    updatedAt = Date()
                )
            }

            videoRepository.addVideos(videosToAdd)

            Timber.i("ScanLocalVideosUseCase: 扫描目录完成，新增 ${videosToAdd.size} 个视频")
            Result.success(videosToAdd.size)

        } catch (e: Exception) {
            Timber.e(e, "ScanLocalVideosUseCase: 扫描目录失败")
            Result.failure(e)
        }
    }
}
