package com.kstudio.agenda.data

import android.content.Context
import android.net.Uri
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 数据备份与导入（导出为单个 JSON 文本文件，通过系统文件选择器保存/读取，不需要任何权限）。
 *
 * 备份内容（**只含本机用户数据，不含任何上传行为**）：
 * - `settings`：全部设置（含学号、学校、课程时间、界面/提醒/小组件等偏好）；
 * - `agenda`：日程与计划（`agenda.json`）；
 * - `courseEdits`：课程修改/删除/新增/调课记录（`course_edits.json`）；
 * - `extraCourses`：导入与手动添加的课程（`extra_courses.json`）；
 * - `schedule`：最近一次同步到的课表缓存（`schedule_cache.json`，导入后无需重新登录即可看到课表）。
 *
 * **不包含**：登录密码与 AI Key —— 它们用 Android Keystore（设备硬件密钥）加密，
 * 卸载/换机/清数据后必然解不开，写进备份只会让人误以为“已备份”。
 *
 * 导入是「按键覆盖」：备份里有的设置会盖掉当前值，备份里没有的（密码/AI Key 等）保持原样。
 */
object BackupManager {

    /** 备份文件标识（同时作为文件头字段） */
    const val FORMAT = "kagenda-backup"

    /** 当前备份格式版本（导入时拒绝更新的版本号） */
    const val VERSION = 1

    private const val TAG = "Backup"

    /** 单次导入的大小上限（防止误选了一个大文件把内存吃满） */
    private const val MAX_BYTES = 20 * 1024 * 1024

    /** 不写入备份文件的设置项：密码 / AI Key 的密文（设备密钥保护，换机后必然解不开） */
    private val EXCLUDED_SETTING_KEYS = setOf("password_enc", "ai_key_enc")

    /** 导入结果（文案由界面层翻译，便于三语） */
    sealed interface ImportResult {
        /** @param edits 课程修改/删除/新增记录数；@param courses 导入课程数；@param schedule 是否带回了课表 */
        data class Success(
            val agenda: Int,
            val edits: Int,
            val courses: Int,
            val schedule: Boolean,
        ) : ImportResult

        /** 不是 KAgenda 备份文件 */
        data object NotABackup : ImportResult

        /** 备份格式版本比当前应用新 */
        data class NewerVersion(val version: Int) : ImportResult

        data class Failed(val detail: String) : ImportResult
    }

    // ------------------------------------------------------------ 导出

