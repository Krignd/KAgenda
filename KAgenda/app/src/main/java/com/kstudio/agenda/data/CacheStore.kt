package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.model.SemesterSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 课表与接口原始数据的本地缓存：
 * - schedule_cache.json：最近一次解析成功的课表（用于离线展示 / 提醒调度）
 * - raw_capture/：网页接口原始 JSON（保留最近几次，便于排查改版问题）
 */
object ScheduleCache {

    internal const val SCHEDULE_FILE = "schedule_cache.json"
    private const val RAW_DIR = "raw_capture"
    private const val RAW_KEEP = 5

    // 进程内内存缓存：课表会被 启动引导/提醒重排/小组件/常驻通知 等多条链路反复读取，
    // 每次都“读文件 + 解析整学期 JSON”成本高；这里只在首次读取时解析，save/clear 时同步更新。
    private val memLock = Any()
    private var memLoaded = false
    private var memValue: SemesterSchedule? = null

    fun save(context: Context, semester: SemesterSchedule) {
        synchronized(memLock) {
            memValue = semester
            memLoaded = true
        }
        try {
            context.openFileOutput(SCHEDULE_FILE, Context.MODE_PRIVATE).use { out ->
                out.write(toJson(semester).toString().toByteArray(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }
    }

    /** 读取课表缓存（首次调用读盘并解析，之后直接返回内存实例） */
    fun load(context: Context): SemesterSchedule? {
        synchronized(memLock) {
            if (memLoaded) return memValue
        }
        var foreignSchool = false
        val parsed = try {
            val file = File(context.filesDir, SCHEDULE_FILE)
            if (!file.exists()) {
                null
            } else {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                val school = root.optString("school")
                // 缓存属于另一所学校（刚切换学校、还没重新同步）：当作没有缓存，
                // 并且**不写入内存缓存** —— 用户切回原学校时这份缓存还能直接用。
                //
                // 例外：冷启动时设置还没读出来（Schools.currentKnown=false），此刻 currentId
                // 只是默认值，不能用它判定“属于别的学校”，否则江大用户冷启动会看到空白课表
                // （2026-10-08 修复）。
                if (school.isNotBlank() && school != Schools.currentId && Schools.currentKnown) {
                    foreignSchool = true
                    null
                } else {
                    fromJson(root)
                }
            }
        } catch (_: Throwable) {
            null
        }
        if (foreignSchool) return null
        synchronized(memLock) {
            if (!memLoaded) {
                memValue = parsed
                memLoaded = true
            }
            return memValue
        }
    }

    fun clear(context: Context) {
        synchronized(memLock) {
            memValue = null
            memLoaded = true
        }
        try {
            File(context.filesDir, SCHEDULE_FILE).delete()
            File(context.filesDir, RAW_DIR).deleteRecursively()
        } catch (_: Throwable) {
        }
    }

    /** 丢弃内存缓存（下次 [load] 重新读盘；**不删文件**）——备份导入后使用 */
    fun invalidateMemory() {
        synchronized(memLock) {
            memValue = null
            memLoaded = false
        }
    }

    /** 保存网页接口原始响应片段（按时间戳命名，超过数量上限自动清理） */
    fun saveRawCapture(context: Context, url: String, body: String) {
        try {
            val dir = File(context.filesDir, RAW_DIR)
            if (!dir.exists()) dir.mkdirs()
            val name = "capture_${System.currentTimeMillis()}.json"
            File(dir, name).writeText("// url: $url\n$body", Charsets.UTF_8)
            dir.listFiles()?.sortedByDescending { it.name }?.drop(RAW_KEEP)?.forEach { it.delete() }
        } catch (_: Throwable) {
        }
    }

    private fun toJson(semester: SemesterSchedule): JSONObject {
        val root = JSONObject()
        root.put("semester", semester.semesterLabel)
        root.put("anchorEpochDay", semester.anchorEpochDay)
        root.put("fetchedAt", semester.fetchedAtMillis)
        // 记录缓存属于哪所学校：切学校后另一所学校的课表不能再拿来展示（见 load）
        root.put("school", Schools.currentId)
        val weeksObj = JSONObject()
        semester.weeks.forEach { (weekNo, courses) ->
            val arr = JSONArray()
            courses.forEach { c -> arr.put(courseToJson(c)) }
            weeksObj.put(weekNo.toString(), arr)
        }
        root.put("weeks", weeksObj)
        return root
    }

    private fun fromJson(root: JSONObject): SemesterSchedule? {
        // 新格式：{ semester, anchorEpochDay, fetchedAt, weeks: { "2": [课程...] } }
        val weeksObj = root.optJSONObject("weeks")
        if (weeksObj != null) {
            val weeks = linkedMapOf<Int, List<Course>>()
            val keys = weeksObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val weekNo = key.toIntOrNull() ?: continue
                val arr = weeksObj.optJSONArray(key) ?: continue
                val courses = mutableListOf<Course>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { courses.add(courseFromJson(it)) }
                }
                if (courses.isNotEmpty()) weeks[weekNo] = courses
            }
            if (weeks.isEmpty()) return null
            return SemesterSchedule(
                semesterLabel = root.optString("semester"),
                anchorEpochDay = root.optLong("anchorEpochDay"),
                weeks = weeks,
                fetchedAtMillis = root.optLong("fetchedAt", System.currentTimeMillis()),
            )
        }

        // 旧格式（单周缓存）兼容：{ weekNo, anchorEpochDay, courses: [...] }
        val arr = root.optJSONArray("courses") ?: return null
        val weekNo = root.optInt("weekNo", 1)
        val courses = mutableListOf<Course>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { courses.add(courseFromJson(it)) }
        }
        if (courses.isEmpty()) return null
        return SemesterSchedule(
            semesterLabel = root.optString("semester"),
            anchorEpochDay = root.optLong("anchorEpochDay"),
            weeks = mapOf(weekNo to courses),
            fetchedAtMillis = root.optLong("fetchedAt", System.currentTimeMillis()),
        )
    }

    private fun courseToJson(c: Course): JSONObject = JSONObject().apply {
        put("title", c.title)
        put("code", c.code)
        put("teacher", c.teacher)
        put("weeks", c.weeksRaw)
        put("room", c.room)
        put("start", c.startPeriod)
        put("end", c.endPeriod)
        put("day", c.dayOfWeek)
        put("tag", c.tag)
        if (c.extraInfo.isNotBlank()) put("extra", c.extraInfo)
    }

    private fun courseFromJson(o: JSONObject): Course = Course(
        title = o.optString("title"),
        code = o.optString("code"),
        teacher = o.optString("teacher"),
        weeksRaw = o.optString("weeks"),
        room = o.optString("room"),
        startPeriod = o.optInt("start", 1),
        endPeriod = o.optInt("end", o.optInt("start", 1)),
        dayOfWeek = o.optInt("day", 1),
        tag = o.optString("tag"),
        extraInfo = o.optString("extra"),
    )
}
