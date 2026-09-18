package com.kstudio.agenda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 「智能识别填充」回归测试（所有样例均为虚构文字）。
 * 基准日期：2026-09-18（周五）。
 */
class AgendaTextParserTest {

    private val base = LocalDate.of(2026, 9, 18)

    private fun parse(text: String): AgendaTextParser.Parsed = AgendaTextParser.parse(text, base)

    private fun dateOf(p: AgendaTextParser.Parsed): LocalDate = LocalDate.ofEpochDay(p.dateEpochDay)

    @Test
    fun interviewBracketTitleRoom() {
        val p = parse("【面试通知】9月20日下午3点50分于科技园B座201室面试，请携带简历与项目材料。")
        assertEquals("面试通知", p.title)
        assertEquals("interview", p.type)
        assertEquals(LocalDate.of(2026, 9, 20), dateOf(p))
        assertEquals("15:50", p.startTime)
        assertEquals("", p.endTime)
        assertTrue(p.location.contains("科技园"))
    }

    @Test
    fun lectureRangeLocation() {
        val p = parse("本周五 14:00-15:30 在图书馆报告厅举办“人工智能前沿技术”讲座，请感兴趣的同学提前 10 分钟入场。")
        assertEquals("lecture", p.type)
        assertEquals(LocalDate.of(2026, 9, 18), dateOf(p))
        assertEquals("14:00", p.startTime)
        assertEquals("15:30", p.endTime)
        assertEquals("图书馆报告厅", p.location)
    }

    @Test
    fun examMorningRange() {
        val p = parse("9 月 24 日上午 9:00-11:00 在教学楼 C305 进行《数据分析基础》期中考试，请携带学生证，提前 15 分钟到场。")
        assertEquals("exam", p.type)
        assertEquals(LocalDate.of(2026, 9, 24), dateOf(p))
        assertEquals("09:00", p.startTime)
        assertEquals("11:00", p.endTime)
        assertTrue(p.location.contains("C305"))
    }

    @Test
    fun afternoonRangeAutoEndHalfDay() {
        val p = parse("下午2点到4点开会")
        assertEquals("meeting", p.type)
        assertEquals("14:00", p.startTime)
        assertEquals("16:00", p.endTime)
    }

    @Test
    fun relativeWeekdayMorningCollection() {
        val p = parse("本周六早 8 点 20 集合，地点南区操场")
        assertEquals(LocalDate.of(2026, 9, 19), dateOf(p))
        assertEquals("08:20", p.startTime)
        assertEquals("南区操场", p.location)
    }

    @Test
    fun cnHalfHour() {
        val p = parse("晚上7点半开始排练")
        assertEquals("19:30", p.startTime)
    }

    @Test
    fun bigDayAfter() {
        val p = parse("大后天上午10点体检")
        assertEquals(LocalDate.of(2026, 9, 21), dateOf(p))
        assertEquals("10:00", p.startTime)
    }

    @Test
    fun explicitYearDash() {
        val p = parse("2026-09-24 10:00 组会")
        assertEquals(LocalDate.of(2026, 9, 24), dateOf(p))
        assertEquals("10:00", p.startTime)
        assertEquals("meeting", p.type)
    }

    @Test
    fun slashDate() {
        val p = parse("9/24 14:00 提交材料")
        assertEquals(LocalDate.of(2026, 9, 24), dateOf(p))
        assertEquals("14:00", p.startTime)
    }

    @Test
    fun fullWidthDigits() {
        val p = parse("９月２４日 １４：００ 答辩")
        assertEquals(LocalDate.of(2026, 9, 24), dateOf(p))
        assertEquals("14:00", p.startTime)
        assertEquals("exam", p.type)
    }

    @Test
    fun genericBracketFallbackTitle() {
        val p = parse("【通知】关于选课的通知，9.20 10:00 在教务系统开放")
        assertEquals("关于选课的通知", p.title)
        assertEquals(LocalDate.of(2026, 9, 20), dateOf(p))
        assertEquals("10:00", p.startTime)
    }

    @Test
    fun nudeWeekdayPicksNextWeek() {
        val p = parse("周三下午 1 点 30 分上交报告")
        assertEquals(LocalDate.of(2026, 9, 23), dateOf(p))
        assertEquals("13:30", p.startTime)
    }

    @Test
    fun noonTwelve() {
        val p = parse("中午12点午餐会")
        assertEquals("12:00", p.startTime)
    }

    @Test
    fun dawnOne() {
        val p = parse("凌晨 1 点集合出发")
        assertEquals("01:00", p.startTime)
    }

    @Test
    fun octoberFirstEvening() {
        val p = parse("10.1 晚 7 点国庆晚会")
        assertEquals(LocalDate.of(2026, 10, 1), dateOf(p))
        assertEquals("19:00", p.startTime)
    }

    @Test
    fun locationKeywordColon() {
        val p = parse("地点：C1-2003/2004，请准时")
        assertEquals("C1-2003/2004", p.location)
    }

    @Test
    fun dayAfterTomorrow() {
        val p = parse("后天 9:00 交表")
        assertEquals(LocalDate.of(2026, 9, 20), dateOf(p))
        assertEquals("09:00", p.startTime)
    }

    @Test
    fun longWeekdayMeetingBuilding() {
        val p = parse("下周一 19:30 在教学楼 2 栋 301 教室召开班委会")
        assertEquals(LocalDate.of(2026, 9, 21), dateOf(p))
        assertEquals("19:30", p.startTime)
        assertTrue(p.location.contains("301"))
        assertEquals("meeting", p.type)
    }

    @Test
    fun sundaySuffixLocation() {
        val p = parse("本周日 8 点集合前往南区操场")
        assertEquals(LocalDate.of(2026, 9, 20), dateOf(p))
        assertEquals("08:00", p.startTime)
        assertTrue(p.location.contains("南区操场"))
    }
}
