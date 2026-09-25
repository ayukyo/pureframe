package com.pureframe.player.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP 代理服务器
 * 
 * 用于边下边播功能，充当播放器和下载引擎之间的桥梁：
 * 1. 接收播放器（ExoPlayer）的 HTTP 请求
 * 2. 从 TorrentEngine 获取已下载的数据
 * 3. 返回 HTTP 响应给播放器
 * 
 * 核心原理：
 * - ExoPlayer 发送 Range 请求（如 Range: bytes=0-1000）
 * - 代理服务器解析请求，确定需要的数据范围
 * - 检查该范围是否已下载
 * - 如果已下载，返回数据；否则等待下载完成或返回部分数据
 */
@Singleton
class StreamProxyServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val torrentEngine: TorrentEngine
) {
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 代理服务器端口
    private var serverSocket: ServerSocket? = null
    // 监听线程与调用方在不同线程访问，必须可见
    @Volatile
    private var isRunning = false
    @Volatile
    private var serverPort = 0
    
    // 当前活跃的流
    private val activeStreams = ConcurrentHashMap<String, StreamSession>()

    // 监听 accept 循环的协程，停止时只取消它而不取消 serverScope，
    // 否则 stop() 之后再 start() 会因为 scope 已取消而再也起不来
    private var acceptJob: Job? = null
    
    // 服务器状态
    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    // 流播放状态（按 taskId）
    private val _streamStatus = MutableStateFlow<Map<String, StreamPlaybackStatus>>(emptyMap())
    val streamStatus: StateFlow<Map<String, StreamPlaybackStatus>> = _streamStatus.asStateFlow()

    /**
     * 启动代理服务器
     */
    fun start(): Result<Int> {
        if (isRunning) {
            return Result.success(serverPort)
        }
        
        return try {
            // 找一个可用端口
            serverSocket = ServerSocket(0)  // 0 表示自动分配端口
            serverPort = serverSocket!!.localPort
            isRunning = true
            _serverStatus.value = ServerStatus.RUNNING
            
            // 启动监听协程
            acceptJob = serverScope.launch {
                listenForConnections()
            }
            
            Timber.i("StreamProxyServer: 启动成功，端口=$serverPort")
            Result.success(serverPort)
            
        } catch (e: Exception) {
            Timber.e(e, "StreamProxyServer: 启动失败")
            _serverStatus.value = ServerStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * 监听客户端连接
     */
    private fun listenForConnections() {
        while (isRunning && serverSocket != null) {
            try {
                val clientSocket = serverSocket!!.accept()
                Timber.d("StreamProxyServer: 新连接 - ${clientSocket.inetAddress}")
                
                // 为每个连接启动处理协程
                serverScope.launch {
                    handleConnection(clientSocket)
                }
                
            } catch (e: SocketException) {
                if (isRunning) {
                    Timber.e(e, "StreamProxyServer: 连接异常")
                }
                // 服务器关闭时会抛出 SocketException，这是正常的
            } catch (e: Exception) {
                Timber.e(e, "StreamProxyServer: 监听异常")
            }
        }
    }
    
    /**
     * 处理客户端连接
     */
    private fun handleConnection(clientSocket: Socket) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            // 解析 HTTP 请求
            val request = parseHttpRequest(input)
            
            if (request == null) {
                sendErrorResponse(output, 400, "Bad Request")
                clientSocket.close()
                return
            }
            
            Timber.d("StreamProxyServer: 请求 - ${request.method} ${request.path}")
            
            // 处理请求
            when (request.method) {
                "GET" -> handleGetRequest(request, output, clientSocket)
                "HEAD" -> handleHeadRequest(request, output)
                else -> sendErrorResponse(output, 405, "Method Not Allowed")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "StreamProxyServer: 连接处理异常")
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Timber.e(e, "StreamProxyServer: 关闭连接异常")
            }
        }
    }
    
    /**
     * 解析 HTTP 请求
     */
    private fun parseHttpRequest(input: InputStream): HttpRequest? {
        try {
            val reader = BufferedReader(InputStreamReader(input))
            
            // 读取请求行
            val requestLine = reader.readLine() ?: return null
            val parts = requestLine.split(" ")
            if (parts.size < 3) return null
            
            val method = parts[0]
            val path = parts[1]
            
            // 读取请求头
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val headerParts = line!!.split(": ", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0]] = headerParts[1]
                }
            }
            
            return HttpRequest(method, path, headers)
            
        } catch (e: Exception) {
            Timber.e(e, "StreamProxyServer: 解析请求异常")
            return null
        }
    }
    
    /**
     * 处理 GET 请求（返回流数据）
     */
    private fun handleGetRequest(
        request: HttpRequest,
        output: OutputStream,
        clientSocket: Socket
    ) {
        // 解析 taskId 和文件索引
        val pathParts = request.path.split("/")
        if (pathParts.size < 3) {
            sendErrorResponse(output, 404, "Not Found")
            return
        }
        
        val taskId = pathParts[1]
        val fileIndex = pathParts[2].toIntOrNull() ?: 0
        
        // 获取边下边播信息
        val streamableInfo = torrentEngine.getStreamableInfo(taskId)
        if (streamableInfo == null) {
            sendErrorResponse(output, 404, "Stream Not Found")
            return
        }
        
        // 解析 Range 头
        val rangeHeader = request.headers["Range"]
        val (startByte, endByte) = parseRangeHeader(rangeHeader, streamableInfo.totalSize)
        
        // 检查请求范围是否已下载
        val maxAvailable = streamableInfo.maxSeekPosition
        if (startByte > maxAvailable) {
            // 请求的数据尚未下载
            sendErrorResponse(output, 416, "Range Not Satisfiable")
            return
        }
        
        // 调整结束位置到已下载的最大位置
        val actualEnd = minOf(endByte, maxAvailable)
        val contentLength = actualEnd - startByte + 1
        
        Timber.d("StreamProxyServer: 流请求 - taskId=$taskId, range=$startByte-$actualEnd, length=$contentLength")
        
        // 创建流会话
        val session = StreamSession(taskId, fileIndex, startByte, actualEnd, clientSocket)
        activeStreams[taskId] = session
        
        // 更新流状态
        updateStreamStatus(taskId, StreamPlaybackStatus.PLAYING, startByte, actualEnd)
        
        // 发送响应头
        sendPartialContentHeader(output, contentLength, startByte, actualEnd, streamableInfo.totalSize)
        
        // 发送数据
        try {
            streamData(output, taskId, streamableInfo, startByte, actualEnd)
        } catch (e: Exception) {
            Timber.e(e, "StreamProxyServer: 流数据发送异常")
            updateStreamStatus(taskId, StreamPlaybackStatus.ERROR, startByte, actualEnd)
        }
        
        // 清理会话
        activeStreams.remove(taskId)
        updateStreamStatus(taskId, StreamPlaybackStatus.IDLE, startByte, actualEnd)
    }
    
    /**
     * 处理 HEAD 请求（只返回头信息）
     */
    private fun handleHeadRequest(request: HttpRequest, output: OutputStream) {
        // 解析 taskId 和文件索引
        val pathParts = request.path.split("/")
        if (pathParts.size < 3) {
            sendErrorResponse(output, 404, "Not Found")
            return
        }
        
        val taskId = pathParts[1]
        val streamableInfo = torrentEngine.getStreamableInfo(taskId)
        if (streamableInfo == null) {
            sendErrorResponse(output, 404, "Stream Not Found")
            return
        }
        
        // 发送完整文件头
        sendFullContentHeader(output, streamableInfo.totalSize)
    }
    
    /**
     * 解析 Range 头
     * 
     * Range: bytes=0-1000 -> (0, 1000)
     * Range: bytes=0-     -> (0, totalSize-1)
     * Range: bytes=1000-  -> (1000, totalSize-1)
     * 无 Range 头 -> (0, totalSize-1)
     */
    private fun parseRangeHeader(rangeHeader: String?, totalSize: Long): Pair<Long, Long> {
        if (rangeHeader == null) {
            return Pair(0, totalSize - 1)
        }
        
        // bytes=0-1000
        val rangeMatch = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toLong()
            val endStr = rangeMatch.groupValues[2]
            val end = if (endStr.isEmpty()) totalSize - 1 else endStr.toLong()
            return Pair(start, minOf(end, totalSize - 1))
        }
        
        // 无法解析，返回完整范围
        return Pair(0, totalSize - 1)
    }
    
    /**
     * 发送 206 Partial Content 响应头
     */
    private fun sendPartialContentHeader(
        output: OutputStream,
        contentLength: Long,
        startByte: Long,
        endByte: Long,
        totalSize: Long
    ) {
        val header = """
            HTTP/1.1 206 Partial Content
            Content-Type: video/mp4
            Content-Length: $contentLength
            Content-Range: bytes $startByte-$endByte/$totalSize
            Accept-Ranges: bytes
            Connection: close
            
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
        
        output.write(header.toByteArray())
        output.flush()
    }
    
    /**
     * 发送 200 OK 响应头（完整文件）
     */
    private fun sendFullContentHeader(output: OutputStream, totalSize: Long) {
        val header = """
            HTTP/1.1 200 OK
            Content-Type: video/mp4
            Content-Length: $totalSize
            Accept-Ranges: bytes
            Connection: close
            
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
        
        output.write(header.toByteArray())
        output.flush()
    }
    
    /**
     * 发送错误响应
     */
    private fun sendErrorResponse(output: OutputStream, code: Int, message: String) {
        val header = """
            HTTP/1.1 $code $message
            Content-Type: text/plain
            Content-Length: ${message.length}
            Connection: close
            
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
        
        output.write(header.toByteArray())
        output.write(message.toByteArray())
        output.flush()
    }
    
    /**
     * 流数据发送
     * 
     * 从 TorrentEngine 获取已下载的数据并发送给播放器
     */
    private fun streamData(
        output: OutputStream,
        taskId: String,
        streamableInfo: StreamableInfo,
        startByte: Long,
        endByte: Long
    ) {
        // 分块发送数据（每次 8KB）
        val chunkSize = 8192L
        var currentPos = startByte
        
        while (currentPos <= endByte) {
            val chunkEnd = minOf(currentPos + chunkSize - 1, endByte)
            val chunkLength = chunkEnd - currentPos + 1
            
            // 检查数据是否可用
            if (currentPos > streamableInfo.maxSeekPosition) {
                // 等待数据下载
                waitForData(taskId, currentPos)
            }
            
            // 获取数据块
            val data = getChunkData(taskId, streamableInfo, currentPos, chunkLength.toInt())
            
            if (data != null && data.isNotEmpty()) {
                output.write(data)
                output.flush()
                currentPos = chunkEnd + 1
            } else {
                // 数据获取失败
                Timber.w("StreamProxyServer: 无法获取数据块 - pos=$currentPos")
                break
            }
        }
    }
    
    /**
     * 等待数据下载
     */
    private fun waitForData(taskId: String, position: Long) {
        // 等待最多 30 秒
        val maxWaitMs = 30000L
        val checkIntervalMs = 500L
        var waited = 0L
        
        while (waited < maxWaitMs) {
            val streamableInfo = torrentEngine.getStreamableInfo(taskId)
            if (streamableInfo != null && position <= streamableInfo.maxSeekPosition) {
                return  // 数据已就绪
            }
            
            Thread.sleep(checkIntervalMs)
            waited += checkIntervalMs
        }
        
        Timber.w("StreamProxyServer: 等待数据超时 - taskId=$taskId, pos=$position")
    }
    
    /**
     * 从 TorrentEngine 获取数据块
     */
    private fun getChunkData(
        taskId: String,
        streamableInfo: StreamableInfo,
        position: Long,
        length: Int
    ): ByteArray? {
        // 使用 TorrentEngine 的 readDataBlock 方法
        return torrentEngine.readDataBlock(
            taskId,
            streamableInfo.largestFileIndex,
            position,
            length
        )
    }
    
    /**
     * 更新流播放状态
     */
    private fun updateStreamStatus(
        taskId: String,
        status: StreamPlaybackStatus,
        _position: Long,  // 未来用于详细状态
        _total: Long  // 未来用于详细状态
    ) {
        val currentMap = _streamStatus.value.toMutableMap()
        currentMap[taskId] = status
        _streamStatus.value = currentMap
    }
    
    /**
     * 获取流播放 URL
     * 
     * 格式：http://localhost:{port}/{taskId}/{fileIndex}
     */
    fun getStreamUrl(taskId: String, fileIndex: Int = 0): String? {
        if (!isRunning || serverPort == 0) {
            return null
        }
        
        return "http://127.0.0.1:$serverPort/$taskId/$fileIndex"
    }
    
    /**
     * 停止代理服务器
     */
    fun stop() {
        isRunning = false
        _serverStatus.value = ServerStatus.STOPPED
        
        // 关闭所有活跃流
        activeStreams.values.forEach { session ->
            try {
                session.clientSocket.close()
            } catch (e: Exception) {
                Timber.e(e, "StreamProxyServer: 关闭流异常")
            }
        }
        activeStreams.clear()
        
        // 关闭服务器 Socket
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Timber.e(e, "StreamProxyServer: 关闭服务器异常")
        }
        serverSocket = null

        // 只取消 accept 循环，保留 serverScope，保证可以再次 start()
        acceptJob?.cancel()
        acceptJob = null

        Timber.i("StreamProxyServer: 已停止")
    }
    
    /**
     * 检查服务器是否运行
     */
    fun isRunning(): Boolean = isRunning && serverSocket != null
    
    /**
     * 获取服务器端口
     */
    fun getPort(): Int = serverPort

    companion object {
        const val TAG = "StreamProxyServer"
    }
}

/**
 * HTTP 请求
 */
data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>
)

/**
 * 流会话
 */
data class StreamSession(
    val taskId: String,
    val fileIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val clientSocket: Socket
)

/**
 * 服务器状态
 */
enum class ServerStatus {
    STOPPED,   // 已停止
    RUNNING,   // 运行中
    ERROR      // 错误
}

/**
 * 流播放状态
 */
enum class StreamPlaybackStatus {
    IDLE,      // 空闲
    PLAYING,   // 播放中
    PAUSED,    // 暂停
    ERROR      // 错误
}