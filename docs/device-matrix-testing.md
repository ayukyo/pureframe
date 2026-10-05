# PureFrame 多设备适配测试方案

> 目标：在不同分辨率 / 屏幕比例 / 系统版本上验证 app 无崩溃、UI 无错位、核心链路可用。
> 本地真机基线：MI 9 SE（1080×2340，Android 11，MIUI）——R1-R4 已全部通过。

## 一、测试矩阵设计

### 维度与风险点

| 维度 | 档位 | 潜在风险 |
|------|------|----------|
| 系统版本 | API 26-28（旧）、30-33（中）、34+（新） | 旧版缺少 API（如 `setImageTintList` 行为差异）、新版 scoped storage / media store 变化 |
| 分辨率 | 小屏 720×1280、普通 1080×2340、平板 2560×1600 | Compose 布局挤压、播放器手势区域、底部导航溢出、对话框高度 |
| 屏幕比例 | 16:9（旧）、19.5:9~20:9（全面屏） | 视频画面裁切、浮窗比例计算 |
| 横竖屏 | 竖屏为主 + 横屏播放 | 全屏切换、横屏浮窗尺寸 |

### 本地 AVD 矩阵（HAXM 可用，选 ATD 轻量镜像）

| AVD 名 | API | 分辨率 | 密度 | 覆盖风险 |
|--------|-----|--------|------|----------|
| pf_api30_small | 30 (11) | 720×1280 | 320dpi (tvdpi 用 213 更省) | 小屏旧版：布局挤压 + minSdk 附近行为 |
| pf_api33_phone | 33 (13) | 1080×2340 | 440dpi | 中档主流：与真机同分辨率，验证 API 33 scoped storage |
| pf_api35_phone | 35 (15) | 1080×2400 | 420dpi | 最新版本：边缘 API 行为 |
| pf_api30_tablet | 30 (11) | 2560×1600 | 240dpi | 平板大屏：双栏布局/列表拉伸 |

> ATD 镜像说明：Automated Test Device，去除相机/联系人等应用，启动快、内存占用小，适合 UI 冒烟。

### 云真机方案（免费额度）→ 已落地 Firebase Test Lab

| 平台 | 免费额度 | 特点 | 限制 |
|------|----------|------|------|
| **Firebase Test Lab** ✅ 已用 | 每天 10 虚拟 + 5 真机测试；Robo 自动爬测 | Google 官方，报错带截图/日志/视频 | 需 Google 账号 + gcloud CLI；真机多在海外机房；国内访问需代理 |
| **Samsung Remote Test Lab** | 每天 10 积分 ≈ 2.5h 真机 | 三星全系真机（Galaxy S/A 系列） | 仅三星设备；需三星账号；手动操作走浏览器 |
| **AWS Device Farm** | 新账户 1000 设备分钟 | 400 台真机 | 试用期一次，之后 $0.17/min |
| **Google Firebase Device Streaming** | 每项目 30 分钟/月 | ADB 直连云端设备（像本地一样） | 额度少，适合临时调试 |

推荐组合：**本地 AVD 打底**（矩阵回归）→ **Samsung RTL 补三星真机**（One UI 兼容）→ **Firebase Robo test 兜底崩溃扫描**。

## 二、每台设备的冒烟清单（R 精简版）

1. **安装启动**：apk 安装成功，冷启动进入本地视频页（SignatureGuard 不误杀）
2. **布局检查**（截图目检）：
   - 底部导航三个 tab 完整显示
   - 本地列表卡片文字不溢出/截断
   - 添加下载对话框完整可见（不超出屏幕）
3. **核心链路**：
   - 播放本地视频（推入一个测试 mp4 到 /sdcard/Movies 或 Download）
   - 控制条显示/隐藏、±10s
   - 横屏全屏切换（平板重点）
4. **设置页**：语言切换、排序开关可见且可点
5. **下载页**：打开添加对话框，输入 .torrent 链接确认识别为「种子文件链接」（纯 UI，不必真下载）

## 三、执行脚本

