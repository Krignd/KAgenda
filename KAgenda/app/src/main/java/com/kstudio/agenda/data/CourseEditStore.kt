package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 一条「课程修改」记录（用户发现教务系统没改、但实际课程信息已变时的手动修正）。
 *
 * @param applyAll true=修改全部同一课程；false=仅修改这一次（同一个课表位置）
 * @param original 存档：用户点「修改课程」那一刻的教务系统课程信息
 * @param edited 用户修改后的课程信息
 */
data class CourseEdit(
    val applyAll: Boolean,
    val original: Course,
    val edited: Course,
    val editedAtMillis: Long,
) {
    /** 目标标识：同一门课 + （仅这一次时）具体课表位置 */
    val targetKey: String
        get() = if (applyAll) original.seriesKey else original.seriesKey + "#" + original.slotKey

    /** 该课程是否命中本记录的目标（全部修改只看同一门课；单次修改还要看课表位置） */
    fun matchesTarget(course: Course): Boolean =
        course.sameSeriesAs(original) && (applyAll || course.slotKey == original.slotKey)
}

/**
 * 同步后需要用户决定的课程修改冲突：教务系统的数据既不是用户改前那一版，也不是用户改后那一版。
 *
 * @param latest 教务系统中的最新课程信息（已在教务系统中找不到这门课时为 null）
 */
data class CourseEditConflict(
    val edit: CourseEdit,
    val latest: Course?,
)

/**
 * 「课程修改」存储：应对「课程信息已经变了、教务系统还没改」的情况。
 *
 * 数据流：
 * 1. 用户点课程卡片 → 详情 → 「修改课程」→ 原课程作为存档写入本存储；
 * 2. 展示层用 [apply] 把用户修改覆盖到教务课表上（不改动缓存文件，缓存里始终是教务原始数据）；
 * 3. 每次同步成功后用 [reconcile] 与最新教务数据比对：
 *    - 教务已与用户修改一致 → 删除记录（同时去掉「已修改」标记与存档）；
 *    - 教务与存档一致（教务没变）→ 保留用户修改，不做处理；
 *    - 两者都不同 → 交给界面询问用户是否与教务系统同步（见 [CourseEditConflict]）。
 */
object CourseEditStore {

    private const val FILE_NAME = "course_edits.json"
    private const val TAG = "CourseEdit"

