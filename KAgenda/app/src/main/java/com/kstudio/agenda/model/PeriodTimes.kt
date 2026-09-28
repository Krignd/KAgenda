package com.kstudio.agenda.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程节次对应时间（默认来源：项目文件夹中的“课程时间.txt”）
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
 *
 * 课程时间可由用户在「设置 → 用户自定义 → 课程时间」中逐节自定义；
 * 未自定义（或自定义数据损坏）时使用上面的默认作息。
 * 自定义数据以 "HH:mm-HH:mm|HH:mm-HH:mm|…"（共 14 段）的字符串持久化。
 */
object PeriodTimes {

    const val count = 14

    private val defaultStarts = listOf(
        LocalTime.of(8, 0), LocalTime.of(8, 50), LocalTime.of(9, 50), LocalTime.of(10, 40),
        LocalTime.of(11, 30), LocalTime.of(14, 0), LocalTime.of(14, 50), LocalTime.of(15, 50),
        LocalTime.of(16, 40), LocalTime.of(17, 30), LocalTime.of(19, 0), LocalTime.of(19, 50),
        LocalTime.of(20, 40), LocalTime.of(21, 30),
    )

    private val defaultEnds = listOf(
        LocalTime.of(8, 45), LocalTime.of(9, 35), LocalTime.of(10, 35), LocalTime.of(11, 25),
        LocalTime.of(12, 15), LocalTime.of(14, 45), LocalTime.of(15, 35), LocalTime.of(16, 35),
        LocalTime.of(17, 25), LocalTime.of(18, 15), LocalTime.of(19, 45), LocalTime.of(20, 35),
        LocalTime.of(21, 25), LocalTime.of(22, 15),
    )

    /** 默认作息（设置界面「恢复默认」用） */
    val defaults: List<Pair<LocalTime, LocalTime>> =
        (0 until count).map { defaultStarts[it] to defaultEnds[it] }

    // 当前生效的起止时间（用数组缓存，避免高频访问时反复做 list 映射/分配）
    @Volatile
    private var startArr: Array<LocalTime> = defaultStarts.toTypedArray()

    @Volatile
    private var endArr: Array<LocalTime> = defaultEnds.toTypedArray()

    /** 是否正在使用用户自定义作息 */
    @Volatile
    var isCustom: Boolean = false
        private set

    fun startOf(period: Int): LocalTime = startArr[(period - 1).coerceIn(0, count - 1)]

    fun endOf(period: Int): LocalTime = endArr[(period - 1).coerceIn(0, count - 1)]

    fun defaultStartOf(period: Int): LocalTime = defaultStarts[(period - 1).coerceIn(0, count - 1)]

    fun defaultEndOf(period: Int): LocalTime = defaultEnds[(period - 1).coerceIn(0, count - 1)]

    /** 当前生效的 14 节起止时间（设置界面展示 / 编辑用） */
    fun current(): List<Pair<LocalTime, LocalTime>> = (1..count).map { startOf(it) to endOf(it) }

    /**
     * 应用用户自定义作息。[list] 为 null、长度不符时回落到默认作息。
     * 返回是否成功应用了自定义作息。
     */
    fun applyCustom(list: List<Pair<LocalTime, LocalTime>>?): Boolean {
        val ok = list?.takeIf { it.size == count }
        if (ok == null) {
            startArr = defaultStarts.toTypedArray()
            endArr = defaultEnds.toTypedArray()
            isCustom = false
            return false
        }
        startArr = Array(count) { ok[it].first }
        endArr = Array(count) { ok[it].second }
        isCustom = true
        return true
    }

    /** 编码为持久化字符串（与默认作息完全相同时返回空串，表示「使用默认」） */
    fun encode(list: List<Pair<LocalTime, LocalTime>>): String {
        if (list.size != count) return ""
        val sameAsDefault = list.withIndex().all { (i, t) ->
            t.first == defaultStarts[i] && t.second == defaultEnds[i]
        }
        if (sameAsDefault) return ""
        return list.joinToString("|") { "${format(it.first)}-${format(it.second)}" }
    }

    /** 解析持久化字符串（空串或无法解析时返回 null，表示使用默认作息） */
    fun decode(raw: String?): List<Pair<LocalTime, LocalTime>>? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val parts = s.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != count) return null
        val out = ArrayList<Pair<LocalTime, LocalTime>>(count)
        for (p in parts) {
            val dash = p.indexOf('-')
            if (dash <= 0) return null
            val start = runCatching { LocalTime.parse(p.substring(0, dash).trim()) }.getOrNull()
                ?: return null
            val end = runCatching { LocalTime.parse(p.substring(dash + 1).trim()) }.getOrNull()
                ?: return null
            out.add(start to end)
        }
        return out
    }

    /** “第3-4节” 的上课时间段，例如 09:50-11:25 */
    fun rangeOf(startPeriod: Int, endPeriod: Int): String =
        "${format(startOf(startPeriod))}-${format(endOf(endPeriod))}"

    /** 某个日期某一节次的上课开始时间戳（毫秒） */
    fun startMillisEpoch(date: LocalDate, period: Int): Long =
        date.atTime(startOf(period)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun format(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
}
