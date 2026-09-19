package com.kstudio.agenda.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 教学周计算回归：第 1 周之前的日期必须落到“第 0/-1 周”（负差值需向下取整） */
class TeachingWeekTest {

    private val anchor = LocalDate.of(2026, 9, 7)   // 第 1 周周一
    private val sem = SemesterSchedule("测试学期", anchor.toEpochDay(), emptyMap(), 0L)

    @Test
    fun `week 1 monday is week 1`() {
        assertEquals(1, sem.teachingWeekOf(LocalDate.of(2026, 9, 7)))
        assertEquals(1, sem.teachingWeekOf(LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun `dates before week 1 use floor division`() {
        // 修复前：09/06 会因向 0 取整被误判为第 1 周（周条不前移、选中框消失）
        assertEquals(0, sem.teachingWeekOf(LocalDate.of(2026, 9, 6)))
        assertEquals(0, sem.teachingWeekOf(LocalDate.of(2026, 9, 1)))
        // 08/31 是“第 0 周”的周一；08/30 才进入第 -1 周
        assertEquals(0, sem.teachingWeekOf(LocalDate.of(2026, 8, 31)))
        assertEquals(-1, sem.teachingWeekOf(LocalDate.of(2026, 8, 30)))
    }

    @Test
    fun `week 0 monday is the week before first monday`() {
        assertEquals(LocalDate.of(2026, 8, 31), sem.week(0).monday)
        assertEquals(LocalDate.of(2026, 9, 7), sem.week(1).monday)
        assertEquals(LocalDate.of(2026, 9, 14), sem.week(2).monday)
    }

    @Test
    fun `dates after week 1`() {
        assertEquals(2, sem.teachingWeekOf(LocalDate.of(2026, 9, 14)))
        assertEquals(2, sem.teachingWeekOf(LocalDate.of(2026, 9, 20)))
        assertEquals(3, sem.teachingWeekOf(LocalDate.of(2026, 9, 21)))
    }
}
