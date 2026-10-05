# 安全策略 / Security Policy

## 支持版本

| 版本 | 支持状态 |
|------|----------|
| main 分支最新构建 | ✅ 支持 |
| 历史发布 APK | ❌ 不支持，请更新到最新构建 |

## 报告漏洞

**请不要以公开 Issue 的形式报告安全漏洞。**

报告方式（按优先级）：

1. GitHub 私密安全建议（Security Advisories → Report a vulnerability）
2. 通过仓库所有者的 GitHub 联系方式私下联系

请在报告中包含：

- 漏洞类型与影响范围
- 复现步骤或 PoC
- 可能的修复建议（如有）

## 响应时间

- 确认收到：3 天内
- 初步评估：7 天内
- 修复发布：视严重程度，高危问题优先

## 安全设计说明

- 本软件**不内置任何内容源**，不与任何第三方内容服务通信
- 网络访问仅用于用户主动发起的磁力/HTTP 下载
- 应用为本地工具，不上传用户数据、不包含分析/追踪 SDK
- 使用 BT 下载时，本机 IP 会按 BitTorrent 协议暴露给 peer 节点，这是协议固有行为，请使用者自行评估

---

## 签名密钥管理（维护者文档）/ Release Signing Key Management

本节面向项目维护者，描述 release 签名密钥的存放位置、备份策略与恢复流程。

### 密钥存放原则

1. **密钥永不进入 git 仓库**（包括历史提交）。当前 keystore 位于仓库之外：
   - 本机路径：`D:/AI_CODING/pureframe-signing/pureframe-release.jks`
2. **CI 使用 GitHub Secrets**（Secrets 经 API 不可读，公共/私有仓库同样安全）：
   - `PF_STOREFILE_BASE64` — keystore 的 base64 编码
   - `PF_STOREPASSWORD` / `PF_KEYPASSWORD` — keystore 与 key 口令
   - `PF_KEYALIAS` — key 别名（`pureframe`）
   - `PF_SIGNING_SHA256` — 证书 SHA-256 指纹（SignatureGuard 启动自校验用）
3. **本地构建**从 `code/local.properties` 读取（该文件在 `.gitignore` 中，不进 git）。

### 备份策略（3-2-1 原则的简化版）

加密归档 `pureframe-signing-backup.enc` 至少保存在 **2 处物理隔离的位置**：

| 位置 | 路径 | 说明 |
|------|------|------|
| 本机（工作盘） | `D:/AI_CODING/pureframe-signing/pureframe-signing-backup.enc` | 与原 keystore 不同目录 |
| 异地（云盘） | `OneDrive/backups/pureframe-signing-backup-20261005.enc` | 云端同步，本地损坏可恢复 |

归档加密方式：`openssl enc -aes-256-cbc -pbkdf2 -iter 200000`，口令存于**密码管理器**（条目名「PureFrame release keystore」），**不与归档存放在一起**。

### 完整恢复流程（keystore 丢失/损坏时）

```bash
# 1. 解密归档（口令见密码管理器）
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in pureframe-signing-backup-20261005.enc \
  -pass pass:"<口令>" | tar -xzv

# 2. 还原 keystore 到工作目录
cp pureframe-release.jks D:/AI_CODING/pureframe-signing/

# 3. 验证指纹必须完全一致（否则 SignatureGuard 会让所有用户的应用启动即崩溃）
keytool -list -v -keystore pureframe-release.jks -storepass <storePassword>
# 期望输出: SHA256: BD:17:38:13:8F:C0:7F:66:92:BA:74:D5:7B:B0:24:B5:DA:76:25:16:4C:0D:FD:7D:87:42:CC:0A:E2:DC:3E:4F

# 4. CI 无需改动：GitHub Secrets 中的 PF_* 是独立副本，不依赖本地
#    本地构建需恢复 code/local.properties 的 pureframe.* 条目（凭据见归档内 credentials.txt）
```

### 如果密钥彻底丢失（所有备份均失效）

1. 生成新 keystore，更新 GitHub Secrets 全套 PF_*。
2. **必须同步更新 SignatureGuard 校验值**（`PF_SIGNING_SHA256`），否则老用户更新后应用启动即死。
3. 更新各分发渠道说明：F-Droid/Play 用户不受影响（渠道从源码构建或商店重签），直装用户须卸载重装。
4. 在 CHANGELOG 与 Release 说明中显著标注签名变更。

### 第三方分发的签名豁免

SignatureGuard 仅在构建期注入 `PF_SIGNING_SHA256` 时启用校验。社区从源码构建（无该环境变量）时校验自动关闭——这正是 F-Droid 等第三方渠道可用同一代码库分发的原因。未来接入 Play App Signing（商店重签）时，需以 Play Integrity API 替代 hash 校验。
