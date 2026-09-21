# KAgenda · 项目协作指南（Agent Instructions）

> 本文件自动加载到本工作区的每次对话。只写"每次都必须生效"的规则；细节见本地（不入库）的 `对话记录/项目大事记.md`。

## 仓库结构（最容易踩的坑）

- **工作区根 = git 仓库根 = `KAgenda/`；Android 工程在其同名子目录 `KAgenda/KAgenda/`** → 构建或改 Gradle 之前先 `cd KAgenda`
- 源码在 `app/src/main/java/com/kstudio/agenda/`，包名 `com.kstudio.agenda`：
  - `data/`：`WebScheduleEngine`（WebView 同步引擎）、`JsScripts`（注入脚本）、`ScheduleParser`、`AgendaStore`（本地日程/计划）、`Schools`（多校适配器 v2）、`AiClient`（AI 识别）
  - `ui/`（Compose 界面 + `AppViewModel` 单 Activity 状态驱动）、`i18n/AppText.kt`（三语）、`notif/`（提醒/常驻通知）、`widget/`（桌面小组件）、`export/`（课表导出图片）、`overlay/`（系统悬浮球）
- 不入库（见 `.gitignore`）：构建产物、`.gradle/.idea`、`local.properties`、`对话记录/`、`相关文件/`、`日志/`、`/log.md`、`*.zip`
- 已隐藏功能与恢复方法：`KAgenda/HIDDEN_FEATURES.md`（原则：只隐藏入口，不删实现）

## 构建 / 安装 / 校验

```bash
cd KAgenda
.\gradlew.bat :app:assembleRelease   # release 用 debug 签名，可直接安装（约 2MB）
.\gradlew.bat :app:lintDebug         # 发版前必跑：NewApi 必须 0 错误
```

- 产物：`KAgenda/app/build/outputs/apk/release/app-release.apk`
- 真机安装：`adb install -r <apk>`（adb 在 `%LOCALAPPDATA%\Android\Sdk\platform-tools`）
- JDK 17；Gradle wrapper 走腾讯镜像，**不要改回官方源**
- 改完代码的默认验证 = 构建通过 + 装真机冒烟（用户会在真机上实测）

## 硬性约定

- **版本号只在用户明确要求「发版 / 更新版本 / 打 tag / 归档」时改**；其余改动只改代码 + 构建 + 提交
- 版本命名：`versionName = 年份.月份 v序号[.补丁]`（如 `2026.9 v2.0.5`）；`versionCode = 年×10000 + 月×100 + 序号`（补丁号在原序号上 +1）
- **公开仓库文案不得出现学校名称**；README 里已有的那一句支持范围说明保持原样，不要新增
- 任何用户可见文案必须**三语同步**：`i18n/AppText.kt`（Zh/En/Fr）+ `res/values{,-zh,-fr}/strings.xml`
- 提交信息用中文，风格 `<版本或批次>：<要点>`；不加 tag（除非明确要求）
- **不要为了脱敏去改代码**（学校域名等属功能必需）
- 密钥与隐私：内置 API Key 不得以明文出现在源码 / APK / 日志中；日志需遮蔽密钥；不上传任何个人信息
- **安装后不主动申请权限**：权限在真正用到时逐项申请
- 注释、文案风格沿用既有中文注释风格

## 禁止回退（历史已定稿，勿"优化"回去）

1. 设置页「使用开发者的 API key」**默认勾选**（内置 Key 开箱即用）
2. 学校列表**只显示校名**，不加「已适配 / 示例（未适配）」之类文案
3. **应用启动器图标**保持现状，不要改动
4. 内置 Key 对应的默认模型固定 `deepseek-flash`
5. APK 等构建产物**不入库**；归档只放仓库外的归档目录
6. 新增功能不得以"申请一堆权限"为代价（权限收敛原则）

## 本地资料（不入库，需要时主动查阅）

- `对话记录/项目大事记.md`：批次 / 版本索引（提交号、tag、当天做了什么）
- `对话记录/历史请求清单.md`：3 个会话 118 条用户请求全文（搜"当时提了什么要求"）
- `对话记录/buaa-kebiao-notes.md`：详细流水账（含行号级备忘）
- `对话记录/*.jsonl`：历史对话全文（可 grep / 解析，65MB 级别）
- `相关文件/`：教务页面快照、通知范例、构建指南与提示词
