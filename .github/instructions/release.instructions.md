---
description: "Use when changing versionName/versionCode, building release APKs, tagging, publishing to GitHub releases, or archiving APK copies for KAgenda."
applyTo: "**/app/build.gradle.kts"
---

# 版本与发布规则

- **未获明确指令（发版 / 更新版本 / 打 tag / 归档）时，不要改 `versionCode`/`versionName`，不要打 tag**；普通改动只改代码 + 构建 + 提交
- 命名逻辑：`versionName = 年份.月份 v序号[.补丁]`（如 `2026.9 v2.0.5`）；`versionCode = 年×10000 + 月×100 + 序号`（如 `20260907`；带补丁号时在原序号上 +1）
- **预览版 / 修订版后缀（2026-09-29 用户明确纠正，务必照此执行）**：
  - **正式版**：无后缀，如 `2026.9 v2.0.8`
  - **预览版**：用户要求加功能、能跑但**用户还没自己检查**时 → 版本号后加 `(preN)`（N 从 1 递增），如 `2026.9 v2.0.8(pre1)`
  - 预览版经**用户检查并修完 bug** → 转正式版，**去掉后缀**（`2026.9 v2.0.8`）
  - **修订版**：正式版之后用户又发现需要修改的地方 → 版本号后加 `(revN)`，如 `2026.9 v2.0.8(rev1)`
  - **`(revN)` 只接在版本号后，绝不叠加在 `(preN)` 之后**；预览阶段自己发现的修复直接留在同一个 `(preN)` 里
    （曾把 `(pre1)` + 自检修复错写成 `2026.9 v2.0.8(pre1)(rev1)`，已纠正为 `2026.9 v2.0.8(pre1)`）
  - tag 不带括号：`2026.9-v2.0.8-pre1`
- **tag 命名：`<年份>.<月份>-v<序号>`，不加 `v` 前缀**（如 `2026.9-v2.0.6`；2026-09-22 用户明确要求去掉前缀）。历史 tag（`v1.x`、`v2026.9-v2`、`v2026.9-v2.0.1` ~ `v2026.9-v2.0.5`）保持不变，不要重命名
- 发版顺序：`:app:lintDebug` 0 错误 → `:app:assembleRelease` → 真机 `adb install -r` 冒烟 → 提交（此时仍不打 tag）→ 用户确认后才打 tag / 发 Release
- 公开仓库（GitHub `Krignd/KAgenda`）更新通道：本地 `main` 保留完整历史（含旧提交与 tags，不推）；公开侧走 `public` 分支。
  **不要用 `git merge --squash main`**（两分支无关历史，即使加 `--allow-unrelated-histories` 也会每个改动文件 add/add 冲突；`git merge --abort` 还会因无 MERGE_HEAD 报错）。
  正确做法（用 main 覆盖 public 工作区，不做历史合并）：
  `git checkout public` → `git reset --hard HEAD` → `git checkout main -- .` → `git diff --stat <public提交> main`（必须为空，确认公开树 == main 树）→ `git commit`（英文提交信息，不含校名）→ `git push origin HEAD:main` → `git push origin HEAD:refs/tags/<tag>`（tag 只能指向公开提交，绝不能推本地 main 提交）→ `git checkout main`
- 发布 Release 用 `gh release create <tag> <apk> --title ... --notes-file ... --target main --latest`；标题与说明**不得出现学校名称**
- **Release 说明里的 bug 修复措辞（2026-09-28 用户要求）**：
  - 一般 bug 修复**只需一句**「修复了一些 bug」或等价的英文表述（如 `Fixed several bugs`），不要把逐条细节写进公开面；
  - 只有**特别重大**的 bug（如数据丢失、无法启动、账号风险）才写具体描述；
  - 新功能仍可正常逐条列出（只有 bug 修复才做概括）。
- **迭代记录纪律（2026-09-28 用户要求，同日经用户纠正）**：
  - 以后每次迭代**只改代码**；README 等文档不要跟着改用户可见功能，确有需要**放到最后一步**再改；
  - **必须**把本轮完整细节写进项目内的 `对话记录\`（`项目大事记.md` 记批次结论、`buaa-kebiao-notes.md` 记详细条目、
    `历史请求清单.md` 记请求原文，当轮完整记录单独成文件）；同时把结论写进 AI 记忆（`/memories/`）。
    （2026-09-28 曾一度误认为「不要写本地对话记录」并裁剪过记录，**已纠正**——不要按旧说法执行）
  - 「日程表」模块的改动必须同时评估并同步「计划」模块。
- 归档：APK 复制到**仓库外**的归档目录，命名 `KAgenda_版本_日期.apk`，并更新该目录的 `归档清单.md`（追加一行并更新"推荐安装"备注）
- **构建产物禁止入库**：`app/release/`、`app/build/`、`*.apk` 一律留在 .gitignore 之外（历史上曾误提交 `app/release/`，需 `git rm -r --cached` 修正）

## release 签名（2026-10-04 起）

- 私钥：`<仓库根>/krignd-release-key.jks`；**口令不进代码、不进 Git、不进聊天**，写在 `KAgenda/local.properties`（已被 .gitignore）里的四个键：
  `KAGENDA_STORE_FILE` / `KAGENDA_STORE_PASSWORD` / `KAGENDA_KEY_ALIAS` / `KAGENDA_KEY_PASSWORD`
- 四项齐全 → release 用正式签名；一项都没有 → 退回 debug 签名（保证别人克隆公开仓库能直接构建）；
  **只填一半 → 构建直接报错**（这条是故意的，防止把 debug 签名包当正式包发出去）
- `.gitignore` 必须始终包含 `*.jks` / `*.keystore` / `keystore.properties`；新增任何私钥文件后先 `git check-ignore -v <文件>` 确认
- 校验签名：
  `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
  并与 `keytool -list -v -keystore krignd-release-key.jks` 的 SHA-256 指纹比对
- **换签名 = 一次性断层**：签名变化的包无法覆盖安装（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），老用户必须先卸载（本地数据会清空）。
  换签名的那次发版要在 Release 说明里写明"需先卸载旧版再安装"。
- **私钥务必备份**（仓库外，如密码管理器 + 离线副本）：丢失后所有已安装用户都无法再收到升级
