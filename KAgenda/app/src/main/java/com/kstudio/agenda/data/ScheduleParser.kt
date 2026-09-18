package com.kstudio.agenda.data

import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.model.WeekSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 将网页抓取结果解析为课表模型。
 *
 * 主路径：解析 EXTRACT 脚本从周网格 DOM 中提取的 JSON；
 * 兜底路径：当 DOM 提取失败时，尝试从挂钩捕获到的接口 JSON 中启发式解析。
 */
object ScheduleParser {

    /**
     * 解析「学校适配器」返回的课表 JSON：
     * - 数组形式：[{title,day,start,end,room,teacher,weeks?},…]
     * - 对象形式：{"weekNo": N, "courses":[…]}
     * day：1..7 或 "周一"…；start/end：节次；weeks："2-16" 或 "2,4,6"（缺省归入当前周）。
     */
    fun fromCustomAdapter(rawJson: String, today: LocalDate): SemesterSchedule? = runCatching {
        val root = runCatching { JSONObject(rawJson) }.getOrNull()
        var weekNoHint = 0
        val arr: JSONArray = if (root != null && root.has("courses")) {
            weekNoHint = root.optInt("weekNo", 0)
            root.getJSONArray("courses")
        } else {
            JSONArray(rawJson)
        }
        val courses = mutableListOf<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title").trim()
            if (title.isBlank()) continue
            val day = parseAdapterDay(o.opt("day")) ?: continue
            val start = o.optInt("start", 1).coerceIn(1, 14)
            val end = o.optInt("end", start).coerceIn(start, 14)
            courses.add(
                Course(
                    title = title,
                    code = o.optString("code").trim(),
                    teacher = o.optString("teacher").trim(),
                    weeksRaw = o.optString("weeks").trim(),
                    room = o.optString("room").trim(),
                    startPeriod = start,
                    endPeriod = end,
                    dayOfWeek = day,
                )
            )
        }
        if (courses.isEmpty()) return null
        val weekMap = linkedMapOf<Int, MutableList<Course>>()
        for (c in courses) {
            val ranges = c.weeksRanges
            if (ranges.isEmpty()) {
                weekMap.getOrPut(weekNoHint.coerceAtLeast(1)) { mutableListOf() }.add(c)
            } else {
                for (r in ranges) for (w in r) {
                    weekMap.getOrPut(w) { mutableListOf() }.add(c)
                }
            }
        }
        val anchorWeek = weekNoHint.coerceAtLeast(1)
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val anchor = monday.minusWeeks((anchorWeek - 1).toLong()).toEpochDay()
        SemesterSchedule(
            semesterLabel = "",
            anchorEpochDay = anchor,
            weeks = weekMap.mapValues { e -> e.value.sortedBy { it.startPeriod } },
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }.getOrNull()

    /** 适配器 day 字段：数字或中文星期 */
    private fun parseAdapterDay(v: Any?): Int? = when (v) {
        is Number -> v.toInt().coerceIn(1, 7)
        is String -> {
            val s = v.trim()
            s.toIntOrNull()?.coerceIn(1, 7) ?: when {
                s.contains("一") -> 1
                s.contains("二") -> 2
                s.contains("三") -> 3
                s.contains("四") -> 4
                s.contains("五") -> 5
                s.contains("六") -> 6
                s.contains("日") || s.contains("天") || s.contains("七") -> 7
                else -> null
            }
        }
        else -> null
    }

    /**
     * 解析一次抓取结果，返回课表。
     *
     * 数据来源优先级：
     * 1. 挂钩捕获到的 getMyScheduleDetail 接口响应（结构化、字段最全，含 beginSection 等）；
     * 2. 网页 DOM 提取结果（EXTRACT 脚本，抗改版兜底）；
     * meta（学期 / 教学周 / 日期范围）优先取 DOM 探针结果，缺失时从接口请求参数推断。
     */
    fun parseExtraction(json: String, rawApiBodies: List<String> = emptyList()): WeekSchedule? {
        val meta = if (json.isNotBlank()) parseMeta(json) else null
        val apiCourses = parseApiEnvelopes(rawApiBodies)
        val domCourses = if (json.isNotBlank()) parseDomCourses(json) else emptyList()
        val courses = apiCourses.ifEmpty { domCourses }
        if (courses.isEmpty()) return null

        val weekNo = meta?.weekNo?.takeIf { it > 0 } ?: inferWeekNo(rawApiBodies) ?: 1
        val range = meta?.weekRange ?: ""
        val semester = meta?.semester ?: ""
        val fetchedAt = meta?.fetchedAt ?: System.currentTimeMillis()

        return WeekSchedule(
            semesterLabel = semester,
            weekNo = weekNo,
            weekRangeLabel = range,
            anchorEpochDay = computeAnchorEpochDay(range, weekNo, fetchedAt),
            courses = courses.distinctBy { it.title + it.code + it.dayOfWeek + it.startPeriod + it.room },
            fetchedAtMillis = fetchedAt,
        )
    }

