package com.kstudio.agenda.model

import org.junit.Assert.*
import org.junit.Test

class TimelineLayoutTest {
    @Test fun `overlapping classes occupy different columns`() {
        val result = TimelineLayout.place(listOf(TimelineLayout.Item("A", 480, 570), TimelineLayout.Item("B", 510, 600), TimelineLayout.Item("C", 600, 660)))
        val a = result.single { it.item.value == "A" }; val b = result.single { it.item.value == "B" }; val c = result.single { it.item.value == "C" }
        assertNotEquals(a.column, b.column)
        assertEquals(2, a.columns); assertEquals(2, b.columns)
        assertEquals(1, c.columns)
    }
    @Test fun `nested intervals and chains do not share a column while overlapping`() {
        val input = listOf(TimelineLayout.Item("A", 0, 120), TimelineLayout.Item("B", 10, 20), TimelineLayout.Item("C", 20, 130), TimelineLayout.Item("D", 25, 30))
        val result = TimelineLayout.place(input)
        for (a in result) for (b in result) {
            if (a == b) continue
            if (a.item.start < b.item.end && b.item.start < a.item.end) assertNotEquals(a.column, b.column)
        }
        assertEquals(3, result.maxOf { it.columns })
    }
    @Test fun `invalid zero duration intervals cannot create negative sized cards`() {
        assertTrue(TimelineLayout.place(listOf(TimelineLayout.Item("A", 10, 10), TimelineLayout.Item("B", 20, 10))).isEmpty())
    }
}
