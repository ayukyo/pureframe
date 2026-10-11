# 纯帧 - PureFrame

> 纯粹观影，只留帧影 · *Pure viewing, frame by frame*

简体中文 | [English](./README_EN.md)

**纯帧** 是一款文艺极简的 Android 本地视频播放器，支持磁力 / 直链下载与边下边播。

- **无广告** - 零推广、零打扰
- **纯黑沉浸** - 高级感观影体验
- **极简设计** - 干净克制的界面风格

## ✨ 核心功能

### 本地视频播放
- 自动扫描本地视频（mp4 / mkv / mov / avi / flv / ts 等），使用细粒度媒体权限（`READ_MEDIA_VIDEO`），不索取「所有文件访问」；可在「设置 → 视频库」中添加额外扫描目录
- 三种解码方式：**硬解**（硬件加速）/ **软解**（软件解码，扩展支持 DTS/AC3/EAC3 音轨与 H.264/HEVC/VP9）/ **自动**（优先硬解，失败后回退软解）
- 倍速播放 0.5x ~ 3x，支持预设档位与自定义速度
- 手势调节亮度、音量、进度；双击播放 / 暂停；快退 / 快进 10 秒
- 全屏沉浸播放，edge-to-edge 适配；画面比例可选「自动（不裁切）/ 填充（铺满屏幕）」
- 画中画（PiP），可设置「回桌面时自动进入系统画中画」
- 播放锁屏：一键锁定控制栏，避免误触
- 自动记忆播放进度，重新打开时给出续播提示；支持循环播放
- **侧载字幕**：自动加载视频同目录同名 `.srt` / `.ass` / `.ssa` / `.vtt` 字幕，半透明底色，设置页「显示字幕」开关实时生效
- **同目录队列连播**：按自然排序自动衔接下一集，通知栏可直接上 / 下曲切换
- 媒体库分「全部 / 收藏 / 最近」三个筛选，支持按名称搜索
- 视频条目长按菜单：播放 / 详情 / 删除记录 / 删除文件；详情页展示大小、时长、分辨率、格式、播放次数、最近播放与路径
- 收藏管理：播放页与列表均可一键收藏
- 排序：日期 / 大小 / 时长一键切换，偏好持久化
- 主题：浅色 / 深色 / 跟随系统；应用语言可切换简体中文 / English / 跟随系统

### 投屏
- **DLNA / UPnP**：自动发现局域网内可投屏设备，推送视频到电视、盒子播放，支持音量与进度控制
  - 按设备描述中的 `AVTransport` 能力过滤，路由器、NAS 等媒体服务器不会混入列表
  - 连接过程显示「连接中…」，失败时在设备面板内直接提示并可重试
  - 投屏中本机进度条实时跟随远端播放进度，播完自动断开
  - 可开启「自动连接上次投屏设备」
- **Google Cast**：原生支持 Chromecast 与内置投屏接收器设备
- 无 GMS 设备自动降级，不影响其他功能

### 磁力 / 直链下载
- 支持三种链接：`magnet:` 磁链、`.torrent` 种子文件（含种子文件直链），以及 HTTP / HTTPS 直链下载
- 种子任务可选择需要下载的文件（种子内文件选择）
- 多任务并行下载（1 ~ 5 可配），支持暂停、继续、删除（可选是否同时删除文件），以及批量清理已完成记录
- 自定义保存目录（SAF 目录选择器），仅 Wi-Fi 下载可选
- 后台下载服务，通知栏显示进度

### 边下边播（核心亮点）
- 下载到可播放阈值（约 10%）即可播放；下载列表中标有「✓ 可边下边播」并可一键播放
- 仅允许在已下载区域内拖动进度，界面显示「已缓存 XX%」
- 播放不影响下载速度

## 🛠️ 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| 架构 | MVVM + Hilt + Room + DataStore |
| UI | Jetpack Compose (Material3) |
| 播放器 | Media3 / ExoPlayer（内置 NextLib FFmpeg 软解扩展） |
| 下载引擎 | libtorrent4j |
| 投屏 | UPnPCast（DLNA / UPnP）+ Media3 Cast |
| 编译 / 目标 | compileSdk & targetSdk 36，最低支持 Android 8.0 (API 26) |

## 🔄 自动构建

配置了 GitHub Actions CI（`.github/workflows/`）：

- push 到 main 自动触发快照构建，产出以 commit 命名的快照 Release
- 打 `v*` 语义化版本 tag 触发正式 Release：debug / release 签名 APK + AAB + SHA256SUMS 校验文件
- Release 成功后自动更新自建 F-Droid 仓库索引

## 📦 项目结构

```
pureframe/
├── code/              # 源代码（Gradle 根）
│   └── app/           # Android 应用模块
├── docs/              # 需求、技术设计与分发相关文档
├── fastlane/          # 应用商店元数据
├── LICENSE            # GPL-3.0
├── NOTICE             # 第三方组件许可声明
├── CONTRIBUTING.md    # 贡献指南
├── SECURITY.md        # 安全策略
└── DISCLAIMER.md      # 免责声明
```

## 📥 下载安装

- **GitHub Releases**：[Latest release](https://github.com/ayukyo/pureframe/releases/latest) 下载 APK 直接安装（附 SHA256SUMS 校验文件）
- **F-Droid 自建仓库**：在 F-Droid 客户端「设置 → 仓库」添加
  - 仓库地址：`https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F`
  - （也可只填 `https://ayukyo.github.io/pureframe/repo/`，然后核对指纹为上述值）
- **Obtainium**：添加本仓库地址 `https://github.com/ayukyo/pureframe` 即可自动跟进更新

## 🤝 参与贡献

欢迎 Issue 与 PR，请先阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)。如果觉得好用，欢迎去 [Releases 页面](https://github.com/ayukyo/pureframe/releases/latest) 点个 Star ⭐

## 🔒 隐私

**零收集、零追踪、零广告** —— 详见[隐私政策](./docs/privacy-policy.md)。应用内也可随时查看（设置 → 关于 → 隐私政策）。

## 📄 许可证

本项目基于 [GNU General Public License v3.0](./LICENSE) 开源。

- 选择 GPL-3.0 的原因：内置的 FFmpeg 软解扩展 [NextLib](https://github.com/anilbeesetti/nextlib) 以 GPL-3.0 发布，按许可证要求本项目整体以 GPL-3.0 分发
- 第三方组件及其许可声明见 [NOTICE](./NOTICE)，应用内「设置 → 关于 → 开源许可证」亦可查看
- **使用本软件前请阅读[免责声明](./DISCLAIMER.md)**：本软件仅为用户端工具，不提供、不存储、不传播任何内容；使用下载功能的合法性由使用者自行负责。

---

**Slogan**: 纯粹观影，只留帧影
