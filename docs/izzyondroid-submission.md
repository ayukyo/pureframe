# IzzyOnDroid 提交指引（批次 B）

对应分发地图 Tier 1。元数据（fastlane 结构）已在仓库 `fastlane/` 就绪。

## 准入自查（对照官方 App Inclusion Policy）

| 要求 | 我们的状态 |
|------|-----------|
| 自由开源许可（OSI/FSF） | ✅ GPL-3.0 |
| 代码可公开访问 | ✅ GitHub（转 public 后） |
| 无追踪/广告模块 | ✅ 零收集 |
| APK 由 release key 签名，非 debuggable/testOnly | ✅ pureframe-release-*.apk |
| APK 挂在 GitHub tagged releases | ✅ v1.1.0 起 |
| 无自更新/下载可执行文件 | ✅ 无更新器 |
| usesCleartextTraffic | ⚠️ 提交前检查 manifest；播放器本地网络（DLNA/代理）场景政策明文允许，建议补 Network Security Config 说明 |
| 30MB 体积上限（rule-of-thumb） | ⚠️ 我们 ~92MB。政策允许大应用例外（播放器类常见豁免，如 VLC/mpv），须在 issue 中主动说明原因（FFmpeg 软解全编解码器 + libtorrent） |
| fastlane 元数据（短/长描述+图标+截图） | ✅ fastlane/metadata/android/（截图 3 张真机） |

## 提交步骤（需维护者 GitHub/GitLab 账号）

1. **确认仓库已转 public**（收录的前提）
2. 在 https://gitlab.com/IzzyOnDroid/repo/-/issues 新建 issue，标题：
   `Add app: PureFrame (ayukyo/pureframe)`
3. 正文模板：

```
Repo: https://github.com/ayukyo/pureframe
License: GPL-3.0
APKs: attached to tagged GitHub releases (e.g. v1.1.0 → pureframe-release-1.1.0.apk)
Fastlane metadata: present in repo (fastlane/metadata/android), en-US + zh-CN

App description:
PureFrame is a clean, local-first video player — hardware decoding with
FFmpeg software fallback (H.264/HEVC/VP9, DTS/AC3/EAC3), BT streaming
(libtorrent4j), DLNA + Google Cast, auto-loading external subtitles,
natural-ordered same-folder queue.

Notes:
- APK is ~92 MB (FFmpeg software decoding with full codec set + libtorrent).
  We understand the 30 MB rule of thumb and hope for an exception as seen
  with other media players; happy to discuss.
- Permission usage:
  - READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE: scan local videos (core feature)
  - MANAGE_EXTERNAL_STORAGE: optional full-device scan, app works without it
    (if we keep it; alternatively we drop it before submission — see checklist)
  - INTERNET: user-initiated downloads (BT/HTTP), DLNA/Cast streaming only
  - FOREGROUND_SERVICE / POST_NOTIFICATIONS: media playback notification
- No trackers, no analytics, no ads, no self-updater.
- usesCleartextTraffic (if present) is required for DLNA/UPnP and local HTTP
  streaming (media player home-network use case per your policy).
```

4. 提交前最后两个动作：
   - [ ] 决定 MEES 去留：**建议直接移除**（SAF + READ_MEDIA_VIDEO 已覆盖；去掉可少一轮问询）
   - [ ] `grep usesCleartextTraffic code/app/src/main/AndroidManifest.xml` 确认现状，
         若为 true 补一句 Network Security Config 或在 issue 说明
5. Izzy 审核通过后，把 badge 加进 README（assets 见 IzzyOnDroid wiki）

## 时间线

快则数天，慢则 1~2 周（含体积豁免讨论）。审核中 Izzy 会用 VirusTotal + 自有
扫描器检查 APK——零追踪零专有组件的包不会有障碍。
