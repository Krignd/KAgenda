package com.kstudio.agenda.model

import java.time.LocalDate

/**
 * 一条课程安排（对应网页“我的课表”周网格中的一个课程块）
 *
 * @param weeksRaw 上课周次原文，如 "2-17"、"2-9,11-17"，无法解析时为 ""
 * @param dayOfWeek 1=周一 ... 7=周日
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
) {
    /** 稳定标识：用于提醒的 requestCode / 图片配色 */
    val id: String
        get() = "${code.ifBlank { title }}@${dayOfWeek}-${startPeriod}-${endPeriod}-$room"

    val periodLabel: String
        get() = if (startPeriod == endPeriod) "第${startPeriod}节" else "第${startPeriod}-${endPeriod}节"

    val timeRange: String
        get() = PeriodTimes.rangeOf(startPeriod, endPeriod)

    val weeksRanges: List<IntRange>
        get() = parseWeeks(weeksRaw)

    /** 该课程在指定教学周是否上课；周次信息缺失时视为每周都上 */
    fun occursInWeek(weekNo: Int): Boolean {
        val ranges = weeksRanges
        if (ranges.isEmpty()) return true
        return ranges.any { weekNo in it }
    }

    companion object {
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

    /** 转换为单周模型（供日/周视图、图片导出、提醒等复用原有逻辑） */
    fun week(weekNo: Int): WeekSchedule = WeekSchedule(
        semesterLabel = semesterLabel,
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
