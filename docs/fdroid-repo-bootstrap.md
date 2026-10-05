# 自建 F-Droid 仓库引导手册

本文记录自建 F-Droid 仓库（GitHub Pages 承载）的一次性初始化步骤。
CI workflow：`.github/workflows/fdroid-repo.yml`（Build 成功后自动/手动触发）。

## 用户侧最终效果

在 F-Droid 客户端（F-Droid / Droid-ify / Neo Store）添加仓库：

```
https://ayukyo.github.io/pureframe/fdroid/repo?fingerprint=<index指纹>
```

即可搜索安装 PureFrame 并接收更新。

## 一次性初始化（维护者操作）

### 1. 生成 index 签名密钥

index 密钥与 APK 签名密钥**必须分离**（不同用途、不同泄露面）：

```bash
keytool -genkeypair -v \
  -keystore fdroid-index.keystore -storetype PKCS12 \
  -alias fdroid -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass <生成强口令>
```

### 2. 凭据入 GitHub Secrets

| Secret | 内容 |
|--------|------|
| `PF_FDROID_KEYSTORE_BASE64` | `base64 -w0 fdroid-index.keystore` 的输出 |
| `PF_FDROID_KEYPASS` | keystore 口令 |

### 3. 提交 gh-pages 分支骨架

```bash
git checkout --orphan gh-pages
mkdir -p repo
# 放入 config.py（workflow 首跑也会自动生成，可先空仓库）
git add repo && git commit -m "bootstrap fdroid repo" && git push origin gh-pages
git checkout main
```

### 4. 启用 GitHub Pages

仓库 Settings → Pages → Source 选 **GitHub Actions**。

### 5. 首跑验证

手动触发 `F-Droid repo` workflow，确认：
- `fdroid update` 成功生成 `repo/index-v1.json`（F-Droid 客户端兼容格式）与 `index.jar`（签名）
- 从日志取 index 指纹（`fdroid` 输出 `SHA-256 of signing key:` 一行），填入上面的仓库 URL
- README 的下载章节补充自建仓库入口

## 与分发地图的关系

对应 `docs/oss-distribution-plan.md` 的 **Tier 2（自建 F-Droid 仓库）**：
比 Tier 1（IzzyOnDroid）更早可用的无审查渠道，也是 Tier 3（F-Droid 主仓库）
收录前验证元数据质量的关键演练场。

## 注意事项

- APK 来源是 GitHub Releases 的 `pureframe-release-<ver>.apk`（release 签名），
  与直装用户拿到的包完全一致
- index 密钥只存在 GitHub Secrets 与本机备份各一份（备份策略同 SECURITY.md）
- 大 APK（本项目 ~92MB）会占仓库空间：`archive_older = 0` 只保留最新版本
