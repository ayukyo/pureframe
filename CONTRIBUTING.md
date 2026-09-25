# 贡献指南 / Contributing

感谢关注纯帧（PureFrame）！欢迎以 Issue 或 Pull Request 的形式参与贡献。

## 提交 Issue

- **Bug 报告**：请注明设备型号、Android 版本、复现步骤、预期/实际行为，如可能请附 logcat 日志（`adb logcat --pid=$(pidof com.pureframe.player.debug)`）。
- **功能建议**：说明使用场景与期望效果，最好附上简单的交互描述。

## 提交 Pull Request

1. Fork 仓库并从 `main` 创建功能分支：`git checkout -b feat/your-feature`
2. 保持提交信息符合 Conventional Commits 风格（参考现有提交）：
   - `feat(scope): 新功能`
   - `fix(scope): 修复`
   - `refactor(scope): 重构`
   - `docs: 文档`
3. 提交前请确认：
   - [ ] `./gradlew assembleDebug` 编译通过
   - [ ] `./gradlew lintDebug` 无新增错误
   - [ ] 已在真机或模拟器上完成手工验证
4. CI（构建 + Lint）通过后才会被合并。

## 代码约定

- UI 一律使用 Jetpack Compose + Material3，颜色引用 `MaterialTheme.colorScheme`，避免硬编码
- 新增用户偏好走 `UserPreferencesRepository`（DataStore），不要自建存储
- 播放相关改动集中在 `PlayerManager`，UI 层通过 `PlayerViewModel` 访问
- 注释使用中文，说明「为什么」而不仅是「做了什么」

## 安全问题

涉及安全的问题请勿公开发布 Issue，参阅 [SECURITY.md](./SECURITY.md) 的私下报告流程。
