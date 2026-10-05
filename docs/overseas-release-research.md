# PureFrame 海外付费买断上架适配研究

> 调研日期：2026-10-04。范围：Google Play（主）+ Samsung/Amazon（备选），付费买断模式。
> 结论：**可以上架，但存在 1 个许可证战略决策 + 4 项技术适配 + 1 项账号流程门槛**。

---

## 〇、结论速览

| 类别 | 项目 | 状态 | 严重度 |
|---|---|---|---|
| 许可证 | nextlib GPL-3.0 传染 → 全 app 须 GPL-3.0 | **待决策** | 🔴 战略级 |
| 技术 | targetSdk 34 → 36（含 edge-to-edge 强制） | 未做 | 🔴 必做 |
| 技术 | SignatureGuard 与 Play App Signing 冲突（启动即死） | 未做 | 🔴 必做 |
| 技术 | AAB 产物（Play 只收 AAB） | 未做 | 🟡 必做 |
| 技术 | MANAGE_EXTERNAL_STORAGE 受限权限申报 | 未决策 | 🟡 建议移除 |
| 技术 | 16KB page size（64-bit） | **已合规** ✅ | — |
| 技术 | FGS type / READ_MEDIA_* / 64-bit / minSdk 26 | **已合规** ✅ | — |
| 流程 | 个人账号 12+ 测试者 × 14 天封闭测试 | 未启动 | 🟡 时间门槛 |
| 流程 | Data Safety 表 / 隐私政策 / IARC 分级 / 商店素材 | 未启动 | 🟢 常规 |
| 商业 | 付费买断无需 Billing Library，需商家账号 | 待开通 | 🟢 常规 |

---

## 一、许可证问题（最重要，先决策）

### 现状
`nextlib-media3ext`（ffmpeg 软解扩展，DTS/AC3/EAC3 音轨 + H.264/HEVC/VP9 软解）是 **GPL-3.0** 许可。
GPL 具有传染性：只要链接了它，**整个 PureFrame 必须以 GPL-3.0 发布**。其余依赖均宽松：
media3/AndroidX（Apache-2.0）、UPnPCast（MIT）、nanohttpd（BSD）、libtorrent4j（BSD-2）、Coil/Timber（Apache-2.0）——不构成障碍。

### 关键认知：GPL ≠ 不能收费
- **付费买断与 GPL 完全兼容**。GPL 明确允许销售副本（Free Software = freedom, not price）。
- 义务只有一个：向获得二进制的用户提供对应源码（GitHub 公开仓库即满足）。
- 先例：大量 GPL 应用在 Play 付费销售；VLC 本身就是 GPL。

### 两条路线

**路线 A：接受 GPL-3.0，app 开源（推荐）**
- GitHub 仓库转 public，LICENSE 标 GPL-3.0
- 保留 ffmpeg 软解（DTS/AC3/HEVC 老片源兼容是这个 app 的核心卖点之一）
- 开源反而利于海外口碑（privacy-friendly、no ads no tracking 的叙事成立）
- 风险：竞品可 fork。应对：功能迭代速度即壁垒；签名校验保留防魔改包

**路线 B：闭源商业（买断制的传统预期）**
- 必须移除 nextlib → 失去全部软解能力
- 只剩硬解：DTS/AC3/EAC3 音轨在无 GMS 硬解的设备上直接无声，HEVC 10bit 老设备崩
- 视频播放器市场里这是伤筋动骨的功能阉割，**不建议**

### 悬浮窗/画中画等其它功能无许可证问题。

---

## 二、Google Play 技术适配清单

### 1. targetSdk 34 → 36（必做，有时间窗）
- **2026-08-31 起，新应用必须 target API 36** 才能提交 Play（可申请延期至 11-01）。
- 我们 compileSdk 已是 36，只差 targetSdk 改动 + 行为变更适配：
  - **targetSdk 35 起 edge-to-edge 强制**：内容默认延伸到状态栏/导航栏下方。播放页（全屏视频 + 控制条）、设置页需要 insets 审计，控制条底部按钮可能被手势条遮挡。这是我们最大的 UI 回归点，需真机全屏矩阵验证。
  - targetSdk 36 行为变更相对温和（预测性返回仍为 opt-in）。
- 工作量评估：代码改动小（WindowInsets 处理），验证工作量中等。

