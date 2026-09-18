package com.kstudio.agenda.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 本地日程（用户自建，与教务课表无关）。
 * - 短日程：单日事件（dateEpochDay），起止时刻可选；
 * - 长日程：跨天时间段（dateEpochDay ~ endDateEpochDay），例如“考试报名 9/10 10:00 ~ 10/30 22:00”，
 *   在「进行中」页专门展示。
 * 支持类型（面试/比赛/讲座/考试/会议/其他，各有默认色）与自定义颜色，
 * 可在日/周/月视图与图片导出中展示。
 */
data class AgendaEvent(
    val id: String,
    val title: String,
    /** 短日程的日期 / 长日程的开始日期（epochDay） */
    val dateEpochDay: Long,
    /** 开始时刻 "HH:mm"（空串表示不设置） */
    val startTime: String = "",
    /** 结束时刻 "HH:mm"（空串表示不设置） */
    val endTime: String = "",
    val location: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** 长日程：跨天时间段 */
    val isLong: Boolean = false,
    /** 长日程结束日期（短日程为 null） */
    val endDateEpochDay: Long? = null,
    /** 类型键：interview/contest/lecture/exam/meeting/other（持久化存键，显示时本地化） */
    val type: String = "",
    /** 自定义颜色（ARGB；0=使用类型默认色） */
    val colorArgb: Int = 0,
    /** true=「计划」页的个人计划；false=「日程表」的日程 */
    val isPlan: Boolean = false,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)

    val endDate: LocalDate get() = LocalDate.ofEpochDay(endDateEpochDay ?: dateEpochDay)

    /** "19:00-21:00" / "19:00" / ""（未设置时间时为空串） */
    val timeLabel: String
        get() = when {
            startTime.isNotBlank() && endTime.isNotBlank() -> "$startTime-$endTime"
            startTime.isNotBlank() -> startTime
            else -> ""
        }

    /** 起止文案：长日程 "9/10 10:00 ~ 10/30 22:00"；短日程同 timeLabel */
    val rangeLabel: String
        get() = if (isLong) {
            buildString {
                append("${date.monthValue}/${date.dayOfMonth}")
                if (startTime.isNotBlank()) append(" ").append(startTime)
                append(" ~ ")
                append("${endDate.monthValue}/${endDate.dayOfMonth}")
                if (endTime.isNotBlank()) append(" ").append(endTime)
            }
        } else timeLabel

    fun startDateTime(): LocalDateTime = date.atTime(parseTime(startTime) ?: LocalTime.MIN)

    fun endDateTime(): LocalDateTime = endDate.atTime(parseTime(endTime) ?: LocalTime.MAX)

    /** 当前是否正在进行（长日程跨天适用；短日程按当天时刻判断） */
    fun isOngoing(now: LocalDateTime = LocalDateTime.now()): Boolean =
        !now.isBefore(startDateTime()) && !now.isAfter(endDateTime())

    /** 是否覆盖指定日期（短日程=当天；长日程=起止日期之间任意一天） */
    fun coversDate(d: LocalDate): Boolean {
        val day = d.toEpochDay()
        val start = minOf(dateEpochDay, endDateEpochDay ?: dateEpochDay)
        val end = maxOf(dateEpochDay, endDateEpochDay ?: dateEpochDay)
        return day in start..end
    }

    /** 展示颜色（0 表示未指定，由 UI 决定默认色） */
    val displayColor: Int
        get() = if (colorArgb != 0) colorArgb else AgendaTypes.defaultColor(type)

    companion object {
        private fun parseTime(s: String): LocalTime? = runCatching { LocalTime.parse(s) }.getOrNull()
    }
}

/** 日程类型（默认颜色）与编辑器可选色板。类型以稳定键持久化，显示时按当前语言本地化。 */
object AgendaTypes {

    /** 类型键顺序（编辑器 chips 按此展示） */
    val ORDER = listOf("interview", "contest", "lecture", "exam", "meeting", "other")

    /** 类型键 → 默认颜色 */
    private val COLORS: Map<String, Int> = mapOf(
        "interview" to 0xFF8E24AA.toInt(),
        "contest" to 0xFFE53935.toInt(),
        "lecture" to 0xFF1E88E5.toInt(),
        "exam" to 0xFFD84315.toInt(),
        "meeting" to 0xFF00897B.toInt(),
        "other" to 0xFF607D8B.toInt(),
    )

    /** 编辑器颜色板 */
    val PALETTE: List<Int> = listOf(
        0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB,
        0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFF4511E, 0xFFFDD835,
    ).map { it.toInt() }

    fun defaultColor(key: String): Int = COLORS[key] ?: 0

    /** 兼容旧版本数据：把此前存的中文类型名映射为键 */
    fun fromLegacy(raw: String): String = when (raw) {
        "面试" -> "interview"
        "比赛" -> "contest"
        "讲座" -> "lecture"
        "考试" -> "exam"
        "会议" -> "meeting"
        "其他" -> "other"
        else -> raw
    }
}
