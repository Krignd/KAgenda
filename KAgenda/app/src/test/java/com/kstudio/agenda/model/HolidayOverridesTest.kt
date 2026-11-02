package com.kstudio.agenda.model

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HolidayOverridesTest {
    @After fun restore() {
        HolidayTable.replaceOverrides(emptyMap())
        HolidayTable.setShowCoursesOnHoliday(false)
    }
    @Test fun customMarksOverrideBothOfficialKindsAndCanRestore() {
        val holiday = LocalDate.parse("2026-10-01")
        val workday = LocalDate.parse("2026-10-10")
        HolidayTable.replaceOverrides(mapOf(
            holiday.toEpochDay() to HolidayOverride(holiday, HolidayKind.WORKDAY),
            workday.toEpochDay() to HolidayOverride(workday, HolidayKind.HOLIDAY, "校庆")))
        assertTrue(HolidayTable.isWorkday(holiday))
        assertNull(HolidayTable.nameOf(holiday))
        assertFalse(HolidayTable.hidesCourses(holiday))
        assertFalse(HolidayTable.isWorkday(workday))
        assertTrue(HolidayTable.hidesCourses(workday))
        assertEquals("校庆", HolidayTable.nameOf(workday))
        HolidayTable.setShowCoursesOnHoliday(true)
        assertFalse(HolidayTable.hidesCourses(workday))
        HolidayTable.replaceOverrides(emptyMap())
        assertEquals("国庆", HolidayTable.nameOf(holiday))
        assertTrue(HolidayTable.isWorkday(workday))
    }
    @Test fun normalSuppressesHolidayAndFestivalMarksWithoutChangingWeekday() {
        val date = LocalDate.parse("2027-02-06")
        assertEquals("春节", HolidayTable.nameOf(date))
        HolidayTable.replaceOverrides(mapOf(date.toEpochDay() to HolidayOverride(date, HolidayKind.NORMAL)))
        assertNull(HolidayTable.nameOf(date))
        assertFalse(HolidayTable.isHoliday(date))
        assertFalse(HolidayTable.isWorkday(date))
        assertTrue(HolidayTable.isWeekend(date))
    }
    @Test fun changingOverridesInvalidatesCalendarButIdenticalValuesDoNot() {
        val before = HolidayTable.revision.value
        val date = LocalDate.parse("2026-10-19")
        val values = mapOf(date.toEpochDay() to HolidayOverride(date, HolidayKind.HOLIDAY, "休息"))
        HolidayTable.replaceOverrides(values)
        assertTrue(HolidayTable.revision.value > before)
        val revision = HolidayTable.revision.value
        HolidayTable.replaceOverrides(values)
        assertEquals(revision, HolidayTable.revision.value)
    }
    @Test fun payloadRoundTripsNamesAndSortsDates() {
        val values = listOf(HolidayOverride(LocalDate.parse("2026-10-19"), HolidayKind.HOLIDAY, "校庆"),
            HolidayOverride(LocalDate.parse("2026-01-01"), HolidayKind.NORMAL))
        assertEquals(values.toSet(), HolidayOverrides.decode(HolidayOverrides.encode(values)).values.toSet())
        assertTrue(HolidayOverrides.decode("").isEmpty())
    }
    @Test fun malformedDatesKindsEmptyNamesAndDuplicateDatesAreRejected() {
        for (raw in listOf("2026-02-30|NORMAL|", "1999-01-01|NORMAL|", "2026-01-01|UNKNOWN|",
            "2026-01-01|HOLIDAY|", "2026-01-01|HOLIDAY|bad|name", "2026-01-01|NORMAL|\n2026-01-01|WORKDAY|")) {
            assertTrue(raw, runCatching { HolidayOverrides.decode(raw) }.isFailure)
        }
    }
}