### 2. SignatureGuard 与 Play App Signing 冲突（必做，不改就是启动即死）
- 现状：`SignatureGuard` 启动自校验 `ORIGINAL_SIGNING_SHA256`（release keystore 的 SHA-256），不符即 `killProcess`。
- 冲突：**Play App Signing 会用 Google 管理的密钥重签产物**——商店下发的 APK 签名 ≠ 我们的 release keystore → app 一启动就自杀。
- 方案（三选一）：
  1. **改用 Play Integrity API**（官方推荐，防二次打包 + 防篡改，标准做法）
  2. 改为校验「签名一致性」而非「固定 hash」：首次启动记录签名，变更时警告（防升级替换，弱于 1）
  3. 直接移除校验（依赖 Play 生态自身的签名链防篡改）
- 建议：方案 1。Play Integrity 免费且是付费 app 防盗版/防魔改的正解，与我们防二次打包的初衷完全一致。

### 3. 产物格式：APK → AAB（必做）
- Play 自 2021-08 起新应用**只收 AAB**。
- CI 目前产出 universal APK（98MB，4 ABI 全包含）。改 `bundleRelease` 产出 AAB 后 Play 自动按 ABI/密度/语言拆分，**arm64 用户实际下载约 35-45MB**（大头是 ffmpeg/libtorrent so）。
- CI workflow 需要加 AAB 构建 + 签名（Play App Signing 用 upload key 签 AAB）。
- Amazon/Samsung 商店仍收 APK，CI 可同时保留 APK 产物。

### 4. MANAGE_EXTERNAL_STORAGE（建议移除，省一次受限权限审核）
- 现状 manifest 声明了它（Android 11+ 「所有文件访问」），这是 **Play 受限权限**：必须在 Console 提交 Permissions Declaration + 使用场景演示视频，过审才能上架。视频播放器不是明文允许类别（允许的是文件管理/杀毒/备份/设备迁移），申报通过率不可控。
- 逐项审视我们的使用场景：
  - 媒体库扫描 → `READ_MEDIA_VIDEO`（已声明）+ MediaStore，**不需要 MEES**
  - 自定义扫描目录 → SAF DocumentFile（已实现），**不需要 MEES**
  - BT 下载目录 → 可写应用专属目录（`getExternalFilesDir`）或 SAF 选目录，**不需要 MEES**
  - 侧载字幕/任意路径播放（file:// 直播放）→ 这是唯一真正需要广泛读权限的场景；可降级为「通过 SAF 选择」路径白名单机制
