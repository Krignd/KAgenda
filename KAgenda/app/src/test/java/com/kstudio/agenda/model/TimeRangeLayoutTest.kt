package com.kstudio.agenda.model

import org.junit.Assert.*
import org.junit.Test

class TimeRangeLayoutTest {
    private fun layout(start: String, end: String, width: Float) = TimeRangeLayout.lines(start, end, width) { text, size -> text.codePointCount(0, text.length) * size * .6f }
    @Test fun `normal clock ranges stay on one line`() {
        assertEquals(listOf(TimeRangeLayout.Line("08:00–09:35", 20f)), layout("08:00", "09:35", 160f))
    }
    @Test fun `moderate shrinking keeps a readable one line range`() {
        val lines = layout("08:00", "09:35", 120f)
        assertEquals(1, lines.size)
        assertTrue(lines.single().fontSize in 16f..20f)
        assertEquals("08:00–09:35", lines.single().text)
    }
    @Test fun `narrow cells split rather than truncate the range`() {
        val lines = layout("08:00", "09:35", 80f)
        assertEquals(listOf("08:00", "–09:35"), lines.map { it.text })
        assertEquals("08:00–09:35", lines.joinToString("") { it.text })
    }
    @Test fun `long fuzzy labels remain complete within their width`() {
        val start = "下午完成会议以后"; val end = "晚间活动开始以前"
        val lines = layout(start, end, 70f)
        assertEquals("$start–$end", lines.joinToString("") { it.text })
        assertTrue(lines.all { it.text.codePointCount(0, it.text.length) * it.fontSize * .6f <= 70.01f })
    }
    @Test fun `blank and single ended times do not waste empty rows`() {
        assertTrue(layout("", "", 160f).isEmpty())
        assertEquals("08:00", layout("08:00", "", 160f).single().text)
        assertEquals("–09:35", layout("", "09:35", 160f).single().text)
    }
}
