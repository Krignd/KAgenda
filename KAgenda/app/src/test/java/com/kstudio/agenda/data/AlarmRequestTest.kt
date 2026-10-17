package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AlarmRequestTest {
    @Test fun `offline request can create an alarm at start time`() {
        val result = LocalSmartParser.parseEvents("明天早上7点设置起床闹钟", LocalDate.of(2026, 10, 17)).first().item
        assertEquals(true, result.alarm)
        assertEquals(0, result.leadMinutes)
        assertEquals("07:00", result.startTime)
    }
    @Test fun `a normal event does not acquire an unsolicited alarm`() {
        val result = LocalSmartParser.parseEvents("明天早上9点参加会议", LocalDate.of(2026, 10, 17)).first().item
        assertNull(result.alarm)
    }
}