    private val _edits = MutableStateFlow<List<CourseEdit>>(emptyList())
    val edits: StateFlow<List<CourseEdit>> = _edits.asStateFlow()

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
                    _edits.value = parse(JSONObject(f.readText(Charsets.UTF_8)).optJSONArray("edits"))
                }
            }.onFailure { AppLog.e(TAG, "课程修改读取失败", it) }
        }
    }

    /** 保存一条修改（同一目标重复修改时覆盖旧记录，存档仍用第一次的原课程） */
    fun save(context: Context, original: Course, edited: Course, applyAll: Boolean): CourseEdit {
        ensureLoaded(context)
        val candidate = CourseEdit(
            applyAll = applyAll,
            original = original.copy(edited = false),
            edited = edited.copy(edited = false),
            editedAtMillis = System.currentTimeMillis(),
        )
        synchronized(this) {
            val old = _edits.value.firstOrNull { it.targetKey == candidate.targetKey }
            // 保留最初的存档：用户反复修改同一门课时，存档要一直是「教务系统原本的样子」
            val record = if (old != null) candidate.copy(original = old.original) else candidate
            _edits.value = _edits.value.filterNot { it.targetKey == record.targetKey } +
                listOf(record)
            persist(context)
            AppLog.i(TAG, "已保存课程修改：${record.edited.title}（applyAll=${record.applyAll}）")
            return record
        }
    }

    /** 保留用户修改、把存档更新为最新教务数据（冲突里用户选择「保留我的修改」时调用） */
    fun keepEdited(context: Context, edit: CourseEdit, latest: Course?) {
        ensureLoaded(context)
        synchronized(this) {
            _edits.value = _edits.value.map {
                if (it.targetKey == edit.targetKey) {
                    if (latest != null) {
                        // 存档换成最新教务版本：课程位置也可能变了，同步更新定位依据
                        it.copy(original = latest.copy(edited = false))
                    } else {
                        it
                    }
                } else {
                    it
                }
            }
            persist(context)
        }
    }

    /** 还原（删除）单条修改记录 */
    fun remove(context: Context, targetKey: String) {
        ensureLoaded(context)
        synchronized(this) {
            _edits.value = _edits.value.filterNot { it.targetKey == targetKey }
            persist(context)
        }
    }

    /** 清空全部课程修改（设置里的「仅还原课程修改」/「与教务系统同步并清除」共用） */
    fun clear(context: Context) {
        synchronized(this) {
            val had = _edits.value.isNotEmpty()
            _edits.value = emptyList()
            runCatching { File(context.filesDir, FILE_NAME).delete() }
            if (had) AppLog.i(TAG, "已清除全部课程修改")
        }
    }

    /**
     * 把课程修改覆盖到课表上（纯函数，不动缓存文件）。
     * - 目标课程块：整条替换（位置也可以改）；
     * - 「全部同一课程」时，同一门课的其它课表位置只套用文字信息（课程名/老师/地点/周次/代码），
     *   保留各自的星期与节次——避免把一周多次的课程折叠成一次；
     * - 教务课表里已经找不到这门课（且该周本来应该上）时，把用户的版本显示出来，
     *   避免用户「保留我的修改」后课程反而从视图里消失；
     * - 用户把周次改到不含本周时，该周不再出现这门课。
     */
    fun apply(
        semester: SemesterSchedule?,
        edits: List<CourseEdit> = _edits.value,
    ): SemesterSchedule? {
        if (semester == null || edits.isEmpty()) return semester
        val weeks = LinkedHashMap<Int, List<Course>>()
        for ((weekNo, list) in semester.weeks) {
            var current = list
            for (e in edits) {
                val matched = current.filter { e.matchesTarget(it) }
                val kept = current.filterNot { e.matchesTarget(it) }
                if (matched.isEmpty()) {
                    // 本课表里完全没有这门课（教务已删除）→ 按存档的周次补上用户版本
                    val seriesPresent = current.any { it.sameSeriesAs(e.original) }
                    val add = e.edited.copy(edited = true)
                    if (!seriesPresent && add.occursInWeek(weekNo) &&
                        e.original.occursInWeek(weekNo) && kept.none { it.id == add.id }
                    ) {
                        current = kept + add
                    }
                    continue
                }
                val patched = matched.map { m ->
                    if (e.applyAll && m.slotKey != e.original.slotKey) {
                        m.copy(
                            title = e.edited.title,
                            code = e.edited.code,
                            teacher = e.edited.teacher,
                            room = e.edited.room,
                            weeksRaw = e.edited.weeksRaw,
                            edited = true,
                        )
                    } else {
                        e.edited.copy(edited = true)
                    }
                }
                current = kept + patched.filter { it.occursInWeek(weekNo) }
            }
            weeks[weekNo] = current
        }
        return semester.copy(weeks = weeks)
    }

    /**
     * 供「读缓存文件」的链路使用（课前提醒 / 小组件 / 常驻通知）：
     * 缓存文件里是教务原始数据，这里补上用户修改后再返回。
     */
    fun appliedFromCache(context: Context): SemesterSchedule? {
        ensureLoaded(context)
        return apply(ScheduleCache.load(context))
    }

    /**
     * 同步成功后与最新教务数据比对（会直接更新存储）：
     * - 教务已与用户修改一致 → 删除记录（去掉「已修改」标记与存档）；
     * - 教务与存档一致 → 保留用户修改，不做处理；
     * - 教务既没变也不同于用户的修改（如：课程被调到别的星期/节次，或地点老师变了）→ 作为冲突返回，
     *   由界面询问用户是否与教务系统同步；
     * - 整份课表都找不到这门课（可能是本次同步范围不全 / 课程已取消）→ 静默保留用户修改，
     *   不打扰用户（避免每次同步都重复询问）。
     */
    fun reconcile(context: Context, semester: SemesterSchedule?): List<CourseEditConflict> {
        ensureLoaded(context)
        val all = semester?.weeks?.values?.flatten().orEmpty()
        if (semester == null || _edits.value.isEmpty()) return emptyList()
        val keep = mutableListOf<CourseEdit>()
        val conflicts = mutableListOf<CourseEditConflict>()
        var resolved = 0
        for (e in _edits.value) {
            // 先精确命中「存档所在的课表位置」；位置被改过时回退到同一门课的其它位置
            val latest = pickLatest(e, all.filter { e.matchesTarget(it) })
                ?: pickLatest(e, all.filter { it.sameSeriesAs(e.original) })
            when {
                // 教务系统已经改成了用户要的样子 → 修改已无意义，去掉标记与存档
                latest != null && latest.sameEditableContent(e.edited) -> resolved++
                // 教务系统没有变化（或已找不到这门课）→ 保留用户的修改，静默处理
                latest == null || latest.sameEditableContent(e.original) -> keep += e
                // 教务系统变过了，且与用户改的不同 → 需要用户决定
                else -> {
                    keep += e
                    conflicts += CourseEditConflict(e, latest)
                }
            }
        }
        if (keep.size != _edits.value.size) {
            _edits.value = keep
            persist(context)
            AppLog.i(TAG, "同步后有 $resolved 门课程的修改已与教务系统一致，已移除修改标记")
        }
        return conflicts
    }

    /** 代表条目：优先“存档所在的课表位置”，否则取第一条（同一门课可能一周有多个课表位置） */
    private fun pickLatest(e: CourseEdit, matched: List<Course>): Course? =
        matched.firstOrNull { it.slotKey == e.original.slotKey } ?: matched.firstOrNull()

    // ------------------------------------------------------------ 持久化

    private fun persist(context: Context) {
        runCatching {
            val root = JSONObject().apply {
                put(
                    "edits",
                    JSONArray().apply {
                        _edits.value.forEach { e ->
                            put(
                                JSONObject().apply {
                                    put("applyAll", e.applyAll)
                                    put("at", e.editedAtMillis)
                                    put("original", courseToJson(e.original))
                                    put("edited", courseToJson(e.edited))
                                }
                            )
                        }
                    },
                )
            }
            File(context.filesDir, FILE_NAME).writeText(root.toString(), Charsets.UTF_8)
        }.onFailure { AppLog.e(TAG, "课程修改写盘失败", it) }
    }

    private fun parse(arr: JSONArray?): List<CourseEdit> {
        if (arr == null) return emptyList()
        val out = mutableListOf<CourseEdit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val original = o.optJSONObject("original")?.let { courseFromJson(it) } ?: continue
            val edited = o.optJSONObject("edited")?.let { courseFromJson(it) } ?: continue
            if (edited.title.isBlank()) continue
            out.add(
                CourseEdit(
                    applyAll = o.optBoolean("applyAll", true),
                    original = original,
                    edited = edited,
                    editedAtMillis = o.optLong("at"),
                )
            )
        }
        return out
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
