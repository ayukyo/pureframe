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

    // 手动暂停的任务 ID 集合
    private final Set<String> pausedTasks = ConcurrentHashMap.newKeySet();

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

            // 设置监听端口（关键！BT 需要监听端口接收 peer 连接）
            // 必须在 session.start() 之后调用才生效
            try {
                // libtorrent4j 2.1.0 使用 listenInterfaces 设置监听接口
                // 格式: "0.0.0.0:6881" 或 "[::]:6881"
                Method listenInterfaces = SessionManager.class.getMethod("listenInterfaces", String.class);
                String interfaceStr = "0.0.0.0:6881-6899";
                listenInterfaces.invoke(sessionManager, interfaceStr);
                Log.i("LibTorrentWrapper", "监听端口已设置: " + interfaceStr);
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "listenInterfaces 设置失败", e);
                // 尝试旧的 listenOn 方法
                try {
                    Method listenOn = SessionManager.class.getMethod("listenOn", String.class, int.class);
                    int[] ports = (int[]) listenOn.invoke(sessionManager, "6881-6899", 100);
                    if (ports != null && ports.length >= 2) {
                        Log.i("LibTorrentWrapper", "监听端口已设置: " + ports[0] + "-" + ports[1]);
                    }
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "listenOn(String, int) 也不存在，尝试 listenOn(String)", e2);
                    try {
                        Method listenOn2 = SessionManager.class.getMethod("listenOn", String.class);
                        listenOn2.invoke(sessionManager, "6881-6899");
                        Log.i("LibTorrentWrapper", "监听端口已设置 (listenOn String)");
                    } catch (Exception e3) {
                        Log.w("LibTorrentWrapper", "listenOn 备选方法也失败", e3);
                    }
                }
            }

            // 在 startDht 之前添加节点
            addDhtNodes();

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

    /**
     * 添加公共 DHT 节点，加快 magnet 链接的 peer 发现速度
     */
    private void addDhtNodes() {
        // 扩展的公共 DHT 节点列表（更多节点 = 更快发现 peers）
        String[] dhtNodes = {
            // 主流通用节点
            "router.utorrent.com:6881",
            "router.bittorrent.com:6881",
            "dht.transmissionbt.com:6881",
            "dht.aelitis.com:6881",
            "dht.1ts.org:6881",
            // 额外节点
            "dht.ccc45.de:6881",
            "dht.aelitis.com:6881",
            "dht.bitcomet.org:6881",
            "dht.baka.pw:6881",
            "dhttracker.13hz.fr:6881",
            "dht.ficial.net:6881",
            "dhttracker.fatelore.net:6881",
            "dhttracker.gamecopyparty.com:6881",
            "dhttracker.hackinthebox.nl:6881",
            "dhttracker.ioniscs.com:6881",
            "dhttracker.jamendo.com:6881",
            "dhttracker.laware.org:6881",
            "dhttracker.magnatune.com:6881",
            "dhttracker.minestream.com:6881",
            "dhttracker.nyaatorrents.org:6881",
            "dhttracker.open.freeworld.it:6881",
            "dhttracker.p2pmafia.com:6881",
            "dhttracker.preciseden.com:6881",
            "dhttracker.saravideo.org:6881",
            "dhttracker.sonata-app.com:6881",
            "dhttracker.tank-s03.gntx.net:6881",
            "dhttracker.torrent.、愛:6881",
            "dht.udp.cn:6881",
            "dht.udp.work:6881",
            // 中国常用节点
            "dht.4 trackers.com:6881",
            "dht.5 trackers.com:6881",
            "dht.6 trackers.com:6881",
            "dht.7 trackers.com:6881",
            "dht.8 trackers.com:6881",
            "dht.9 trackers.com:6881",
            "dht.10 trackers.com:6881"
        };

        try {
            // 尝试使用 addDhtNode 方法
            Method addDhtNodeMethod = SessionManager.class.getMethod("addDhtNode", String.class, int.class);
            if (addDhtNodeMethod != null) {
                for (String node : dhtNodes) {
                    try {
                        String[] parts = node.split(":");
                        if (parts.length == 2) {
                            String host = parts[0];
                            int port = Integer.parseInt(parts[1]);
                            addDhtNodeMethod.invoke(sessionManager, host, port);
                            Log.i("LibTorrentWrapper", "添加 DHT 节点: " + node);
                        }
                    } catch (Exception e) {
                        Log.w("LibTorrentWrapper", "添加 DHT 节点失败: " + node, e);
                    }
                }
            } else {
                Log.w("LibTorrentWrapper", "addDhtNode 方法不可用");
                // 尝试使用备选方法 - 通过 URI 添加
                try {
                    Method addDhtNodeUri = SessionManager.class.getMethod("addDhtNode", String.class);
                    for (String node : dhtNodes) {
                        try {
                            addDhtNodeUri.invoke(sessionManager, node);
                            Log.i("LibTorrentWrapper", "添加 DHT 节点(URI): " + node);
                        } catch (Exception e) {
                            Log.w("LibTorrentWrapper", "添加 DHT 节点失败: " + node, e);
                        }
                    }
                } catch (Exception e2) {
                    Log.e("LibTorrentWrapper", "addDhtNode URI 方法也不可用", e2);
                }
            }
        } catch (Exception e) {
            Log.e("LibTorrentWrapper", "addDhtNodes 失败", e);
        }

        // 同时使用 announce 来加快 peer 发现
        try {
            Method dhtAnnounceMethod = SessionManager.class.getMethod("dhtAnnounce", String.class, int.class);
            for (String node : dhtNodes) {
                try {
                    String[] parts = node.split(":");
                    if (parts.length == 2) {
                        dhtAnnounceMethod.invoke(sessionManager, parts[0], Integer.parseInt(parts[1]));
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        } catch (Exception e) {
            Log.w("LibTorrentWrapper", "dhtAnnounce 方法不可用", e);
        }
    }

    private void configureSession() {
        try {
            // 设置下载速度限制 (0 = 无限制，单位 bytes/s)
            try {
                Method setDownloadRateLimit = SessionManager.class.getMethod("setDownloadRateLimit", long.class);
                setDownloadRateLimit.invoke(sessionManager, 0L);
                Log.i("LibTorrentWrapper", "下载速度限制已设置");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setDownloadRateLimit 不存在，尝试 downloadRateLimit", e);
                try {
                    Method m = SessionManager.class.getMethod("downloadRateLimit", long.class);
                    m.invoke(sessionManager, 0L);
                    Log.i("LibTorrentWrapper", "downloadRateLimit 已设置");
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "downloadRateLimit 也不存在", e2);
                }
            }

            // 设置上传速度限制 (0 = 无限制)
            try {
                Method setUploadRateLimit = SessionManager.class.getMethod("setUploadRateLimit", long.class);
                setUploadRateLimit.invoke(sessionManager, 0L);
                Log.i("LibTorrentWrapper", "上传速度限制已设置");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setUploadRateLimit 不存在，尝试 uploadRateLimit", e);
                try {
                    Method m = SessionManager.class.getMethod("uploadRateLimit", long.class);
                    m.invoke(sessionManager, 0L);
                    Log.i("LibTorrentWrapper", "uploadRateLimit 已设置");
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "uploadRateLimit 也不存在", e2);
                }
            }

            // 设置最大连接数 (libtorrent4j 2.1.0 使用 maxConnections)
            try {
                Method setMaxConnections = SessionManager.class.getMethod("maxConnections", int.class);
                setMaxConnections.invoke(sessionManager, 500);
                Log.i("LibTorrentWrapper", "最大连接数已设置为 500");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "maxConnections 不存在，尝试 setMaxConnections", e);
                try {
                    Method m = SessionManager.class.getMethod("setMaxConnections", int.class);
                    m.invoke(sessionManager, 500);
                    Log.i("LibTorrentWrapper", "setMaxConnections 已设置");
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "setMaxConnections 也不存在", e2);
                }
            }

            // 设置最大 peers 数 (libtorrent4j 2.1.0 使用 maxPeers)
            try {
                Method setMaxPeers = SessionManager.class.getMethod("maxPeers", int.class);
                setMaxPeers.invoke(sessionManager, 200);
                Log.i("LibTorrentWrapper", "最大 peers 数已设置为 200");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "maxPeers 不存在，尝试 setMaxPeers", e);
                try {
                    Method m = SessionManager.class.getMethod("setMaxPeers", int.class);
                    m.invoke(sessionManager, 200);
                    Log.i("LibTorrentWrapper", "setMaxPeers 已设置");
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "setMaxPeers 也不存在", e2);
                }
            }

            // 设置每个 torrent 的最大 peers
            try {
                Method setMaxPeersPerTorrent = SessionManager.class.getMethod("maxPeersPerTorrent", int.class);
                setMaxPeersPerTorrent.invoke(sessionManager, 100);
                Log.i("LibTorrentWrapper", "每个 torrent 最大 peers 已设置为 100");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "maxPeersPerTorrent 不存在，尝试 setMaxPeersPerTorrent", e);
                try {
                    Method m = SessionManager.class.getMethod("setMaxPeersPerTorrent", int.class);
                    m.invoke(sessionManager, 100);
                    Log.i("LibTorrentWrapper", "setMaxPeersPerTorrent 已设置");
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "setMaxPeersPerTorrent 也不存在", e2);
                }
            }

            // 设置活动下载数 (libtorrent4j 2.1.0 使用 maxActiveDownloads)
            try {
                Method setActiveDownloads = SessionManager.class.getMethod("maxActiveDownloads", int.class);
                setActiveDownloads.invoke(sessionManager, 10);
                Log.i("LibTorrentWrapper", "活动下载数已设置为 10");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "maxActiveDownloads 不存在，尝试 setActiveDownloads", e);
                try {
                    Method m = SessionManager.class.getMethod("setActiveDownloads", int.class);
                    m.invoke(sessionManager, 10);
                    Log.i("LibTorrentWrapper", "setActiveDownloads 已设置");
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "setActiveDownloads 也不存在", e2);
                }
            }

            // 设置活动 seeds 数 (libtorrent4j 2.1.0 使用 maxActiveSeeds)
            try {
                Method setActiveSeeds = SessionManager.class.getMethod("maxActiveSeeds", int.class);
                setActiveSeeds.invoke(sessionManager, 10);
                Log.i("LibTorrentWrapper", "活动 seeds 数已设置为 10");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "maxActiveSeeds 不存在，尝试 setActiveSeeds", e);
                try {
                    Method m = SessionManager.class.getMethod("setActiveSeeds", int.class);
                    m.invoke(sessionManager, 10);
                    Log.i("LibTorrentWrapper", "setActiveSeeds 已设置");
                } catch (Exception e2) {
                    Log.w("LibTorrentWrapper", "setActiveSeeds 也不存在", e2);
                }
            }

            // 设置主动管理时间（缩短，加快调度）
            try {
                Method setAutoManageTime = SessionManager.class.getMethod("setAutoManageTime", int.class);
                setAutoManageTime.invoke(sessionManager, 100);
                Log.i("LibTorrentWrapper", "主动管理时间已设置为 100ms");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setAutoManageTime 不存在", e);
            }

            // 设置连接超时缩短
            try {
                Method setConnectTimeout = SessionManager.class.getMethod("setConnectTimeout", int.class);
                setConnectTimeout.invoke(sessionManager, 5);
                Log.i("LibTorrentWrapper", "连接超时已设置为 5 秒");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setConnectTimeout 不存在", e);
            }

            // 设置 peer 连接超时
            try {
                Method setPeerTimeout = SessionManager.class.getMethod("setPeerTimeout", int.class);
                setPeerTimeout.invoke(sessionManager, 5);
                Log.i("LibTorrentWrapper", "peer 超时已设置为 5 秒");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setPeerTimeout 不存在", e);
            }

            // 启用 PeX (Peer Exchange) - 加速 peer 发现，不要禁用！
            try {
                Method enablePeX = SessionManager.class.getMethod("enablePeX");
                enablePeX.invoke(sessionManager);
                Log.i("LibTorrentWrapper", "PeX 已启用");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "enablePeX 不存在", e);
            }

            // 启用 LSD (Local Service Discovery) - 局域网 peer 发现
            try {
                Method startLsd = SessionManager.class.getMethod("startLsd");
                startLsd.invoke(sessionManager);
                Log.i("LibTorrentWrapper", "LSD 已启用");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "startLsd 不存在", e);
            }

            // 启用 UPnP 端口映射
            try {
                Method startUpnp = SessionManager.class.getMethod("startUpnp");
                startUpnp.invoke(sessionManager);
                Log.i("LibTorrentWrapper", "UPnP 已启用");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "startUpnp 不存在", e);
            }

            // 启用 NAT-PMP 端口映射
            try {
                Method startNatpmp = SessionManager.class.getMethod("startNatpmp");
                startNatpmp.invoke(sessionManager);
                Log.i("LibTorrentWrapper", "NAT-PMP 已启用");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "startNatpmp 不存在", e);
            }

            // 设置 cache 大小（单位 KB）
            try {
                Method setCacheSize = SessionManager.class.getMethod("setCacheSize", int.class);
                setCacheSize.invoke(sessionManager, 1024 * 1024); // 1GB cache
                Log.i("LibTorrentWrapper", "Cache 大小已设置为 1GB");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setCacheSize 不存在", e);
            }

            // 设置 cache 过期时间
            try {
                Method setCacheExpiry = SessionManager.class.getMethod("setCacheExpiry", int.class);
                setCacheExpiry.invoke(sessionManager, 60); // 60 秒
                Log.i("LibTorrentWrapper", "Cache 过期时间已设置为 60 秒");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setCacheExpiry 不存在", e);
            }

            // 禁用缓洪控制，提高下载速度
            try {
                Method setIgnoreLimits = SessionManager.class.getMethod("setIgnoreLimits", boolean.class);
                setIgnoreLimits.invoke(sessionManager, true);
                Log.i("LibTorrentWrapper", "已禁用缓洪限制");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "setIgnoreLimits 不存在", e);
            }

            // 增加连接数限制（高速下载需要更多连接）
            try {
                Method m = SessionManager.class.getMethod("maxConnections", int.class);
                m.invoke(sessionManager, 1000);
                Log.i("LibTorrentWrapper", "最大连接数已设置为 1000");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "maxConnections 设置失败", e);
            }

            // 增加 peer 连接数限制
            try {
                Method m = SessionManager.class.getMethod("maxPeers", int.class);
                m.invoke(sessionManager, 500);
                Log.i("LibTorrentWrapper", "最大 peers 已设置为 500");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "maxPeers 设置失败", e);
            }

            // 设置 tracker 超时缩短（加快 tracker 响应）
            try {
                Method m = SessionManager.class.getMethod("trackerBackoff", int.class);
                m.invoke(sessionManager, 5);
                Log.i("LibTorrentWrapper", "trackerBackoff 已设置");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "trackerBackoff 不存在", e);
            }

            // 启用 IP 过滤（禁用）
            try {
                Method disableIPFiltering = SessionManager.class.getMethod("disableIPFiltering");
                disableIPFiltering.invoke(sessionManager);
                Log.i("LibTorrentWrapper", "IP 过滤已禁用");
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "disableIPFiltering 不存在", e);
            }

            Log.i("LibTorrentWrapper", "Session 配置完成");
        } catch (Exception e) {
            Log.e("LibTorrentWrapper", "Session 配置失败", e);
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
                    Thread.sleep(500);  // 500ms 轮询间隔，加快状态更新
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
            for (Map.Entry<String, Sha1Hash> entry : taskIdToInfoHash.entrySet()) {
                String taskId = entry.getKey();
                Sha1Hash infoHash = entry.getValue();
                TorrentHandle handle = sessionManager.find(infoHash);
                if (handle != null && handle.isValid()) {
                    updateTorrentStatus(handle);
                } else {
                    Timber.d("LibTorrentWrapper: pollStatus - taskId=" + taskId + " 未找到有效 handle");
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

        // 如果任务是手动暂停的，不更新状态
        if (pausedTasks.contains(taskId)) {
            return;
        }

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

            // 调试：打印 peers 连接信息
            int numPeers = status.numPeers();
            int numSeeds = status.numSeeds();
            long downloadRate = status.downloadRate();
            Log.d("LibTorrentWrapper", "taskId=" + taskId + ", state=" + state +
                    ", peers=" + numPeers + ", seeds=" + numSeeds +
                    ", downloadRate=" + downloadRate + " bytes/s, progress=" + (status.progress() * 100) + "%");

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

            // 尝试获取 tracker 列表
            try {
                java.util.List<String> trackers = new java.util.ArrayList<>();
                // 遍历 tracker_urls() 或类似方法
                Method trackersMethod = TorrentInfo.class.getMethod("trackers");
                Object trackerObj = trackersMethod.invoke(torrentInfo);
                if (trackerObj instanceof Iterable) {
                    for (Object t : (Iterable<?>) trackerObj) {
                        Log.i("LibTorrentWrapper", "Tracker found: " + t);
                    }
                }
            } catch (Exception e) {
                Log.w("LibTorrentWrapper", "获取 tracker 列表失败", e);
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

            // 存储 infoHash 映射（在调用 download 之前，以便 pollStatus 能找到）
            if (infoHashStr != null) {
                try {
                    Sha1Hash infoHash = Sha1Hash.parseHex(infoHashStr);
                    taskIdToInfoHash.put(taskId, infoHash);
                    Log.i("LibTorrentWrapper", "infoHash 映射已存储 - taskId=" + taskId);

                    // 验证是否能找到
                    TorrentHandle handle = sessionManager.find(infoHash);
                    Log.i("LibTorrentWrapper", "添加前查找 handle - " + (handle != null ? "找到" : "未找到"));
                } catch (Exception e) {
                    Log.w("LibTorrentWrapper", "解析 infoHash 失败", e);
                }
            }

            // 使用 libtorrent4j 2.1.0 正确的 API
            // SessionManager.download(String url, File saveLocation, torrent_flags_t flags)
            torrent_flags_t flags = new torrent_flags_t();
            Log.i("LibTorrentWrapper", "调用 sessionManager.download(magnetLink, saveDir, flags)");
            sessionManager.download(magnetLink, saveDir, flags);
            Log.i("LibTorrentWrapper", "download 方法调用成功");

            // 等待 torrent 被添加到 session 并自动开始下载
            // magnet 需要先下载 metadata，所以需要等待一段时间
            Thread.sleep(1000);  // 等待 1 秒让 metadata 开始下载
            Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
            if (infoHash != null) {
                TorrentHandle handle = sessionManager.find(infoHash);
                if (handle != null && handle.isValid()) {
                    try {
                        // 设置上传和下载限制为无限制
                        handle.setDownloadLimit(0);
                        handle.setUploadLimit(0);
                        Log.i("LibTorrentWrapper", "torrent 速度限制已设置");

                        // 强制开始下载
                        handle.resume();
                        Log.i("LibTorrentWrapper", "torrent resume 已调用");
                    } catch (Exception e) {
                        Log.w("LibTorrentWrapper", "设置 torrent 参数失败", e);
                    }
                } else {
                    Log.w("LibTorrentWrapper", "找不到 torrent handle - " + taskId);
                }
            } else {
                Log.w("LibTorrentWrapper", "找不到 infoHash - " + taskId);
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
        Log.i("LibTorrentWrapper", ">>> pause - taskId=" + taskId + ", infoHash=" + infoHash);
        if (infoHash == null) {
            Log.w("LibTorrentWrapper", "pause - infoHash 为 null");
            return;
        }

        // 标记为手动暂停
        pausedTasks.add(taskId);

        TorrentHandle handle = sessionManager.find(infoHash);
        Log.i("LibTorrentWrapper", "pause - handle=" + handle + ", isValid=" + (handle != null ? handle.isValid() : "N/A"));
        if (handle != null && handle.isValid()) {
            try {
                handle.pause();
                Log.i("LibTorrentWrapper", "暂停成功 - " + taskId);
            } catch (Exception e) {
                Log.e("LibTorrentWrapper", "暂停异常", e);
            }
        } else {
            Log.w("LibTorrentWrapper", "暂停失败 - handle 无效或为 null - " + taskId);
        }
    }

    public void resume(String taskId) {
        Sha1Hash infoHash = taskIdToInfoHash.get(taskId);
        Log.i("LibTorrentWrapper", ">>> resume - taskId=" + taskId + ", infoHash=" + infoHash);

        // 移除手动暂停标记
        pausedTasks.remove(taskId);

        if (infoHash == null) {
            Log.w("LibTorrentWrapper", "resume - infoHash 为 null");
            return;
        }

        TorrentHandle handle = sessionManager.find(infoHash);
        Log.i("LibTorrentWrapper", "resume - handle=" + handle);
        if (handle != null && handle.isValid()) {
            try {
                handle.resume();
                Log.i("LibTorrentWrapper", "恢复成功 - " + taskId);
            } catch (Exception e) {
                Log.e("LibTorrentWrapper", "恢复异常", e);
            }
        } else {
            Log.w("LibTorrentWrapper", "恢复失败 - handle 无效或为 null - " + taskId);
        }
    }

    public void startDownload(String taskId) {
        resume(taskId);
    }

    public void remove(String taskId, boolean deleteFiles) {
        Sha1Hash infoHash = taskIdToInfoHash.remove(taskId);
        if (infoHash == null) return;

        // 清除暂停状态
        pausedTasks.remove(taskId);

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
        // 支持 40 字符 hex (base16) 和 32 字符 base32 编码的 infoHash
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("urn:btih:([a-zA-Z0-9]+)");
        java.util.regex.Matcher matcher = pattern.matcher(magnetLink);
        if (matcher.find()) {
            String hash = matcher.group(1).toLowerCase();
            // 如果是 32 字符的 base32 编码，需要解码为 40 字符的 hex
            if (hash.length() == 32) {
                try {
                    hash = base32ToHex(hash);
                } catch (Exception e) {
                    Log.w("LibTorrentWrapper", "base32 解码失败", e);
                }
            }
            return hash;
        }
        return null;
    }

    private String base32ToHex(String base32) {
        // 标准 Base32 解码 (RFC 4648)
        String base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder binary = new StringBuilder();
        for (char c : base32.toUpperCase().toCharArray()) {
            int val = base32Chars.indexOf(c);
            if (val < 0) {
                // 跳过填充字符 '='
                continue;
            }
            String binaryStr = Integer.toBinaryString(val);
            while (binaryStr.length() < 5) binaryStr = "0" + binaryStr;
            binary.append(binaryStr);
        }
        // 移除填充的 0
        while (binary.length() % 8 != 0) {
            if (binary.length() > 0) {
                binary.deleteCharAt(binary.length() - 1);
            }
        }
        // 转换为 hex
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < binary.length(); i += 8) {
            String byteStr = binary.substring(i, Math.min(i + 8, binary.length()));
            int byteVal = Integer.parseInt(byteStr, 2);
            hex.append(String.format("%02x", byteVal));
        }
        return hex.toString();
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
