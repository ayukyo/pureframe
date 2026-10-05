# Google Play 免费上架材料清单

对应分发地图 Tier 4（最后一步）。个人开发者账号（一次性 $25）。
**重要前提**：2023-11-13 后注册的个人账号须完成 12+ 测试者连续 14 天的封闭测试，
才能申请正式上架——时间线最短 3~4 周，先启动封闭测试再补其余材料。

## 1. 商店素材

| 素材 | 要求 | 状态 |
|------|------|------|
| 应用名称 | ≤30 字符 | ✅ PureFrame（已有 fastlane title 可复用） |
| 简短说明 | ≤80 字符 | ✅ fastlane short_description（需微调，中文 23 字符 OK） |
| 完整说明 | ≤4000 字符 | ✅ fastlane full_description |
| 应用图标 | 512×512 PNG | ⚠️ 需从 192px 放大或重导出（位图放大会糊） |
| 功能图 featureGraphic | 1024×500 PNG/JPG | ❌ 待制作（可用播放画面拼图） |
| 手机截图 | ≥2 张，16:9 或 9:16，每边 320~3840px | ✅ 已有 3 张真机截图（1080×2340，9:19.5 需确认在限内） |
| 分类 | — | 多媒体/视频播放器 |

## 2. 内容分级与政策表单

- **内容分级问卷**：无暴力/赌博/用户生成内容分享 → 预期评 Everyone/3+
- **数据安全表单**（最关键，必须与实际一致）：
  - 是否收集用户数据？→ **否**（零收集是我们的核心卖点）
  - 是否分享数据？→ 否
  - 是否必需安全措施？→ 数据不收集则多数项不适用
  - BT 下载暴露 IP 是协议行为，不属于"收集"，但建议在数据说明里主动提及
- **广告**：包含广告？→ 否
- **target Audience**：面向大众（不面向儿童的声明）
- **政府应用/金融功能**：均否

## 3. 技术合规（已完成项）

| 项目 | 状态 |
|------|------|
| targetSdk 36（2026-08-31 起强制） | ✅ |
| 16KB page size（2025-11 起新提交强制） | ✅ 实测 28 个 so 全部 0x4000 对齐 |
| 64 位支持 | ✅ arm64-v8a（另有 x86_64） |
| MEES 全盘访问权限 | ⚠️ **移除申报**：我们实际用 READ_MEDIA_VIDEO + SAF 已覆盖，MEES 是受限权限需视频演示+审查，且不必要。**提交前确认 manifest 是否已移除，未移除则先移除** |
| Edge-to-edge（Android 15+ 强制） | ✅ 已适配（真机回归通过） |
| 签名 | Play App Signing（上传密钥用我们的 release keystore；商店分发密钥由 Google 管理） |

## 4. SignatureGuard 与 Play App Signing 冲突（唯一代码改动项）

Play 商店分发的 APK 由 Google 的密钥重签，SignatureGuard 的 hash 校验会失败 →
启动即 killProcess。方案（上 Play 前必做）：

1. 接入 Play Integrity API（免费，判定"应用未被篡改"的标准方式）
2. SignatureGuard 逻辑改为：hash 匹配 → 通过；不匹配 → 调 Play Integrity 验证
   正版渠道 → 通过；否则 killProcess
3. 保留"无 Play Services 设备 + hash 不匹配"的降级策略（F-Droid/直装包不受影响，
   因为它们的构建没有 PF_SIGNING_SHA256 注入，校验本来就关闭）

工作量估计：1~2 天开发 + 真机/商店包双路验证。

## 5. 上架步骤

1. Play Console 注册（$25，需身份验证）
2. 创建应用 → 填商店信息（上表素材）
3. **封闭测试轨道**：上传 AAB/APK → 邀请 12+ 测试者（邮箱列表）→ 开始 14 天
4. 测试期间完成数据安全表单 + 内容分级 + 隐私政策 URL（已有 docs/privacy-policy.md，
   需挂在可达 URL——可用 GitHub Pages 或仓库 raw 链接）
5. 14 天后申请正式上架 → 审核（通常 1~7 天）

## 6. AAB 格式说明

Play 要求新应用用 AAB（Android App Bundle）。
`./gradlew bundleRelease` 即可产出；CI 需加一条 bundle 构建并把
`pureframe-release-<ver>.aab` 附到 Release（届时 SHA256SUMS 同步加入）。

## 与商业策略的关系

免费上架（无 IAP、无广告）与「开源免费立口碑」战略一致。Play 版本的价值是
触达不用 F-Droid 的普通用户 + 官方渠道信任背书；GPL 要求下源码仍须公开
（可在 Play 描述与仓库双向链接）。
