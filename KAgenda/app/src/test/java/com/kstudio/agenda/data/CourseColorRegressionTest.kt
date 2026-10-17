package com.kstudio.agenda.data

import com.kstudio.agenda.model.*
import org.junit.Assert.*
import org.junit.Test

class CourseColorRegressionTest {
    private fun course(day: Int, start: Int) = Course("数学", "M101", "老师", "1-16", "A201", start, start + 1, day)
    @Test fun `series recolor keeps each courses own weekday and period`() {
        val mon = course(1, 1)
        val wed = course(3, 5)
        val color = 0xFF009988.toInt()
        val sem = SemesterSchedule("", 0, mapOf(1 to listOf(mon, wed)), 0)
        val edit = CourseEdit(true, mon, mon.copy(colorArgb = color), 0)
        val result = CourseEditStore.apply(sem, listOf(edit))!!
        assertEquals(setOf(mon.slotKey, wed.slotKey), result.weeks[1]!!.map { it.slotKey }.toSet())
        assertTrue(result.weeks[1]!!.all { it.colorArgb == color })
    }
    @Test fun `single recolor does not affect the other course slot`() {
        val mon = course(1, 1)
        val wed = course(3, 5)
        val sem = SemesterSchedule("", 0, mapOf(1 to listOf(mon, wed)), 0)
        val result = CourseEditStore.apply(sem, listOf(CourseEdit(false, mon, mon.copy(colorArgb = 1234), 0)))!!
        assertEquals(1234, result.weeks[1]!!.first { it.dayOfWeek == 1 }.colorArgb)
        assertEquals(0, result.weeks[1]!!.first { it.dayOfWeek == 3 }.colorArgb)
        assertEquals(0, mon.colorArgb) // original cache object remains untouched
    }
}