- **结论：技术上可以完全移除 MEES**，把「任意路径播放」改为 SAF 收藏目录 + MediaStore 合集。代价是某些高级用户场景（直接输路径）受限，但换来上架流程顺畅。若坚持保留 MEES，需准备英文申报材料 + 演示视频走审核。
- 顺带：`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 也是需申报的权限（后台播放保活用）。媒体播放类 app 可改用 FGS + 通知渠道的正当路径，建议一并评估移除。

### 5. 已合规项（实测确认，无需动作）
- **16KB page size**：逐个 ELF 检查 28 个 .so——arm64-v8a / x86_64 全部 LOAD 段 16KB 对齐（0x4000）。**满足 Play 对 64-bit 16KB 的强制要求**（nextlib 0.13.0 已带 16KB 支持，libtorrent4j 64-bit 亦对齐；未对齐的只有 32-bit 的 libtorrent4j，不适用该要求）
- 64-bit 支持：arm64-v8a 在列 ✓
- FGS 类型：PlaybackService `mediaPlayback`、下载服务 `dataSync` 均已声明 ✓
- 运行时媒体权限：READ_MEDIA_VIDEO / READ_MEDIA_AUDIO 已声明 ✓
- POST_NOTIFICATIONS：媒体通知走 MediaSession 豁免路径，无需额外处理
- minSdk 26：覆盖 Android 8.0+，海外合理（Android 8+ 市占 ~98%）
- 默认语言：values/ 默认英文 + zh-rCN 中文，海外直接可用；需审计少量硬编码中文字符串（如通知渠道名等）
- Cast 投屏：海外 GMS 设备普及，比国内环境更好；DLNA 兼容国内电视盒子的场景在海外变次要

---

## 三、付费买断商业模型

### Play 付费应用路径（最简）
- 上架时选 Paid，定价即买断，**无需集成 Play Billing Library**
- 需要：Play Console 商家资料（收款银行 + 税务）。中国开发者可注册，收款走美元电汇；税务填 W-8BEN（中美税收协定，美国来源收入预扣可降低）
- 服务费：30%，参加开发者计划（首 $1M 营收）可享 15%
- 付费应用自带 48 小时无条件退款窗口（自动）

### 备选：免费 + 一次性 IAP 解锁（建议认真对比）
- 转化漏斗通常显著优于付费墙（用户先体验后付费）
- 可做试用限制（如试看/水印/功能子集）
- 代价：必须集成 Play Billing Library v7+，多一块代码 + 恢复购买逻辑
- 业界现状：Play 新上架的独立付费工具大多走此路径

### 补充渠道（可选，不冲突）
- **直销**：Paddle / Gumroad 卖许可证码或直发 APK，无 30% 抽成，但需要自己做激活校验（现有 SignatureGuard 思路可扩展成 license key）
- **Samsung Galaxy Store / Amazon Appstore**：收 APK，付费支持良好，作为增量渠道；Amazon 对 torrent 类审核更严

---

## 四、账号与流程门槛（非代码，但决定时间线）

1. **封闭测试硬门槛**：2023-11-13 之后创建的**个人**开发者账号，必须完成 **12 名测试者选择持续参与 × 连续 14 天** 的封闭测试，才能申请正式发布权限（企业账号无此要求但需邓氏编码）。
   - 含义：从注册账号到正式上架，**最短约 3-4 周**（注册 + 14 天测试 + 审核缓冲）。测试者需真实 opt-in 并从 Play 安装。
   - 行动：测试窗口期间并行准备商店素材与 Data Safety。
2. **Data Safety 表**：我们零收集、零 SDK 追踪，填「不收集任何数据」即可（Timber 日志仅本地）。这是独立工具的卖点，商店页可明示。
3. **隐私政策 URL**：必须有（哪怕一句话的静态页，GitHub Pages 免费托管）。
4. **IARC 内容分级问卷**、英文商店描述、截图（建议 4-8 张）、feature graphic 1024×500、512×512 图标。
5. App 名称/品牌：PureFrame 无冲突风险（上架前建议查一遍 Play 同名应用）。

---

## 五、建议执行顺序（待拍板后立项）

**Phase 0 — 战略决策（人工拍板）**
1. GPL-3.0 开源 vs 移除软解闭源（决定产品形态）
2. 付费直购 vs 免费+IAP（决定是否引入 Billing）
3. MEES 移除 vs 申报（决定扫描功能边界）

**Phase 1 — 技术适配（1-2 个开发批次）**
- targetSdk 36 + edge-to-edge UI 适配（全屏/手势条回归验证）
- SignatureGuard → Play Integrity
- CI 增 AAB 产物
- MEES/电池优化白名单权限清理（按 Phase 0 决策）
- 硬编码中文审计

**Phase 2 — 商店上线（与 Phase 1 并行启动账号流程）**
- 开发者/商家账号、封闭测试排期、Data Safety、隐私政策页、商店素材

---

## 附：实测数据记录（2026-10-04）
- debug APK：98,463,982 bytes，1362 条目，4 ABI × 7 so（libavcodec/libavutil/libmedia3ext/libswresample/libswscale/libtorrent4j/libandroidx.graphics.path）
- 16KB 对齐：arm64-v8a 7/7 OK，x86_64 7/7 OK，armeabi-v7a 6/7（libtorrent4j 0x1000），x86 6/7（libtorrent4j 0x1000）——64-bit 全合规
- manifest 权限清单：INTERNET / ACCESS_NETWORK_STATE / READ_EXTERNAL_STORAGE(maxSdk) / WRITE_EXTERNAL_STORAGE(maxSdk) / READ_MEDIA_VIDEO / READ_MEDIA_AUDIO / MANAGE_EXTERNAL_STORAGE / FOREGROUND_SERVICE(+MEDIA_PLAYBACK+DATA_SYNC) / WAKE_LOCK / REQUEST_IGNORE_BATTERY_OPTIMIZATIONS / SYSTEM_ALERT_WINDOW
- targetSdk=34，minSdk=26，compileSdk=36（AGP 9.0.1）
