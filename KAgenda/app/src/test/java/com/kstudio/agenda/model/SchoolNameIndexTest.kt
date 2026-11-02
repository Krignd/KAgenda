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
    private fun school(id: String, name: String, supported: Boolean = false, province: String = "") = School(id, name, "", "", "", supported, province = province)
    private fun results(schools: List<School>, query: String) = SchoolDirectory.filter(SchoolDirectory.sorted(schools, index), query)
        .rows.filterIsInstance<SchoolDirectory.Row.Item>().map { it.entry.school }
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
    @Test fun `browsing uses alphabet targets and searching has no alphabet sections`() {
        val entries = SchoolDirectory.sorted(listOf(school("b", "北京大学"), school("c", "重庆大学"), school("z", "浙江大学")), index)
        val all = SchoolDirectory.filter(entries, "")
        assertEquals(listOf("B", "C", "Z"), all.positions.keys.toList())
        all.positions.forEach { (letter, position) -> assertEquals(SchoolDirectory.Row.Header(letter), all.rows[position]) }
        val filtered = SchoolDirectory.filter(entries, "cqdx")
        assertTrue(filtered.positions.isEmpty())
        assertTrue(filtered.searching)
        assertTrue(filtered.rows.all { it is SchoolDirectory.Row.Item })
        assertEquals(1, filtered.rows.filterIsInstance<SchoolDirectory.Row.Item>().size)
        assertTrue(SchoolDirectory.filter(entries, "not-found").positions.isEmpty())
    }
    @Test fun `full school name outranks extensions even when their pinyin sorts first`() {
        val ranked = results(listOf(school("annex", "浙江大学城市学院"), school("main", "浙江大学"), school("other", "浙江科技大学")), "浙江大学")
        assertEquals("main", ranked.first().id)
        assertEquals("annex", ranked[1].id)
    }
    @Test fun `abbreviated Han names match characters in order without requiring adjacent text`() {
        val schools = listOf(school("buaa", "北京航空航天大学", province = "北京市"), school("col", "北京航空航天大学北海学院", province = "广西壮族自治区"), school("unrelated", "北京大学"))
        assertEquals(listOf("buaa", "col"), results(schools, "北航").map { it.id })
        assertEquals(listOf("wmu"), results(listOf(school("wmu", "温州医科大学")), "温医大").map { it.id })
    }
    @Test fun `overlap is compared before province and does not duplicate a name character`() {
        val ranked = results(listOf(school("main", "浙江大学", province = "浙江省"), school("extended", "浙江大学某某学院", province = "北京市")), "浙大")
        assertEquals(listOf("main", "extended"), ranked.map { it.id })
        assertTrue(results(listOf(school("one", "天津大学")), "天天").isEmpty())
    }
    @Test fun `province breaks equally relevant matches without an alphabet grouping`() {
        val ranked = results(listOf(school("a", "安徽航空学院", province = "安徽省"), school("z", "浙江航空学院", province = "浙江省")), "航空")
        assertEquals(listOf("z", "a"), ranked.map { it.id })
    }
    @Test fun `direct school text outranks province only matches`() {
        val ranked = results(listOf(school("a", "阿尔法学院", province = "北京市"), school("b", "北京大学", province = "北京市")), "北京")
        assertEquals("b", ranked.first().id)
    }
    @Test fun `partial overlap finds a near name behind exact and ordered matches`() {
        val ranked = results(listOf(school("a", "北京理工大学"), school("b", "北京工业大学")), "北京理工")
        assertEquals(listOf("a", "b"), ranked.map { it.id })
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
