package com.kstudio.agenda.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HolidayNoticeParserTest {
    // State Council notice, 2025-11-04; fixture keeps official date clauses only.
    private val notice = """
        <h1>国务院办公厅关于2026年 部分节假日安排的通知</h1>
        <p>一、元旦：1月1日（周四）至3日（周六）放假调休，共3天。1月4日（周日）上班。</p>
        <p>二、春节：2月15日（农历腊月二十八、周日）至23日（周一）放假调休，共9天。2月14日（周六）、2月28日（周六）上班。</p>
        <p>三、清明节：4月4日（周六）至6日（周一）放假，共3天。</p>
        <p>四、劳动节：5月1日（周五）至5日（周二）放假调休，共5天。5月9日（周六）上班。</p>
        <p>五、端午节：6月19日（周五）至21日（周日）放假，共3天。</p>
        <p>六、中秋节：9月25日（周五）至27日（周日）放假，共3天。</p>
        <p>七、国庆节：10月1日（周四）至7日（周三）放假调休，共7天。9月20日（周日）、10月10日（周六）上班。</p>
    """.trimIndent()
    private fun day(s: String) = LocalDate.parse(s).toEpochDay()
    @Test fun `official 2026 notice contains all holiday days and workdays`() {
        val result = HolidayNoticeParser.parse(notice, 2026)
        assertEquals(33, result.holidays.size)
        assertEquals(6, result.workdays.size)
        assertEquals("春节", result.holidays[day("2026-02-15")])
        assertEquals("春节", result.holidays[day("2026-02-23")])
        assertTrue(day("2026-09-20") in result.workdays)
        assertFalse(day("2026-09-20") in result.holidays)
    }
    @Test(expected = IllegalArgumentException::class) fun `wrong year is rejected instead of overwriting calendar`() {
        HolidayNoticeParser.parse(notice, 2027)
    }
    @Test(expected = IllegalArgumentException::class) fun `incomplete notice is rejected`() {
        HolidayNoticeParser.parse(notice.substringBefore("七、国庆节"), 2026)
    }
    @Test fun `cross month vacation is parsed correctly`() {
        val result = HolidayNoticeParser.parse(notice.replace("2月15日（农历腊月二十八、周日）至23日（周一）", "1月28日（周三）至2月5日（周四）"), 2026)
        assertEquals("春节", result.holidays[day("2026-01-28")])
        assertEquals("春节", result.holidays[day("2026-02-05")])
    }
    @Test fun `built in calendar matches verified official spring dates`() {
        assertTrue(HolidayTable.isHoliday(LocalDate.parse("2026-02-15")))
        assertTrue(HolidayTable.isHoliday(LocalDate.parse("2026-02-23")))
        assertTrue(HolidayTable.isWorkday(LocalDate.parse("2026-01-04")))
        assertFalse(HolidayTable.isRestDay(LocalDate.parse("2026-01-04")))
    }
}
