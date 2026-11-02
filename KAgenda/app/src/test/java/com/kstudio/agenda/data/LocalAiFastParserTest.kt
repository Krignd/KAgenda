package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LocalAiFastParserTest {
    private val date = LocalDate.of(2026, 10, 18)
    private fun event(title: String = "体检") = AiAssistant.buildEvent(AiSkills.AiItem(
        false, title, "other", date, "10:00", "", "医院", "保留备注",
    ))

    @Test fun `Chinese numerals and terminal punctuation take the fast addition path`() {
        for (input in listOf("明天下午三点开会。", "今天09:30体检", "后天晚上八点半看电影！", "明天１５：３０开会"))
            assertTrue(input, LocalAiFastParser.simpleAddition(input))
        for (input in listOf("明天三点或四点开会", "明天三点和四点分别开会", "明天3点开会后天4点聚餐", "每周三点开会", "明天三点不要开会", "明天3点到5点开会"))
            assertFalse(input, LocalAiFastParser.simpleAddition(input))
    }

    @Test fun `explicit unique updates use an id and retain unrelated fields`() {
        val e = event()
        val op = LocalAiFastParser.mutation("把体检改到明天下午三点。", listOf(e), date)!!.single() as AiSkills.AiOp.Update
        assertEquals(e.id, op.matchId)
        assertEquals(date.plusDays(1), op.set.date)
        assertEquals("15:00", op.set.startTime)
        assertNull(op.set.note)
        assertNull(op.set.alarm)
        assertNull(op.set.endTime)
    }

    @Test fun `unknown duplicate recurring interval and conditional targets fall back`() {
        val e = event()
        assertNull(LocalAiFastParser.mutation("删除体检", listOf(e, e.copy(id = "duplicate")), date))
        assertNull(LocalAiFastParser.mutation("删除不存在", listOf(e), date))
        assertNull(LocalAiFastParser.mutation("删除体检", listOf(e.copy(repeatRule = "daily:1")), date))
        assertNull(LocalAiFastParser.mutation("把体检改到明天下午三点", listOf(e.copy(endTime = "11:00")), date))
        assertNull(LocalAiFastParser.mutation("如果下雨把体检改到明天", listOf(e), date))
        assertNull(LocalAiFastParser.mutation("删除明天的体检", listOf(e), date))
        assertNull(LocalAiFastParser.mutation("把体检改到每周三", listOf(e), date))
    }

    @Test fun `date-only changes retain an existing time interval`() {
        val e = event().copy(endTime = "11:00")
        val op = LocalAiFastParser.mutation("把体检改到后天", listOf(e), date)!!.single() as AiSkills.AiOp.Update
        assertEquals(date.plusDays(2), op.set.date)
        assertNull(op.set.startTime)
        assertNull(op.set.endTime)
    }

    @Test fun `deletion requires an exact unique title`() {
        val e = event()
        assertEquals(e.id, (LocalAiFastParser.mutation("删除体检", listOf(e), date)!!.single() as AiSkills.AiOp.Delete).matchId)
        assertNull(LocalAiFastParser.mutation("删除体", listOf(e), date))
        assertNull(LocalAiFastParser.mutation("不要删除体检", listOf(e), date))
    }

    @Test fun `candidate limit is applied before serialization without losing the named target`() {
        val events = (1..100).map { event("活动$it") }
        val lines = AiAssistant.contextLines(events, "修改活动100", date, limit = 8)
        assertEquals(8, lines.size)
        assertTrue(lines.first().contains("活动100"))
    }
}
