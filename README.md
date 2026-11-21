# KAgenda · K日程

一个本地优先的 Android 课表与日程应用，使用 Kotlin 和 Jetpack Compose 编写，最低支持 Android 8.0（API 26）。

这个项目最初是我配合 AI 助手（GitHub Copilot 等）通过自然语言对话一点点搭出来的：需求整理、真机测试和具体取舍由我来做，代码实现主要由 AI 辅助完成，因此仓库里保留了不少偏口语的中文注释和分阶段演进的痕迹。

> **教务支持范围说明**：独立教务流程支持北航、江大。此外还收录了教育部高校目录检索，以及来自拾光课程表、WakeUp 和部分单校开源项目的适配脚本与通用解析方案。除作者自测过的少数学校外，绝大多数社区脚本未经过真实账号逐一验证，实际能否导入需要自行实测。详见 [教务导入覆盖清单](KAgenda/docs/academic-import-coverage.md) 与 [单校适配调研记录](KAgenda/docs/single-school-research/README.md)。

## 主要功能

- **日 / 周 / 月视图**：支持左右手势翻页（范围 ±4 年），提供「课程表模式」与「真实时间线模式」切换、当前时间线以及节假日与调休标注。
- **日程、计划与进行中**：除课表外，可单独记录个人日程与计划；「进行中」页面集中展示跨天安排和带状态标签的待办。支持重复规则、本地调课/改课覆盖，以及把日/周/月课表导出为图片、CSV 或 Markdown。
- **多种导入方式**：除教务系统同步外，还支持从网页、课表截图（本机 OCR）、文档（Word / Excel / PPT / PDF / Markdown / CSV / HTML）和粘贴文本导入课程，保存前均可预览并处理冲突。
- **AI 识别与悬浮球**：可以把通知或一段文字拆解为日程或计划，也支持修改、删除已有条目。默认使用本地规则和可选下载的离线 Qwen3 模型（轻量版约 397 MB，标准版约 1.11 GB），也可以在设置中手动切换到 DeepSeek 云端模式。开启系统悬浮球后，可在任意界面呼出输入框。
- **提醒与桌面小组件**：支持课前与日程提醒（优先使用精确闹钟，未授权时降级兜底）、多尺寸桌面小组件和锁屏常驻状态通知。
- **按需组件与三语言**：主安装包约 6.5 MB，OCR、PDF 读取和本地 AI 运行库在首次使用时按需下载，不依赖 Google Play 服务。界面支持简体中文、English 和 Français。

## 隐私说明

应用采用本地优先设计，没有账号体系。教务登录凭据通过 Android Keystore 加密后仅保存在本机。选择本地 AI 或规则解析时，输入文本不会上传；仅当手动切换为 DeepSeek 云端模式（或使用实验性的 AI 生成学校适配脚本）时，才会将必要文本发送至对应接口。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Dynamic Feature 按需组件 · DataStore + Android Keystore · WebView 同步引擎 · WorkManager / AlarmManager · minSdk 26 / targetSdk 35

## 构建

```bash
cd KAgenda
.\gradlew.bat :app:assembleRelease   # Linux / macOS: ./gradlew :app:assembleRelease
```

构建需要 JDK 17 和 Android SDK Platform 35（Gradle wrapper 默认配置了腾讯镜像）。如果在 `KAgenda/local.properties` 中未填写正式签名配置，Release 会自动回退到 debug 签名，方便直接装到设备上测试。

如果连带编译本地 AI 原生组件（`:engine_ai`），还需要 Android NDK `27.2.12479018` 和 CMake `3.22.1`。首次原生构建会从官方仓库拉取固定提交的 `llama.cpp` 源码（`a7a98e0fffed794396b3fbad4dcdbbc184963645`）并校验哈希；离线环境可通过 `-Pkagenda.llamaSource=/absolute/path/to/llama.cpp` 指定本地源码路径。本地推理仅支持 64 位设备，32 位设备仍可正常使用其余功能、规则解析和云端模式。

更多构建细节、模块结构与按需组件说明见 [KAgenda 开发文档](KAgenda/README.md) 和 [按需功能组件说明](KAgenda/docs/optional-engines.md)。

## 自定义学校适配（实验性）

在「设置 → 学校 → 添加学校」中可以粘贴 JSON 适配脚本自行调试接入。不过各校教务系统差异很大，通用或 AI 生成的脚本往往需要对着真实页面反复调整，不保证开箱可用。

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

## 免责声明与许可证

本项目仅用于个人课表与日程管理学习交流；教务同步功能依赖各校网页结构，若学校系统改版可能会失效。

代码采用 [MIT License](LICENSE) 开源。内置的社区适配脚本与校徽字体各自保留原许可证及作者署名，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。
