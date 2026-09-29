# PureFrame 投屏功能技术方案

> 2026-09-29 起草。目标：支持国内外投屏（Google Cast + DLNA 双栈），本地视频/边下边播流均可投到电视。

## 一、总体架构

```
┌─────────────────────────────────────────────────┐
│ PlayerScreen（播放页）                            │
│   └─ CastButton（投屏入口）→ CastDevicePicker    │
│        ├─ Google Cast 设备（CastContext 扫描）    │
│        └─ DLNA 设备（SSDP 扫描）                  │
└────────────────┬────────────────────────────────┘
                 │ 统一 CastController 接口
       ┌─────────┴──────────┐
       ▼                    ▼
┌───────────────┐   ┌─────────────────────┐
│ CastRoute     │   │ DlnaRoute           │
│ (Media3 Cast) │   │ (UPnPCast 库)       │
│ CastPlayer 接 │   │ AVTransport SOAP    │
│ 管本地+远程    │   │ + 本机 HTTP 文件服务 │
└───────────────┘   └─────────────────────┘
                 │
                 ▼
     ContentUrlProvider（关键抽象）
     本地文件 → http://<本机IP>:<port>/video/<id>
     BT 流   → 复用 StreamProxyServer 输出 URL
```

**核心设计**：不管哪条投屏路线，电视端拿到的都是一个 HTTP URL。本地文件和 BT 未完成下载的流，统一通过本机 HTTP 服务对外提供。已下载完成的文件可直接 file server 静态服务；BT 边下边播复用现有 `StreamProxyServer`（它已实现 Range/seek 语义，正好满足电视端拖动进度条的需求）。

## 二、技术选型（已核实）

### Google Cast 线：Media3 CastPlayer

- **必须用 `androidx.media3:media3-cast`**（旧版 Cast SDK 已进入维护模式，Google 官方明确新项目用 Media3）
- 关键约束：**media3-cast 要求 Media3 ≥ 1.9.0**；我们当前是 **1.2.0，需要升级**。升级风险评估：1.2.0 → 1.9+ 跨度较大，PlayerManager/PlayerScreen 需回归（这正是我们做过 7 台设备矩阵测试的模块，回归成本可控）
- CastPlayer 设计为「本地+远程统一 Player」：`CastPlayer.Builder(context).setLocalPlayer(exoPlayer).build()`，投屏/退出投屏自动在本地和远程间迁移播放状态，天然契合现有 PlayerManager 单例架构
- 默认 receiver app 即可播本地 mp4/mkv（webm/h264/aac 等），无需自建 Cast Receiver；后续如需投字幕/自定义 UI 再注册自定义 receiver
- minSdk 26 满足要求（Play Services 设备上可用；国内无 GMS 的设备走 DLNA 线）

### DLNA 线：UPnPCast 库（yinnho/UPnPCast v1.3.0）

选它而非 Cling/jUPnP 的理由：

| 候选 | 状态 | 结论 |
|------|------|------|
| Cling 2.1.1 | 停更多年，META-INF 冲突需手工排除 | 不选 |
| jUPnP 3.x | Cling 社区 fork，活跃但 Android 实战案例少 | 备选 |
| DM-UPnP | 活跃，LGPL 协议 + minSdk 26 | 备选（协议需注意） |
| **UPnPCast v1.3.0** | **2026-09 仍在活跃维护，MIT 协议，Kotlin coroutine API，92 个单测，JitPack 分发** | **选用** |

- 能力覆盖我们全部需求：SSDP 发现（含 MulticastLock、1900 端口占用回退）、AVTransport 推流/播放/暂停/seek/音量、**内置本地 HTTP file server（Range/seek 支持）**、外挂字幕（含三星 sec:SubtitleUri）
- API 是纯 suspend 函数，和我们的协程风格匹配；依赖极轻（仅 NanoHTTPD）
- 注意点：JitPack 仓库需要加入 settings.gradle；`DLNACast.init/cleanup` 生命周期要挂在 Application 级或 Service 级而非 Activity（避免旋转重建反复初始化）

## 三、与现有代码的对接点

