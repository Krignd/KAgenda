package com.kstudio.agenda.model

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SchoolNameIndexTest {
    companion object {
        private val assets = File(requireNotNull(System.getProperty("kagenda.assets")))
        private val index by lazy { SchoolNameIndex.parse(File(assets, "catalog/pinyin.json").readText()) }
    }
    private fun school(id: String, name: String, supported: Boolean = false) = School(id, name, "", "", "", supported)
    @Test fun `polyphonic institution names use their contextual pronunciation`() {
        assertEquals("C", index.spelling("重庆大学").letter)
        assertEquals("C", index.spelling("长春大学").letter)
        assertEquals("C", index.spelling("长治学院").letter)
        assertEquals("C", index.spelling("长垣烹饪职业技术学院").letter)
        assertEquals("X", index.spelling("厦门大学").letter)
        assertEquals("C", index.spelling("朝阳师范学院").letter)
    }
    @Test fun `new custom names retain contextual city pronunciation`() {
        assertEquals("chongqingceshidaxue", index.spelling("重庆测试大学").full)
        assertEquals("C", index.spelling("重庆测试大学").letter)
        assertEquals("E", index.spelling("École exemple").letter)
        assertEquals("#", index.spelling("123 示例").letter)
    }
    @Test fun `support status cannot override pinyin ordering`() {
        val sorted = SchoolDirectory.sorted(listOf(school("z", "浙江大学", true), school("b", "北京大学"), school("a", "安徽大学")), index)
        assertEquals(listOf("a", "b", "z"), sorted.map { it.school.id })
    }
    @Test fun `every alphabet target points to a section header after filtering`() {
        val entries = SchoolDirectory.sorted(listOf(school("b", "北京大学"), school("c", "重庆大学"), school("z", "浙江大学")), index)
        val all = SchoolDirectory.filter(entries, "")
        assertEquals(listOf("B", "C", "Z"), all.positions.keys.toList())
        all.positions.forEach { (letter, position) -> assertEquals(SchoolDirectory.Row.Header(letter), all.rows[position]) }
        val filtered = SchoolDirectory.filter(entries, "cqdx")
        assertEquals(mapOf("C" to 0), filtered.positions)
        assertEquals(1, filtered.rows.filterIsInstance<SchoolDirectory.Row.Item>().size)
        assertTrue(SchoolDirectory.filter(entries, "not-found").positions.isEmpty())
    }
    @Test fun `full pinyin and initials find the same school`() {
        val entries = SchoolDirectory.sorted(listOf(school("c", "重庆大学")), index)
        assertEquals(SchoolDirectory.filter(entries, "chong qing"), SchoolDirectory.filter(entries, "cqdx"))
    }
    @Test fun `the official catalog has complete alphabetic spellings and unique index targets`() {
        val rows = JSONArray(File(assets, "catalog/schools.json").readText())
        val schools = (0 until rows.length()).map { rows.getJSONObject(it) }.map { school(it.getString("id"), it.getString("name")) }
        val entries = SchoolDirectory.sorted(schools, index)
        assertEquals(3196, entries.size)
        assertTrue(entries.all { it.spelling.letter in ('A'..'Z').map(Char::toString) })
        assertEquals(entries.map { it.spelling.full }.sorted(), entries.map { it.spelling.full })
        val listing = SchoolDirectory.filter(entries, "")
        assertEquals(schools.size + listing.positions.size, listing.rows.size)
    }
}
