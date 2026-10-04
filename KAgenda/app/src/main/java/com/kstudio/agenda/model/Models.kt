package com.kstudio.agenda.model

import java.time.LocalDate

/**
 * 一条课程安排（对应网页“我的课表”周网格中的一个课程块）
 *
 * @param weeksRaw 上课周次原文，如 "2-17"、"2-9,11-17"，无法解析时为 ""
 * @param dayOfWeek 1=周一 ... 7=周日
 * @param edited 该课程由用户手动修改过（本地修改，尚未与教务系统一致）；
 *   只在展示层由 [com.kstudio.agenda.data.CourseEditStore] 置位，缓存文件里始终是教务原始数据
 * @param extraInfo 教务页面里的额外信息（如开设班级、开课学期、教学班号、学分、课程性质、选课备注），
 *   多行「标签：值」文本；没抓到就是空串。不参与课程对比与提醒计算，只用于详情展示。
 */
data class Course(
    val title: String,
    val code: String,
    val teacher: String,
    val weeksRaw: String,
    val room: String,
    val startPeriod: Int,
    val endPeriod: Int,
    val dayOfWeek: Int,
    val tag: String = "",
    val edited: Boolean = false,
    val extraInfo: String = "",
) {
    /** 稳定标识：用于提醒的 requestCode / 图片配色 */
    val id: String
        get() = "${code.ifBlank { title }}@${dayOfWeek}-${startPeriod}-${endPeriod}-$room"

    /** 同一门课的标识（「修改全部同一课程」的判定依据）：优先课程代码，无代码时用课程名 */
    val seriesKey: String
        get() = code.trim().ifBlank { title.trim() }.lowercase()

    /** 课表位置（星期 + 起止节次）：「仅修改这一次」的定位依据 */
    val slotKey: String
        get() = "$dayOfWeek-$startPeriod-$endPeriod"

    /**
     * 可编辑信息是否完全一致（用于「同步后与教务系统比对」）：
     * 只比较用户可改的字段；`tag` / `edited` 等展示态字段不参与判定。
     */
    fun sameEditableContent(other: Course): Boolean =
        title.trim() == other.title.trim() &&
            code.trim() == other.code.trim() &&
            teacher.trim() == other.teacher.trim() &&
            room.trim() == other.room.trim() &&
            weeksRaw.trim() == other.weeksRaw.trim() &&
            dayOfWeek == other.dayOfWeek &&
            startPeriod == other.startPeriod &&
            endPeriod == other.endPeriod

    /** 是否与另一条课程属于同一门课（课程代码优先，无代码时用课程名） */
    fun sameSeriesAs(other: Course): Boolean = seriesKey == other.seriesKey

    val periodLabel: String
        get() = if (startPeriod == endPeriod) "第${startPeriod}节" else "第${startPeriod}-${endPeriod}节"

    val timeRange: String
        get() = PeriodTimes.rangeOf(startPeriod, endPeriod)

    // 周次区间解析结果缓存：occursInWeek 在月/周视图的循环中会被高频调用，
    // 原实现每次访问都重新解析字符串；改为惰性求值，每个 Course 实例只解析一次
    val weeksRanges: List<IntRange> by lazy { parseWeeks(weeksRaw) }

    /** 该课程在指定教学周是否上课；周次信息缺失时视为每周都上 */
    fun occursInWeek(weekNo: Int): Boolean {
        val ranges = weeksRanges
        if (ranges.isEmpty()) return true
        return ranges.any { weekNo in it }
    }

    /** 周次集合（解析结果展开；周次为空表示“每周”，此时返回 1..MAX_WEEK） */
    fun weeksSet(): Set<Int> =
        weeksRanges.flatMapTo(LinkedHashSet()) { it.toList() }.ifEmpty { (1..MAX_WEEK).toSet() }
    companion object {
        /** 一门课最多支持的周次（与 [parseWeeks] 上限一致） */
        const val MAX_WEEK = 40

        /** 
         * 把周次集合写成课表使用的原文（相邻周合并成区间，如 [1,3,4,5] → "1,3-5"）。
         * 空集合返回 ""（注意：课表里 "" 表示“每周”，不要用它表示“不上课”）。
         */
        fun encodeWeeks(weeks: Iterable<Int>): String {
            val sorted = weeks.filter { it in 1..MAX_WEEK }.toSortedSet()
            if (sorted.isEmpty()) return ""
            val parts = mutableListOf<String>()
            var start = -1
            var prev = -1
            for (w in sorted) {
                if (start < 0) {
                    start = w
                } else if (w != prev + 1) {
                    parts.add(if (start == prev) "$start" else "$start-$prev")
                    start = w
                }
                prev = w
            }
            if (start > 0) parts.add(if (start == prev) "$start" else "$start-$prev")
            return parts.joinToString(",")
        }

        /** 解析 "2-17" / "2" / "2-9,11-17" 形式的周次 */
        fun parseWeeks(raw: String): List<IntRange> {
            if (raw.isBlank()) return emptyList()
            val result = mutableListOf<IntRange>()
            raw.split(',', '，', '、').forEach { part ->
                val p = part.trim()
                if (p.isEmpty()) return@forEach
                val dash = p.indexOf('-')
                try {
                    if (dash > 0) {
                        val a = p.substring(0, dash).trim().toInt()
                        val b = p.substring(dash + 1).trim().toInt()
                        if (a in 1..40 && b in a..40) result.add(a..b)
                    } else {
                        val a = p.toInt()
                        if (a in 1..40) result.add(a..a)
                    }
                } catch (_: NumberFormatException) {
                    // 忽略无法解析的片段
                }
            }
            return result
        }
    }
}

