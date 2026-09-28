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
 * 课程时间与**节数**都可由用户在「设置 → 用户自定义 → 课程时间」中自定义：
 * - 节数范围 1..24（默认 14 节，不同学校/学段差异很大）；
 * - 可逐节修改起止时间，也可增删节次；
 * - 未自定义（或自定义数据损坏）时使用上面的默认作息。
 * 自定义数据以 "HH:mm-HH:mm|HH:mm-HH:mm|…" 的字符串持久化（段数即节数）。
 */
object PeriodTimes {

    /** 默认节数（与教务处作息一致） */
    const val DEFAULT_COUNT = 14

    /** 允许的最少 / 最多节数（不同学校、不同学段差异很大，因此完全可自定义） */
    const val MIN_COUNT = 1
    const val MAX_COUNT = 24

    /**
     * 当前生效的节数（可由用户自定义，见「设置 → 用户自定义 → 课程时间」）。
     * 注意：课程数据里可能出现超出该值的节次，[startOf]/[endOf] 仍能给出兜底时间。
     */
    @Volatile
    var count: Int = DEFAULT_COUNT
        private set

    /** 内置默认作息：前 14 节与教务处一致；之后的节次按“45 分钟上课 + 5 分钟课间”推算 */
    private val defaultStarts: List<LocalTime> = buildList {
        addAll(
            listOf(
                LocalTime.of(8, 0), LocalTime.of(8, 50), LocalTime.of(9, 50), LocalTime.of(10, 40),
                LocalTime.of(11, 30), LocalTime.of(14, 0), LocalTime.of(14, 50), LocalTime.of(15, 50),
                LocalTime.of(16, 40), LocalTime.of(17, 30), LocalTime.of(19, 0), LocalTime.of(19, 50),
                LocalTime.of(20, 40), LocalTime.of(21, 30),
            )
        )
        var cursor = LocalTime.of(22, 15)
        while (size < MAX_COUNT) {
            val start = cursor.plusMinutes(5)
            add(start)
            cursor = start.plusMinutes(45)
        }
    }

    private val defaultEnds: List<LocalTime> = buildList {
        addAll(
            listOf(
                LocalTime.of(8, 45), LocalTime.of(9, 35), LocalTime.of(10, 35), LocalTime.of(11, 25),
                LocalTime.of(12, 15), LocalTime.of(14, 45), LocalTime.of(15, 35), LocalTime.of(16, 35),
                LocalTime.of(17, 25), LocalTime.of(18, 15), LocalTime.of(19, 45), LocalTime.of(20, 35),
                LocalTime.of(21, 25), LocalTime.of(22, 15),
            )
        )
        while (size < MAX_COUNT) {
            add(defaultStarts[size].plusMinutes(45))
        }
    }

    /** 默认作息（设置界面「恢复默认」用；节数 = [DEFAULT_COUNT]） */
    val defaults: List<Pair<LocalTime, LocalTime>> =
        (0 until DEFAULT_COUNT).map { defaultStarts[it] to defaultEnds[it] }

    /** 第 n 节的默认起止时间（n 可超过默认节数，用于用户新增节次时预填） */
    fun defaultPairOf(period: Int): Pair<LocalTime, LocalTime> {
        val i = (period - 1).coerceIn(0, MAX_COUNT - 1)
        return defaultStarts[i] to defaultEnds[i]
    }

    // 当前生效的起止时间（定长数组，按整个数组原子替换，读取无需加锁）
    @Volatile
    private var startArr: Array<LocalTime> = defaultStarts.toTypedArray()

    @Volatile
    private var endArr: Array<LocalTime> = defaultEnds.toTypedArray()

    /** 是否正在使用用户自定义作息 */
    @Volatile
    var isCustom: Boolean = false
        private set

    fun startOf(period: Int): LocalTime = startArr[(period - 1).coerceIn(0, startArr.size - 1)]

    fun endOf(period: Int): LocalTime = endArr[(period - 1).coerceIn(0, endArr.size - 1)]

    /** 当前生效的起止时间（设置界面展示 / 编辑用） */
    fun current(): List<Pair<LocalTime, LocalTime>> = (1..count).map { startOf(it) to endOf(it) }

    /**
     * 应用用户自定义作息。[list] 为 null 或长度非法时回落到默认作息。
     * 支持 1..[MAX_COUNT] 节（不再固定 14 节）。返回是否成功应用了自定义作息。
     */
    fun applyCustom(list: List<Pair<LocalTime, LocalTime>>?): Boolean {
        if (list.isNullOrEmpty() || list.size > MAX_COUNT) {
            startArr = defaultStarts.toTypedArray()
            endArr = defaultEnds.toTypedArray()
            count = DEFAULT_COUNT
            isCustom = false
            return false
        }
        val n = list.size.coerceIn(MIN_COUNT, MAX_COUNT)
        val s = Array(MAX_COUNT) { defaultStarts[it] }
        val e = Array(MAX_COUNT) { defaultEnds[it] }
        for (i in 0 until n) {
            s[i] = list[i].first
            e[i] = list[i].second
        }
        startArr = s
        endArr = e
        count = n
        isCustom = true
        return true
    }

    /** 编码为持久化字符串（与默认作息完全一致且节数为默认值时返回空串，表示「使用默认」） */
    fun encode(list: List<Pair<LocalTime, LocalTime>>): String {
        if (list.isEmpty() || list.size > MAX_COUNT) return ""
        val sameAsDefault = list.withIndex().all { (i, t) ->
            t.first == defaultStarts[i] && t.second == defaultEnds[i]
        }
        if (sameAsDefault && list.size == DEFAULT_COUNT) return ""
        return list.joinToString("|") { "${format(it.first)}-${format(it.second)}" }
    }

    /** 解析持久化字符串（空串或无法解析时返回 null，表示使用默认作息） */
    fun decode(raw: String?): List<Pair<LocalTime, LocalTime>>? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val parts = s.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > MAX_COUNT) return null
        val out = ArrayList<Pair<LocalTime, LocalTime>>(parts.size)
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
