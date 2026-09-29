package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 「导入课程」存储：用户通过文档导入 / 手动添加的课程。
 *
 * 与教务同步得到的课表相互独立：
 * - 教务课表来自网页同步（[SemesterSchedule]）；
 * - 这里保存用户自己录入/导入的课程，并在生成周课表时与教务课表**合并**展示。
 *
 * 第 1 教学周周一（anchor）优先沿用当前学期的锚点；没有学期数据时，
 * 由用户在导入时指定（默认取本周周一），保证「第 N 周」的日期换算正确。
 */
object ExtraCoursesStore {

    private const val FILE_NAME = "extra_courses.json"

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    /** 用户指定的「第 1 教学周周一」epochDay（无导入课程时无意义） */
    private val _anchorEpochDay = MutableStateFlow<Long?>(null)
    val anchorEpochDay: StateFlow<Long?> = _anchorEpochDay.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            runCatching {
                val f = File(context.filesDir, FILE_NAME)
                if (f.exists()) {
                    val o = JSONObject(f.readText(Charsets.UTF_8))
                    _anchorEpochDay.value = if (o.has("anchorEpochDay")) o.optLong("anchorEpochDay") else null
                    _courses.value = parseCourses(o.optJSONArray("courses"))
                }
            }.onFailure { AppLog.e("ExtraCourses", "导入课程读取失败", it) }
        }
    }

    /** 覆盖式保存（导入时用）：返回最终保存的课程数 */
    fun replace(context: Context, list: List<Course>, anchor: LocalDate?): Int {
        ensureLoaded(context)
        synchronized(this) {
            _courses.value = list
            _anchorEpochDay.value = anchor?.toEpochDay()
            persist(context)
            return list.size
        }
    }

    /** 追加保存（去重：同一门课只保留一条）：返回新增数量 */
    fun addAll(context: Context, list: List<Course>, anchor: LocalDate?): Int {
        ensureLoaded(context)
        synchronized(this) {
            val map = LinkedHashMap<String, Course>()
            _courses.value.forEach { map[it.id] = it }
            var added = 0
            list.forEach {
                if (map.put(it.id, it) == null) added++
            }
            _courses.value = map.values.toList()
            if (_anchorEpochDay.value == null && anchor != null) {
                _anchorEpochDay.value = anchor.toEpochDay()
            }
            persist(context)
            return added
        }
    }

    fun delete(context: Context, id: String) {
        ensureLoaded(context)
        synchronized(this) {
            _courses.value = _courses.value.filterNot { it.id == id }
            persist(context)
        }
    }

    /**
     * 修改单条导入课程（导入课程属于本地数据，直接改本地记录）。
     * 返回 false 表示找不到该课程。
     */
    fun replace(context: Context, id: String, edited: Course): Boolean {
        ensureLoaded(context)
        synchronized(this) {
            var found = false
            _courses.value = _courses.value.map {
                if (it.id == id) {
                    found = true
                    edited
                } else {
                    it
                }
            }
            if (found) persist(context)
            return found
        }
    }

    /**
     * 修改全部同一门导入课程：
     * - 目标课程块：整条替换（位置也可以改）；
     * - 其他课程块：只套用文字信息（课程名/老师/地点/周次/代码），保留各自的星期与节次。
     */
    fun replaceSeries(context: Context, target: Course, edited: Course): Int {
        ensureLoaded(context)
        synchronized(this) {
            var count = 0
            _courses.value = _courses.value.map { c ->
                if (!c.sameSeriesAs(target)) return@map c
                count++
                if (c.id == target.id) {
                    edited
                } else {
                    c.copy(
                        title = edited.title,
                        code = edited.code,
                        teacher = edited.teacher,
                        room = edited.room,
                        weeksRaw = edited.weeksRaw,
                    )
                }
            }
            if (count > 0) persist(context)
            return count
        }
    }

    fun clear(context: Context) {
        synchronized(this) {
            _courses.value = emptyList()
            _anchorEpochDay.value = null
            runCatching { File(context.filesDir, FILE_NAME).delete() }
        }
    }

    private fun persist(context: Context) {
        runCatching {
            val o = JSONObject().apply {
                _anchorEpochDay.value?.let { put("anchorEpochDay", it) }
                put(
                    "courses",
                    JSONArray().apply {
                        _courses.value.forEach { c ->
                            put(
                                JSONObject().apply {
                                    put("title", c.title)
                                    put("code", c.code)
                                    put("teacher", c.teacher)
                                    put("weeksRaw", c.weeksRaw)
                                    put("room", c.room)
                                    put("startPeriod", c.startPeriod)
                                    put("endPeriod", c.endPeriod)
                                    put("dayOfWeek", c.dayOfWeek)
                                    put("tag", c.tag)
                                }
                            )
                        }
                    },
                )
            }
            File(context.filesDir, FILE_NAME).writeText(o.toString(), Charsets.UTF_8)
        }.onFailure { AppLog.e("ExtraCourses", "导入课程写盘失败", it) }
    }

    private fun parseCourses(arr: JSONArray?): List<Course> {
        if (arr == null) return emptyList()
        val out = mutableListOf<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            val day = o.optInt("dayOfWeek", 0)
            val sp = o.optInt("startPeriod", 0)
            val ep = o.optInt("endPeriod", 0)
            if (title.isBlank() || day !in 1..7 || sp !in 1..PeriodTimes.MAX_COUNT || ep < sp) continue
            out.add(
                Course(
                    title = title,
                    code = o.optString("code"),
                    teacher = o.optString("teacher"),
                    weeksRaw = o.optString("weeksRaw"),
                    room = o.optString("room"),
                    startPeriod = sp,
                    endPeriod = ep.coerceAtMost(PeriodTimes.MAX_COUNT),
                    dayOfWeek = day,
                    tag = o.optString("tag"),
                )
            )
        }
        return out
    }

    /**
     * 把导入课程并入学期课表：
     * - 学期存在时沿用学期的锚点，只在学期已抓取的周次内追加（不改变周次列表）；
     * - 学期为空但有导入课程时，用用户指定的锚点（默认本周周一）合成一份「导入课表」。
     */
    fun mergeInto(semester: SemesterSchedule?, extra: List<Course>, anchorDay: Long?): SemesterSchedule? {
        if (extra.isEmpty()) return semester
        if (semester == null) {
            val anchor = anchorDay ?: mondayOfCurrentWeek().toEpochDay()
            val weeks = LinkedHashMap<Int, List<Course>>()
            for (w in 1..40) {
                val list = extra.filter { it.occursInWeek(w) }
                if (list.isNotEmpty()) weeks[w] = list
            }
            if (weeks.isEmpty()) return null
            return SemesterSchedule(
                semesterLabel = "导入课表",
                anchorEpochDay = anchor,
                weeks = weeks,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }
        val targetWeeks = semester.weeks.keys.ifEmpty { (1..40).toSet() }
        val weeks = LinkedHashMap<Int, List<Course>>(semester.weeks)
        for (w in targetWeeks) {
            val add = extra.filter { it.occursInWeek(w) }
            if (add.isEmpty()) continue
            val base = semester.weeks[w].orEmpty()
            // 同一门课（id 相同）以学员导入的为准，避免重复显示
            val ids = add.map { it.id }.toSet()
            weeks[w] = base.filterNot { it.id in ids } + add
        }
        return semester.copy(weeks = weeks)
    }

    private fun mondayOfCurrentWeek(): LocalDate =
        LocalDate.now().with(java.time.DayOfWeek.MONDAY)

    /** 与传入日期相差的周数（用于导入时提示「第 N 周」） */
    fun weeksBetween(anchor: LocalDate, date: LocalDate): Long =
        ChronoUnit.WEEKS.between(anchor, date.with(java.time.DayOfWeek.MONDAY))
}
