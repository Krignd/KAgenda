# KAgenda · K日程

> A local-first timetable & agenda app for Android — built with **vibe coding**.
> 一个「本地优先」的多学校课表 / 日程 Android 应用 —— **纯 Vibe Coding 产物**。
>
> **支持范围**：目前仅支持北航系统。

## 关于本项目 / About

本项目是 **Vibe Coding（氛围编程）** 的实践成果：从需求描述、界面设计到功能实现，
几乎全部通过与 AI 编程助手（GitHub Copilot）的自然语言对话迭代完成；
人类负责提出需求、真机测试与最终拍板。代码库的形态因此非常“对话式”——
你会看到密集的中文注释、按批次演进的功能模块，以及与 AI 结对开发留下的痕迹。

This repository is the result of **vibe coding**: the app was designed and implemented
almost entirely through natural-language conversations with an AI coding assistant
(GitHub Copilot). The human side focused on requirements, real-device testing and decisions.

## 功能特性 / Features

- 📚 **多学校适配框架**：任何学校都可通过一份 JSON 适配代码
  （`KagendaSchoolAdapter/2`：登录脚本 / 页面探针 / 提取脚本）接入，AI 可辅助生成
- 📅 **日 / 周 / 月视图**：左右滑动切换（边界 ±4 年）、节假日标注、当前时间线、课程表 / 时间线双模式
- 🗓️ **本地日程与计划**：类型、颜色、跨天长日程、日历选择器；日历视图一键导出课表图片
- ✨ **AI 快速添加**：粘贴一段通知文字，自动拆解为多条日程 / 计划（需自备 DeepSeek API Key，加密存于本机）
- 🫧 **系统悬浮球**：显示在其他应用上层，拖动自动吸附屏幕侧边，原地唤起 AI 输入框
- ⏰ **提醒与桌面小组件**：课前提醒（精确闹钟 + 降级兜底）、四尺寸桌面小组件、锁屏常驻状态通知
- 🌏 **三语言**：简体中文 / English / Français
- 🔐 **隐私**：本地优先、无账号体系、不上传任何个人数据；AI 功能仅在你配置自己的 Key 后生效

## 技术栈 / Tech Stack

Kotlin · Jetpack Compose (Material 3) · 单 Activity 状态驱动 · DataStore + Keystore 加密 ·
WebView 同步引擎 · WorkManager / AlarmManager · AGP 8.x · minSdk 26（Android 8.0+）

## 构建 / Build

```bash
cd KAgenda
./gradlew :app:assembleRelease   # Windows: .\gradlew.bat :app:assembleRelease
```

要求 JDK 17。Release 包使用 debug 签名，可直接安装体验。

## 自定义学校适配 / Custom School Adapter

在应用内「设置 → 学校 → 添加学校」粘贴一段 JSON 即可接入任意学校（节选字段）：

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
