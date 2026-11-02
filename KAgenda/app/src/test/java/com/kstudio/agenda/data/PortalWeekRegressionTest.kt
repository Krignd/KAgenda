package com.kstudio.agenda.data

import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.AgendaEvent
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PortalWeekRegressionTest {
    @Test fun repeatedWeekSuffixRetainsNinthWeek() {
        for (raw in listOf("6周,9周", "5周,9周", "6,9周")) {
            val json = """{"ok":true,"weeks":{"9":[{"courseName":"习近平新时代中国特色社会主义思想概论","dayOfWeek":5,"beginSection":1,"endSection":4,"cellDetail":[{"text":"教师[$raw]"},{"text":"科研一号楼1040"}]}]}}"""
            val c = ScheduleParser.parseSemesterFetchResult(json).getValue(9).single()
            assertTrue(raw, c.occursInWeek(9))
            assertFalse(raw, c.occursInWeek(8))
            assertEquals(5, c.dayOfWeek)
            assertEquals("教师", c.teacher)
        }
    }
    @Test fun ujsParityIsRespected() {
        assertEquals(listOf(4,6,8,10), Course.parseWeeks("4-10周(双)").flatMap { it.toList() })
        assertEquals((5..19 step 2).toList(), Course.parseWeeks("5-19周（单）").flatMap { it.toList() })
        val json = """{"courses":[{"title":"高等数学","day":5,"start":3,"end":4,"weeks":"5-19周(单)"}]}"""
        val semester = ScheduleParser.fromCustomAdapter(json, LocalDate.of(2026,11,2))!!
        assertTrue(semester.weeks.containsKey(9))
        assertFalse(semester.weeks.containsKey(8))
    }
    @Test fun singleStartNeverBecomesOngoingIncludingRecurringProjection() {
        val day = LocalDate.of(2026,11,2)
        val event = AgendaEvent("test", "会议", day.toEpochDay(), startTime="09:00")
        val now = day.atTime(10,0)
        assertFalse(event.isOngoing(now))
        val repeated = event.copy(repeatRule="daily:1")
        assertFalse(repeated.isOngoing(now))
        assertEquals(day.plusDays(1), repeated.upcomingView(now).date)
        assertTrue(event.copy(endTime="11:00").isOngoing(now))
        assertFalse(event.copy(endTime="10:00").isOngoing(now))
        assertTrue(event.copy(isLong=true,endDateEpochDay=day.plusDays(1).toEpochDay()).isOngoing(now))
    }
    @Test fun independentSimpleLinesCanBypassLocalModel() {
        assertTrue(LocalAiPrompt.simpleRuleInput("明天3点开会\n后天4点交报告"))
        assertFalse(LocalAiPrompt.simpleRuleInput("明天3点开会\n每周三4点锻炼"))
    }
    @Test fun originalNoticeSurvivesStorageRoundTripAndLegacyData() {
        val event = AgendaEvent("test", "会议", LocalDate.of(2026,11,2).toEpochDay(), note="备注", originalText="通知原文\n第二行")
        assertEquals(event, AgendaStore.parse(AgendaStore.toJson(listOf(event))).single())
        val legacy = org.json.JSONArray(AgendaStore.toJson(listOf(event)))
        legacy.getJSONObject(0).remove("originalText")
        assertEquals("", AgendaStore.parse(legacy.toString()).single().originalText)
    }
}
