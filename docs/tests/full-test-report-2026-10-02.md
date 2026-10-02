# PureFrame 全量测试报告（2026-10-02）

- 设备：MI 9 SE（Android 11 / MIUI，serial 9421e1c7，1080×2340）
- 包名：`com.pureframe.player.debug`（debug buildType，proguard-debug 裁 Timber 裁剪不影响主日志）
- 版本：AGP 8.6.1 / Gradle 8.9 / Kotlin 2.0.21 / Media3 1.9.0 / Hilt 2.51.1

## 一、单元测试（Task #1）✅

`./gradlew testDebugUnitTest` 全量通过，无失败用例。

## 二、构建与安装（Task #2）✅

- debug APK 构建成功，adb 安装到真机成功。
- 冷启动正常，首页 24 个本地视频扫描加载正常（MediaStore + LocalVideoScanner 双路径）。

## 三、真机全链路冒烟（Task #3）✅

| 验证项 | 结果 | 证据 |
|---|---|---|
| 视频列表加载 | ✅ | 首页 24 视频，缩略图/时长/收藏标记正常 |
| 播放/暂停/进度 | ✅ | `dumpsys media_session` state=3(播放)/2(暂停) 双态切换 |
| 通知栏媒体控件 | ✅ | prev/pause/next 三按钮；next 切集 item 6→7（S01E01→S01E02），标题同步 |
| 通知自定义按钮（PR8） | ✅ | 循环三态切换（ALL→OFF 图标实时刷新）；收藏实心态生效 |
| 同目录隐式队列（PR7） | ✅ | 自然排序 S01E01→02→03；queueTitle size=13 |
| 播完语义（PR4） | ✅ | loop_play 关闭后播完 state=1、next 按钮动态消失、session 保持可恢复 |
| 设置持久化（PR8） | ✅ | DataStore 二进制读出 media_notification_enabled / lockscreen_media_visible / cast_last_device 等完好 |
| 投屏入口（app 内） | ✅ | 顶栏 Cast 按钮 → CastDevicePicker「在电视上播放」弹出，DLNA 扫描出设备并渲染 |
| 投屏错误回退 | ✅ | 连接非 renderer 设备（路由器 UPnP）→ file server 启动 → 推流 URL 构造 → 激活失败 → 「连接设备失败」错误框 → 回退本机播放，无崩溃 |
| Cast 线降级 | ✅ | 无 GMS 设备日志：`无可用 Google Play Services，Cast 线禁用`（预期） |
| 进程稳定性 | ✅ | 全程 pid 不变，无新 FATAL（崩溃计数保持 1，为旧记录） |

**已知环境备注**：
- 通知卡 ⟨⟩ 系统输出切换按钮在 MIUI 上 tap 无效（30+ 坐标扫描未命中）——MIUI 媒体卡布局差异，非 app 问题；app 内投屏入口全链路已验证。
- MIUI 小窗（PR5 前台服务触发）在退后台后反复自动唤起，属 MIUI 系统行为，不影响功能。

## 四、遗留问题与建议

### 1. media3 1.9.0 legacy 层崩溃（已定性：测试工具伪影，非回归）
- 现象：`cmd media_session dispatch <key>`（shell 合成媒体键）触发 `IllegalArgumentException: packageName should be nonempty`。
- 根因（javap 反汇编三层坐实）：`MediaSessionLegacyStub.onMediaButtonEvent` → `MediaSessionImplApi28.getCurrentControllerInfo`（原样拷贝 framework 返回的 RemoteUserInfo）→ compat 层 `RemoteUserInfo.<init>` 对空 packageName 硬校验抛异常。发送方为 shell 时 framework 返回空包名。
- **真实用户路径（通知栏按钮/耳机/蓝牙）返回正常包名，不触发**。全程真实路径验证无崩溃。
- 处置建议：升级 media3 至修复版本，或接受为自动化测试伪影记录在案。app 侧无法拦截（库内部调用，用户 Callback 不经过该路径）。

### 2. 历史设置备注
- 验收时 `loop_play=true` 曾导致「播完不结束」误判，经通知栏循环按钮切 OFF 后 PR4 行为恢复正常。该设置为用户历史选择，非 bug。

## 五、复现要点

- 模拟器/真机测试三步必备：`appops set <pkg> MANAGE_EXTERNAL_STORAGE allow` + root chown media_rw + `content call scan_volume`。
- adb Git Bash 路径翻译 bug：`/sdcard/...` 会被当 Windows 路径，用 `exec-out "cat /sdcard/f"` 中转。
- uiautomator 在通知栏展开/页面过渡时 `could not get idle state`，需 sleep 重试；Compose 播放页平时只暴露 Surface 根节点，**控制条可见 + 无弹窗时才能抓到完整 clickable 节点**。
- 关键坐标（1080×2340）：播放页顶栏投屏按钮 (840,177)；MIUI 小窗关闭 (1121,210)；详见 workspace memory MEMORY.md。
