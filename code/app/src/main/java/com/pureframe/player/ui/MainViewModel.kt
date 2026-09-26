package com.pureframe.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureframe.player.domain.model.DownloadStatus
import com.pureframe.player.domain.usecase.download.GetAllDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 主界面 ViewModel
 *
 * 职责：
 * - 提供活跃下载数量，用于底部导航"下载"Tab 的角标
 * （用户停留在本地/设置页时也能感知后台是否有任务在下载）
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    getAllDownloadsUseCase: GetAllDownloadsUseCase
) : ViewModel() {

    /**
     * 活跃下载数：正在下载 + 排队等待的任务数（暂停不算，用户主动暂停的不打扰）
     */
    val activeDownloadCount: StateFlow<Int> = getAllDownloadsUseCase()
        .map { tasks ->
            tasks.count {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}