/**
 * 一次抓取得到的“当前教学周”课表
 *
 * @param anchorEpochDay 第 1 教学周周一的 epochDay（各周日期均以此为基准推算）
 */
data class WeekSchedule(
    val semesterLabel: String,
    val weekNo: Int,
    val weekRangeLabel: String,
    val anchorEpochDay: Long,
    val courses: List<Course>,
    val fetchedAtMillis: Long,
) {
    /** 第 1 教学周周一（其余周次均以此为基准推算） */
    val firstMonday: LocalDate
        get() = LocalDate.ofEpochDay(anchorEpochDay)

    /** 本周（第 weekNo 周）周一 —— 修复：此前误把第 1 周周一当作本周周一，日期整体偏移 (weekNo-1) 周 */
    val monday: LocalDate
        get() = firstMonday.plusWeeks((weekNo - 1).toLong())

    fun dateOfWeekday(dayOfWeek: Int): LocalDate = monday.plusDays((dayOfWeek - 1).toLong())

    fun coursesOfDay(dayOfWeek: Int): List<Course> =
        courses.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.startPeriod }

    /** 指定日期是否为本周范围内 */
    fun containsDate(date: LocalDate): Boolean =
        !date.isBefore(monday) && !date.isAfter(monday.plusDays(6))

    /**
     * 指定日期对应的教学周（以第 1 周周一为基准）。
     * 注意：epochDay 差值可能为负，必须用向下取整——普通整除会向 0 取整，
     * 导致第 1 周之前的日期被误判为第 1 周（周条不再前移、选中框消失）。
     */
    fun teachingWeekOf(date: LocalDate): Int =
        Math.floorDiv(date.toEpochDay() - anchorEpochDay, 7L).toInt() + 1

    /** 指定日期要上的课程（日期不在本学期范围或该课程当天不上课则为空） */
    fun coursesOnDate(date: LocalDate): List<Course> {
        val week = teachingWeekOf(date)
        if (week < 1 || week > 40) return emptyList()
        // 法定节假日默认停课：课表按「周次 + 星期」排课，不会自动避开假日，统一在此扣除
        // （用户可在设置里开启「节假日显示课表」，开启后照常返回当天课程）
        if (HolidayTable.hidesCourses(date)) return emptyList()
        return coursesOfDay(date.dayOfWeek.value).filter { it.occursInWeek(week) }
    }

    /**
     * 指定日期「原本要上的课」，**不扣除法定节假日**。
     *
     * 调休 / 调课需要看到节假日当天的课（否则节日当天课表是空的，没东西可调）。
     */
    fun rawCoursesOnDate(date: LocalDate): List<Course> {
        val week = teachingWeekOf(date)
        if (week < 1 || week > 40) return emptyList()
        return coursesOfDay(date.dayOfWeek.value).filter { it.occursInWeek(week) }
    }
}

/**
 * 学期课表：包含多个教学周的课程。
 * 由“按周重放 getMyScheduleDetail 接口”抓取合并而来，支持周切换与整个学期的课前提醒。
 *
 * @param anchorEpochDay 第 1 教学周周一的 epochDay
 * @param weeks 教学周号 → 该周课程
 */
data class SemesterSchedule(
    val semesterLabel: String,
    val anchorEpochDay: Long,
    val weeks: Map<Int, List<Course>>,
    val fetchedAtMillis: Long,
) {
    /** 已抓取到的所有周次（升序） */
    val weekNumbers: List<Int>
        get() = weeks.keys.sorted()

    fun mondayOf(weekNo: Int): LocalDate =
        LocalDate.ofEpochDay(anchorEpochDay).plusWeeks((weekNo - 1).toLong())

    /** 教学周计算同样需要向下取整（见 WeekSchedule.teachingWeekOf 的说明） */
    fun teachingWeekOf(date: LocalDate): Int =
        Math.floorDiv(date.toEpochDay() - anchorEpochDay, 7L).toInt() + 1

    /** 指定日期要上的课程（含节假日停课判定，与 [WeekSchedule.coursesOnDate] 一致） */
    fun coursesOnDate(date: LocalDate): List<Course> = week(teachingWeekOf(date)).coursesOnDate(date)

    /**
     * 指定日期「原本要上的课」，**不扣除法定节假日**（调休/调课需要看到节假日当天的课）。
     * 日期不在本学期范围内时返回空列表。
     */
    fun rawCoursesOnDate(date: LocalDate): List<Course> {
        val weekNo = teachingWeekOf(date)
        if (weekNo < 1 || weekNo > 40) return emptyList()
        return weeks[weekNo].orEmpty()
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(weekNo) }
    }

    /** 转换为单周模型（供日/周视图、图片导出、提醒等复用原有逻辑） */
    fun week(weekNo: Int): WeekSchedule = WeekSchedule(        semesterLabel = semesterLabel,
        weekNo = weekNo,
        weekRangeLabel = rangeLabelOf(weekNo),
        anchorEpochDay = anchorEpochDay,
        courses = weeks[weekNo].orEmpty(),
        fetchedAtMillis = fetchedAtMillis,
    )

    private fun rangeLabelOf(weekNo: Int): String {
        val monday = mondayOf(weekNo)
        val sunday = monday.plusDays(6)
        return "%02d/%02d~%02d/%02d".format(
            monday.monthValue, monday.dayOfMonth, sunday.monthValue, sunday.dayOfMonth
        )
    }
}
