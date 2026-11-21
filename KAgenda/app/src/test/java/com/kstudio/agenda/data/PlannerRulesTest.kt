package com.kstudio.agenda.data

import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.SemesterSchedule
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlannerRulesTest {
    private val monday = LocalDate.parse("2026-11-16")
    private val morning = Course("Calculus", "M101", "", "1-2", "A", 1, 2, 1,
        customStartTime = "09:00", customEndTime = "10:00")

    private fun semester(vararg courses: Course) = SemesterSchedule("Fall", monday.toEpochDay(), mapOf(1 to courses.toList()), 1)

    @Test fun monthlyBillReturnsToItsOriginalDayAfterFebruary() {
        val bill = BillRule("a", "Rent", "2024-01-31", "month", 30)
        assertEquals(LocalDate.parse("2024-02-29"), bill.next(LocalDate.parse("2024-02-01")))
        assertEquals(LocalDate.parse("2024-03-31"), bill.next(LocalDate.parse("2024-03-01")))
        assertEquals(LocalDate.parse("2024-03-31"), bill.next(LocalDate.parse("2024-03-31")))
    }

    @Test fun fixedIntervalUsesExactDaysAcrossDifferentLengthMonths() {
        val bill = BillRule("a", "Rent", "2024-01-31", "days", 30)
        assertEquals(LocalDate.parse("2024-03-01"), bill.next(LocalDate.parse("2024-02-01")))
        assertEquals(LocalDate.parse("2024-03-31"), bill.next(LocalDate.parse("2024-03-02")))
    }

    @Test fun everyTwoMonthsKeepsTheOriginalDayAfterAShortMonth() {
        val bill = BillRule("a", "Rent", "2026-08-31", "month", 30, interval = 2)
        assertEquals(LocalDate.parse("2026-10-31"), bill.next(LocalDate.parse("2026-09-01")))
        assertEquals(LocalDate.parse("2027-02-28"), bill.next(LocalDate.parse("2027-01-01")))
        assertEquals(LocalDate.parse("2027-04-30"), bill.next(LocalDate.parse("2027-03-01")))
    }

    @Test fun yearlyBillReturnsToLeapDayAndSupportsMultipleYears() {
        val bill = BillRule("a", "Renewal", "2024-02-29", "year", 30, interval = 2)
        assertEquals(LocalDate.parse("2026-02-28"), bill.next(LocalDate.parse("2025-05-01")))
        assertEquals(LocalDate.parse("2028-02-29"), bill.next(LocalDate.parse("2026-03-01")))
    }

    @Test fun courseTagRenameKeepsExistingAssignmentsAndDeleteRemovesThem() {
        val lesson = "buaa/20778/series/m101"
        val state = PlannerData(courseTags = mapOf(lesson to listOf("quiz", "exam")))
        val renamed = state.copy(courseTagDefinitions = state.courseTagDefinitions.map {
            if (it.id == "quiz") it.copy(label = "In-class check") else it
        })
        assertEquals(listOf("quiz", "exam"), renamed.courseTags[lesson])
        assertEquals(listOf("exam"), renamed.removeCourseTag("quiz").courseTags[lesson])
    }

    @Test fun capturePrefersLiveClassAndNeverPicksAConflictArbitrarily() {
        val overlapping = morning.copy(title = "Physics", code = "P101", customStartTime = "09:30", customEndTime = "10:30")
        val schedule = semester(morning, overlapping)
        assertEquals(listOf(morning, overlapping), lessonCaptureCandidates(schedule, monday.atTime(9, 45)))
        assertEquals(listOf(overlapping), lessonCaptureCandidates(schedule, monday.atTime(10, 0)))
        assertEquals(listOf(overlapping), lessonCaptureCandidates(schedule, monday.atTime(11, 0)))
        assertTrue(lessonCaptureCandidates(schedule, monday.atTime(11, 1)).isEmpty())
        assertTrue(lessonCaptureCandidates(schedule, monday.atTime(8, 59)).isEmpty())
    }

    @Test fun recurringAgendaLinkUsesSelectedOccurrenceAndTracksUpdatedTime() {
        val event = AgendaEvent("original", "Meeting", monday.toEpochDay(), startTime = "08:15", repeatRule = "daily:1")
        val occurrence = monday.plusDays(1)
        val linked = CountdownLink("agenda/original/$occurrence", event.title, occurrence.atTime(8, 15).toString(), agendaId = event.id)
        val planner = PlannerData(links = listOf(linked))
        assertEquals(occurrence.atTime(9, 10),
            planner.countdowns(listOf(event.copy(startTime = "09:10")), null, "").single().at)
        assertTrue(planner.countdowns(emptyList(), null, "").isEmpty())
    }

    @Test fun syncDetectsDateRoomAndTimesWithoutComparingRawWeekFormatting() {
        val previous = semester(morning)
        assertNull(compareCourseSync(previous, semester(morning.copy(weeksRaw = "1,2")).copy(fetchedAtMillis = 5)))
        assertNull(compareCourseSync(previous, previous.copy(calendarRevision = 33)))
        val moved = morning.copy(room = "B", dayOfWeek = 2)
        val review = requireNotNull(compareCourseSync(previous, semester(moved)))
        assertEquals(monday, review.removed.single().date)
        assertEquals(monday.plusDays(1), review.added.single().date)
        assertEquals("B", review.added.single().room)
        val regular = semester(morning.copy(endPeriod = 1, customStartTime = "", customEndTime = ""))
        assertEquals("06:00-06:30", compareCourseSync(regular, regular, listOf(LocalTime.of(6, 0) to LocalTime.of(6, 30)))?.added?.single()?.time)
    }
}
