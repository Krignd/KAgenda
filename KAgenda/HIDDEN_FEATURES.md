# 已隐藏功能标记（HIDDEN FEATURES）

> 本文件记录**当前已隐藏但未删除**的功能及其残留代码位置，便于日后恢复。
> 原则：只隐藏入口、不改动/不删除实现代码。

## 1. 「网页登录」入口（Visible WebView 登录）

- **隐藏原因**：按需求当前界面仅展示**账密登录**，隐藏网页登录入口。
- **当前状态**：入口隐藏（`SHOW_WEB_LOGIN_ENTRY = false`），**所有相关代码保留未删除**。

### 如何恢复

把下面这个开关改为 `true` 即可（其余代码无需任何改动）：

| 文件 | 位置 |
| --- | --- |
| `app/src/main/java/com/kstudio/agenda/ui/SettingsScreen.kt` | 文件顶部 `private const val SHOW_WEB_LOGIN_ENTRY = false` |

### 残留代码清单（均带 `【已隐藏保留】` 注释标记）

| 文件 | 内容 | 作用 |
| --- | --- | --- |
| `ui/SettingsScreen.kt` | `SHOW_WEB_LOGIN_ENTRY` 开关、`if (SHOW_WEB_LOGIN_ENTRY) { OutlinedButton … }` | 按钮显隐控制 |
| `ui/MainScreen.kt` | `webLoginLauncher` 启动器、`onWebLogin` 回调 | 启动登录页并接收“是否登录成功”结果 |
| `ui/WebLoginActivity.kt` | 整个可见网页登录页（含 SSO 跳转、自动填充、iframe 兜底、CDN 拦截、退出确认弹窗） | 手动完成统一认证 |
| `ui/AppViewModel.kt` | `onWebLoginFinished(loggedIn)` | 登录页返回后提示并按结果触发同步 |
| `data/WebScheduleEngine.kt` | 若干 `LoginRequired` 提示文案已改为中性表述（原为“请使用「网页登录」…”） | 用户提示 |
| `AndroidManifest.xml` | `WebLoginActivity` 组件注册（`exported=false`） | 页面声明 |

### 相关背景（设计说明）

- 网页登录页负责在**可见 WebView** 中完成统一身份认证（支持验证码等需要人工介入的场景）；
- 隐藏入口后，登录唯一路径是：设置页输入**学号 + 密码** → 后台无界面 WebView 自动登录；
- 若统一认证出现验证码/二次确认，自动登录会失败并提示“暂时无法自动完成”；恢复网页登录入口即可人工完成。
