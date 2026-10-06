# IzzyOnDroid 提交指引（批次 B）

对应分发地图 Tier 1。元数据（fastlane 结构）已在仓库 `fastlane/` 就绪。

## 准入自查（对照官方 App Inclusion Policy）

| 要求 | 我们的状态 |
|------|-----------|
| 自由开源许可（OSI/FSF） | ✅ GPL-3.0 |
| 代码可公开访问 | ✅ GitHub（转 public 后） |
| 无追踪/广告模块 | ✅ 零收集 |
| APK 由 release key 签名，非 debuggable/testOnly | ✅ pureframe-release-*.apk |
| APK 挂在 GitHub tagged releases | ✅ v1.1.0 起 || 无自更新/下载可执行文件 | ✅ 无更新器 |
| usesCleartextTraffic | ⚠️ 提交前检查 manifest；播放器本地网络（DLNA/代理）场景政策明文允许，建议补 Network Security Config 说明 |
| MEES（MANAGE_EXTERNAL_STORAGE） | ✅ 已移除（2026-10-06）：扫描走 READ_MEDIA_VIDEO，下载默认落 Movies/PureFrame 或应用私有目录，SAF 覆盖自定义目录——不再需要全盘访问，省一轮问询 |
| 30MB 体积上限（rule-of-thumb） | ⚠️ 我们 ~92MB。政策允许大应用例外（播放器类常见豁免，如 VLC/mpv），须在 issue 中主动说明原因（FFmpeg 软解全编解码器 + libtorrent） |
| 隐私政策 | ✅ https://ayukyo.github.io/pureframe/privacy-policy.html |
| fastlane 元数据（短/长描述+图标+截图） | ✅ fastlane/metadata/android/（截图 3 张真机） |

## 提交入口（2026-10-06 更新：已迁移至 Codeberg）

> ⚠️ **gitlab.com/IzzyOnDroid/repo 已归档**，提交入口迁移至 Codeberg：
> **https://codeberg.org/IzzyOnDroid/repodata** → New Issue，标题：
> `[AppRequest] PureFrame`
>
> 2026-10-06 尝试：gitlab.com 网页被 Cloudflare 人机验证拦截（自动化浏览器无法
> 通过），随后发现仓库已归档。Codeberg API 匿名 POST 需 token，故仍走浏览器
> 复制粘贴。**issue 正文最终版（按 Codeberg 官方模板重写，含 AI 声明）：
> `docs/izzyondroid-issue-final.txt`**，浏览器登录 codeberg.org 后复制粘贴
> 提交即可（约 2 分钟）。若用户有 Codeberg Personal Access Token（repo 权限），
> 也可由 agent 通过 API 直接代提。

<!-- 历史入口（已失效）：gitlab.com/IzzyOnDroid/repo/-/issues，
     标题 `Add app: PureFrame (ayukyo/pureframe)` -->

### Codeberg 模板要点（final.txt 已按此重写）

- 标题格式：`[AppRequest] AppName`
- Guidelines 四勾选（开发者本人 / 符合 Inclusion Policy / 未重复收录 / Fastlane 就绪）
- App meta-data：源码链接 / 其他商店链接 / License / Categories / Summary / Description
  （Description 末行附 Version / Package / License / Source / Signing key SHA256）
- Technical instructions：Build instructions（完整可复现命令）
- **AI Tools Usage（2026 起成为惯例，30 条新 issue 中绝大多数含此节）**：
  政策原文「vibe-coded apps will be rejected；LLM 用于研究/头脑风暴/调试等
  只读任务可接受，前提是输出不进代码」。我们按「Moderate 辅助 + 人类开发者
  审核全部产出 + 真机手动测试回归」如实声明，勾选 AI Accountability 两项。
  同类先例：OptiCast #659（同为视频播放器，Moderate 辅助，已收录）
- Further Notices：92MB 豁免说明（引用 OptiCast 先例）、权限用途、无 MEES、
  cleartext 说明、隐私政策 URL、自建 F-Droid 仓库指纹

正文模板（历史 GitLab 版，仅存档）：

```
Repo: https://github.com/ayukyo/pureframe
License: GPL-3.0
APKs: attached to tagged GitHub releases (e.g. v1.1.1 → pureframe-release-1.1.1.apk)
Fastlane metadata: present in repo (fastlane/metadata/android), en-US + zh-CN

App description:
PureFrame is a clean, local-first video player — hardware decoding with
FFmpeg software fallback (H.264/HEVC/VP9, DTS/AC3/EAC3), BT streaming
(libtorrent4j), DLNA + Google Cast, auto-loading external subtitles,
and a natural-ordered same-folder playback queue. No trackers, no ads,
no analytics.

Notes:
- APK is ~92 MB (FFmpeg software decoding with the full codec set +
  libtorrent). We understand the 30 MB rule of thumb and hope for an
  exception as seen with other media players; happy to discuss.
- Permission usage:
  - READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE (legacy, maxSdk 32): scan
    local videos (core feature). No MANAGE_EXTERNAL_STORAGE — removed
    in v1.1.1.
  - INTERNET: user-initiated downloads (BT/HTTP) and DLNA/Cast
    streaming on the home network only.
  - FOREGROUND_SERVICE (+ MEDIA_PLAYBACK / DATA_SYNC) /
    POST_NOTIFICATIONS: media playback notification and download tasks.
  - usesCleartextTraffic="true" is required for DLNA/UPnP and the local
    HTTP streaming proxy (media player home-network use case, allowed
    per your policy).
- No trackers, no analytics, no ads, no self-updater.
- Privacy policy: https://ayukyo.github.io/pureframe/privacy-policy.html
- Fastlane screenshots and metadata are in the repo; 1080x2340 phone
  screenshots (3) included.
- The app is also distributed via our self-hosted F-Droid repo
  (index fingerprint 466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F),
  which may be convenient for your build metadata checks.
```

> ⚠️ 以上为历史版本；**提交时以 `docs/izzyondroid-issue-final.txt` 为准**（v1.1.1 口径）。

4. 提交前最后两个动作：
   - [x] MEES 已移除（2026-10-06，见自查表）
   - [x] usesCleartextTraffic="true" 确认在 manifest（DLNA/本地串流需要，issue 里已说明）
5. Izzy 审核通过后，把 badge 加进 README（assets 见 IzzyOnDroid wiki）

## 时间线

快则数天，慢则 1~2 周（含体积豁免讨论）。审核中 Izzy 会用 VirusTotal + 自有
扫描器检查 APK——零追踪零专有组件的包不会有障碍。
