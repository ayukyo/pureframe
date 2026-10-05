# PureFrame 开源免费分发计划（口碑优先阶段）

> 决策背景（2026-10-05）：先不接变现，以 GPL-3.0 开源 + 免费分发建立口碑；
> 变现（IAP/广告）推迟到用户量起来之后再评估。
> 前置研究报告：`docs/overseas-release-research.md`

---

## 一、分发平台地图（按自动化程度与门槛排序）

### Tier 0 — GitHub Releases（已建成 80%，补版本化即可）
- **现状**：CI 每次 push 自动发「阶段性构建」Release（build-\<sha\>）——已经是自动发布，但形态是快照流，不适合当用户的正式更新源
- **改造**：
  1. 引入语义化版本 tag（v1.0.0 起），CI 监听 tag 触发正式 Release（changelog + APK + SHA-256 校验值）
  2. 快照流保留（供测试），正式 Release 走 tag
- **顺带收益**：**Obtainium 自动兼容**——Obtainium 用户把仓库 URL 加进去就永久自动更新，零额外工作。这是海外 FOSS 玩家目前最流行的安装方式
- 成本：CI 脚本半天

### Tier 1 — IzzyOnDroid 仓库（最务实的商店入口）
- F-Droid 生态里最大的第三方仓库，欧美 FOSS 用户的常备源
- **审核轻量**：接受开发者自构建的二进制（不要求他们侧从源码构建），APK 必须来自官方渠道（GitHub Releases ✓）
- 上架方式：向 izzyondroid 的 metadata 仓库提 PR；**之后每次我们发 GitHub Release，他们的机器人自动跟进同步**——接近全自动
- 我们的预编译 so（nextlib/libtorrent4j）在 Izzy 不构成障碍
- 成本：1-2 天（元数据 + 截图 + 提交 + 沟通）

### Tier 2 — 自建 F-Droid 仓库（真·全自动发布）
- fdroidserver 在 CI 里生成仓库索引，GitHub Pages 托管
- 用户添加我们的仓库 URL 后，**以后每个版本自动推送到所有订阅者**——完全掌握发布节奏
- 我们自己的签名密钥，SignatureGuard 兼容 ✓
- 成本：CI 集成 fdroidserver 约 1-2 天，一次性

### Tier 3 — F-Droid 主仓库（曝光最大，门槛最高）
- 他们侧从源码构建（提交 fdroiddata 配方），一次配置后**新版本自动构建发布**
- 已知摩擦点：
  1. **构建配方**：nextlib/libtorrent4j 的预编译 .so 可能被要求从源码构建（上游均开源，理论可解，调试成本不可控）
  2. **GMS Cast 依赖**：play-services-cast-framework 是预编译二进制 → 触发 F-Droid「NonFreeDep」反特性标签；要么接受标签（app 无 GMS 时已自动降级，功能无损），要么把 Cast 做成可选模块
  3. **F-Droid 侧签名**：他们用自己的密钥重签 → SignatureGuard 必须先改造（见适配清单）
- 建议：Tier 1/2 跑通、口碑起量后再攻 Tier 3

### Tier 4（后续）— Google Play 免费上架
- 免费 app 也要过「个人账号 12 测试者 × 14 天封闭测试」门槛
- 放到口碑起来后作为触达普通用户的渠道，配合 Play Integrity

### 其他（顺手提交，非重点）
- Accrescent（现代 FOSS 商店，开发者签名连续性验证）：提交门槛低
- APKPure / Aptoide：表单提交，免费 app 低摩擦

---

## 二、开源化代码适配清单

| # | 事项 | 说明 | 量级 |
|---|---|---|---|
| 1 | LICENSE 文件 + README 重写 | GPL-3.0 全文；英文 README（功能矩阵、截图、下载渠道徽章） | 小 |
| 2 | 仓库转 public | **先审计 git 历史无敏感信息**（keystore 在仓库外 ✓、local.properties 已 ignore ✓、CI 凭据在 GitHub Secrets ✓），人工过一遍早期提交 | 用户操作 + 半天审计 |
| 3 | **SignatureGuard 改造** | 自校验 hash 与 F-Droid 主仓库/任何第三方重签冲突（同 Play App Signing 逻辑）。方案：构建期注入「是否启用自校验」，官方 GitHub/自建仓库构建启用，第三方分发构建禁用；后续 Play 化再评估 Play Integrity | 中 |
| 4 | 版本化体系 | versionName 语义化（当前 1.0.0 死值）+ CHANGELOG.md + tag 流程 | 小 |
| 5 | fastlane metadata 目录 | F-Droid/Izzy 通用商店素材格式（标题/简介/截图/图标按 locale），一次准备多平台复用 | 小 |
| 6 | GMS 依赖预检 | Cast 框架保留（降级机制已验证）→ 接受 NonFreeDep；或抽可选模块（投入大，建议先接受） | 决策项 |
| 7 | 硬编码中文审计 | 通知渠道名等少量字符串 | 小 |
| 8 | 隐私政策静态页 | GitHub Pages 一页声明「零收集零追踪」，商店/仓库都引用 | 小 |

---

## 三、口碑运营配套（与分发并行）

1. **README 卖相**：动图/GIF 演示（投屏、软解信息面板、BT 下载）、功能对比表（vs VLC/MX/Next Player）
2. **GitHub Topics 打标**：`video-player` `exoplayer` `dlna` `google-cast` `torrent` `android` `jetpack-compose`——被搜索/被推荐的基础
3. **发布帖**：r/androidapps、XDA、酷安（国内侧载圈）——开源播放器在这些社区有天然受众
4. **文档**：docs/ 目录已有测试报告/调研文档，公开后是「工程认真度」的信任背书
5. Issues/PR 礼仪模板：开源口碑的一半来自 issue 响应速度

---

## 四、建议执行顺序

| 批次 | 内容 |
|---|---|
| **批次 A（起步）** | 适配 #1/#3/#4/#7/#8 + 版本化 CI（Tier 0 完成）→ 打 v1.0.0 → 仓库转 public |
| **批次 B** | fastlane 素材 + IzzyOnDroid 提交（Tier 1）+ Accrescent/APKPure 顺手提交 |
| **批次 C** | 自建 F-Droid 仓库 CI（Tier 2） |
| **批次 D（口碑起量后）** | F-Droid 主仓库配方攻坚（Tier 3）；评估 Play 免费上架（Tier 4） |

**注意**：GPL 开源后变现路线并未锁死——版权人可随时对后续版本做双许可（开源 GPL + 商业授权），且「先开源立口碑、后加 IAP」在 Just Player 等项目上有成熟先例。真正不可逆的只有一件事：**历史版本必须永远提供源码**。

---

## 五、与前期研究结论的衔接
- GPL-3.0 路线 → 确认执行（本计划即其落地）
- targetSdk 36 / edge-to-edge 适配 → 仍是批次 A 的前置或并行项（任何分发渠道都受益）
- MEES 权限移除 → ✅ 已完成（2026-10-06）：全渠道受益——Play 免受限权限申报，FOSS 渠道强化「隐私友好」叙事
- SignatureGuard → 本计划 #3 取代原「Play Integrity」方案（Play 阶段再叠加）
