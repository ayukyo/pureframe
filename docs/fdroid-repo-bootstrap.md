# 自建 F-Droid 仓库引导手册

本文记录自建 F-Droid 仓库（GitHub Pages 承载）的一次性初始化步骤。
CI workflow：`.github/workflows/fdroid-repo.yml`（Build 成功后自动/手动触发）。

> 触发机制（2026-10-06 修正）：workflow_run **不加** `branches: [main]` 过滤。
> 加了会导致 tag（vX.Y.Z）正式构建发布后仓库不链式更新（v1.1.1 当初就是
> 手动 dispatch 才把 index 从 1.1.0 换成 1.1.1）。index 始终跟踪
> `releases/latest`，快照构建与正式构建发布最终一致。

## 用户侧最终效果

在 F-Droid 客户端（F-Droid / Droid-ify / Neo Store）添加仓库：

```
https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F
```

即可搜索安装 PureFrame 并接收更新。index 签名指纹为
`466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F`
（fdroidserver 惯例：SHA-256 去冒号大写形式）。

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
# config.yml 由 workflow 每次生成，无需预置
git add repo && git commit -m "bootstrap fdroid repo" && git push origin gh-pages
git checkout main
```

### 4. 启用 GitHub Pages

仓库 Settings → Pages → Source 选 **GitHub Actions**。
若 Pages 环境带部署分支策略（deployment branch policy），需把 `main`
加入允许列表（workflow 从 main 触发，environment 校验按触发分支）。

### 5. 首跑验证（已于 2026-10-05 完成）

- `fdroid update` 成功生成 `repo/index-v1.json` 与签名 `index.jar`
- index.jar 签名指纹经 `keytool -printcert -jarfile` 验证 =
  `466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F`（与 index keystore 一致）
- 线上验证：`https://ayukyo.github.io/pureframe/repo/index-v1.json` 返回 200，
  含 `com.pureframe.player` 1.1.0 (versionCode 2)
- README 下载章节已补充自建仓库入口

## 与分发地图的关系

对应 `docs/oss-distribution-plan.md` 的 **Tier 2（自建 F-Droid 仓库）**：
比 Tier 1（IzzyOnDroid）更早可用的无审查渠道，也是 Tier 3（F-Droid 主仓库）
收录前验证元数据质量的关键演练场。

## 注意事项

- APK 来源是 GitHub Releases 的 `pureframe-release-<ver>.apk`（release 签名），
  与直装用户拿到的包完全一致
- index 密钥只存在 GitHub Secrets 与本机备份各一份（备份策略同 SECURITY.md）
- 大 APK（本项目 ~92MB）会占仓库空间：`archive_older: 0` 只保留最新版本
- fdroidserver 2.x **只认 config.yml**（config.py 已弃用忽略）；workflow 每次
  运行都重新生成 config.yml 并注入口令，部署前删除 keystore.p12
- workflow 的 run 步骤内**禁用 heredoc / 顶格内容**——会终止 YAML 块标量，
  导致 GitHub 无法解析 workflow 文件（run 名退化为文件路径、0 jobs、
  dispatch API 422 的三重症状，2026-10-05 踩坑实录）
