# 按需功能组件

用户约束：无需 Google Play 服务，保留原 ML Kit 中文 OCR 效果；应用内下载后交由系统确认安装，之后可离线使用。

## 拆分边界

| 主包保留 | 可选组件 |
| --- | --- |
| 日程、课表、学校适配、普通文本和 Office 导入、规则解析、云端 AI、本地模型下载管理 | 不依赖组件安装 |
| 截图导入入口与坐标布局还原 | `engine_ocr`：原 ML Kit 中文识别 SDK、识别模型与对应架构的本地库 |
| PDF 导入入口 | `engine_pdf`：原 PDFBox、字体/字符映射与相关依赖 |
| 本地 AI 调度、模型清单与已有权重文件 | `engine_ai`：原 JNI/llama.cpp 运行库；权重仍独立按需下载 |

组件为同一应用的 Android split APK，不另装一个带桌面图标的应用。使用系统 `PackageInstaller.MODE_INHERIT_EXISTING` 添加组件，保留已有主包和数据。用户首次需允许本应用安装包，并在系统页面确认。安装可能重启进程，界面明确提示先保存编辑；文件导入需在安装后重新选择文件，不自动保存半份解析结果。

原 ML Kit 自动 ContentProvider 被禁用，实际执行 OCR 时按官方 API 手动初始化，避免未装组件就触发类加载。是否依赖谷歌服务以禁用 Google Play 服务的真实模拟器测试为准；不能仅凭依赖名作结论。

SDK 的轻量公共资源及已有后台调度回调保留在主包，避免应用升级移除组件后，旧的系统回调引用不存在的类；这些不包含识别模型或 OCR 原生库。

## 下载与安装校验

- 版本化清单：`https://20071009.xyz/KAgenda/engines/<versionCode>/manifest.json`。这是待部署路径，代码变更不代表资源已经上线。
- 更新站尚未部署或无法取得有效清单时，回退同版本 GitHub Release 的 `manifest.json` 及组件附件；两条线路都验证相同的主包绑定和组件哈希。
- 清单 schema=1，限定本应用包名、当前 versionCode、当前主 APK 的 SHA-256；相同版本号的不同构建也不能混装。
- 只下载当前设备 ABI 的组件；PDF 可使用 `any`。每个组件最多8个文件，文件名、路径、大小及哈希均验证。
- 主包不直接加载远程 dex/so。下载到应用私有 `noBackupFilesDir`；WorkManager 提供前台进度、取消和部分文件续传。
- 206 响应必须匹配 Content-Range；服务器返回200时覆盖部分文件。超大、截断、损坏文件拒绝安装；校验不通过不执行代码。
- 安装前重新核对主包、所有组件的完整 SHA-256，以及组件自身的 id 收据。Android 安装器最终强制校验相同包名、签名和版本。
- 用户取消或系统拒绝时保留可重试入口，不影响已有日程。整个下载流程不上传文档、图片、账号或课表。

## 本地构建与部署材料

模块目录使用连字符，Gradle 项目与 split 名称使用下划线。例如 `engine-ocr/` 对应 `:engine_ocr`。

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:stageOptionalEngineRelease :app:lintRelease
python scripts/package_engine_packs.py --variant release --abi universal
```

正式发布使用通用主包，保留全部原有架构支持。Gradle 同批生成动态模块后，按 ABI 移除不需要的原生库、重新完成16KiB ZIP对齐，并在进程内使用已有正式签名重新签署组件；代码、资源及 R8 映射均来自同一通用构建。OCR提供四架构，AI运行库提供两种64位架构，PDF共用。打包脚本会拒绝混入其他架构或主包仍含大引擎的产物。

本地产物位于 `app/build/engine-packs/release/<versionCode>/`，包括组件APK及带文件大小、SHA-256、主包绑定的 `manifest.json`。主 APK 位于原 release 输出目录。发布前必须给修改后的应用使用新的正式版本号，重新整体构建、签名、生成清单；不能将新组件上传到旧单体包的同版本路径后当作完成升级。

架构专用调试构建仍可用 `-Pkagenda.engineAbi=<abi>`；正式组件应使用上面的同批通用构建，不能混合不同源码快照的产物。安装组件与主包更新之间的关联需在真实设备复核；用户数据和模型权重不能因更新被清空。

## 验证要求

1. 主包单独安装：冷启动、纯文本/Office 导入可用；OCR/PDF/本地推理组件缺失时明确引导下载。
2. 生产下载器的隔离 HTTPS 测试：错误主包绑定在请求APK前拒绝，坏哈希清理，206续传、200覆盖，完整校验后才形成缓存收据。
3. 真实系统确认安装：验证签名/版本、取消与重试、安装后的 split 清单；不能用 adb 安装成功冒充用户确认流程通过。
4. Google Play 服务禁用：合成中文课表 OCR 保留课程/周次/位置；PDF 普通文字和 ToUnicode 中文；JNI 可加载。模型推理性能另记，加载成功不是速度验证。
5. Release 单测、lint、签名、主包与组件大小/哈希、资源排除核查。

线上更新站由用户自行部署。GitHub发布附件包含同版本的清单和组件，可作为站点尚未部署时的备用来源。实际验证结果以本地构建报告和发版记录为准。

## 官方依据

- https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#MODE_INHERIT_EXISTING
- https://developers.google.com/android/reference/com/google/mlkit/common/MlKit
- https://developers.google.com/ml-kit/tips/reduce-app-size
- https://developer.android.com/guide/playcore/feature-delivery

这里使用 Android 自身的包安装接口，不调用 Play 的下载服务。
