package com.kstudio.agenda.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 重复规则（计划）：token 合法性、命中判断与中文宽松解析 */
class RepeatRulesTest {

    private val start = LocalDate.of(2026, 9, 15) // 周二

    @Test
    fun `daily every 3 days hits start and +3n only`() {
        val rule = RepeatRules.daily(3)
        assertTrue(RepeatRules.occursOn(start, rule, start))
        assertTrue(RepeatRules.occursOn(start, rule, start.plusDays(3)))
        assertTrue(RepeatRules.occursOn(start, rule, start.plusDays(9)))
        assertFalse(RepeatRules.occursOn(start, rule, start.plusDays(1)))
        assertFalse(RepeatRules.occursOn(start, rule, start.plusDays(2)))
    }

    @Test
    fun `weekly hits selected weekdays`() {
        val rule = RepeatRules.weekly(listOf(2, 4, 6)) // 周二/四/六
        assertTrue(RepeatRules.occursOn(start, rule, start))                    // 周二
        assertTrue(RepeatRules.occursOn(start, rule, start.plusDays(2)))        // 周四
        assertTrue(RepeatRules.occursOn(start, rule, start.plusDays(4)))        // 周六
        assertFalse(RepeatRules.occursOn(start, rule, start.plusDays(1)))       // 周三
        assertFalse(RepeatRules.occursOn(start, rule, start.plusDays(5)))       // 周日
    }

    @Test
    fun `biweekly skips every other week`() {
        val rule = RepeatRules.biweekly(listOf(2)) // 隔周周二
        assertTrue(RepeatRules.occursOn(start, rule, start))
        assertTrue(RepeatRules.occursOn(start, rule, start.plusWeeks(2)))
        assertTrue(RepeatRules.occursOn(start, rule, start.plusWeeks(4)))
        assertFalse(RepeatRules.occursOn(start, rule, start.plusWeeks(1)))
        assertFalse(RepeatRules.occursOn(start, rule, start.plusWeeks(3)))
    }

    @Test
    fun `monthly hits same day of month`() {
        assertTrue(RepeatRules.occursOn(start, RepeatRules.MONTHLY, LocalDate.of(2026, 10, 15)))
        assertTrue(RepeatRules.occursOn(start, RepeatRules.MONTHLY, LocalDate.of(2026, 11, 15)))
        assertFalse(RepeatRules.occursOn(start, RepeatRules.MONTHLY, LocalDate.of(2026, 10, 16)))
    }

    @Test
    fun `nothing before start`() {
        assertFalse(RepeatRules.occursOn(start, RepeatRules.daily(1), start.minusDays(1)))
        assertFalse(RepeatRules.occursOn(start, RepeatRules.weekly(listOf(2)), start.minusWeeks(1)))
    }

    @Test
    fun `validity check`() {
        assertTrue(RepeatRules.isValid(""))
        assertTrue(RepeatRules.isValid("monthly"))
        assertTrue(RepeatRules.isValid("daily:3"))
        assertTrue(RepeatRules.isValid("weekly:1,4,6"))
        assertTrue(RepeatRules.isValid("biweekly:2"))
        assertFalse(RepeatRules.isValid("daily:0"))
        assertFalse(RepeatRules.isValid("weekly:"))
        assertFalse(RepeatRules.isValid("weekly:8"))
        assertFalse(RepeatRules.isValid("yearly:1"))
    }

    @Test
    fun `chinese parse`() {
        assertEquals("daily:1", RepeatRules.parse("每天"))
        assertEquals("daily:3", RepeatRules.parse("每3天"))
        assertEquals("daily:5", RepeatRules.parse("每五天"))
        assertEquals("weekly:2,4,6", RepeatRules.parse("每周二、四、六"))
        assertEquals("weekly:7", RepeatRules.parse("每周日"))
        assertEquals("biweekly:2", RepeatRules.parse("隔周周二"))
        assertEquals("biweekly:3", RepeatRules.parse("双周三"))
        assertEquals("monthly", RepeatRules.parse("每月"))
        assertEquals("", RepeatRules.parse("不重复"))
        assertEquals("weekly:1,4,6", RepeatRules.parse("weekly:1,4,6"))  // 已是 token
        assertEquals(null, RepeatRules.parse("随缘"))
    }

    @Test
    fun `event occursOn uses repeat rule`() {
        val plan = AgendaEvent(
            id = "1",
            title = "跑步",
            dateEpochDay = start.toEpochDay(),
            isPlan = true,
            repeatRule = RepeatRules.weekly(listOf(2, 4, 6)),
        )
        assertTrue(plan.occursOn(start.plusDays(2)))     // 周四
        assertFalse(plan.occursOn(start.plusDays(3)))    // 周五
        val plain = plan.copy(repeatRule = "")
        assertTrue(plain.occursOn(start))
        assertFalse(plain.occursOn(start.plusDays(2)))
    }
}
