package com.pureframe.player.download;

import android.content.Context;

import androidx.annotation.Nullable;

import org.libtorrent4j.AddTorrentParams;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.Sha1Hash;
import org.libtorrent4j.swig.torrent_flags_t;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import kotlinx.coroutines.flow.MutableSharedFlow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.SharedFlow;
import kotlinx.coroutines.flow.StateFlow;
import timber.log.Timber;
import android.util.Log;

/**
 * libtorrent4j 的 Java 封装层
 *
 * 解决 Kotlin 无法直接调用 libtorrent4j 枚举的问题
 * 提供文件优先级设置的封装
 */
@Singleton
public class LibTorrentWrapper {

    private final Context context;

    // Session 管理器
    private SessionManager sessionManager;

    // 轮询线程引用
    private Thread pollerThread;

    // 任务 ID 到 info hash 的映射
    private final Map<String, Sha1Hash> taskIdToInfoHash = new ConcurrentHashMap<>();

    // 任务 ID 到选中的文件索引集合
    private final Map<String, Set<Integer>> taskIdToSelectedFiles = new ConcurrentHashMap<>();

    // Kotlin Flow
    private final MutableSharedFlow<DownloadProgressInfo> _downloadProgress;
    private final MutableSharedFlow<TorrentAddedInfo> _torrentAdded;
    private final MutableStateFlow<Map<String, StreamableInfo>> _streamableStatus;
    private final MutableSharedFlow<TorrentMetadataInfo> _metadataReceived;

    // 优先级常量
    private static final int PRIORITY_NORMAL = 4;
    private static final int PRIORITY_ZERO = 0;

    // 静态初始化 - 尝试加载 native 库
    static {
        try {
            Log.i("LibTorrentWrapper", ">>> 静态初始化开始，尝试加载 native 库");
            System.loadLibrary("torrent4j");
            Log.i("LibTorrentWrapper", ">>> native 库加载成功");
        } catch (Throwable e) {
            Log.e("LibTorrentWrapper", ">>> native 库加载失败", e);
        }
    }

    @Inject
    public LibTorrentWrapper(Context context) {
        Log.i("LibTorrentWrapper", "=== LibTorrentWrapper 构造函数开始 ===");
        this.context = context;
        this._downloadProgress = FlowFactory.INSTANCE.createDownloadProgressFlow();
        this._torrentAdded = FlowFactory.INSTANCE.createTorrentAddedFlow();
        this._streamableStatus = FlowFactory.INSTANCE.createStreamableStatusFlow();
        this._metadataReceived = FlowFactory.INSTANCE.createMetadataReceivedFlow();

        initialize();
    }

    public SharedFlow<DownloadProgressInfo> getDownloadProgress() {
        return _downloadProgress;
    }

    public SharedFlow<TorrentAddedInfo> getTorrentAdded() {
        return _torrentAdded;
    }

    public StateFlow<Map<String, StreamableInfo>> getStreamableStatus() {
        return _streamableStatus;
    }

    public SharedFlow<TorrentMetadataInfo> getMetadataReceived() {
        return _metadataReceived;
    }

    private void initialize() {
        try {
            Log.i("LibTorrentWrapper", "=== 开始初始化 Session ===");

            // 尝试加载 native 库
            try {
                System.loadLibrary("torrent4j");
                Log.i("LibTorrentWrapper", "Native 库加载成功");
            } catch (Throwable e) {
                Log.e("LibTorrentWrapper", "Native 库加载失败", e);
            }

            sessionManager = new SessionManager();
            Log.i("LibTorrentWrapper", "SessionManager 创建成功");

            // 配置 Session 优化下载速度
            configureSession();

            sessionManager.start();
            Log.i("LibTorrentWrapper", "Session 已启动");
            sessionManager.startDht();
            Log.i("LibTorrentWrapper", "DHT 已启动");

            // 打印 SessionManager 的所有公共方法（调试用）
            printSessionManagerMethods();

            // 使用简单的轮询来更新状态
            startStatusPolling();

            Timber.i("LibTorrentWrapper: Session 初始化成功");
        } catch (Exception e) {
            Log.e("LibTorrentWrapper", "Session 初始化失败", e);
            Timber.e(e, "LibTorrentWrapper: Session 初始化失败");
        }
    }

