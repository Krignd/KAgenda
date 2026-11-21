# KAgenda（K日程）开发与功能说明

本目录是 KAgenda 的 Android 工程根目录（基于 Kotlin 与 Jetpack Compose，支持 Android 8.0 / API 26 及以上）。

> **教务支持范围**：独立教务流程支持北航、江大；同时内置高校目录检索与拾光、WakeUp 及部分单校开源项目的适配脚本与通用解析后备。社区和通用脚本未经过逐校真实账号实测，实际可用性以各自教务系统为准。详情见 [教务导入覆盖清单](docs/academic-import-coverage.md)、[非教务导入说明](docs/nonacademic-import.md)、[按需功能组件](docs/optional-engines.md) 与 [第三方声明](../THIRD_PARTY_NOTICES.md)。

---

## 功能概况

- **课表同步与学校目录**：内置高校目录（支持拼音、首字母和右侧 A–Z 索引）。独立教务流程通过内置 WebView 保持真实登录会话并注入脚本提取课表，优先读取接口结构化数据，失败时回退 DOM 提取；社区方案在可见浏览器内登录后预览并确认导入；保留实验性的 JSON 自定义适配入口。
- **日 / 周 / 月视图**：支持「课程表模式」（按节次等高显示）与「真实时间线模式」（按实际起止时间排布，课程与个人日程混排，并标出当前时间线）。周视图可选默认显示 5 天或 7 天；内置法定节假日表并支持从通知更新调休放假。
- **个人日程、计划与本地改课**：区分「日程表」「计划」与「进行中」页面，支持跨天长日程、重复规则（按天 / 按周 / 隔周 / 每月，可设截止日期）以及「延后 / 提前 / 等待通知」状态标签。教务未及时更新时可直接在本地修改单次或整门课程，后续同步时自动比对并提示冲突选择。
- **非教务导入**：支持从网页、课表截图（本机 ML Kit 中文 OCR）、Word / Excel / PPT（OOXML）/ PDF / Markdown / CSV / HTML 文档以及粘贴文本导入课程，导入前均可预览并校验周次和节次。
- **AI 识别与系统悬浮球**：可将通知或聊天文字拆解为日程或计划，并支持修改与删除已有条目。默认使用规则解析和按需下载的本地 Qwen3 模型（轻量版约 397 MB，标准版约 1.11 GB，保存在应用私有目录，推理在本地完成）；也可在设置中手动启用 DeepSeek 云端模式。开启悬浮球后可在其他应用界面呼出输入框。
- **提醒、桌面小组件与常驻通知**：支持课前与日程提醒（优先使用 AlarmManager 精确闹钟，未授权时降级兜底，配合 WorkManager 每 6 小时重排与开机恢复）。提供今日/周概览、今日/周课表及「下一节课」（2×1 / 2×2 / 4×1 / 4×2）小组件（预览图见 [widget-previews.png](design/widget-previews.png) 和 [legacy-widget-previews.png](design/legacy-widget-previews.png)），以及可自选显示内容的锁屏常驻状态通知。
- **图片 / 文档导出与外观**：日 / 周 / 月视图均可通过 Canvas 完整绘制并导出 1080px 宽图片到相册（`Pictures/K日程`），同时支持导出为可重新导入的 CSV 与 Markdown。提供默认简约与液态玻璃两种界面风格，支持 简体中文 / English / Français 切换。
- **开发者工具与隐藏入口**：设置内提供运行日志查看/分享/清空、通知与闹钟链路测试、内置普通浏览器和分段重置功能。手动「网页登录」按钮默认隐藏，恢复方法见 [HIDDEN_FEATURES.md](HIDDEN_FEATURES.md)。

---

## 环境要求与构建

### 基础环境

- **JDK 17**（Android Studio 自带 JDK 21 亦可正常以 Java 17 目标编译）
- **Android SDK**：`compileSdk 35`、`targetSdk 35`、`minSdk 26`（Android 8.0+）
- **Gradle Wrapper**：工程已配置镜像仓库（`settings.gradle.kts`），直接通过 `gradlew` 构建即可，无需手动安装第三方库。

主要依赖版本（集中管理于 `gradle/libs.versions.toml`）：

| 类别 | 组件 | 版本 |
| --- | --- | --- |
| 构建工具 | Android Gradle Plugin / Kotlin | 8.13.0 / 2.0.21 |
| 界面 | Jetpack Compose BOM / Material 3 / Haze | 2024.12.01 / 1.1.1 |
| 生命周期与宿主 | activity-compose / lifecycle | 1.9.3 / 2.8.7 |
| 存储与后台 | datastore-preferences / work-runtime-ktx | 1.1.1 / 2.10.0 |
| 本地解析与网页 | jsoup / gson | 1.23.2 / 2.11.0 |

### 可选组件（Dynamic Feature）的额外要求

工程拆分为主包 `:app` 与三个按需动态模块：`:engine_ocr`（`engine-ocr/`）、`:engine_pdf`（`engine-pdf/`）、`:engine_ai`（`engine-ai/`）。