| 现有模块 | 对接方式 |
|----------|----------|
| `PlayerManager.kt`（单例 ExoPlayer 封装） | Cast 线：把 ExoPlayer 包进 CastPlayer 后，PlayerManager 对外接口不变（都是 Player 接口），改动最小 |
| `PlaybackService.kt`（MediaSessionService） | Cast 线：MediaSession 的 player 换成 CastPlayer；DLNA 线：投屏后本地 player 释放/暂停，由 DlnaRoute 接管进度轮询 |
| `StreamProxyServer.kt`（BT 边下边播） | 直接复用：把它的监听 URL 交给电视端（局域网可达），BT 流投屏不要求下载完成 |
| `PlayerScreen.kt` 控制条 | 增加投屏按钮；投屏中显示远程进度（DLNA 线轮询 getProgress，Cast 线 CastPlayer 自带状态） |
| 手势/倍速/字幕 | 投屏后本地手势失效（内容不在本机播）；倍速/音量通过 Cast/DLNA 控制协议下发；侧载字幕 Cast 线走 TextTrack、DLNA 线走 subtitleUri |
| `FloatingVideoService` 悬浮窗 | 投屏中与悬浮窗互斥（同一内容两处播没意义），投屏时自动关悬浮窗 |

## 四、实施拆解（建议三个 PR）

### PR1：DLNA 投屏（国内优先，价值最高）
1. 接入 UPnPCast（JitPack + 依赖 + proguard keep 规则）
2. `ContentUrlProvider`：本机 HTTP file server 承载本地文件 URL（UPnPCast 自带 file server 可直接用；或统一走我们自己的轻量 server，二选一，倾向后者减少进程内 server 数量）
3. `DlnaRoute` + `CastController` 抽象：搜索/连接/播放/暂停/seek/断开
4. `CastDevicePicker` 底部弹层（Compose）：设备列表 + 连接状态
5. PlayerScreen 投屏按钮 + 投屏中 UI（远程进度条、断开按钮）
6. Settings 增加「投屏」分区（默认画质/自动连接上次设备开关）

### PR2：Google Cast 投屏（出海）
1. Media3 1.2.0 → 1.9+ 升级 + 全量回归（重点：PlayerManager、PlayerScreen、PiP、悬浮窗、后台播放）
2. media3-cast 接入：manifest OptionsProvider、CastPlayer 替换 MediaSession player、MediaRouteButton
3. 与 CastController 抽象对接（CastRoute）
4. 本地文件 URL 同样走 ContentUrlProvider（default receiver 要求 HTTP URL，不能 file://）

### PR3：体验打磨
1. BT 流投屏（StreamProxyServer URL 下发 + 电视端拖动 seek 映射到 torrent 顺序下载优先级——二期先不做优先级调整，纯 seek）
2. 投屏续播：切换设备时进度跟随
3. 字幕投递、音频轨兼容性提示（电视解不了的 codec 给出明确报错而非黑屏）
4. 多设备矩阵回归（模拟器跑不了 Cast/DLNA，需真机 + 实体电视/盒子验证）

## 五、风险与未决项

1. **Media3 大版本升级**是 PR2 的最大风险，建议 PR1 独立先行，不被升级阻塞
2. **电视端 codec 兼容**：DLNA 是把 URL 交给电视播，电视解不了的音轨/字幕轨会静默失败或无声，无法在 app 端转码（转码属后续可选功能：手机端 ffmpeg 软转码再推流，成本高、暂缓）
3. **运营商隔离网络/酒店 Wi-Fi**：AP 隔离下 DLNA/Cast 发现都会失效，属环境限制，UI 上要给出「请确认手机与电视在同一网络」的引导
4. **Android 13+ 附近设备权限**：`CHANGE_WIFI_MULTICAST_STATE` 无需运行时授权，但 Android 13 的 NEARBY_WIFI_DEVICES 可能触发系统提示，需真机验证
5. **商业闭环（买断制）本方案未覆盖**：投屏做完后另起讨论——Play 付费上架 or 免费+IAP，技术方案里预留了 Pro feature 开关位（`cast` 模块独立成 `:feature:cast`，便于将来加 license 校验层）

## 六、验收清单（草稿）

- [ ] DLNA：小米/海信电视 + 当贝盒子能发现并播放本地 mp4/mkv
- [ ] DLNA：BT 边下边播流可投屏，电视端可拖进度
- [ ] DLNA：外挂 .srt 字幕可投递（三星设备验证 sec:SubtitleUri）
- [ ] Cast：Chromecast/Android TV 播放本地文件，控制条状态实时同步
- [ ] 投屏中来电/断网/App 被杀的异常路径不崩溃
- [ ] 退出投屏回本地播放，进度无缝衔接
