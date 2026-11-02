# KAgenda · K日程

> A local-first timetable & agenda app for Android — built with **vibe coding**.
> 一个「本地优先」的多学校课表 / 日程 Android 应用 —— **纯 Vibe Coding 产物**。
>
> **独立教务流程**：北航、江大。社区及通用适配见下文，各校实际可用性需账号验证。
>
> **教务导入扩展**：254 组拾光专属适配之外，另从 10 个单校项目接入 13 所学校的 15 个入口；保留 4 个拾光通用脚本与 15 种 WakeUp 旧版解析。已执行 3202 个学校名称的首轮定向查询，后续已确认漏检，仓库交叉复核尚未完成；各校实际可用性待账号验证。[逐校覆盖](KAgenda/docs/academic-import-coverage.md) · [仓库调查与来源](KAgenda/docs/single-school-research/README.md)。

## 关于本项目 / About

本项目是 **Vibe Coding（氛围编程）** 的实践成果：从需求描述、界面设计到功能实现，
几乎全部通过与 AI 编程助手（GitHub Copilot）的自然语言对话迭代完成；
人类负责提出需求、真机测试与最终拍板。代码库的形态因此非常“对话式”——
你会看到密集的中文注释、按批次演进的功能模块，以及与 AI 结对开发留下的痕迹。

This repository is the result of **vibe coding**: the app was designed and implemented
almost entirely through natural-language conversations with an AI coding assistant
(GitHub Copilot). The human side focused on requirements, real-device testing and decisions.

## 功能特性 / Features

- 📚 **多学校适配框架（实验性，可用性存疑）**：学校可通过一份 JSON 适配代码
  （`KagendaSchoolAdapter/2`：登录脚本 / 页面探针 / 提取脚本）接入，AI 可辅助生成；
  但各校教务系统差异很大，通用适配器往往无法直接可用
- 📅 **日 / 周 / 月视图**：左右滑动切换（边界 ±4 年）、节假日标注、当前时间线、课程表 / 时间线双模式
- 🗓️ **本地日程与计划**：类型、颜色、跨天长日程、日历选择器；日历视图一键导出课表图片
- ✨ **AI 助手**：粘贴文字拆解为日程 / 计划，也可修改、删除已有条目。默认本地模式，安装包不含模型；可在设置中自选下载 Qwen3 轻量版（约 397MB）或标准版（约 1.11GB），下载后离线识别。未下载时保留规则解析；也可主动选择 DeepSeek 云端模式。
- 🫧 **系统悬浮球**：显示在其他应用上层，拖动自动吸附屏幕侧边，原地唤起 AI 输入框
- ⏰ **提醒与桌面小组件**：课前提醒（精确闹钟 + 降级兜底）、多尺寸桌面小组件（含今日与周概览）、锁屏常驻状态通知
- 🌏 **三语言**：简体中文 / English / Français
- 🏫 **学校目录与社区导入**：全屏搜索教育部内地高校目录，支持拼音、首字母搜索和右侧 A–Z 点按/滑动索引；右侧区分独立适配、拾光仓库及未支持。社区脚本先手动登录、提取，再核对课程与作息后确认导入，各校实际可用性需账号验证
- 🔐 **隐私**：本地优先、无账号体系。本地 AI 模式不上传识别文本，也不会自动回退云端；主动选择 DeepSeek 云端模式后，识别会发送输入文本及必要日程字段。实验性学校适配代码生成仍使用云端。

## 技术栈 / Tech Stack

Kotlin · Jetpack Compose (Material 3) · 单 Activity 状态驱动 · DataStore + Keystore 加密 ·
WebView 同步引擎 · WorkManager / AlarmManager · AGP 8.x · minSdk 26（Android 8.0+）

## 构建 / Build

```bash
cd KAgenda
./gradlew :app:assembleRelease   # Windows: .\gradlew.bat :app:assembleRelease
```

要求 JDK 17。Release 优先使用本机配置的正式签名；未配置时回退 debug 签名。

本地 AI 运行组件需要 Android NDK `27.2.12479018`、CMake `3.22.1`。首次原生构建从官方仓库获取固定提交的 llama.cpp 源码并校验 SHA-256；模型权重不参与构建。离线构建可传 `-Pkagenda.llamaSource=/absolute/path/to/llama.cpp`，源码应为 `a7a98e0fffed794396b3fbad4dcdbbc184963645`。本地推理仅支持 64 位进程，32 位设备仍可使用其余功能、规则解析和云端识别。

设置 → 功能&权限 → AI 识别：模型由用户主动下载，默认仅使用不限流量网络，可取消、继续或删除。下载固定版本的 Q4_K_M 文件，大小与 SHA-256 均匹配后才能启用；文件保存在应用私有的 `noBackupFilesDir/local-ai`，不进入系统备份或导出的日程备份。建议轻量版使用 6GB 及以上物理内存，标准版使用 8GB 及以上，实际延迟与准确率仍需真机验证。模型每次识别后释放，按输入长度分配上下文缓存；明确的简单新增、唯一标题的简单修改/删除优先使用快速规则，不加载模型。重复、跨天、重名、条件分支和需调整结束时间的操作继续交给模型或手动编辑。模型上下文只序列化最多 8 条相关日程；本地输入限制为 1600 字符，超长内容需分批识别。

## 自定义学校适配 / Custom School Adapter

> ⚠️ **实验性功能，可用性存疑**：各校教务系统的页面结构差异很大，通用适配器（含 AI 生成）
> 往往无法直接可用，可能需要自行修改脚本并反复真机调试。入口保留供自行尝试，不保证成功。

在应用内「设置 → 学校 → 添加学校」粘贴一段 JSON 即可尝试接入（节选字段）：

```json
{
  "format": "KagendaSchoolAdapter/2",
  "name": "我的大学",
  "homeUrl": "https://jw.example.edu.cn/schedule",
  "probeJs": "return JSON.stringify({ready: !!document.querySelector('#grid'), login: !!document.querySelector('input[type=password]'), error: '', netError: false})",
  "loginJs": "var u=document.querySelector('#username'); var p=document.querySelector('#password'); if(u&&p){u.value=window.__kagendaUser; p.value=window.__kagendaPass; document.querySelector('form').submit(); return 'ok';}",
  "waitSelector": "#grid",
  "extractJs": "return JSON.stringify([{title:'高等数学',day:1,start:1,end:2,room:'C101',teacher:'张三',weeks:'2-16'}])"
}
```

## 免责声明 / Disclaimer

本项目仅用于个人学习与课表自动化管理；同步能力依赖各学校网页结构，可能随学校改版失效。

## 许可证 / License

本项目采用 **MIT License** 开源，详见仓库根目录的 [`LICENSE`](LICENSE)。

This project is licensed under the **MIT License** — see the [`LICENSE`](LICENSE) file for details.

Bundled community scripts and emblem fonts retain their original licenses and author credits; see [third-party notices](THIRD_PARTY_NOTICES.md).