    /** 收集当前数据生成备份 JSON 文本（[prefs] = 已导出的设置，见 [SettingsStore.exportPreferences]） */
    fun buildJson(context: Context, appVersion: String, prefs: Map<String, Any>): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("app", appVersion)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", settingsJson(prefs))
        putFileIfPresent(root, "agenda", File(context.filesDir, AgendaStore.FILE_NAME))
        putFileIfPresent(root, "courseEdits", File(context.filesDir, CourseEditStore.FILE_NAME))
        putFileIfPresent(root, "extraCourses", File(context.filesDir, ExtraCoursesStore.FILE_NAME))
        putFileIfPresent(root, "schedule", File(context.filesDir, ScheduleCache.SCHEDULE_FILE))
        return root.toString()
    }

    /** 写入全部设置（排除密码/AI Key） */
    private fun settingsJson(prefs: Map<String, Any>): JSONArray {
        val arr = JSONArray()
        prefs.forEach { (key, value) ->
            if (key in EXCLUDED_SETTING_KEYS) return@forEach
            val o = JSONObject().apply { put("k", key) }
            when (value) {
                is Boolean -> {
                    o.put("t", "b")
                    o.put("v", value)
                }
                is Int -> {
                    o.put("t", "i")
                    o.put("v", value)
                }
                is Long -> {
                    o.put("t", "l")
                    o.put("v", value)
                }
                is String -> {
                    o.put("t", "s")
                    o.put("v", value)
                }
                else -> return@forEach
            }
            arr.put(o)
        }
        return arr
    }

    /** 把文件内容（JSON 对象或数组）原样嵌进备份里；文件不存在或不是 JSON 时跳过 */
    private fun putFileIfPresent(root: JSONObject, key: String, file: File) {
        runCatching {
            if (!file.exists()) return
            val text = file.readText(Charsets.UTF_8)
            when {
                text.trimStart().startsWith("[") -> root.put(key, JSONArray(text))
                else -> root.put(key, JSONObject(text))
            }
        }.onFailure { AppLog.e(TAG, "备份打包失败：${file.name}", it) }
    }

    /** 导出到 SAF 目标；返回写入的字节数，失败返回 -1 */
    suspend fun exportTo(context: Context, uri: Uri, appVersion: String): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                val prefs = SettingsStore.exportPreferences(context)
                val bytes = buildJson(context, appVersion, prefs).toByteArray(Charsets.UTF_8)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("no output stream")
                bytes.size.toLong()
            }.onFailure { AppLog.e(TAG, "备份导出失败", it) }.getOrDefault(-1L)
        }

    // ------------------------------------------------------------ 导入

    /** 从 SAF 目标读取并导入 */
    suspend fun importFrom(context: Context, uri: Uri): ImportResult {
        val text = runCatching {
            val size = context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_BYTES) error("too large")
                bytes.toString(Charsets.UTF_8)
            } ?: error("no input stream")
            size
        }.getOrElse { return ImportResult.Failed(it.message ?: "read failed") }
        return import(context, text)
    }

    /** 导入备份文本（会覆盖同名的数据文件） */
    suspend fun import(context: Context, text: String): ImportResult {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return ImportResult.NotABackup
        if (root.optString("format") != FORMAT) return ImportResult.NotABackup
        val version = root.optInt("version", 0)
        if (version <= 0) return ImportResult.NotABackup
        if (version > VERSION) return ImportResult.NewerVersion(version)

        runCatching { SettingsStore.importPreferences(context, parseSettings(root.optJSONArray("settings"))) }
            .onFailure { AppLog.e(TAG, "备份导入：设置写入失败", it) }

        var failed = 0
        failed += writeFile(context, AgendaStore.FILE_NAME, root.opt("agenda"))
        failed += writeFile(context, CourseEditStore.FILE_NAME, root.opt("courseEdits"))
        failed += writeFile(context, ExtraCoursesStore.FILE_NAME, root.opt("extraCourses"))
        failed += writeFile(context, ScheduleCache.SCHEDULE_FILE, root.opt("schedule"))

        // 内存状态失效后重新读盘，界面/提醒/小组件立刻跟上
        AgendaStore.invalidateMemory()
        CourseEditStore.invalidateMemory()
        ExtraCoursesStore.invalidateMemory()
        AgendaStore.ensureLoaded(context)
        CourseEditStore.ensureLoaded(context)
        ExtraCoursesStore.ensureLoaded(context)

        if (failed > 0) {
            AppLog.e(TAG, "备份导入：$failed 个文件写入失败")
            return ImportResult.Failed("write failed ($failed)")
        }
        AppLog.i(
            TAG,
            "备份导入完成：日程/计划 ${AgendaStore.events.value.size} 条，" +
                "课程修正 ${CourseEditStore.edits.value.size} 条，" +
                "导入课程 ${ExtraCoursesStore.courses.value.size} 门",
        )
        return ImportResult.Success(
            agenda = AgendaStore.events.value.size,
            edits = CourseEditStore.edits.value.size,
            courses = ExtraCoursesStore.courses.value.size,
            schedule = root.opt("schedule") != null,
        )
    }

    /** 把备份里的一个数据块写回文件；不存在返回 0，写失败返回 1 */
    private fun writeFile(context: Context, name: String, value: Any?): Int {
        if (value == null) return 0
        return runCatching {
            File(context.filesDir, name).writeText(value.toString(), Charsets.UTF_8)
            0
        }.onFailure { AppLog.e(TAG, "备份导入失败：$name", it) }.getOrDefault(1)
    }

    private fun parseSettings(arr: JSONArray?): Map<String, Any> {
        if (arr == null) return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("k")
            if (key.isBlank() || key in EXCLUDED_SETTING_KEYS) continue
            when (o.optString("t")) {
                "b" -> out[key] = o.optBoolean("v")
                "i" -> out[key] = o.optInt("v")
                "l" -> out[key] = o.optLong("v")
                "s" -> out[key] = o.optString("v")
            }
        }
        return out
    }
}
