package com.kstudio.agenda.model

/**
 * “模糊时间”支持：凌晨 / 早晨 / 上午 / 下午 / 晚上 / 午夜。
 * 存储时直接以中文词作为 startTime/endTime 的值；
 * 提供排序键与大致时刻，供列表排序、小组件倒计时等使用。
 */
object FuzzyTime {

    /** 可选模糊时刻（顺序=从早到晚） */
    val ORDER = listOf("凌晨", "早晨", "上午", "下午", "晚上", "午夜")

    /** 模糊词对应的大致分钟数（排序与锚点用） */
    private val APPROX_MINUTES: Map<String, Int> = mapOf(
        "凌晨" to 5 * 60,
        "早晨" to 7 * 60,
        "上午" to 9 * 60 + 30,
        "下午" to 14 * 60 + 30,
        "晚上" to 19 * 60 + 30,
        "午夜" to 0,
    )

    private val PRECISE = Regex("^\\d{1,2}:\\d{2}$")

    /** 是否精确时刻（"HH:mm"） */
    fun isPrecise(s: String): Boolean = PRECISE.matches(s.trim())

    /** 是否模糊时刻词 */
    fun isFuzzy(s: String): Boolean = APPROX_MINUTES.containsKey(s.trim())

    /** 解析为分钟数（精确 "HH:mm" 或模糊词；无法解析返回 null） */
    fun minutesOf(s: String): Int? {
        val t = s.trim()
        APPROX_MINUTES[t]?.let { return it }
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(t) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    /** 排序键：空串最前（-1）；精确/模糊按大致时间；无法解析的放最后 */
    fun sortKey(s: String): Int {
        val t = s.trim()
        if (t.isEmpty()) return -1
        return minutesOf(t) ?: Int.MAX_VALUE
    }
}
