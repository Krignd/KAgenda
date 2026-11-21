package com.kstudio.agenda.data

import android.content.Context
import android.net.Uri
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException

/**
 * 数据备份与导入（新备份为单个 ZIP，通过系统文件选择器保存/读取）。
 *
 * 备份内容（**只含本机用户数据，不含任何上传行为**）：
 * - `settings`：全部设置（含学号、学校、课程时间、界面/提醒/小组件等偏好）；
 * - `agenda`：日程与计划（`agenda.json`）；
 * - `courseEdits`：课程修改/删除/新增/调课记录（`course_edits.json`）；
 * - `extraCourses`：导入与手动添加的课程（`extra_courses.json`）；
 * - `schedule`：最近一次同步到的课表缓存（`schedule_cache.json`，导入后无需重新登录即可看到课表）。
 * - `planner`：底栏、小组件点击动作、课程标签、倒计时、账单、作业和截止时间。ZIP 同时携带原图。
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
    const val VERSION = 2

    private const val TAG = "Backup"

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
    private fun buildJson(context: Context, appVersion: String, prefs: Map<String, Any>, planner: PlannerData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("app", appVersion)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", settingsJson(prefs))
        root.put("planner", JSONObject(PlannerStore.encode(planner)))
        putFileIfPresent(root, "agenda", File(context.filesDir, AgendaStore.FILE_NAME))
        putFileIfPresent(root, "courseEdits", File(context.filesDir, CourseEditStore.FILE_NAME))
        putFileIfPresent(root, "extraCourses", File(context.filesDir, ExtraCoursesStore.FILE_NAME))
        putFileIfPresent(root, "schedule", File(context.filesDir, ScheduleCache.SCHEDULE_FILE))
        putFileIfPresent(root, "locations", File(context.filesDir, SchoolLocationsStore.FILE_NAME))
        putFileIfPresent(root, "holidays", File(context.filesDir, OfficialHolidayStore.FILE_NAME))
        val schoolSchedules = JSONObject()
        val schoolExtraCourses = JSONObject()
        File(context.filesDir, "school_data").listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[a-zA-Z0-9_-]+")) }?.forEach { directory ->
            putFileIfPresent(schoolSchedules, directory.name, File(directory, ScheduleCache.SCHEDULE_FILE))
            putFileIfPresent(schoolExtraCourses, directory.name, File(directory, ExtraCoursesStore.FILE_NAME))
        }
        root.put("schoolSchedules", schoolSchedules)
        root.put("schoolExtraCourses", schoolExtraCourses)
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

    private suspend fun <T> withArchive(context: Context, appVersion: String, write: (File) -> T): T =
        withContext(Dispatchers.IO) {
            val prefs = SettingsStore.exportPreferences(context)
            val file = File.createTempFile("kagenda-backup-", ".zip", context.cacheDir)
            try {
                PlannerStore.withSnapshot(context) { planner ->
                    val metadata = buildJson(context, appVersion, prefs, planner)
                    val photos = planner.notes.mapNotNull { it.photo }.distinct().associateWith { id ->
                        require(isPlannerPhotoId(id)) { "Invalid lesson photo ID" }
                        PlannerStore.photo(context, id)
                    }
                    file.outputStream().use { BackupArchive.write(it, metadata, photos) }
                }
                write(file)
            } finally {
                file.delete()
            }
        }

    suspend fun exportTo(context: Context, uri: Uri, appVersion: String): Long = try {
        withArchive(context, appVersion) { archive ->
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                archive.inputStream().use { it.copyTo(out) }
            } ?: error("no output stream")
        }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        AppLog.e(TAG, "备份导出失败", e)
        -1L
    }

    suspend fun exportToDefault(context: Context, appVersion: String): BackupStore.Entry? = try {
        withArchive(context, appVersion) { BackupStore.writeToDefault(context, it) }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        AppLog.e(TAG, "备份导出失败", e)
        null
    }

    /** 新 ZIP 及旧 JSON 共用同一导入入口。 */
    suspend fun importFrom(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val scratch = runCatching { Files.createTempDirectory(context.cacheDir.toPath(), "backup-read-").toFile() }
            .getOrElse { return@withContext ImportResult.Failed(it.message ?: "cannot stage backup") }
        try {
            val contents = context.contentResolver.openInputStream(uri)?.use { BackupArchive.read(it, scratch) }
                ?: error("no input stream")
            import(context, contents)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            ImportResult.Failed(e.message ?: "read failed")
        } finally {
            scratch.deleteRecursively()
        }
    }

    private suspend fun import(context: Context, contents: BackupArchive.Contents): ImportResult {
        val text = contents.metadata
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return ImportResult.NotABackup
        if (root.optString("format") != FORMAT) return ImportResult.NotABackup
        val version = root.optInt("version", 0)
        if (version <= 0) return ImportResult.NotABackup
        if (version > VERSION) return ImportResult.NewerVersion(version)

        val (importedSettings, planner) = runCatching {
            BackupPayload.validate(root)
            val planner = if (version >= 2) {
                require(contents.zipped) { "Planner backups must be ZIP archives" }
                plannerBackupData(root.getJSONObject("planner"), contents.photos.keys)
            } else {
                require(contents.photos.isEmpty())
                null
            }
            parseSettings(root.optJSONArray("settings")) to planner
        }.getOrElse { return ImportResult.Failed(it.message ?: "invalid backup") }
        val files = linkedMapOf<String, String>()
        fun add(name: String, value: Any?) {
            if (value != null && value != JSONObject.NULL) files[name] = value.toString()
        }
        add(AgendaStore.FILE_NAME, root.opt("agenda"))
        add(CourseEditStore.FILE_NAME, root.opt("courseEdits"))
        add(ExtraCoursesStore.FILE_NAME, root.opt("extraCourses"))
        add(ScheduleCache.SCHEDULE_FILE, root.opt("schedule"))
        for ((key, name) in listOf("schoolSchedules" to ScheduleCache.SCHEDULE_FILE, "schoolExtraCourses" to ExtraCoursesStore.FILE_NAME)) {
            root.optJSONObject(key)?.let { entries ->
                entries.keys().forEach { id ->
                    add("school_data/$id/$name", entries.getJSONObject(id))
                }
            }
        }

        add(SchoolLocationsStore.FILE_NAME, root.opt("locations"))
        add(OfficialHolidayStore.FILE_NAME, root.opt("holidays"))
        val oldPhotos = if (planner == null) emptySet() else
            runCatching { PlannerStore.read(context).notes.mapNotNull { it.photo }.toSet() }.getOrDefault(emptySet())
        if (planner != null) files[PlannerStore.FILE_NAME] = PlannerStore.encode(planner)
        val photos = contents.photos.mapKeys { (id, _) -> "lesson-photos/$id.jpg" }
        try {
            BackupFileTransaction.restore(context.filesDir, files, photos) {
                SettingsStore.importPreferences(context, importedSettings)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            AppLog.e(TAG, "备份导入失败，已回滚文件", e)
            return ImportResult.Failed(e.message ?: "write failed")
        }
        if (planner != null) {
            PlannerStore.reload(context)
            (oldPhotos - contents.photos.keys).filter(::isPlannerPhotoId).forEach { id ->
                PlannerStore.photo(context, id).delete()
            }
        }
        // 旧备份的导入课程没有学校字段，先确定学校再迁移归属。
        com.kstudio.agenda.model.Schools.setCurrent(SettingsStore.read(context).schoolId)
        SchoolLocationsStore.load(context, force = true)
        OfficialHolidayStore.load(context)
        // 内存状态失效后重新读盘，界面/提醒/小组件立刻跟上
        AgendaStore.invalidateMemory()
        CourseEditStore.invalidateMemory()
        ExtraCoursesStore.invalidateMemory()
        AgendaStore.ensureLoaded(context)
        CourseEditStore.ensureLoaded(context)
        ExtraCoursesStore.ensureLoaded(context)

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
            schedule = !root.isNull("schedule"),
        )
    }

    private fun parseSettings(arr: JSONArray?): Map<String, Any> {
        if (arr == null) return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("k")
            if (key.isBlank() || key in EXCLUDED_SETTING_KEYS) continue
            when (o.optString("t")) {
                "b" -> out[key] = o.getBoolean("v")
                "i" -> out[key] = o.getInt("v")
                "l" -> out[key] = o.getLong("v")
                "s" -> out[key] = o.getString("v")
            }
        }
        return out
    }
}
