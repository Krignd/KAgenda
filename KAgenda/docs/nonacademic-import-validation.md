# 非教务导入验证记录（2026-10-20）

- 单元测试：245 项，失败 0、错误 0。
- Debug 与 Release 构建通过；Release 已执行 R8 压缩。
- Lint：0 errors（现有及依赖警告见 app/build/reports/lint-results-debug.html）。
- `NonAcademicImportDeviceRunner` 在 emulator-5554 以 `-e ai true` 执行通过：真实内置中文 OCR；普通与中文 CID/ToUnicode PDF；生产 WebView 表格跨格和表单排除；独立目录保存后重读、周次并集、重复/冲突保护；本地 Qwen 0.6B 两门课与截图转换，核对星期、起止节次和周次。
- 实测发现轻量模型复制示例周次，已改为抄录周次原文并由程序展开；截图用表头/坐标证据校验字段，无可靠表头时强制补齐，缺失周次不自动扩展。
- 截图测试使用可读的合成双列课表。小字、粘连、漏字或缺失表头可能需要编辑原文/标准文本，不能据此保证任意图片准确率。
- 不调用云端 API，不使用真实学校账号，不修改真实手机或模拟器用户课表。
- 实际界面检查：设置首页入口可见；从 ACTION_SEND 接收标准 JSON 后正确预览中文课程、星期、节次、时间、周次和教师，确认按钮可用。预览后取消，未写入用户课表。截图位于 build/import-final-preview.png。
- 版本保持 versionCode=20261009，versionName=2026.10 v1.2(pre3)。

Release APK：`app/build/outputs/apk/release/app-release.apk`，63765203 字节。
SHA-256：`626f16af1a326a302b6bdc5e15ac50d021a581a3e5cb47ce23631438b36250fa`。

功能与格式说明见 [nonacademic-import.md](nonacademic-import.md)。

## 设置 UI 调整

课表导入位于设置首页「学校&账号」正下方；进入后使用同一个 SettingsSubHeader、分隔线、16dp 间距和 CollapsibleSectionCard，并保留底部导航。模拟器实际检查顺序、返回和折叠/展开通过；Debug/Release 构建与现有回归测试通过。已保留数据更新到手机 cef0662f，版本号未变。界面对比截图：build/import-ui-account.png、build/import-ui-inline.png。
