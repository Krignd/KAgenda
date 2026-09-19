package com.kstudio.agenda.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 计划/日程的重复规则。持久化为稳定 token 字符串：
 * - ""                不重复
 * - "daily:N"         每 N 天一次（N∈1..30；1=每天）
 * - "weekly:2,4,6"    每周的周几（1=周一 … 7=周日，多选）
 * - "biweekly:2,4"    隔周的周几（以开始日期所在自然周为基准，隔周命中）
 * - "monthly"         每月与开始日期同一天
 *
 * 仅短日程（!isLong）支持重复；长日程本身已是时间段，不再叠加。
 */
object RepeatRules {

    const val MONTHLY = "monthly"

    private val CN_WEEKDAY = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)
    private val CN_NUM = mapOf('一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10)

    fun daily(n: Int): String = "daily:" + n.coerceIn(1, 30)

    fun weekly(days: Collection<Int>): String =
        "weekly:" + days.filter { it in 1..7 }.toSortedSet().joinToString(",")

    fun biweekly(days: Collection<Int>): String =
        "biweekly:" + days.filter { it in 1..7 }.toSortedSet().joinToString(",")

    /** token 是否合法（空串=不重复，视为合法） */
    fun isValid(rule: String): Boolean = when {
        rule.isBlank() -> true
        rule == MONTHLY -> true
        rule.startsWith("daily:") -> rule.removePrefix("daily:").toIntOrNull()?.let { it in 1..30 } == true
        rule.startsWith("weekly:") || rule.startsWith("biweekly:") -> {
            val body = rule.substringAfter(":")
            body.isNotBlank() && body.split(",").all { it.toIntOrNull()?.let { d -> d in 1..7 } == true }
        }
        else -> false
    }

    /** 相对开始日期的“下一次命中”判断：d 是否命中该重复规则 */
    fun occursOn(start: LocalDate, rule: String, d: LocalDate): Boolean {
        if (rule.isBlank() || d.isBefore(start)) return false
        return when {
            rule == MONTHLY -> d.dayOfMonth == start.dayOfMonth
            rule.startsWith("daily:") -> {
                val n = rule.removePrefix("daily:").toIntOrNull() ?: return false
                if (n <= 0) false else ChronoUnit.DAYS.between(start, d) % n == 0L
            }
            rule.startsWith("weekly:") -> {
                val days = parseIntSet(rule.removePrefix("weekly:"))
                d.dayOfWeek.value in days
            }
            rule.startsWith("biweekly:") -> {
                val days = parseIntSet(rule.removePrefix("biweekly:"))
                if (d.dayOfWeek.value !in days) return false
                val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
                val dMonday = d.minusDays((d.dayOfWeek.value - 1).toLong())
                ChronoUnit.WEEKS.between(startMonday, dMonday) % 2 == 0L
            }
            else -> false
        }
    }

    /** 宽松解析“重复描述”（AI 字段或用户手输中文）；已经是合法 token 也直接通过；无法解析返回 null */
    fun parse(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s == MONTHLY) return MONTHLY
        if (s.contains(':') && isValid(s)) return s
        when {
            s == "none" || s == "无" || s.contains("不重复") -> return ""
            s.contains("每天") || s == "每日" -> return daily(1)
        }
        Regex("每\\s*([0-9一两二三四五六七八九十]+)\\s*天").find(s)?.let { m ->
            parseCnNumber(m.groupValues[1])?.let { return daily(it) }
        }
        val days = s.mapNotNull { CN_WEEKDAY[it] }.distinct()
        val bi = s.contains("隔周") || s.contains("双周") || s.contains("隔一") || s.contains("每隔一周")
        if (days.isNotEmpty()) {
            val weeklyWord = s.contains("周") || s.contains("星期") || s.contains("礼拜")
            if (bi) return biweekly(days)
            if (weeklyWord) return weekly(days)
        }
        if (s.contains("每月") || s.contains("每个月")) return MONTHLY
        return null
    }

    /** 编辑/保存入口：空白视为“不重复”；合法 token 原样；否则宽松解析，失败时保留原值 */
    fun normalizeInput(raw: String, keep: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        if (isValid(s) && (s == MONTHLY || s.contains(':'))) return s
        return parse(s) ?: keep
    }

    private fun parseIntSet(body: String): Set<Int> =
        body.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet()

    private fun parseCnNumber(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        if (s.isEmpty()) return null
        if (s == "十") return 10
        if (s.length == 1) return CN_NUM[s[0]]
        if (s.length == 2 && s[0] == '十') return 10 + (CN_NUM[s[1]] ?: return null)
        if (s.length == 2 && s[1] == '十') return (CN_NUM[s[0]] ?: return null) * 10
        if (s.length == 3 && s[1] == '十') {
            val tens = CN_NUM[s[0]] ?: return null
            val ones = CN_NUM[s[2]] ?: return null
            return tens * 10 + ones
        }
        return null
    }
}
