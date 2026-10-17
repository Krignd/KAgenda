package com.kstudio.agenda.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** fixtures 由固定版本原脚本在模拟门户运行后生成，再交给生产解析器。 */
class WarehouseScriptCompatibilityTest {
    private fun parse(id: String): WarehousePayload.Batch {
        val raw = javaClass.getResourceAsStream("/warehouse/$id.json")!!.bufferedReader().use { it.readText() }
        val fixture = JSONObject(raw)
        var config = ""
        val updates = fixture.getJSONArray("configUpdates")
        for (i in 0 until updates.length()) config = WarehousePayload.mergeConfig(config, updates.getString(i))
        return WarehousePayload.parse(fixture.getString("coursesRaw"), fixture.getString("timesRaw"), config)
    }
    @Test fun `parallel API adapter retains its alternate totalWeeks field`() {
        val batch = parse("cqu-api")
        assertEquals(22, batch.totalWeeks)
        assertEquals("2026-09-07", batch.firstMonday.toString())
        assertEquals(setOf(1, 3, 5), batch.courses.single().weeksSet())
        assertEquals("模拟教师", batch.courses.single().teacher)
    }
    @Test fun `interactive term selection adapter imports its timetable and times`() {
        val batch = parse("tongji-selection")
        assertEquals(2, batch.courses.single().dayOfWeek)
        assertEquals(2, batch.times.size)
        assertEquals("2026-09-07", batch.firstMonday.toString())
    }
    @Test fun `form adapter keeps odd and even teaching week ranges`() {
        val batch = parse("uzz-form")
        assertEquals(setOf(1, 3, 5, 8, 10), batch.courses.single().weeksSet())
        assertEquals(3, batch.courses.single().dayOfWeek)
        assertEquals(10, batch.times.size)
        assertNull(batch.firstMonday)
    }
    @Test fun `duplicate API rows are merged before the app receives them`() {
        val batch = parse("niit-merge")
        assertEquals(1, batch.courses.size)
        assertEquals(setOf(1, 3, 5), batch.courses.single().weeksSet())
        assertEquals(11, batch.times.size)
        assertNull(batch.firstMonday)
    }
    @Test fun `successive config updates preserve the previously supplied semester start`() {
        var raw = WarehousePayload.mergeConfig("", """{"semesterStartDate":"2026-09-07","semesterTotalWeeks":18}""")
        raw = WarehousePayload.mergeConfig(raw, """{"totalWeeks":22}""")
        val result = JSONObject(raw)
        assertEquals("2026-09-07", result.getString("semesterStartDate"))
        assertEquals(22, result.getInt("semesterTotalWeeks"))
        raw = WarehousePayload.mergeConfig(raw, """{"semesterTotalWeeks":16}""")
        assertEquals(16, JSONObject(raw).getInt("semesterTotalWeeks"))
    }
}