- `make_avds.ps1` — 批量创建 AVD
- `push_test_media.sh` — 向设备推测试视频
- `smoke_test.py` — adb 冒烟：安装 → 启动 → keyevent 巡航 → 截图存 `_shots/avd_<name>/`

## 四、结果记录

每台设备一轮截图 + 结论写入 `_shots/avd_<name>/README.md`，问题汇总回填本文件第五节。

### 2026-09-29 本地 AVD 冒烟测试结果（4/4 通过）

| AVD | API | 分辨率/密度 | 安装启动 | 扫描列表 | 播放 | Settings | Downloads | 横屏 | 结论 |
|-----|-----|-------------|----------|----------|------|----------|-----------|------|------|
| pf_api30_small | 30 | 720×1280 / 320dpi | ✅ | ✅ All(1) | ✅ 全屏正常 | ✅ | ✅ | 未测（小屏竖屏为主） | 通过 |
| pf_api33_phone | 33 | 1080×2340 / 440dpi | ✅ | ✅ All(1) | ✅ 居中比例正确 | ✅ | ✅ | 未测 | 通过 |
| pf_api35_phone | 35 | 1080×2400 / 420dpi | ✅ | ✅ All(1) | ✅ 无拉伸 | ✅ | ✅ | 未测 | 通过 |
| pf_api30_tablet | 30 | 2560×1600 / 240dpi | ✅ | ✅ All(1) | ✅ 横屏全屏铺满 | ✅ | ✅ | ✅ 布局正常 | 通过 |

验证方法：uiautomator dump 文本/坐标断言 + screencap 目检（ATD 无头镜像旋转后截图可能黑屏，以 dump 为准）。

### 2026-09-29 Firebase Test Lab 云真机 Robo 测试结果（3/3 通过）

用 gcloud 向 3 台物理真机提交 Robo 自动爬测（`--type robo --timeout 3m`，debug 包 APK），全部 Passed：

| 真机 | API | 厂商/系统 | 安装启动 | Robo 爬取 | 崩溃/ANR | 覆盖屏幕 | 结论 |
|------|-----|-----------|----------|-----------|----------|----------|------|
| SH-01L（AQUOS sense2） | 28 | SHARP / Android 9 | ✅ | 46 动作全 SUCCESS | 0 | 11 屏 | 通过 |
| RMX3231（realme C53 同型） | 30 | realme / Android 11 | ✅ | 48 动作，47 SUCCESS+1 脚本结束标记 | 0 | 13 屏 | 通过 |
| SO-41A（Xperia 10 II） | 31 | Sony / Android 12 | ✅ | 45 动作，43 SUCCESS+2 脚本结束标记 | 0 | 13 屏 | 通过 |

补充观察：
- **API 28 真机通过**补齐了本地 AVD 矩阵缺失的最低版本（本地最低 API 30）
- logcat 全程无 `FATAL EXCEPTION`、无 pureframe 进程被杀/ANR 记录
- 截图目检：MainActivity 冷启动正常（+2.8s~+6.2s Displayed）、底部导航三 tab 完整、Settings 页（Player/Downloads/Interface 分组）渲染正常、添加下载对话框完整可见
- Robo 主动触发了 `AppManageExternalStorageActivity`（API 30+ 权限页）和 `PickActivity`（SAF），权限引导链路工作正常
- 产物：每台设备 12+ 截图、爬取轨迹图（sitemap.png）、logcat、录屏 video.mp4；云端保留在 `gs://test-lab-ydwcw2hjh9444-hb4cp62tdbun6/2026-09-29_13:53:54.290421_FNZP/`

复现命令：

```bash
gcloud firebase test android run --type robo \
  --app app-debug.apk \
  --device model=SH-01L,version=28 \
  --device model=RMX3231,version=30 \
  --device model=SO-41A,version=31 \
  --timeout 3m
```

### 测试环境关键操作（复现用）

