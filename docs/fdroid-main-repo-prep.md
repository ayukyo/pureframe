# F-Droid 主仓库收录筹备

对应分发地图 Tier 3。前置条件：批次 B（IzzyOnDroid）与批次 C（自建仓库）完成，
元数据经真实用户验证。

## 收录方式

F-Droid 主仓库通过 [fdroiddata](https://gitlab.com/fdroid/fdroiddata) 的 Merge Request
收录，应用**从源码构建**（不是接收 APK）。我们需要提交的是 build metadata。

## fdroiddata metadata 草稿

文件名：`metadata/com.pureframe.player.yml`
（注意：F-Droid 主仓库要求 applicationId 为发布 id；我们发布两个包名，
主仓库建议收录 release 包名 `com.pureframe.player`）

```yaml
Categories:
  - Multimedia
  - Internet

License: GPL-3.0-only
AuthorName: ayukyo
SourceCode: https://github.com/ayukyo/pureframe
IssueTracker: https://github.com/ayukyo/pureframe/issues

AutoName: PureFrame

RepoType: git
Repo: https://github.com/ayukyo/pureframe

Builds:
  - versionName: 1.1.0
    versionCode: 2
    commit: v1.1.0
    subdir: code/app
    gradle:
      - yes
    # 构建要点：
    # - 工程用 Gradle 9.1.0 / AGP 9.0.1（内置 Kotlin），fdroidserver 的构建环境需支持
    # - subdir 是 code/app（仓库结构是 monorepo，Android 工程在 code/ 下）
    # - release 签名由 F-Droid 自己重签，无需我们的 keystore
    # - SignatureGuard 在无 PF_SIGNING_SHA256 注入时自动关闭校验，无需改代码

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags v^[\d.]+$
CurrentVersion: 1.1.0
CurrentVersionCode: 2
```

## 提交流程（需维护者亲自操作的部分）

1. fork `fdroid/fdroiddata`，放入上面的 metadata + `metadata/com.pureframe.player/`
   目录（fastlane 结构会被直接复用，我们已就绪）
2. 本地 `fdroid build -v -l com.pureframe.player` 验证能从源码构建成功
   （需要 Linux 环境 + fdroidserver；可先用 CI 验证——我们的 build.yml 已证明
   GitHub runner 能构建，风险主要是 fdroidserver 的 SDK/Gradle 版本约束）
3. 提 MR 到 fdroid/fdroiddata，在描述里附上：
   - 权限用途说明（已有：docs/privacy-policy.md）
   - 已在 IzzyOnDroid 的收录链接（信任背书）
   - 自建仓库地址（可选）

## 需要注意的合规点（提交前自查）

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 许可证 OSI/FSF 认可 | ✅ GPL-3.0 | LICENSE 已更正 |
| 无追踪/广告 SDK | ✅ | 零收集 |
| 无自更新/动态下载代码 | ✅ | 无更新器代码 |
| usesCleartextTraffic | ⚠️ 需查 | 播放器本地网络场景政策明文允许，但要有 Network Security Config 或说明 |
| 完整源码可构建 | ✅ | CI 即证明；monorepo subdir 已标注 |
| FFmpeg 软解（nextlib） | ✅ GPL 兼容 | nextlib 是 GPL-3.0，与整体许可一致 |
| libtorrent4j | ✅ BSD | 与 GPL 兼容，NOTICE 已注明 |

## 预计时间线

MR 提交后由 F-Droid 审核志愿者 review，通常 1~4 周（视队列）。期间可能被要求
调整 metadata 格式或回答构建问题——批次 B/C 的经验直接复用。
