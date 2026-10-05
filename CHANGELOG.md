# 更新日志 / Changelog

本文件记录用户可感知的变化。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

## [1.1.0] - 2026-10

### Changed
- **许可证更正：Apache-2.0 → GPL-3.0**。内置的 FFmpeg 软解扩展 NextLib 以 GPL-3.0 发布，
  按许可证要求本合并作品须整体以 GPL-3.0 分发（详见 NOTICE）
- targetSdk 34 → 36（Google Play 2026-08-31 起新应用强制要求；Android 15/16
  强制 edge-to-edge，应用已启用 enableEdgeToEdge + Compose insets 适配）

### Added
- 开源分发基础设施：语义化版本 tag（vX.Y.Z）触发正式 GitHub Release
  （含 SHA256SUMS 校验文件），快照构建流程保留
- 隐私政策文档（零收集 / 零追踪 / 零广告声明与权限用途说明）

## [1.0.0] - 2026-09

### Added
- 本地视频扫描与播放（硬解/软解、倍速、手势、进度记忆、收藏、排序）
- 磁力链接 / 种子文件下载（libtorrent4j），多任务并行、暂停恢复、文件选择
- 边下边播（StreamProxyServer 局部代理，限制未下载区域拖动）
- 全部文件访问权限的全盘视频扫描
- 自定义下载目录（SAF）、仅 Wi-Fi 下载、并行数配置
- 主题模式设置（初始版本深色为主）
- 本地视频播放自动加载同目录同名字幕（.srt/.ass/.ssa/.vtt），半透明底色样式
- 设置页「显示字幕」开关实时生效（无需重新进入播放页）
- 主题模式（浅色/深色/跟随系统）真正接入 MaterialTheme
- 仓库授权文件：LICENSE、NOTICE、免责声明

### Changed
- 播放页启用 edge-to-edge，全屏/非全屏下控制栏不再被系统栏遮挡
- 设置页移除无实际作用的「下载画质」选项

### Fixed
- 下载任务「更多」菜单弹出位置错误（原锚定到整行左上角）

[Unreleased]: https://github.com/ayukyo/pureframe/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/ayukyo/pureframe/releases/tag/v1.1.0
[1.0.0]: https://github.com/ayukyo/pureframe/releases/tag/v1.0.0
