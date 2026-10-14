# 纯帧 - PureFrame

> 纯粹观影，只留帧影 · *Pure viewing, frame by frame*

简体中文 | [English](./README_EN.md)

**纯帧** 是一款文艺极简的 Android 本地视频播放器，支持磁力 / 直链下载与边下边播。

[![Latest Release](https://img.shields.io/github/v/release/ayukyo/pureframe?label=%E6%9C%80%E6%96%B0%E7%89%88&color=white)](https://github.com/ayukyo/pureframe/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-white.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg)](https://github.com/ayukyo/pureframe/releases/latest)
[![F-Droid](https://img.shields.io/badge/F--Droid-%E8%87%AA%E5%BB%BA%E4%BB%93%E5%BA%93-1976D2.svg)](https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F)
[![GitHub Stars](https://img.shields.io/github/stars/ayukyo/pureframe?style=flat&color=F5C518)](https://github.com/ayukyo/pureframe/stargazers)

- **无广告** - 零推广、零打扰
- **纯黑沉浸** - 高级感观影体验
- **极简设计** - 干净克制的界面风格

<p align="center">
  <img src="https://ayukyo.github.io/pureframe/screenshots/library.png" width="250" alt="媒体库"/>
  &nbsp;
  <img src="https://ayukyo.github.io/pureframe/screenshots/player.png" width="250" alt="播放器"/>
  &nbsp;
  <img src="https://ayukyo.github.io/pureframe/screenshots/settings.png" width="250" alt="设置"/>
</p>

## ✨ 核心功能

### 本地视频播放
- 自动扫描本地视频（mp4 / mkv / mov 等），细粒度媒体权限，不索取「所有文件访问」
- 硬解 / 软解 / 自动三档解码（软解扩展 DTS/AC3/EAC3 音轨与 H.264/HEVC/VP9），倍速 0.5x ~ 3x
- 手势调节亮度、音量、进度；也可切换为**上下滑切换视频**的短视频式交互，播完自动连播
- 自动记忆播放进度、循环播放、播放锁屏、画中画、画面比例调节
- 侧载字幕：自动加载同目录同名 `.srt` / `.ass` / `.ssa` / `.vtt` 字幕
- 同目录队列连播：按自然排序自动衔接下一集，通知栏可直接上 / 下曲切换
- 收藏、搜索、筛选、排序，偏好持久化

### 投屏
- **DLNA / UPnP**：自动发现局域网内可投屏设备（路由器、NAS 不混入），推送视频到电视、盒子播放，支持音量与进度控制；连接失败有明确提示，投屏中本机进度跟随远端
- **Google Cast**：原生支持 Chromecast 与内置投屏接收器设备
- 播完自动断开；无 GMS 设备自动降级，不影响其他功能

### 下载
- 支持 `magnet:` 磁链、`.torrent` 种子文件（含直链）与 HTTP / HTTPS 直链
- 多任务并行下载（1 ~ 5 可配），暂停、继续、删除、种子内文件选择
- 自定义保存目录（SAF），仅 Wi-Fi 下载可选，后台下载 + 通知栏进度

### 边下边播（核心亮点）
- 下载到可播放阈值（约 10%）即可播放
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
| 系统要求 | Android 8.0 (API 26) 及以上 |

## 📦 下载安装

- **GitHub Releases**：[Latest release](https://github.com/ayukyo/pureframe/releases/latest) 下载 APK 直接安装（附 SHA256SUMS 校验文件）
- **F-Droid 自建仓库**：在 F-Droid 客户端「设置 → 仓库」添加
  - 仓库地址：`https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F`
  - （也可只填 `https://ayukyo.github.io/pureframe/repo/`，然后核对指纹为上述值）
- **Obtainium**：添加本仓库地址 `https://github.com/ayukyo/pureframe` 即可自动跟进更新

版本更新记录见 [CHANGELOG.md](./CHANGELOG.md)。

## 🤝 参与贡献

欢迎 Issue 与 PR，请先阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)。如果觉得好用，欢迎去 [Releases 页面](https://github.com/ayukyo/pureframe/releases/latest) 点个 Star ⭐

## 🔒 隐私

**零收集、零追踪、零广告** —— 详见[隐私政策](./docs/privacy-policy.md)。应用内也可随时查看（设置 → 关于 → 隐私政策）。

## 📄 许可证

本项目基于 [GNU General Public License v3.0](./LICENSE) 开源。

- 选择 GPL-3.0 的原因：内置的 FFmpeg 软解扩展 [NextLib](https://github.com/anilbeesetti/nextlib) 以 GPL-3.0 发布，按许可证要求本项目整体以 GPL-3.0 分发
- 第三方组件及其许可声明见 [NOTICE](./NOTICE)
- **使用本软件前请阅读[免责声明](./DISCLAIMER.md)**：本软件仅为用户端工具，不提供、不存储、不传播任何内容；使用下载功能的合法性由使用者自行负责。

---

**Slogan**: 纯粹观影，只留帧影