    /** 页面 meta（学期、教学周、周日期范围），即使课程解析为空也可能存在 */
    data class PageMeta(
        val semester: String,
        val weekNo: Int,
        val weekRange: String,
        val fetchedAt: Long,
    )

    private fun parseMeta(json: String): PageMeta? = try {
        val root = JSONObject(json)
        val meta = root.optJSONObject("meta") ?: JSONObject()
        val weekNo = meta.optInt("weekNo", 0)
        val semester = meta.optString("semester")
        val range = meta.optString("weekRange")
        if (weekNo <= 0 && semester.isBlank() && range.isBlank()) {
            null
        } else {
            PageMeta(
                semester = semester,
                weekNo = if (weekNo > 0) weekNo else 1,
                weekRange = range,
                fetchedAt = meta.optLong("fetchedAt", System.currentTimeMillis()),
            )
        }
    } catch (_: Throwable) {
        null
    }

    private fun parseDomCourses(json: String): List<Course> = try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("courses") ?: JSONArray()
        val courses = mutableListOf<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = cleanTitle(o.optString("title"))
            if (title.isBlank()) continue
            val start = o.optInt("start", 0)
            val end = o.optInt("end", start).let { if (it < start) start else it }
                .coerceIn(1, PeriodTimes.count)
            val day = o.optInt("day", 0)
            if (start !in 1..PeriodTimes.count || day !in 1..7) continue
            courses.add(
                Course(
                    title = title,
                    code = o.optString("code").trim(),
                    teacher = o.optString("teacher").trim(),
                    weeksRaw = normalizeWeeks(o.optString("weeks")),
                    room = o.optString("room").trim(),
                    startPeriod = start,
                    endPeriod = end,
                    dayOfWeek = day,
                    tag = o.optString("tag").trim(),
                )
            )
        }
        courses
    } catch (_: Throwable) {
        emptyList()
    }

    /** 去掉标题前缀 “（本）”“(研)” 等标签 */
    fun cleanTitle(raw: String): String {
        var t = raw.trim()
        val patterns = listOf("（本）", "(本)", "（研）", "(研)", "【本】", "【研】")
        for (p in patterns) {
            if (t.startsWith(p)) {
                t = t.removePrefix(p).trim()
                break
            }
        }
        return t
    }

    /** 保留合法的周次字符（数字、逗号、连字符） */
    private fun normalizeWeeks(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        return if (t.matches(Regex("^[0-9,\\-]+$"))) t else ""
    }

    /**
     * 根据 “09/14~09/20” 与周次号推算【第 1 教学周周一】的 epochDay。
     * range 为空或无法解析时，以抓取日所在周的周一作为第 weekNo 周周一，再回推 (weekNo-1) 周。
     * 年份以抓取时间就近原则判断（跨年时自动修正）。
     */
    private fun computeAnchorEpochDay(range: String, weekNo: Int, fetchedAt: Long): Long {
        val ref = java.time.Instant.ofEpochMilli(fetchedAt)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        // 第 weekNo 周的周一：优先取范围文本中首个日期，否则用抓取日所在周的周一
        val weekMonday = parseWeekStart(range, ref)
            ?: ref.minusDays((ref.dayOfWeek.value - 1).toLong())
        val safeWeek = if (weekNo > 0) weekNo else 1
        return weekMonday.minusWeeks((safeWeek - 1).toLong()).toEpochDay()
    }

    /**
     * 解析范围文本（“09/14~09/20”“2026-09-14” 等）中该周的起始日期；无法解析返回 null。
     * 只取第一个日期作为该周周一（页面按周一到周日展示）。
     */
    private fun parseWeekStart(range: String, ref: LocalDate): LocalDate? {
        val text = range.trim()
        if (text.isEmpty()) return null
        // 完整日期：2026-09-14 / 2026/9/14 / 2026.9.14
        Regex("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").find(text)?.let { m ->
            val y = m.groupValues[1].toIntOrNull()
            val mo = m.groupValues[2].toIntOrNull()
            val d = m.groupValues[3].toIntOrNull()
            if (y != null && mo != null && d != null) {
                runCatching { LocalDate.of(y, mo, d) }.getOrNull()?.let { return it }
            }
        }
        // 月日：09/14 / 9.14 / 9月14日
        val md = Regex("(\\d{1,2})[-/.月](\\d{1,2})").find(text) ?: return null
        val month = md.groupValues[1].toIntOrNull() ?: return null
        val day = md.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        var date = runCatching { LocalDate.of(ref.year, month, day) }.getOrNull() ?: return null
        if (date.isBefore(ref.minusMonths(6))) {
            date = date.plusYears(1)
        } else if (date.isAfter(ref.plusMonths(6))) {
            date = date.minusYears(1)
        }
        return date
    }

    // ---------------------------------------------------------------------
    // 接口响应解析（挂钩捕获的信封 JSON：{ url, request, response }）
    // ---------------------------------------------------------------------

    private val roomPattern = Regex("楼|室|场|馆|机房|中心|R[0-9]|区")
    private val weeksPattern =
        Regex("\\[?([0-9]{1,2}(?:-[0-9]{1,2})?(?:,[0-9]{1,2}(?:-[0-9]{1,2})?)*)周\\]?")

    /** 从多个接口捕获信封中解析课程（当前解析 getMyScheduleDetail 的响应） */
    fun parseApiEnvelopes(raws: List<String>): List<Course> {
        val result = mutableListOf<Course>()
        for (raw in raws) {
            try {
                val envelope = JSONObject(raw)
                val url = envelope.optString("url")
                if (!url.contains("getMyScheduleDetail")) continue
                val response = envelope.optString("response")
                if (response.isBlank()) continue
                result.addAll(parseScheduleResponse(response))
            } catch (_: Throwable) {
            }
        }
        return result
    }

    /** 从捕获的请求参数中提取 termCode / campusCode（用于按周重放接口抓取整学期） */
    fun inferTermParams(raws: List<String>): Pair<String, String>? {
        for (raw in raws) {
            try {
                val envelope = JSONObject(raw)
                if (!envelope.optString("url").contains("getMyScheduleDetail")) continue
                val request = envelope.optString("request")
                val term = Regex("termCode=([^&]*)").find(request)?.groupValues?.get(1)
                if (!term.isNullOrBlank()) {
                    val campus = Regex("campusCode=([^&]*)").find(request)?.groupValues?.get(1) ?: ""
                    return java.net.URLDecoder.decode(term, "UTF-8") to
                        java.net.URLDecoder.decode(campus, "UTF-8")
                }
                val obj = try { JSONObject(request) } catch (_: Throwable) { null }
                if (obj != null && obj.has("termCode")) {
                    return obj.optString("termCode") to obj.optString("campusCode")
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /** 解析页面内按周重放接口抓取到的学期课表：{ ok, weeks: { "2": [items...] } } */
    fun parseSemesterFetchResult(json: String): Map<Int, List<Course>> {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("ok", false)) return emptyMap()
            val weeks = root.optJSONObject("weeks") ?: return emptyMap()
            val result = linkedMapOf<Int, List<Course>>()
            val keys = weeks.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val weekNo = key.toIntOrNull() ?: continue
                val arr = weeks.optJSONArray(key) ?: continue
                val courses = mutableListOf<Course>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    courseFromArrangedItem(item)?.let { courses.add(it) }
                }
                if (courses.isNotEmpty()) {
                    result[weekNo] = courses.distinctBy {
                        it.title + it.code + it.dayOfWeek + it.startPeriod + it.room
                    }
                }
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    /** 组装学期课表（多周数据；currentWeekCourses 用于兼容单周模式兜底） */
    fun buildSemester(
        currentWeekCourses: List<Course>,
        currentWeekNo: Int,
        weekRange: String,
        semesterLabel: String,
        fetchedAt: Long,
        weeks: Map<Int, List<Course>>,
    ): SemesterSchedule? {
        val merged = linkedMapOf<Int, List<Course>>()
        val allWeekNos = (weeks.keys + currentWeekNo).toSortedSet()
        for (w in allWeekNos) {
            val list = weeks[w]?.takeIf { it.isNotEmpty() }
                ?: if (w == currentWeekNo && currentWeekCourses.isNotEmpty()) currentWeekCourses else null
            if (list != null) merged[w] = list
        }
        if (merged.isEmpty()) return null
        return SemesterSchedule(
            semesterLabel = semesterLabel,
            anchorEpochDay = computeAnchorEpochDay(weekRange, currentWeekNo, fetchedAt),
            weeks = merged,
            fetchedAtMillis = fetchedAt,
        )
    }

    /** 外部访问：读取页面 meta（学期 / 教学周 / 日期范围） */
    fun readPageMeta(json: String): PageMeta? = if (json.isBlank()) null else parseMeta(json)

    /**
     * 解析 getMyScheduleDetail 响应。
     * 结构：{ code, msg, datas: { arrangedList: [...], notArrangeList: [...] } }
     * 每个 arrangedList 项的关键字段：
     *   courseName / courseCode / byCode(BEN|YAN) / dayOfWeek /
     *   beginSection / endSection / beginTime / endTime / cellDetail[{ text }]
     */
    private fun parseScheduleResponse(responseText: String): List<Course> {
        return try {
            val root = JSONObject(responseText)
            val datas = root.optJSONObject("datas") ?: return emptyList()
            val arranged = datas.optJSONArray("arrangedList") ?: return emptyList()
            val courses = mutableListOf<Course>()
            for (i in 0 until arranged.length()) {
                val item = arranged.optJSONObject(i) ?: continue
                courseFromArrangedItem(item)?.let { courses.add(it) }
            }
            courses
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun courseFromArrangedItem(o: JSONObject): Course? {
        val day = o.optInt("dayOfWeek", 0)
        if (day !in 1..7) return null
        val start = o.optInt("beginSection", 0)
        if (start !in 1..PeriodTimes.count) return null
        val end = o.optInt("endSection", start).let { if (it < start) start else it }
            .coerceAtMost(PeriodTimes.count)
        val title = cleanTitle(o.optString("courseName"))
        if (title.isBlank()) return null

        // cellDetail 中的文本依次是：教师[周次]、教室、节次说明等
        var weeks = ""
        val texts = mutableListOf<String>()
        val details = o.optJSONArray("cellDetail")
        if (details != null) {
            for (i in 0 until details.length()) {
                val text = details.optJSONObject(i)?.optString("text")?.trim().orEmpty()
                if (text.isBlank() || text.contains('节')) continue
                val matched = weeksPattern.find(text)
                if (matched != null && weeks.isBlank()) {
                    weeks = matched.groupValues[1]
                }
                val cleaned = text.replace(weeksPattern, "").trim()
                if (cleaned.isNotBlank()) texts.add(cleaned)
            }
        }
        var teacher = ""
        var room = ""
        for (text in texts) {
            if (room.isBlank() && roomPattern.containsMatchIn(text)) {
                room = text
                continue
            }
            // 修复：cellDetail 首项通常是课程名（可能带“（本）/（研）”前缀），不能当作教师；
            // 纯数字/编号（如教学班“266111，266112”）同样排除
            if (teacher.isBlank() && !isTitleLike(text, title) && !isNumericList(text)) {
                teacher = text
            }
        }

        val tag = when (o.optString("byCode").uppercase()) {
            "BEN" -> "本"
            "YAN" -> "研"
            else -> ""
        }
        return Course(
            title = title,
            code = o.optString("courseCode").trim(),
            teacher = teacher,
            weeksRaw = normalizeWeeks(weeks),
            room = room,
            startPeriod = start,
            endPeriod = end,
            dayOfWeek = day,
            tag = tag,
        )
    }

    /** 判断 cellDetail 文本是否为课程名本身（带“（本）/（研）”等前缀也算） */
    private fun isTitleLike(text: String, title: String): Boolean {
        if (title.isBlank() || text.isBlank()) return false
        val cleaned = cleanTitle(text)
        return cleaned == title || text.contains(title)
    }

    /** 纯数字/编号列表（教学班目标等），不是教师名 */
    private fun isNumericList(text: String): Boolean =
        text.matches(Regex("^[0-9，,、;；/\\-\\s]+$"))

    /** 从捕获的请求参数中推断教学周（形如 termCode=...&type=week&week=2） */
    private fun inferWeekNo(raws: List<String>): Int? {
        for (raw in raws) {
            try {
                val envelope = JSONObject(raw)
                if (!envelope.optString("url").contains("getMyScheduleDetail")) continue
                val request = envelope.optString("request")
                val matched = Regex("(?:^|[&{\",])week[\"=:]+([0-9]{1,2})").find(request) ?: continue
                val value = matched.groupValues[1].toIntOrNull()
                if (value != null && value in 1..40) return value
            } catch (_: Throwable) {
            }
        }
        return null
    }

}
