package com.kstudio.agenda.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程节次对应时间（来源：项目文件夹中的“课程时间.txt”）
 *
 * 1  08:00-08:45
 * 2  08:50-09:35
 * 3  09:50-10:35
 * 4  10:40-11:25
 * 5  11:30-12:15
 * 6  14:00-14:45
 * 7  14:50-15:35
 * 8  15:50-16:35
 * 9  16:40-17:25
 * 10 17:30-18:15
 * 11 19:00-19:45
 * 12 19:50-20:35
 * 13 20:40-21:25
 * 14 21:30-22:15
 */
object PeriodTimes {

    const val count = 14

    private val starts = listOf(
        LocalTime.of(8, 0), LocalTime.of(8, 50), LocalTime.of(9, 50), LocalTime.of(10, 40),
        LocalTime.of(11, 30), LocalTime.of(14, 0), LocalTime.of(14, 50), LocalTime.of(15, 50),
        LocalTime.of(16, 40), LocalTime.of(17, 30), LocalTime.of(19, 0), LocalTime.of(19, 50),
        LocalTime.of(20, 40), LocalTime.of(21, 30),
    )

    private val ends = listOf(
        LocalTime.of(8, 45), LocalTime.of(9, 35), LocalTime.of(10, 35), LocalTime.of(11, 25),
        LocalTime.of(12, 15), LocalTime.of(14, 45), LocalTime.of(15, 35), LocalTime.of(16, 35),
        LocalTime.of(17, 25), LocalTime.of(18, 15), LocalTime.of(19, 45), LocalTime.of(20, 35),
        LocalTime.of(21, 25), LocalTime.of(22, 15),
    )

    fun startOf(period: Int): LocalTime = starts[(period - 1).coerceIn(0, count - 1)]

    fun endOf(period: Int): LocalTime = ends[(period - 1).coerceIn(0, count - 1)]

    /** “第3-4节” 的上课时间段，例如 09:50-11:25 */
    fun rangeOf(startPeriod: Int, endPeriod: Int): String =
        "${format(startOf(startPeriod))}-${format(endOf(endPeriod))}"

    /** 某个日期某一节次的上课开始时间戳（毫秒） */
    fun startMillisEpoch(date: LocalDate, period: Int): Long =
        date.atTime(startOf(period)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun format(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
}
