package com.kstudio.agenda.data

import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.RepeatRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * AI 助手：日程与计划同样支持重复规则。
 * 回归用例——曾出现“每周二四六晚上8点去操场锻炼”只识别到时间与地点、丢掉重复规则的问题。
 * 注：只覆盖不依赖 org.json（本地单测中为桩实现）的纯逻辑。
 */
class AiAssistantRepeatTest {

    private val start = LocalDate.of(2026, 9, 22) // 周二

    private fun item(isPlan: Boolean, repeat: String) = AiSkills.AiItem(
        isPlan = isPlan,
        title = "操场锻炼",
        type = "other",
        date = start,
        startTime = "20:00",
        endTime = "21:00",
        location = "操场",
        note = "",
        repeat = repeat,
    )

    @Test
    fun `agenda item keeps weekly repeat`() {
        val weekly = RepeatRules.weekly(listOf(2, 4, 6)) // 每周二/四/六
        val event = AiAssistant.buildEvent(item(isPlan = false, repeat = weekly))
        assertFalse(event.isPlan)
        assertEquals("20:00", event.startTime)
        assertEquals("weekly:2,4,6", event.repeatRule)
        assertTrue(RepeatRules.occursOn(start, event.repeatRule, start))             // 周二
        assertTrue(RepeatRules.occursOn(start, event.repeatRule, start.plusDays(2))) // 周四
        assertFalse(RepeatRules.occursOn(start, event.repeatRule, start.plusDays(1)))
    }

    @Test
    fun `plan item keeps repeat as before`() {
        val biweekly = RepeatRules.biweekly(listOf(2))
        val event = AiAssistant.buildEvent(item(isPlan = true, repeat = biweekly))
        assertTrue(event.isPlan)
        assertEquals("biweekly:2", event.repeatRule)
    }

    @Test
    fun `update applies repeat on agenda too`() {
        val existing = AgendaEvent(id = "1", title = "锻炼", dateEpochDay = start.toEpochDay())
        val edited = AiAssistant.applySet(
            existing,
            AiSkills.AiSet(
                title = null, date = null, startTime = null, endTime = null,
                location = null, note = null, repeat = "biweekly:3",
            ),
        )
        assertEquals("biweekly:3", edited.repeatRule)
    }

    @Test
    fun `long event never carries repeat`() {
        val existing = AgendaEvent(
            id = "2",
            title = "报名",
            dateEpochDay = start.toEpochDay(),
            isLong = true,
            endDateEpochDay = start.plusDays(30).toEpochDay(),
        )
        val edited = AiAssistant.applySet(
            existing,
            AiSkills.AiSet(
                title = null, date = null, startTime = null, endTime = null,
                location = null, note = null, repeat = "weekly:2",
            ),
        )
        assertEquals("", edited.repeatRule)
    }
}
