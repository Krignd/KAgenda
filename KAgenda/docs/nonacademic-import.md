# 非教务课表导入

设置首页的「课表导入」集中提供教务、网页 AI、截图、文档和粘贴文本入口。教务入口仍调用原来的同步/适配流程。

## 网页

打开「网页 AI 导入」，输入学校的 HTTPS 地址，登录并进入具体课表页，点击「解析当前课表」。提取的是当前 WebView 中已加载的表格内容和可读取的嵌入页，不是让远程 AI 访问网址。保留表格的行、列和跨格信息，不读取 Cookie、浏览器存储或表单值。跨域嵌入页无法直接读取时，打开该页或使用截图。

沿用现有 Android 网络安全配置：未在清单中的明文 HTTP 主机不能绕过限制；可使用学校 HTTPS 地址或截图。不忽略证书错误。

## 截图

选择截图后，内置中文 ML Kit 模型在本机识别文字及坐标；清晰的星期列和节次行用于标注课程单元格。点击 AI 按钮生成标准文本。没有识别到表头时保留坐标证据，不能据此自动保存猜测的课程。

本地 AI 使用用户已下载并启用的模型；没有模型时给出提示，不自动切换云端。云端模式会发送提取的文字与结构，不发送截图原图、Cookie 或密码。所有结果均需在预览中确认。

## 标准文本

```json
{
  "format": "kagenda.schedule",
  "version": 1,
  "firstWeekMonday": "2026-09-07",
  "courses": [
    {
      "title": "高等数学",
      "weekday": 1,
      "startPeriod": 1,
      "endPeriod": 2,
      "weeks": [1, 3, 5, 7],
      "room": "A101",
      "teacher": "张三",
      "code": ""
    }
  ]
}
```

星期一至日为 1–7，节次为 1–24，周次为 1–40。第一教学周日期必须是周一，已有学期时须一致。缺失日期填 null 后由用户填写；缺失周次不能默认为整个学期。AI 中间结果可使用 `weeksRaw` 原文，程序负责展开区间和单双周，成功后转为 `weeks` 数组。

JSON 不完整、表格中部分行无效、同一存储标识存在不同教师/课程名时，确认导入不可用。非 JSON 文本缺失周次时，用户可明确补充周次。匹配的课程分段周次合并；重复导入不重复新增。失败读取清空旧结果，异步识别期间禁止保存旧预览。

文档支持 UTF-8/BOM、UTF-16/BOM、GBK，CSV、TSV、Markdown、Word/Excel/PPT OOXML、HTML 和文本型 PDF。Word/PPT/HTML 保留表格行列；旧版 doc/xls 不支持。图片型 PDF 可截图后导入。导出的 CSV 与 Markdown 可重新导入课程；数据完整备份仍使用原有备份功能。

## 验证

- `:app:testDebugUnitTest`：包含真实回归样例、导出往返、空列/引号、多段/单双周、时间反查、标准格式校验、部分失败保护和 OCR 单元格定位。
- `:app:lintDebug :app:assembleDebug :app:assembleRelease`：检查 Android API 与压缩后的依赖。
- `:app:assembleDebugAndroidTest -Pkagenda.testRunner=com.kstudio.agenda.NonAcademicImportDeviceRunner`。
- 模拟器执行 `am instrument -w -e ai true com.kstudio.agenda.test/com.kstudio.agenda.NonAcademicImportDeviceRunner`：真实内置中文 OCR、普通及中文 CID PDF、生产 WebView 提取、隔离目录写盘/重读/周次合并，以及已有本地 Qwen 模型转换两门课和截图。

设备测试使用合成数据与独立缓存目录，不修改用户课表或调用云端 AI；仅允许模拟器运行。不等于已用真实学校账号验证任意门户，也不能保证任意图片或 AI 模型都能准确识别；结果必须核对。