    private void configureSession() {
        try {
            // 设置下载速度限制 (0 = 无限制)
            Method setDownloadSpeedLimit = findMethod(SessionManager.class, "setDownloadSpeedLimit", long.class);
            if (setDownloadSpeedLimit != null) {
                setDownloadSpeedLimit.invoke(sessionManager, 0L);
                Timber.i("LibTorrentWrapper: 下载速度限制已设置为无限制");
            }

            // 设置上传速度限制 (0 = 无限制)
            Method setUploadSpeedLimit = findMethod(SessionManager.class, "setUploadSpeedLimit", long.class);
            if (setUploadSpeedLimit != null) {
                setUploadSpeedLimit.invoke(sessionManager, 0L);
                Timber.i("LibTorrentWrapper: 上传速度限制已设置为无限制");
            }

            // 设置连接数限制
            Method setConnectionsLimit = findMethod(SessionManager.class, "setConnectionsLimit", int.class);
            if (setConnectionsLimit != null) {
                setConnectionsLimit.invoke(sessionManager, 100);
                Timber.i("LibTorrentWrapper: 连接数限制已设置为 100");
            }

            // 设置最大 peers 数
            Method setMaxPeers = findMethod(SessionManager.class, "setMaxPeers", int.class);
            if (setMaxPeers != null) {
                setMaxPeers.invoke(sessionManager, 100);
                Timber.i("LibTorrentWrapper: 最大 peers 数已设置为 100");
            }

            Timber.i("LibTorrentWrapper: Session 配置完成");
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: Session 配置失败");
        }
    }

    private void printSessionManagerMethods() {
        try {
            Log.i("LibTorrentWrapper", "=== SessionManager 方法列表 ===");
            for (Method m : SessionManager.class.getMethods()) {
                String params = "";
                for (Class<?> p : m.getParameterTypes()) {
                    params += p.getSimpleName() + ", ";
                }
                Log.i("LibTorrentWrapper", m.getName() + "(" + params + ")");
            }
            Log.i("LibTorrentWrapper", "=== 方法列表结束 ===");
        } catch (Exception e) {
            Log.e("LibTorrentWrapper", "打印方法列表失败", e);
        }
    }

