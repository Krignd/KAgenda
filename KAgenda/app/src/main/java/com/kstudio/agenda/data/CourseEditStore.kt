package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** 记录类型：修改课程信息（原有能力） */
const val EDIT_KIND_MODIFY = "modify"

/** 记录类型：删除（在指定周次里不显示这门课） */
const val EDIT_KIND_DELETE = "delete"

/** 记录类型：新增（教务系统里没有、用户手动加上的课） */
const val EDIT_KIND_ADD = "add"

/**
 * 一条「课程修改」记录（用户发现教务系统没改、但实际课程信息已变时的手动修正）。
 *
 * 同时承载三种本地修正（对外同步逻辑完全一致：存档比对 → 一致即自动消除 → 不一致则询问用户）：
 * - [EDIT_KIND_MODIFY]：改课程信息（[edited] 是改后的课程）；
 * - [EDIT_KIND_DELETE]：删除（[edited] 的 `weeksRaw` 表示**要隐藏的周次**，全选即整门课隐藏）；
 * - [EDIT_KIND_ADD]：新增（[edited] 是要追加到课表里的本地课程，[original] 仅作存档占位）。
 *
 * @param applyAll true=作用于全部同一课程（各星期/节次的安排）；false=仅这一个课表位置
 * @param original 存档：用户点「修改课程」那一刻的教务系统课程信息（ADD 时为 edited 的副本）
 * @param edited 用户修改后的课程信息（DELETE 时为“要删掉的周次”）
 */
