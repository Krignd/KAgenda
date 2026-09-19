package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.Course
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

    private const val SCHEDULE_FILE = "schedule_cache.json"
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
        val parsed = try {
            val file = File(context.filesDir, SCHEDULE_FILE)
            if (!file.exists()) null
            else fromJson(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (_: Throwable) {
            null
        }
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
    )
}