- 如果需要构建或打包 `:engine_ai`，需安装 **Android NDK `27.2.12479018`** 与 **CMake `3.22.1`**。首次原生构建会自动获取固定提交的 `llama.cpp` 源码（`a7a98e0fffed794396b3fbad4dcdbbc184963645`）并校验 SHA-256；离线构建可附加参数 `-Pkagenda.llamaSource=/absolute/path/to/llama.cpp`。
- 本地 AI 原生推理仅支持 64 位架构（`arm64-v8a`、`x86_64`）；OCR 模块覆盖 4 种常见架构；PDF 模块架构通用。

### 常用构建命令

在 `KAgenda/` 目录（即本文件所在目录）下执行：

```powershell
# 构建 Release APK（未配置正式签名时自动回退 debug 签名）
.\gradlew.bat :app:assembleRelease

# 运行 Lint 与单元测试
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest

# 构建并按 ABI 打包按需组件（需要完整签名配置，详见 docs/optional-engines.md）
.\gradlew.bat :app:stageOptionalEngineRelease
python scripts/package_engine_packs.py --variant release --abi universal
```

关于签名：若在 `KAgenda/local.properties`（已被 `.gitignore` 忽略）中完整填写 `KAGENDA_STORE_FILE`、`KAGENDA_STORE_PASSWORD`、`KAGENDA_KEY_ALIAS`、`KAGENDA_KEY_PASSWORD`，Release 会使用正式签名；四项均留空时自动回退到 debug 签名（方便直接安装覆盖调试包）；如果只填了一部分，Gradle 会直接报错中断，避免误把半配置包当成正式版发出。

---

## 代码目录结构

```text
KAgenda/
├─ app/src/main/java/com/kstudio/agenda/
│  ├─ KebiaoApp.kt                应用入口：通知渠道、冷启动初始化与周期任务
│  ├─ data/                       WebView 同步引擎、脚本注入、教务/文档/截图/AI 解析、本地存储与加密、按需引擎下载
│  ├─ model/                      课程与日程模型、作息表（PeriodTimes）、节假日与学校目录索引
│  ├─ notif/                      AlarmManager 闹钟调度、课前通知、常驻通知、WorkManager 兜底与开机恢复
│  ├─ export/                     Canvas 课表图片渲染（ScheduleImageRenderer）与相册导出（ImageExporter）
│  ├─ overlay/                    系统悬浮球服务（FloatingBallService）
│  ├─ widget/                     桌面小组件 Provider、刷新调度与外观配置
│  ├─ i18n/                       中 / 英 / 法三语文本与帮助内容
│  └─ ui/                         Compose 页面（Schedule / Plan / Ongoing / Week / Month / Settings / DocumentImport 等）
├─ engine-ocr/                    按需模块：中文 ML Kit OCR 识别引擎
├─ engine-pdf/                    按需模块：PDFBox 文档文本解析引擎
├─ engine-ai/                     按需模块：基于 llama.cpp 的 JNI 本地推理引擎
├─ scripts/                       学校拼音生成、组件打包与适配器测试脚本
└─ docs/                          覆盖清单、非教务导入与按需组件技术文档
```

---

## 默认课程作息表

内置默认作息为 1–14 节（定义于 `model/PeriodTimes.kt`），也可在「设置 → 用户自定义 → 课程时间」中自由调整起止时间或增删节数（支持 1–24 节），并支持一键恢复默认。

| 节次 | 时间 | 节次 | 时间 |
| --- | --- | --- | --- |
| 1 | 08:00-08:45 | 8 | 15:50-16:35 |
| 2 | 08:50-09:35 | 9 | 16:40-17:25 |
| 3 | 09:50-10:35 | 10 | 17:30-18:15 |
| 4 | 10:40-11:25 | 11 | 19:00-19:45 |
| 5 | 11:30-12:15 | 12 | 19:50-20:35 |
| 6 | 14:00-14:45 | 13 | 20:40-21:25 |
| 7 | 14:50-15:35 | 14 | 21:30-22:15 |

---

## 常见问题与排查

- **后台收不到提醒或提醒延迟**：前往「设置 → 功能&权限 → 权限设置」检查通知权限、精确闹钟授权以及电池优化豁免；部分定制系统还需要在系统设置里允许应用自启动或后台运行。
- **教务自动登录提示需要人工验证**：当统一身份认证触发滑块、图片验证码或短信二次认证时，无界面自动登录无法自行完成；可稍后重试，或使用支持可见浏览器登录的入口，也可以通过截图/文档/网页 AI 方式导入课表。
- **教务页面正常但同步失败**：学校教务改版后提取规则可能失效，可在「设置 → 关于 → 开发者工具」查看并导出运行日志，结合 `data/JsScripts.kt` 或学校对应流程文件调整选择器。
- **本地 AI 与截图导入误差**：本地小模型推理或模糊截图 OCR 可能漏字、错位或遗漏周次，导入预览页允许手动核对并补全信息后再保存。

---

## 许可证

本项目采用 **MIT License** 开源，详见仓库根目录的 [`LICENSE`](../LICENSE)。
