package com.kstudio.agenda.data

import com.kstudio.agenda.model.*
import org.junit.Assert.*
import org.junit.Test

class CourseWeekdayRegressionTest {
    private val mon = Course("数学", "M101", "老师", "1-16", "A201", 1, 2, 1)
    private fun semester(vararg courses: Course) = SemesterSchedule("", 0, mapOf(1 to courses.toList()), 0)
    @Test fun `selecting Monday Wednesday Friday expands one academic block without mutating cache`() {
        val raw = semester(mon)
        val edit = CourseEdit(false, mon, mon.copy(room = "B202"), 0, weekdays = setOf(1, 3, 5))
        val result = CourseEditStore.apply(raw, listOf(edit))!!.weeks[1]!!
        assertEquals(setOf(1, 3, 5), result.map { it.dayOfWeek }.toSet())
        assertTrue(result.all { it.room == "B202" && it.editOriginKey == edit.targetKey && it.editWeekdays == edit.weekdays })
        assertEquals(listOf(mon), raw.weeks[1])
        assertEquals(listOf(mon), CourseEditStore.apply(raw, emptyList())!!.weeks[1])
    }
    @Test fun `series edit expands target and preserves the other existing slot`() {
        val thursday = mon.copy(dayOfWeek = 4, startPeriod = 6, endPeriod = 7)
        val edit = CourseEdit(true, mon, mon.copy(room = "B202"), 0, weekdays = setOf(2, 5))
        val result = CourseEditStore.apply(semester(mon, thursday), listOf(edit))!!.weeks[1]!!
        assertEquals(setOf(2, 4, 5), result.map { it.dayOfWeek }.toSet())
        assertEquals(6, result.single { it.dayOfWeek == 4 }.startPeriod)
        assertTrue(result.all { it.room == "B202" })
    }
    @Test fun `add skips only duplicates and still adds the other selected days`() {
        val edit = CourseEdit(false, mon, mon, 0, kind = EDIT_KIND_ADD, weekdays = setOf(1, 3))
        val result = CourseEditStore.apply(semester(mon), listOf(edit))!!.weeks[1]!!
        assertEquals(listOf(1, 3), result.map { it.dayOfWeek })
    }
    @Test fun `legacy edit defaults to its original single edited weekday`() {
        val edit = CourseEdit(false, mon, mon.copy(dayOfWeek = 2), 0)
        assertEquals(setOf(2), edit.weekdays)
        assertEquals(2, CourseEditStore.apply(semester(mon), listOf(edit))!!.weeks[1]!!.single().dayOfWeek)
    }
    @Test fun `missing academic block still expands all selected weekdays`() {
        val edit = CourseEdit(false, mon, mon, 0, weekdays = setOf(2, 4))
        assertEquals(setOf(2, 4), CourseEditStore.apply(semester(), listOf(edit))!!.weeks[1]!!.map { it.dayOfWeek }.toSet())
    }
}
