package com.pureframe.player.download

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Flow 工厂类（用于 Java 封装层）
 */
object FlowFactory {

    fun createDownloadProgressFlow(): MutableSharedFlow<DownloadProgressInfo> {
        return MutableSharedFlow(replay = 1, extraBufferCapacity = 64)
    }

    fun createTorrentAddedFlow(): MutableSharedFlow<TorrentAddedInfo> {
        return MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
    }

    fun createStreamableStatusFlow(): MutableStateFlow<Map<String, StreamableInfo>> {
        return MutableStateFlow(ConcurrentHashMap())
    }

    fun createMetadataReceivedFlow(): MutableSharedFlow<TorrentMetadataInfo> {
        return MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
    }
}
