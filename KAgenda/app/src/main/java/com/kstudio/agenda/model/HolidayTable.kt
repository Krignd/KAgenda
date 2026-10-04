package com.kstudio.agenda.model

import java.time.LocalDate

/**
 * 法定节假日表（用于周 / 日 / 月视图的节假日与双休日标注）。
 *
 * 说明：
 * - 放假区间依据公开惯例整理（含首尾）；如与国务院当年安排或学校校历不一致，
 *   **直接修改下面的 ranges 即可**（每行：开始日期、结束日期、名称）；
 * - 本表未包含“调休上班”信息；如需要，可仿照 ranges 扩展一个 workday 表；
 * - 双休日（周六/周日）无需在此表登记，由 [isWeekend] 自动判断。
 */
object HolidayTable {

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
        showCoursesOnHoliday = enabled
    }

    /** (开始日期, 结束日期, 名称) —— 含首尾 */
    private val ranges: List<Triple<String, String, String>> = listOf(
        Triple("2026-01-01", "2026-01-03", "元旦"),
        Triple("2026-02-16", "2026-02-22", "春节"),
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

    /** 该日期是节假日则返回名称（如“中秋”），否则 null */
    fun nameOf(date: LocalDate): String? = marks[date.toEpochDay()]

    /** 是否法定节假日（放假中） */
    fun isHoliday(date: LocalDate): Boolean = marks.containsKey(date.toEpochDay())

    /** 是否双休日（周六 / 周日） */
    fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek.value >= 6

    /** 是否休息日（双休或节假日） */
    fun isRestDay(date: LocalDate): Boolean = isWeekend(date) || isHoliday(date)

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
