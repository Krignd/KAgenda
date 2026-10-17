package com.kstudio.agenda.model

import com.kstudio.agenda.data.AiAssistant
import com.kstudio.agenda.data.AiSkills
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AgendaReminderRegressionTest {
    private val date = LocalDate.of(2026, 10, 17)
    private fun event() = AgendaEvent("test", "测试", date.toEpochDay(), "09:00", "10:00")
    @Test fun `weekly ongoing occurrence stays today instead of jumping to next week`() {
        val event = event().copy(dateEpochDay = date.minusWeeks(3).toEpochDay(), repeatRule = "weekly:6")
        val now = date.atTime(9, 30)
        assertTrue(event.isOngoing(now))
        val view = event.upcomingView(now)
        assertEquals(date, view.date)
        assertTrue(view.isOngoing(now))
        assertEquals(date.plusWeeks(1), event.upcomingView(date.atTime(10, 0)).date)
    }
    @Test fun `repeat cutoff is not the ongoing end time`() {
        val event = event().copy(dateEpochDay = date.minusWeeks(1).toEpochDay(), endDateEpochDay = date.plusWeeks(2).toEpochDay(), repeatRule = "weekly:6")
        assertFalse(event.isOngoing(date.atTime(11, 0)))
        assertTrue(event.isOngoing(date.atTime(9, 15)))
        assertFalse(event.isOngoing(date.plusDays(1).atTime(9, 15)))
    }
    @Test fun `at exact end an event is no longer ongoing`() {
        assertFalse(event().isOngoing(date.atTime(10, 0)))
    }
    @Test fun `all day long event remains ongoing`() {
        val event = event().copy(isLong = true, startTime = "", endTime = "", endDateEpochDay = date.plusDays(4).toEpochDay())
        assertTrue(event.isOngoing(date.plusDays(2).atTime(14, 0)))
    }
    @Test fun `individual notification works with global switch off`() {
        val policy = ReminderPolicy.forEvent(event().copy(followGlobalReminder = false, notifyEnabled = true, reminderLeadMinutes = 5), false, false, false, 30)
        assertTrue(policy.notify)
        assertEquals(5, policy.leadMinutes)
    }
    @Test fun `individual disabled event stays disabled with global switch on`() {
        val policy = ReminderPolicy.forEvent(event().copy(followGlobalReminder = false, notifyEnabled = false), true, true, false, 30)
        assertFalse(policy.enabled)
    }
    @Test fun `alarm only is enabled independently`() {
        val policy = ReminderPolicy.forEvent(event().copy(followGlobalReminder = false, notifyEnabled = false, alarmEnabled = true, reminderLeadMinutes = 0), false, false, false, 30)
        assertTrue(policy.alarm)
        assertFalse(policy.notify)
        assertEquals(0, policy.leadMinutes)
    }
    @Test fun `global defaults affect followers only`() {
        val e = event()
        assertFalse(ReminderPolicy.forEvent(e, false, true, true, 10).enabled)
        val enabled = ReminderPolicy.forEvent(e, true, true, true, 10)
        assertTrue(enabled.notify && enabled.alarm)
        assertEquals(10, enabled.leadMinutes)
    }
    @Test fun `AI alarm requests are persisted as individual reminders`() {
        val item = AiSkills.AiItem(false, "起床", "", date, "07:00", "", "", "", alarm = true, leadMinutes = 0)
        val e = AiAssistant.buildEvent(item)
        assertFalse(e.followGlobalReminder)
        assertTrue(e.alarmEnabled)
        assertEquals(0, e.reminderLeadMinutes)
    }
    @Test fun `AI changing location preserves custom reminder and tag`() {
        val e = event().copy(followGlobalReminder = false, alarmEnabled = true, reminderLeadMinutes = 10, statusTag = "waiting")
        val updated = AiAssistant.applySet(e, AiSkills.AiSet(null, null, null, null, "图书馆", null))
        assertEquals("图书馆", updated.location)
        assertTrue(updated.alarmEnabled)
        assertEquals(10, updated.reminderLeadMinutes)
        assertEquals("waiting", updated.statusTag)
    }
    @Test fun `AI can disable alarm without changing dates`() {
        val e = event().copy(alarmEnabled = true)
        val updated = AiAssistant.applySet(e, AiSkills.AiSet(null, null, null, null, null, null, alarm = false))
        assertFalse(updated.followGlobalReminder)
        assertFalse(updated.alarmEnabled)
        assertEquals(e.date, updated.date)
    }
    @Test fun `explicit id targets the correct same-title event`() {
        val first = event().copy(id = "first")
        val second = event().copy(id = "second")
        assertEquals(second, AiAssistant.findMatch(listOf(first, second), "测试", date, "second"))
        assertNull(AiAssistant.findMatch(listOf(first, second), "测试", date, "missing"))
    }
    @Test fun `ambiguous same-day titles cannot be silently changed`() {
        assertNull(AiAssistant.findMatch(listOf(event(), event().copy(id = "another")), "测试", date))
    }
    @Test fun `a date mismatch does not edit another day`() {
        assertNull(AiAssistant.findMatch(listOf(event()), "测试", date.plusDays(1)))
    }
    @Test fun `repeat occurrence can match on its actual later date`() {
        val repeating = event().copy(repeatRule = "weekly:6")
        assertEquals(repeating, AiAssistant.findMatch(listOf(repeating), "测试", date.plusWeeks(1)))
    }

}
