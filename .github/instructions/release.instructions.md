---
description: "Use when changing versionName/versionCode, building release APKs, tagging, publishing to GitHub releases, or archiving APK copies for KAgenda."
applyTo: "**/app/build.gradle.kts"
---

# 版本与发布规则

- **未获明确指令（发版 / 更新版本 / 打 tag / 归档）时，不要改 `versionCode`/`versionName`，不要打 tag**；普通改动只改代码 + 构建 + 提交
- 命名逻辑：`versionName = 年份.月份 v序号[.补丁]`（如 `2026.9 v2.0.5`）；`versionCode = 年×10000 + 月×100 + 序号`（如 `20260907`；带补丁号时在原序号上 +1）
- 发版顺序：`:app:lintDebug` 0 错误 → `:app:assembleRelease` → 真机 `adb install -r` 冒烟 → 提交（此时仍不打 tag）→ 用户确认后才打 tag / 发 Release
- 公开仓库（GitHub `Krignd/KAgenda`）更新通道：本地 `main` 保留完整历史（含旧提交与 tags，不推）；公开侧走 `public` 分支。
  **不要用 `git merge --squash main`**（两分支无关历史，即使加 `--allow-unrelated-histories` 也会每个改动文件 add/add 冲突；`git merge --abort` 还会因无 MERGE_HEAD 报错）。
  正确做法（用 main 覆盖 public 工作区，不做历史合并）：
  `git checkout public` → `git reset --hard HEAD` → `git checkout main -- .` → `git diff --stat <public提交> main`（必须为空，确认公开树 == main 树）→ `git commit`（英文提交信息，不含校名）→ `git push origin HEAD:main` → `git push origin HEAD:refs/tags/<tag>`（tag 只能指向公开提交，绝不能推本地 main 提交）→ `git checkout main`
- 发布 Release 用 `gh release create <tag> <apk> --title ... --notes-file ... --target main --latest`；标题与说明**不得出现学校名称**
- 归档：APK 复制到**仓库外**的归档目录，命名 `KAgenda_版本_日期.apk`，并更新该目录的 `归档清单.md`（追加一行并更新"推荐安装"备注）
- **构建产物禁止入库**：`app/release/`、`app/build/`、`*.apk` 一律留在 .gitignore 之外（历史上曾误提交 `app/release/`，需 `git rm -r --cached` 修正）
