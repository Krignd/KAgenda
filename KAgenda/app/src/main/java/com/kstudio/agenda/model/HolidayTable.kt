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
}
