package com.kstudio.agenda.data

import com.kstudio.agenda.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CourseCustomTimeTest {
    private val original = Course("Course", "C1", "", "1-16", "A", 1, 2, 1)
    private val changed = original.copy(customStartTime = "09:17", customEndTime = "10:43")

    @Test fun arbitraryMinutesOverridePeriodsAndReminderTimestamp() {
        assertEquals(LocalTime.of(9, 17), changed.startTime)
        assertEquals(LocalTime.of(10, 43), changed.endTime)
        assertEquals("09:17-10:43", changed.timeRange)
        val date = LocalDate.of(2026, 11, 9)
        assertEquals(date.atTime(9, 17).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), changed.startMillisEpoch(date))
        assertFalse(changed.sameEditableContent(original))
        assertEquals(original.slotKey, changed.slotKey)
        assertNotEquals(original.id, changed.id)
    }

    @Test fun overrideDoesNotChangeWhenGlobalPeriodsChangeAndCanBeCleared() {
        val before = PeriodTimes.current()
        val wasCustom = PeriodTimes.isCustom
        try {
            PeriodTimes.applyCustom(listOf(LocalTime.of(7, 0) to LocalTime.of(7, 40)))
            assertEquals(LocalTime.of(9, 17), changed.startTime)
            assertEquals(LocalTime.of(7, 0), changed.copy(customStartTime = "", customEndTime = "").startTime)
        } finally { PeriodTimes.applyCustom(if (wasCustom) before else null) }
    }

    @Test fun malformedOrPartialBackupValuesNeverProduceMixedTimeRanges() {
        for ((s, e) in listOf("" to "10:43", "09:17" to "", "25:01" to "26:02", "10:43" to "09:17", "09:17" to "09:17", "9:17" to "10:43")) {
            val invalid = original.copy(customStartTime = s, customEndTime = e)
            assertFalse(invalid.hasCustomTime)
            assertEquals(original.timeRange, invalid.timeRange)
        }
    }

    @Test fun modificationSurvivesApplyingFreshRawSemesterAndLeavesOtherSlotUntouched() {
        val other = original.copy(dayOfWeek = 3, startPeriod = 5, endPeriod = 6)
        val raw = SemesterSchedule("Term", 0, mapOf(1 to listOf(original, other)), 0)
        val record = CourseEdit(false, original, changed, 0)
        val result = CourseEditStore.apply(raw, listOf(record))!!.weeks.getValue(1)
        assertEquals("09:17-10:43", result.single { it.dayOfWeek == 1 }.timeRange)
        assertEquals(other, result.single { it.dayOfWeek == 3 })
        assertFalse(raw.weeks.getValue(1).first().hasCustomTime)
        assertEquals("09:17-10:43", CourseEditStore.apply(raw.copy(fetchedAtMillis = 99), listOf(record))!!.weeks.getValue(1).single { it.dayOfWeek == 1 }.timeRange)
    }

    @Test fun editAndCacheJsonRoundTripKeepsExactTimeAndLegacyJsonUsesPeriods() {
        for (owner in listOf(CourseEditStore, ScheduleCache)) {
            val encode = owner.javaClass.getDeclaredMethod("courseToJson", Course::class.java).apply { isAccessible = true }
            val decode = owner.javaClass.getDeclaredMethod("courseFromJson", JSONObject::class.java).apply { isAccessible = true }
            val json = encode.invoke(owner, changed) as JSONObject
            val restored = decode.invoke(owner, json) as Course
            assertTrue(restored.sameEditableContent(changed))
            json.remove("customStartTime"); json.remove("customEndTime")
            val legacy = decode.invoke(owner, json) as Course
            assertFalse(legacy.hasCustomTime)
            assertEquals(original.timeRange, legacy.timeRange)
        }
    }

    @Test fun orderingUsesActualTimeEvenWhenPeriodNumbersAreEarlier() {
        val late = original.copy(customStartTime = "18:03", customEndTime = "19:29")
        val early = original.copy(startPeriod = 9, endPeriod = 10, customStartTime = "06:23", customEndTime = "07:13")
        assertEquals(listOf(early, late), WeekSchedule("Term", 1, "", 0, listOf(late, early), 0).coursesOfDay(1))
    }
}
