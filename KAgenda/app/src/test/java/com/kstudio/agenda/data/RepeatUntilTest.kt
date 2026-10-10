package com.kstudio.agenda.data

import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.RepeatRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 重复日程的「开始/结束（重复截止）」语义回归用例。
 *
 * 背景：结束日期 = 重复截止；**未填 / ≤ 开始日（含“与开始同日＝不限”的约定）＝ 持续延伸**。
 * 曾经的问题：`ed` 被填成与开始日同一天（AI 常这么干）时，条目**永远不显示**。
 */
class RepeatUntilTest {

    private val monday = LocalDate.of(2026, 10, 12) // 周一

    private fun repeating(end: LocalDate?) = AgendaEvent(
        id = "x",
        title = "例会",
        dateEpochDay = monday.toEpochDay(),
        repeatRule = RepeatRules.weekly(listOf(1)),
        endDateEpochDay = end?.toEpochDay(),
    )

    private fun item(end: LocalDate?, repeat: String = RepeatRules.weekly(listOf(1)), isLong: Boolean = false) =
        AiSkills.AiItem(
            isPlan = false,
            title = "例会",
            type = "",
            date = monday,
            startTime = "",
            endTime = "",
            location = "",
            note = "",
            repeat = repeat,
            isLong = isLong,
            endDate = end,
        )

    // ---------------- 截止语义 ----------------

    @Test
    fun `no end repeats forever`() {
        val e = repeating(null)
        assertTrue(e.occursOn(monday))
        assertTrue(e.occursOn(monday.plusWeeks(10)))
    }

    @Test
    fun `end equal to start means unlimited`() {
        val e = repeating(monday)
        assertTrue(e.occursOn(monday))
        assertTrue(e.occursOn(monday.plusWeeks(5)))
    }

    @Test
    fun `end before start means unlimited`() {
        val e = repeating(monday.minusDays(3))
        assertTrue(e.occursOn(monday.plusWeeks(2)))
    }

    @Test
    fun `end after start limits repeats`() {
        val until = monday.plusWeeks(2)
        val e = repeating(until)
        assertTrue(e.occursOn(monday))
        assertTrue(e.occursOn(until))
        assertFalse(e.occursOn(until.plusWeeks(1)))
    }

    @Test
    fun `non repeat short event keeps single day`() {
        val e = AgendaEvent(id = "y", title = "开会", dateEpochDay = monday.toEpochDay())
        assertTrue(e.occursOn(monday))
        assertFalse(e.occursOn(monday.plusDays(1)))
    }

    // ---------------- AI 通道构建 ----------------

    @Test
    fun `buildEvent drops repeat end equal to start`() {
        val e = AiAssistant.buildEvent(item(monday))
        assertNull(e.endDateEpochDay)
        assertTrue(e.occursOn(monday.plusWeeks(3)))
    }

    @Test
    fun `buildEvent drops repeat end before start`() {
        val e = AiAssistant.buildEvent(item(monday.minusDays(1)))
        assertNull(e.endDateEpochDay)
        assertTrue(e.occursOn(monday.plusWeeks(3)))
    }

    @Test
    fun `buildEvent keeps repeat end after start`() {
        val until = monday.plusWeeks(4)
        val e = AiAssistant.buildEvent(item(until))
        assertEquals(until.toEpochDay(), e.endDateEpochDay)
        assertFalse(e.occursOn(until.plusWeeks(1)))
    }

    @Test
    fun `long item drops repeat but keeps range`() {
        val e = AiAssistant.buildEvent(item(monday.plusDays(5), repeat = "weekly:1", isLong = true))
        assertEquals("", e.repeatRule)
        assertEquals(monday.plusDays(5).toEpochDay(), e.endDateEpochDay)
        assertTrue(e.occursOn(monday.plusDays(2)))
    }

    // ---------------- 小组件 / 常驻通知的「下一次发生」 ----------------

    @Test
    fun `upcomingView projects repeat onto the next occurrence`() {
        val biweekly = AgendaEvent(
            id = "b",
            title = "隔周例会",
            dateEpochDay = monday.toEpochDay(),
            repeatRule = RepeatRules.biweekly(listOf(1)), // 隔周周一（monday 就是周一）
        )
        // 开始日当天：下一次 = 开始日
        assertEquals(monday, biweekly.upcomingView(monday.atTime(6, 0)).date)
        // 隔一周的周一不该命中 → 应投影到再下一周的周一
        assertEquals(monday.plusWeeks(2), biweekly.upcomingView(monday.plusWeeks(1).atTime(6, 0)).date)
    }

    @Test
    fun `upcomingView keeps non repeat item unchanged`() {
        val single = AgendaEvent(id = "s", title = "开会", dateEpochDay = monday.toEpochDay())
        val view = single.upcomingView(monday.plusDays(3).atTime(6, 0))
        assertEquals(monday, view.date)
    }
}
