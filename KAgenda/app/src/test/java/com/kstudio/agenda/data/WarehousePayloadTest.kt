package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WarehousePayloadTest {
    private val slots = """[{"number":2,"startTime":"08:50","endTime":"09:35"},{"number":1,"startTime":"08:00","endTime":"08:45"}]"""
    private val courses = """[{"name":"数学","teacher":"老师","position":"A201","day":3,"startSection":1,"endSection":2,"weeks":[1,3,5,5]}]"""
    @Test fun `odd weeks rooms and reordered time slots survive import`() {
        val batch = WarehousePayload.parse(courses, slots, """{"semesterStartDate":"2027-02-09","semesterTotalWeeks":18}""")
        assertEquals(LocalDate.of(2027, 2, 8), batch.firstMonday)
        assertEquals(18, batch.totalWeeks)
        val course = batch.courses.single()
        assertEquals(setOf(1, 3, 5), course.weeksSet())
        assertEquals(3, course.dayOfWeek)
        assertEquals("A201", course.room)
        assertEquals("08:00", batch.times.first().first.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid weekdays reject the whole batch`() {
        WarehousePayload.parse(courses.replace("\"day\":3", "\"day\":8"), slots, "")
    }
    @Test(expected = IllegalArgumentException::class) fun `overlapping slot times reject import`() {
        WarehousePayload.parse(courses, slots.replace("08:50", "08:20"), "")
    }
    @Test(expected = IllegalArgumentException::class) fun `missing section numbers are not silently renumbered`() {
        WarehousePayload.timeSlots(slots.replace("\"number\":2", "\"number\":3"))
    }
    @Test(expected = IllegalArgumentException::class) fun `course outside supplied periods rejects import`() {
        WarehousePayload.parse(courses.replace("\"endSection\":2", "\"endSection\":3"), slots, "")
    }
    @Test(expected = WarehousePayload.UnsupportedTime::class) fun `custom times that cannot map to periods reject import`() {
        WarehousePayload.parse(courses.replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:15\",\"customEndTime\":\"09:35\""), slots, "")
    }
    @Test fun `exact custom times map to imported periods`() {
        val raw = courses.replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:00\",\"customEndTime\":\"09:35\"")
        val result = WarehousePayload.parse(raw, slots, "").courses.single()
        assertEquals(1, result.startPeriod); assertEquals(2, result.endPeriod)
    }
    @Test(expected = IllegalArgumentException::class) fun `empty course batch never overwrites a cached timetable`() {
        WarehousePayload.parse("[]", "", "")
    }
    @Test(expected = IllegalArgumentException::class) fun `null course names reject the batch`() {
        WarehousePayload.parse(courses.replace("\"name\":\"数学\"", "\"name\":null"), slots, "")
    }
    @Test fun `optional null fields never appear as the literal word null`() {
        val result = WarehousePayload.parse(courses.replace("\"teacher\":\"老师\"", "\"teacher\":null").replace("\"position\":\"A201\"", "\"position\":null"), slots, "").courses.single()
        assertEquals("", result.teacher); assertEquals("", result.room)
    }
}