    private void startStatusPolling() {
        pollerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                    pollStatus();
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Timber.e(e, "LibTorrentWrapper: 状态轮询错误");
                }
            }
        });
        pollerThread.start();
    }

    private void pollStatus() {
        if (sessionManager == null) return;

        try {
            for (Sha1Hash infoHash : taskIdToInfoHash.values()) {
                TorrentHandle handle = sessionManager.find(infoHash);
                if (handle != null && handle.isValid()) {
                    updateTorrentStatus(handle);
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    private void updateTorrentStatus(TorrentHandle handle) {
        if (handle == null) return;

        String taskId = findTaskIdByInfoHash(handle.infoHash());
        if (taskId == null) return;

        try {
            TorrentStatus status = handle.status();
            TorrentInfo torrentInfo = handle.torrentFile();
            long totalBytes = 0;
            if (torrentInfo != null) {
                try {
                    totalBytes = torrentInfo.totalSize();
                } catch (Exception ignored) {}
            }

            TorrentState state = mapTorrentState(status);

            DownloadProgressInfo progressInfo = new DownloadProgressInfo(
                taskId,
                (float) status.progress() * 100,
                status.downloadRate(),
                state,
                status.totalDone(),
                totalBytes
            );

            _downloadProgress.tryEmit(progressInfo);

            // 当元数据可用时，发出 metadataReceived 事件
            if (torrentInfo != null && !taskIdToMetadataEmitted.contains(taskId)) {
                taskIdToMetadataEmitted.add(taskId);
                emitMetadataReceived(handle, taskId, torrentInfo);
            }
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 更新状态错误");
        }
    }

    // 跟踪已发出 metadata 的任务，避免重复发出
    private final Set<String> taskIdToMetadataEmitted = ConcurrentHashMap.newKeySet();

    private void emitMetadataReceived(TorrentHandle handle, String taskId, TorrentInfo torrentInfo) {
        try {
            java.util.List<TorrentFileInfo> files = new java.util.ArrayList<>();
            int numFiles = torrentInfo.files().numFiles();
            for (int i = 0; i < numFiles; i++) {
                String path = torrentInfo.files().filePath(i);
                long size = torrentInfo.files().fileSize(i);
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                boolean isVideo = isVideoFile(fileName);
                String resolution = parseResolution(path);

                files.add(new TorrentFileInfo(i, path, fileName, size, isVideo, resolution));
            }

            TorrentMetadataInfo metadataInfo = new TorrentMetadataInfo(
                taskId,
                handle.infoHash().toString(),
                torrentInfo.name(),
                torrentInfo.totalSize(),
                files
            );

            _metadataReceived.tryEmit(metadataInfo);
            Timber.i("LibTorrentWrapper: 元数据已发出 - taskId=" + taskId + ", 文件数=" + numFiles);
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 发出元数据失败");
        }
    }

    private TorrentState mapTorrentState(TorrentStatus status) {
        try {
            TorrentStatus.State state = status.state();
            switch (state) {
                case DOWNLOADING:
                    return TorrentState.DOWNLOADING;
                case FINISHED:
                case SEEDING:
                    return TorrentState.COMPLETED;
                case CHECKING_FILES:
                case DOWNLOADING_METADATA:
                case CHECKING_RESUME_DATA:
                    return TorrentState.WAITING;
                default:
                    return TorrentState.PAUSED;
            }
        } catch (Exception e) {
            return TorrentState.WAITING;
        }
    }

    public boolean addMagnetLink(String magnetLink, String savePath, String taskId) {
        try {
            Log.i("LibTorrentWrapper", ">>> addMagnetLink 开始 - taskId=" + taskId);
            Log.i("LibTorrentWrapper", ">>> magnetLink=" + magnetLink);
            Log.i("LibTorrentWrapper", ">>> savePath=" + savePath);

            if (sessionManager == null) {
                Log.e("LibTorrentWrapper", "Session 未初始化!");
                Timber.e("LibTorrentWrapper: Session 未初始化");
                return false;
            }

            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            // 解析 infoHash（用于映射）
            String infoHashStr = parseInfoHashFromMagnet(magnetLink);
            Log.i("LibTorrentWrapper", "infoHash=" + infoHashStr);

            // 使用 libtorrent4j 2.1.0 正确的 API
            // SessionManager.download(String url, File saveLocation, torrent_flags_t flags)
            torrent_flags_t flags = new torrent_flags_t();
            Log.i("LibTorrentWrapper", "调用 sessionManager.download(magnetLink, saveDir, flags)");
            sessionManager.download(magnetLink, saveDir, flags);
            Log.i("LibTorrentWrapper", "download 方法调用成功");

            // 存储 infoHash 映射
            if (infoHashStr != null) {
                try {
                    taskIdToInfoHash.put(taskId, Sha1Hash.parseHex(infoHashStr));
                } catch (Exception e) {
                    Log.w("LibTorrentWrapper", "解析 infoHash 失败", e);
                }
            }

            // 发出 torrentAdded 事件
            if (infoHashStr != null) {
                try {
                    TorrentAddedInfo addedInfo = new TorrentAddedInfo(taskId, infoHashStr, null);
                    _torrentAdded.tryEmit(addedInfo);
                    Timber.i("LibTorrentWrapper: 磁力链接添加成功 - taskId=" + taskId + ", infoHash=" + infoHashStr);
                    Log.i("LibTorrentWrapper", "已发出 torrentAdded 事件");
                } catch (Exception e) {
                    Timber.e(e, "LibTorrentWrapper: 发出 torrentAdded 事件失败");
                }
            }

            return true;
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 磁力链接添加失败");
            Log.e("LibTorrentWrapper", "addMagnetLink 异常", e);
            return false;
        }
    }

    public boolean addTorrentFile(File torrentFile, String savePath, String taskId) {
        try {
            if (sessionManager == null) {
                Timber.e("LibTorrentWrapper: Session 未初始化");
                return false;
            }

            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            TorrentInfo torrentInfo = new TorrentInfo(torrentFile);
            String infoHashStr = torrentInfo.infoHash().toString();
            taskIdToInfoHash.put(taskId, torrentInfo.infoHash());
            sessionManager.download(torrentInfo, saveDir);

            // 发出 torrentAdded 事件
            try {
                TorrentAddedInfo addedInfo = new TorrentAddedInfo(taskId, infoHashStr, torrentInfo.name());
                _torrentAdded.tryEmit(addedInfo);
                Timber.i("LibTorrentWrapper: 已发出 torrentAdded 事件 - taskId=" + taskId + ", infoHash=" + infoHashStr);
            } catch (Exception e) {
                Timber.e(e, "LibTorrentWrapper: 发出 torrentAdded 事件失败");
            }

            // 立即触发元数据事件，因为 Torrent 文件已包含元数据
            emitMetadataReceivedInternal(taskId, torrentInfo);

            Timber.i("LibTorrentWrapper: Torrent 文件添加成功 - taskId=" + taskId);
            return true;

        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: Torrent 文件添加失败");
            return false;
        }
    }

    private void emitMetadataReceivedInternal(String taskId, TorrentInfo torrentInfo) {
        try {
            java.util.List<TorrentFileInfo> files = new java.util.ArrayList<>();
            int numFiles = torrentInfo.files().numFiles();
            for (int i = 0; i < numFiles; i++) {
                String path = torrentInfo.files().filePath(i);
                long size = torrentInfo.files().fileSize(i);
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                boolean isVideo = isVideoFile(fileName);
                String resolution = parseResolution(path);

                files.add(new TorrentFileInfo(i, path, fileName, size, isVideo, resolution));
            }

            TorrentMetadataInfo metadataInfo = new TorrentMetadataInfo(
                taskId,
                torrentInfo.infoHash().toString(),
                torrentInfo.name(),
                torrentInfo.totalSize(),
                files
            );

            _metadataReceived.tryEmit(metadataInfo);
            taskIdToMetadataEmitted.add(taskId);
            Timber.i("LibTorrentWrapper: Torrent 文件元数据已发出 - taskId=" + taskId + ", 文件数=" + numFiles);
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 发出 Torrent 文件元数据失败");
        }
    }

    public void setDownloadFiles(String taskId, Set<Integer> fileIndices) {
        taskIdToSelectedFiles.put(taskId, fileIndices);

        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        if (infoHash == null) {
            Timber.w("LibTorrentWrapper: 未找到 taskId 对应的 infoHash - " + taskId);
            return;
        }

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle == null) {
            Timber.w("LibTorrentWrapper: 未找到 torrent handle - " + taskId);
            return;
        }

        try {
            TorrentInfo torrentInfo = handle.torrentFile();
            if (torrentInfo == null) {
                Timber.e("LibTorrentWrapper: torrentFile 为空");
                return;
            }

            int numFiles = torrentInfo.files().numFiles();
            int[] priorities = new int[numFiles];
            for (int i = 0; i < numFiles; i++) {
                priorities[i] = fileIndices.contains(i) ? PRIORITY_NORMAL : PRIORITY_ZERO;
            }

            // 通过反射调用
            Method method = findMethod(TorrentHandle.class, "setFilePriorities", int[].class);
            if (method != null) {
                method.invoke(handle, new Object[]{priorities});
            } else {
                method = findMethod(TorrentHandle.class, "prioritizeFiles", int[].class);
                if (method != null) {
                    method.invoke(handle, new Object[]{priorities});
                } else {
                    Timber.w("LibTorrentWrapper: 未找到设置文件优先级的方法");
                }
            }

            Timber.i("LibTorrentWrapper: 文件优先级设置完成 - taskId=" + taskId);

        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 设置文件优先级时出错");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public void pause(String taskId) {
        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        if (infoHash == null) return;

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle != null && handle.isValid()) {
            handle.pause();
            Timber.d("LibTorrentWrapper: 暂停成功 - " + taskId);
        }
    }

    public void resume(String taskId) {
        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        if (infoHash == null) return;

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle != null && handle.isValid()) {
            handle.resume();
            Timber.d("LibTorrentWrapper: 恢复成功 - " + taskId);
        }
    }

    public void startDownload(String taskId) {
        resume(taskId);
    }

    public void remove(String taskId, boolean deleteFiles) {
        Sha1Hash infoHash = taskIdToInfoHash.remove(taskId);
        if (infoHash == null) return;

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle != null && handle.isValid()) {
            sessionManager.remove(handle);
            taskIdToSelectedFiles.remove(taskId);
            Timber.d("LibTorrentWrapper: 移除任务 - " + taskId);
        }
    }

    @Nullable
    public TorrentMetadataInfo getTorrentMetadata(String taskId) {
        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        if (infoHash == null) return null;

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle == null) return null;

        TorrentInfo torrentInfo = handle.torrentFile();
        if (torrentInfo == null) return null;

        try {
            java.util.List<TorrentFileInfo> files = new java.util.ArrayList<>();
            int numFiles = torrentInfo.files().numFiles();
            for (int i = 0; i < numFiles; i++) {
                String path = torrentInfo.files().filePath(i);
                long size = torrentInfo.files().fileSize(i);
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                boolean isVideo = isVideoFile(fileName);
                String resolution = parseResolution(path);

                files.add(new TorrentFileInfo(i, path, fileName, size, isVideo, resolution));
            }

            return new TorrentMetadataInfo(
                taskId,
                infoHash.toString(),
                torrentInfo.name(),
                torrentInfo.totalSize(),
                files
            );
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 获取 metadata 失败");
            return null;
        }
    }

    public boolean isStreamable(String taskId, float threshold) {
        StreamableInfo info = getStreamableInfoInternal(taskId);
        if (info == null) return false;
        try {
            java.lang.reflect.Field field = StreamableInfo.class.getDeclaredField("isStreamable");
            field.setAccessible(true);
            return (Boolean) field.get(info);
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public StreamableInfo getStreamableInfo(String taskId) {
        return getStreamableInfoInternal(taskId);
    }

    @Nullable
    private StreamableInfo getStreamableInfoInternal(String taskId) {
        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        if (infoHash == null) return null;

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle == null || !handle.isValid()) return null;

        try {
            TorrentInfo torrentInfo = handle.torrentFile();
            if (torrentInfo == null) return null;

            int largestIndex = 0;
            long largestSize = 0;
            int numFiles = torrentInfo.files().numFiles();
            for (int i = 0; i < numFiles; i++) {
                long size = torrentInfo.files().fileSize(i);
                if (size > largestSize) {
                    largestSize = size;
                    largestIndex = i;
                }
            }

            String largestPath = torrentInfo.files().filePath(largestIndex);
            float cachedProgress = (float) handle.status().progress();
            long maxSeekPosition = (long) (largestSize * cachedProgress);

            return new StreamableInfo(
                taskId,
                cachedProgress >= 0.1f,
                largestIndex,
                largestPath,
                cachedProgress,
                maxSeekPosition,
                largestSize
            );
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 获取边下边播信息失败");
            return null;
        }
    }

    @Nullable
    public String getFilePath(String taskId, int fileIndex) {
        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        if (infoHash == null) return null;

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle == null) return null;

        try {
            TorrentInfo torrentInfo = handle.torrentFile();
            if (torrentInfo == null) return null;

            String filePath = torrentInfo.files().filePath(fileIndex);
            String savePath = handle.savePath();

            return new File(savePath, filePath).getAbsolutePath();
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 获取文件路径失败");
            return null;
        }
    }

    @Nullable
    public byte[] readDataBlock(String taskId, int fileIndex, long offset, int length) {
        String filePath = getFilePath(taskId, fileIndex);
        if (filePath == null) return null;

        File file = new File(filePath);
        if (!file.exists()) return null;

        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
            raf.seek(offset);
            byte[] buffer = new byte[length];
            int read = raf.read(buffer);
            raf.close();

            if (read < 0) return null;
            if (read < length) {
                byte[] result = new byte[read];
                System.arraycopy(buffer, 0, result, 0, read);
                return result;
            }
            return buffer;
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 读取数据块失败");
            return null;
        }
    }

    @Nullable
    public DownloadProgressInfo getProgressInfo(String taskId) {
        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        if (infoHash == null) return null;

        TorrentHandle handle = sessionManager.find(infoHash);
        if (handle == null || !handle.isValid()) return null;

        try {
            TorrentStatus status = handle.status();
            TorrentInfo torrentInfo = handle.torrentFile();
            long totalBytes = 0;
            if (torrentInfo != null) {
                try {
                    totalBytes = torrentInfo.totalSize();
                } catch (Exception ignored) {}
            }

            return new DownloadProgressInfo(
                taskId,
                (float) status.progress() * 100,
                status.downloadRate(),
                mapTorrentState(status),
                status.totalDone(),
                totalBytes
            );
        } catch (Exception e) {
            Timber.e(e, "LibTorrentWrapper: 获取进度信息失败");
            return null;
        }
    }

    public void shutdown() {
        Timber.i("LibTorrentWrapper: 关闭...");

        // 停止轮询线程
        if (pollerThread != null) {
            pollerThread.interrupt();
            try {
                pollerThread.join(1000);
            } catch (InterruptedException e) {
                Timber.w("LibTorrentWrapper: 轮询线程中断异常");
            }
            pollerThread = null;
        }

        if (sessionManager != null) {
            try {
                sessionManager.stopDht();
                sessionManager.stop();
            } catch (Exception e) {
                Timber.e(e, "LibTorrentWrapper: 关闭失败");
            }
        }

        taskIdToInfoHash.clear();
        taskIdToSelectedFiles.clear();
        taskIdToMetadataEmitted.clear();

        Timber.i("LibTorrentWrapper: 已关闭");
    }

    private String findTaskIdByInfoHash(Sha1Hash infoHash) {
        for (Map.Entry<String, Sha1Hash> entry : taskIdToInfoHash.entrySet()) {
            if (entry.getValue().equals(infoHash)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String parseInfoHashFromMagnet(String magnetLink) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("urn:btih:([a-fA-F0-9]{40})");
        java.util.regex.Matcher matcher = pattern.matcher(magnetLink);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return null;
    }

    private boolean isVideoFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            ext = fileName.substring(dotIndex + 1).toLowerCase();
        }
        return ext.equals("mp4") || ext.equals("mkv") || ext.equals("avi") ||
                ext.equals("mov") || ext.equals("flv") || ext.equals("ts") ||
                ext.equals("wmv") || ext.equals("webm");
    }

    private String parseResolution(String path) {
        if (path == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{3,4})[xX](\\d{3,4})");
        java.util.regex.Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1) + "x" + matcher.group(2);
        }
        return null;
    }

    public static final float DEFAULT_STREAMABLE_THRESHOLD = 10f;  // 10%
}