```bash
# 1. 启动无头模拟器
emulator -avd pf_api33_phone -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
# 2. 安装（-g 自动授予运行时权限）
adb install -r -g app-debug.apk
# 3. 推视频 + root 修属主（adb push 的文件属主异常会导致扫描器 File.exists() 失败）
adb push test_video.mp4 /sdcard/Movies/
adb root
adb shell "chown media_rw:media_rw /data/media/0/Movies /data/media/0/Movies/test_video.mp4; \
  chmod 770 /data/media/0/Movies; chmod 660 /data/media/0/Movies/test_video.mp4"
# 4. 触发媒体扫描（appops 步骤为 MEES 时代的历史记录——2026-10-06 MEES 已移除，
#    现走 READ_MEDIA_VIDEO，`adb install -g` 即可授予，appops 命令不再需要）
adb shell "content call --uri content://media/none --method scan_volume --arg external_primary; \
  appops set com.pureframe.player.debug MANAGE_EXTERNAL_STORAGE allow"
```

## 五、发现的问题

### P0-已修复：API 30+ 模拟器上显示 0 视频（环境+代码双重因素）

**现象**：app 启动后 `All (0)`，提示 "Grant access" 或 "No supported video files found"。

**根因链**（三个独立因素叠加，逐一定位）：
1. **API 30+ 权限模式**：（历史记录，MEES 已于 2026-10-06 移除）当时 app 在 API 30+ 走 `Environment.isExternalStorageManager()`（所有文件访问），`adb install -g` / `pm grant READ_EXTERNAL_STORAGE` 均无效 → 必须 `appops set <pkg> MANAGE_EXTERNAL_STORAGE allow`。现版本走 READ_MEDIA_VIDEO，无此问题
2. **adb push 文件属主异常**：push 到 `/sdcard/Movies/` 的文件落盘到 `/data/media/0/` 后属主为 `u0_a64`、权限 0600（media provider 私有），app 进程 `File(path).exists()` 失败被跳过 → root 下 chown media_rw + chmod 660 修复
3. **重装 APK 后 MediaStore 记录丢失**：卸载重装会清除该 app 相关 MediaStore 行，且不会自动重扫 → `content call --uri content://media/none --method scan_volume --arg external_primary` 重新收录

**结论**：全部为测试环境搭建问题，**app 代码本身在 4 台设备上无适配缺陷**。真机正常使用不受影响（用户通过 SAF/授权流程写入的文件属主正确）。

### 备忘（低优先级）

- ATD 镜像 `screencap` 在 `user_rotation` 旋转后输出全黑（swiftshader 渲染限制），验证横屏布局需改用 uiautomator dump 坐标断言
- debug 包 Timber 被裁（proguard-debug.pro），模拟器调试时无法看扫描日志；如需深挖可临时在 debug 保留 LocalVideoScanner 的 Timber.i

### 云真机环境踩坑备忘（gcloud / Firebase Test Lab）

- **gcloud 认证**：`auth login --no-launch-browser` 的 PKCE verifier 存在 gcloud 进程内存，**进程退出验证码即作废**；必须让进程保持存活等验证码输入（托管脚本 subprocess.Popen + stdin PIPE 已验证可行，脚本在 `.workbuddy/tmp/oauth_host.py`）
- **沙箱 Python 冲突**：workbuddy managed Python 的 sqlite3 写 gcloud credentials.db 报 readonly，须设 `CLOUDSDK_PYTHON="C:/Windows/py.exe"`
- **API 连通性**：`*.googleapis.com` API 调用本机**直连可达**（无需代理）；但 `storage.googleapis.com`（gsutil 拉结果）直连超时、走代理 502 → 改用 REST API + OAuth Bearer token（urllib）下载产物
- **新项目首次跑测试的两个前置**：① `gcloud services enable testing.googleapis.com`（Google Cloud Testing API，否则 matrix validation 报 SERVICE_NOT_ACTIVATED）；② 不要自建 `--results-bucket`（建桶需计费权限），用默认 `test-lab-<hash>` 桶
- **拉取结果**：`gsutil ls` 不可用时，用 `gcloud auth print-access-token` + `storage/v1/b/<bucket>/o?prefix=...` REST 列举，`?alt=media` 下载（Python 脚本 `.workbuddy/tmp/download_flt_artifacts.py`）
- 项目用的是 Gemini 自动创建的 `gen-lang-client-*` 项目，Test Lab 直接可用（Spark 免费层）
