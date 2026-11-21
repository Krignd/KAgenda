# 已隐藏功能说明（HIDDEN FEATURES）

本文件记录目前在界面中隐藏、但仍保留完整实现代码的功能位置，方便日后按需恢复。原则上只控制入口显隐，不删减底层实现。

## 1. 「网页登录」入口（可见 WebView 登录）

- **现状**：当前设置页仅保留账密自动登录入口，网页登录按钮处于隐藏状态（`SHOW_WEB_LOGIN_ENTRY = false`），相关代码均保留在工程中。
- **恢复方式**：将 `app/src/main/java/com/kstudio/agenda/ui/SettingsScreen.kt` 顶部的 `SHOW_WEB_LOGIN_ENTRY` 改为 `true` 即可，其余文件无需改动。

### 相关代码位置（注释中带有 `【已隐藏保留】` 标记）

| 文件 | 内容 | 作用 |
| --- | --- | --- |
| `ui/SettingsScreen.kt` | `SHOW_WEB_LOGIN_ENTRY` 开关及按钮渲染条件 | 控制设置页入口显隐 |
| `ui/MainScreen.kt` | `webLoginLauncher` 启动器与 `onWebLogin` 回调 | 拉起登录页并接收登录状态 |
| `ui/WebLoginActivity.kt` | 可见网页登录页面（SSO 跳转、自动填充、iframe 兜底等） | 用于人工介入完成统一认证 |
| `ui/AppViewModel.kt` | `onWebLoginFinished(loggedIn)` | 登录返回后提示状态并触发同步 |
| `data/WebScheduleEngine.kt` | `LoginRequired` 相关提示文案 | 提示无法自动完成时的中性文案 |
| `AndroidManifest.xml` | `WebLoginActivity` 注册声明（`exported=false`） | 组件声明 |
