package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WarehousePayloadTest {
    @Test fun `class details survive deduplication of equal timetable facts`() {
        val raw="""[{"name":"同名课","teacher":"甲","position":"A101","day":1,"startSection":1,"endSection":2,"weeks":[1],"extraInfo":"教学班：A"},{"name":"同名课","teacher":"甲","position":"A101","day":1,"startSection":1,"endSection":2,"weeks":[1],"extraInfo":"教学班：B"}]"""
        val batch=WarehousePayload.parse(raw,"","""{"semesterTotalWeeks":18}""")
        assertEquals("教学班：A\n\n教学班：B",batch.courses.single().extraInfo)
    }
    @Test(expected=IllegalArgumentException::class) fun `nontext class details reject the batch`() {
        WarehousePayload.parse("""[{"name":"课","day":1,"startSection":1,"endSection":2,"weeks":[1],"extraInfo":{"class":"A"}}]""","", "")
    }
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
    @Test fun `explicit source sections preserve off-period fixed times without a slot table`() {
        val raw = courses.replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:15\",\"customEndTime\":\"09:35\"")
        val course = WarehousePayload.parse(raw, "", "").courses.single()
        assertEquals(1, course.startPeriod); assertEquals(2, course.endPeriod)
        assertTrue(course.hasCustomTime)
        assertEquals("08:15-09:35", course.timeRange)
    }
    @Test(expected = WarehousePayload.UnsupportedTime::class) fun `unmappable time-only records do not acquire guessed periods`() {
        val raw = courses.replace(",\"startSection\":1,\"endSection\":2", "")
            .replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:15\",\"customEndTime\":\"09:35\"")
        WarehousePayload.parse(raw, slots, "")
    }
    @Test fun `exact custom times map to imported periods`() {
        val raw = courses.replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:00\",\"customEndTime\":\"09:35\"")
        val result = WarehousePayload.parse(raw, slots, "").courses.single()
        assertEquals(1, result.startPeriod); assertEquals(2, result.endPeriod)
        assertEquals("08:00", result.customStartTime); assertEquals("09:35", result.customEndTime)
    }
    @Test fun `time-only legacy records still map exactly to provided periods`() {
        val raw = courses.replace(",\"startSection\":1,\"endSection\":2", "")
            .replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:00\",\"customEndTime\":\"09:35\"")
        val course = WarehousePayload.parse(raw, slots, "").courses.single()
        assertEquals(1, course.startPeriod); assertEquals(2, course.endPeriod)
        assertEquals("08:00-09:35", course.timeRange)
    }
    @Test fun `equal periods with distinct fixed clocks are not merged`() {
        val first = courses.removeSurrounding("[", "]").replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:15\",\"customEndTime\":\"09:35\"")
        val second = first.replace("08:15", "08:20")
        val result = WarehousePayload.parse("[$first,$second]", "", "").courses
        assertEquals(2, result.size)
        assertEquals(setOf("08:15", "08:20"), result.map { it.customStartTime }.toSet())
    }
    @Test fun `malformed fixed times or sections reject the whole mixed batch`() {
        val fixed = courses.removeSurrounding("[", "]").replace("\"day\":3", "\"day\":3,\"isCustomTime\":true,\"customStartTime\":\"08:15\",\"customEndTime\":\"09:35\"")
        val invalid = listOf(
            fixed.replace("09:35", "08:00"),
            fixed.replace("09:35", "25:00"),
            fixed.replace("09:35", "09:35:30"),
            fixed.replace(",\"customEndTime\":\"09:35\"", ""),
            fixed.replace(",\"endSection\":2", ""),
            fixed.replace("\"startSection\":1", "\"startSection\":1.5"),
            fixed.replace("\"endSection\":2", "\"endSection\":0"),
        )
        for (bad in invalid) {
            try {
                WarehousePayload.parse("[${courses.removeSurrounding("[", "]")},$bad]", slots, "")
                fail("Invalid fixed-time record accepted")
            } catch (expected: IllegalArgumentException) {
                // One invalid record prevents the batch from reaching preview/save.
            } catch (expected: org.json.JSONException) {
                // Missing required clock/section fields are equally fatal.
            }
        }
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
