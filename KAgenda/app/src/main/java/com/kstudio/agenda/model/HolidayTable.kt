package com.kstudio.agenda.model

import java.time.LocalDate

/** Official holiday and make-up workday marks.
 * The bundled 2026 dates follow the State Council notice of 2025-11-04.
 * OfficialHolidayStore can atomically replace a year using a validated official notice.
 */
object HolidayTable {
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /**
     * 用户设置：法定节假日是否照常显示课表。
     *
     * - `false`（默认）= 节假日停课，课表里不显示课程（与既有行为一致）；
     * - `true` = 节假日照常显示当天的课程。
     *
     * 与 [PeriodTimes] 同一套路：由设置流（KebiaoApp / AppViewModel）写入全局值，
     * 供模型层与后台入口（小组件 / 常驻通知 / 提醒 / 导出）直接读取。
     */
    @Volatile
    var showCoursesOnHoliday: Boolean = false
        private set

    fun setShowCoursesOnHoliday(enabled: Boolean) {
        if (showCoursesOnHoliday != enabled) { showCoursesOnHoliday = enabled; revision.value++ }
    }

    /** (开始日期, 结束日期, 名称) —— 含首尾 */
    private val ranges: List<Triple<String, String, String>> = listOf(
        Triple("2026-01-01", "2026-01-03", "元旦"),
        Triple("2026-02-15", "2026-02-23", "春节"),
        Triple("2026-04-04", "2026-04-06", "清明"),
        Triple("2026-05-01", "2026-05-05", "劳动节"),
        Triple("2026-06-19", "2026-06-21", "端午"),
        Triple("2026-09-25", "2026-09-27", "中秋"),
        Triple("2026-10-01", "2026-10-07", "国庆"),
    )

    private val marks: Map<Long, String> by lazy {
        val map = HashMap<Long, String>()
        for ((start, end, name) in ranges) {
            runCatching {
                var d = LocalDate.parse(start)
                val e = LocalDate.parse(end)
                while (!d.isAfter(e)) {
                    map[d.toEpochDay()] = name
                    d = d.plusDays(1)
                }
            }
        }
        map
    }

    @Volatile private var officialMarks: Map<Int, Map<Long, String>> = emptyMap()
    @Volatile private var officialWorkdays: Map<Int, Set<Long>> = emptyMap()
    private val builtInWorkdays = setOf("2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10")
        .map { LocalDate.parse(it).toEpochDay() }.toSet()
    @Synchronized fun replaceYear(year: Int, holidays: Map<Long, String>, workdays: Set<Long>) {
        officialMarks = officialMarks + (year to holidays)
        officialWorkdays = officialWorkdays + (year to workdays)
        revision.value++
    }
    @Volatile var overrides: Map<Long, HolidayOverride> = emptyMap()
        private set
    @Synchronized fun replaceOverrides(values: Map<Long, HolidayOverride>) {
        if (overrides != values) { overrides = values.toMap(); revision.value++ }
    }
    fun hasOfficialYear(year: Int): Boolean = year == 2026 || officialMarks.containsKey(year)
    fun isWorkday(date: LocalDate): Boolean = overrides[date.toEpochDay()]?.let { it.kind == HolidayKind.WORKDAY }
        ?: (date.toEpochDay() in (officialWorkdays[date.year] ?: builtInWorkdays))
    private fun holidayName(date: LocalDate): String? {
        overrides[date.toEpochDay()]?.let { return if (it.kind == HolidayKind.HOLIDAY) it.name else null }
        return (officialMarks[date.year] ?: marks)[date.toEpochDay()]
    }

    /** 该日期是节假日则返回名称（如“中秋”），否则 null */
    fun nameOf(date: LocalDate): String? = holidayName(date) ?: if (overrides.containsKey(date.toEpochDay())) null else festivalName(date)

    /** 农历日期核对来源：https://www.hko.gov.hk/tc/gts/time/calendar/pdf/files/2027.pdf */
    fun festivalName(date: LocalDate): String? = when (date.toString()) {
        "2027-02-06" -> "春节"
        else -> null
    }

    /** 是否法定节假日（放假中） */
    fun isHoliday(date: LocalDate): Boolean = holidayName(date) != null

    /** 是否双休日（周六 / 周日） */
    fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek.value >= 6

    /** 是否休息日（双休或节假日） */
    fun isRestDay(date: LocalDate): Boolean = !isWorkday(date) && (isWeekend(date) || isHoliday(date))

    /**
     * 该日期是否应当**隐藏/跳过课程**（即“停课”）。
     *
     * 只有“法定节假日”且用户未开启「节假日显示课表」时返回 true；
     * 双休日不算停课（课表按周次+星期排课，周末本来就没课）。
     *
     * 所有「节假日停课」判定统一走这里，保证小组件 / 常驻通知 / 提醒 / 导出 / 视图行为一致。
     */
    fun hidesCourses(date: LocalDate): Boolean = isHoliday(date) && !showCoursesOnHoliday
}