data class CourseEdit(
    val applyAll: Boolean,
    val original: Course,
    val edited: Course,
    val editedAtMillis: Long,
    val kind: String = EDIT_KIND_MODIFY,
    /**
     * 该记录属于哪所学校（[Schools.currentId]）。
     *
     * 加入学校归属的原因：切学校后旧学校的修正/删除/新增记录会与另一所学校的课表
     * 用「课程代码 / 课程名」匹配（seriesKey），完全可能张冠李戴。
     * 空串 = 旧数据（升级前写入的），为兼容一律视为对任何学校生效。
     */
    val schoolId: String = "",
) {
    /** 是否属于当前使用的学校（旧记录视为通用） */
    fun belongsToCurrentSchool(): Boolean =
        schoolId.isBlank() || schoolId == Schools.currentId

    /** 目标标识：同一门课 + （仅这一次时）具体课表位置；不同类型互不干扰 */
    val targetKey: String
        get() = when (kind) {
            EDIT_KIND_ADD -> "add#" + edited.seriesKey + "#" + edited.slotKey + "#" + edited.weeksRaw
            EDIT_KIND_DELETE -> "del#" + original.seriesKey + suffix
            else -> original.seriesKey + suffix
        }

    private val suffix: String
        get() = if (applyAll) "" else "#" + original.slotKey

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

    internal const val FILE_NAME = "course_edits.json"
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

    /**
     * 保存一条修改：
     * - 同一目标重复修改 → 覆盖旧记录，但**存档仍用第一次的原课程**（还原时要回到教务最初的样子）；
     * - 选择「全部同一课程」时，同一门课已有的「仅这一次」记录会被这条整体修改取代（避免互相打架）。
     */
    fun save(context: Context, original: Course, edited: Course, applyAll: Boolean): CourseEdit {
        ensureLoaded(context)
        val candidate = CourseEdit(
            applyAll = applyAll,
            original = original.copy(edited = false),
            edited = edited.copy(edited = false),
            editedAtMillis = System.currentTimeMillis(),
            schoolId = Schools.currentId,
        )
        synchronized(this) {
            val old = _edits.value.firstOrNull { it.targetKey == candidate.targetKey }
            // 保留最初的存档：用户反复修改同一门课时，存档要一直是「教务系统原本的样子」
            val record = if (old != null) candidate.copy(original = old.original) else candidate
            _edits.value = _edits.value.filterNot {
                it.targetKey == record.targetKey ||
                    (record.applyAll && !it.applyAll && it.original.sameSeriesAs(record.original))
            } + listOf(record)
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

    /** 丢弃内存状态，下次 [ensureLoaded] 重新读盘（备份导入后使用） */
    fun invalidateMemory() {
        synchronized(this) {
            loaded = false
            _edits.value = emptyList()
        }
    }

    /** 把一条记录放进列表：覆盖同目标；「全部同一课程」的修改会取代同门课的「仅这一次」记录 */
    private fun merge(list: List<CourseEdit>, record: CourseEdit): List<CourseEdit> =
        list.filterNot {
            it.targetKey == record.targetKey ||
                (record.kind == EDIT_KIND_MODIFY && record.applyAll &&
                    it.kind == EDIT_KIND_MODIFY && !it.applyAll &&
                    it.original.sameSeriesAs(record.original))
        } + listOf(record)

    /**
     * 新增本地课程（教务系统里没有的课）：写为 ADD 记录，展示时追加到对应周次。
     * 与「修改课程」共用同一套比对逻辑（教务系统后来也出现了同样内容时，记录会自动消除）。
     */
    fun saveAdd(context: Context, course: Course): CourseEdit {
        ensureLoaded(context)
        val record = CourseEdit(
            applyAll = false,
            original = course.copy(edited = false),
            edited = course.copy(edited = false),
            editedAtMillis = System.currentTimeMillis(),
            kind = EDIT_KIND_ADD,
            schoolId = Schools.currentId,
        )
        synchronized(this) {
            _edits.value = merge(_edits.value, record)
            persist(context)
            AppLog.i(TAG, "已新增本地课程：${record.edited.title}（周次 ${record.edited.weeksRaw}）")
            return record
        }
    }

    /**
     * 删除课程：在 [weeks] 选中的周次里不显示。
     * - [weeks] 覆盖全部周次（UI 的「全选」）→ 整门课隐藏，且以后教务把周次改长了也照样隐藏；
     * - [applyAll] true=同一门课的各星期/节次都生效；false=仅这一个课表位置。
     */
    fun saveDelete(context: Context, original: Course, weeks: Set<Int>, applyAll: Boolean): CourseEdit {
        ensureLoaded(context)
        val all = weeks.containsAll((1..Course.MAX_WEEK).toSet())
        val raw = if (all) Course.encodeWeeks(1..Course.MAX_WEEK) else Course.encodeWeeks(weeks)
        val record = CourseEdit(
            applyAll = applyAll,
            original = original.copy(edited = false),
            edited = original.copy(weeksRaw = raw, edited = false),
            editedAtMillis = System.currentTimeMillis(),
            kind = EDIT_KIND_DELETE,
            schoolId = Schools.currentId,
        )
        synchronized(this) {
            val old = _edits.value.firstOrNull { it.targetKey == record.targetKey }
            val merged = if (old != null) record.copy(original = old.original) else record
            _edits.value = merge(_edits.value, merged)
            persist(context)
            AppLog.i(TAG, "已删除课程：${merged.edited.title}（周次 $raw，applyAll=$applyAll）")
            return merged
        }
    }

    /**
     * 调课：把 [courses]（[sourceDate] 当天的课，由调用方按「整天 / 只这一节」筛好）迁到 [targetDate]。
     *
     * 实现与「修改课程」完全同一套机制（不新增同步逻辑）：
     * 1. 来源位置 → 这些周次不再显示（周次减掉源周；整门课都在这周时等价于整门隐藏）；
     * 2. 目标位置 → 写一条 ADD 记录（星期改成目标星期、周次只有目标周）。
     *
     * @return 实际迁移的课程数
     */
    fun reschedule(
        context: Context,
        sourceDate: LocalDate,
        sourceWeek: Int,
        courses: List<Course>,
        targetDate: LocalDate,
        targetWeek: Int,
    ): Int {
        ensureLoaded(context)
        if (courses.isEmpty() || sourceWeek < 1 || targetWeek < 1) return 0
        val targetDay = targetDate.dayOfWeek.value
        val now = System.currentTimeMillis()
        synchronized(this) {
            var list = _edits.value
            var moved = 0
            for (c in courses) {
                if (c.dayOfWeek == targetDay && sourceWeek == targetWeek) continue   // 原地不动
                // 目标位置：新增到目标星期/目标周
                val movedCourse = c.copy(
                    dayOfWeek = targetDay,
                    weeksRaw = Course.encodeWeeks(setOf(targetWeek)),
                    edited = false,
                )
                list = merge(
                    list,
                    CourseEdit(
                        applyAll = false,
                        original = movedCourse,
                        edited = movedCourse,
                        editedAtMillis = now,
                        kind = EDIT_KIND_ADD,
                        schoolId = Schools.currentId,
                    ),
                )
                // 来源位置：这些周次不再显示
                val remain = c.weeksSet().minus(sourceWeek)
                list = if (remain.isEmpty()) {
                    merge(
                        list,
                        CourseEdit(
                            applyAll = false,
                            original = c.copy(edited = false),
                            edited = c.copy(
                                weeksRaw = Course.encodeWeeks(1..Course.MAX_WEEK),
                                edited = false,
                            ),
                            editedAtMillis = now,
                            kind = EDIT_KIND_DELETE,
                            schoolId = Schools.currentId,
                        ),
                    )
                } else {
                    // 存档要保留「教务最初的样子」（与 save 一致）
                    val key = c.seriesKey + "#" + c.slotKey
                    val old = list.firstOrNull { it.kind == EDIT_KIND_MODIFY && it.targetKey == key }
                    merge(
                        list,
                        CourseEdit(
                            applyAll = false,
                            original = old?.original ?: c.copy(edited = false),
                            edited = c.copy(weeksRaw = Course.encodeWeeks(remain), edited = false),
                            editedAtMillis = now,
                            kind = EDIT_KIND_MODIFY,
                            schoolId = Schools.currentId,
                        ),
                    )
                }
                moved++
            }
            _edits.value = list
            persist(context)
            AppLog.i(TAG, "调课：$sourceDate（第 $sourceWeek 周）→ $targetDate（第 $targetWeek 周），共 $moved 门")
            return moved
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
        // 只套用「当前学校」的记录：切学校后旧学校的修正不能挪用到另一所学校的同名/同代码课上
        val active = edits.filter { it.belongsToCurrentSchool() }
        if (active.isEmpty()) return semester
        val weeks = LinkedHashMap<Int, List<Course>>()
        for ((weekNo, list) in semester.weeks) {
            var current = list
            for (e in active) {
                current = when (e.kind) {
                    EDIT_KIND_ADD -> applyAdd(current, e, weekNo)
                    EDIT_KIND_DELETE -> applyDelete(current, e)
                    else -> applyModify(current, e, weekNo)
                }
            }
            weeks[weekNo] = current
        }
        return semester.copy(weeks = weeks)
    }

    /** 修改类型：整条替换命中的课程块（位置也可以改） */
    private fun applyModify(current: List<Course>, e: CourseEdit, weekNo: Int): List<Course> {
        val matched = current.filter { e.matchesTarget(it) }
        val kept = current.filterNot { e.matchesTarget(it) }
        if (matched.isEmpty()) {
            // 本课表里完全没有这门课（教务已删除）→ 按存档的周次补上用户版本
            val seriesPresent = current.any { it.sameSeriesAs(e.original) }
            val add = e.edited.copy(edited = true)
            return if (!seriesPresent && add.occursInWeek(weekNo) &&
                e.original.occursInWeek(weekNo) && kept.none { it.id == add.id }
            ) {
                kept + add
            } else {
                current
            }
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
        return kept + patched.filter { it.occursInWeek(weekNo) }
    }

    /**
     * 删除类型：命中的课程在选中周次里不显示。
     * 本周次不在选中范围内（剩余周次与原来一样）时原样返回，不打「已修改」标记。
     */
    private fun applyDelete(current: List<Course>, e: CourseEdit): List<Course> {
        val delWeeks = e.edited.weeksRanges.flatMapTo(HashSet()) { it.toList() }
        if (delWeeks.isEmpty()) return current
        val matched = current.filter { e.matchesTarget(it) }
        if (matched.isEmpty()) return current
        val kept = current.filterNot { e.matchesTarget(it) }
        val patched = matched.mapNotNull { m ->
            val weeksNow = m.weeksSet()
            val remain = weeksNow - delWeeks
            when {
                remain.isEmpty() -> null                       // 这些周次全被删了 → 整块隐藏
                remain.size == weeksNow.size -> m              // 本周次不受影响
                else -> m.copy(weeksRaw = Course.encodeWeeks(remain), edited = true)
            }
        }
        return kept + patched
    }

    /** 新增类型：该周应上、且课表里没有相同内容时追加（避免与教务课表/重复添加撞车） */
    private fun applyAdd(current: List<Course>, e: CourseEdit, weekNo: Int): List<Course> {
        val add = e.edited
        if (!add.occursInWeek(weekNo)) return current
        if (current.any { it.slotKey == add.slotKey && it.sameEditableContent(add) }) return current
        return current + add.copy(edited = true)
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
            // 其他学校的记录：原样保留，不参与本次比对（切回去还能用）
            if (!e.belongsToCurrentSchool()) {
                keep += e
                continue
            }
            // 新增 / 删除：不参与「教务数据是否变过」的比对，只看教务里现在长什么样
            when (e.kind) {
                EDIT_KIND_ADD -> {
                    // 教务系统里已经出现相同内容（同一位置、同样信息）→ 手动新增已无意义
                    val dup = all.any {
                        it.slotKey == e.edited.slotKey && it.sameEditableContent(e.edited)
                    }
                    if (dup) resolved++ else keep += e
                    continue
                }
                EDIT_KIND_DELETE -> {
                    // 教务系统里已经找不到这门课 → 删除记录自然作废
                    val exists = all.any {
                        it.sameSeriesAs(e.original) &&
                            (e.applyAll || it.slotKey == e.original.slotKey)
                    }
                    if (exists) keep += e else resolved++
                    continue
                }
            }
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
                                    put("kind", e.kind)
                                    if (e.schoolId.isNotBlank()) put("school", e.schoolId)
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
                    kind = o.optString("kind").takeIf { it.isNotBlank() } ?: EDIT_KIND_MODIFY,
                    schoolId = o.optString("school"),
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
