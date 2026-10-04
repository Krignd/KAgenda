package com.kstudio.agenda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 本地识别（离线，[LocalSmartParser] / [AgendaTextParser]）回归测试。
 *
 * 目的：把「通知 / 公告 / 群消息 / 课表粘贴」里真实出现的写法固化成用例，
 * 以后改解析规则时能立刻发现回归（此前完全没有测试）。
 * 基准日固定 2026-09-18（周五），避免结果随运行日期漂移。
 */
class LocalSmartParserTest {

    private val base = LocalDate.of(2026, 9, 18)

    private fun ops(text: String) = LocalSmartParser.parseEvents(text, base)

    /** 取唯一一条事件（数量不为 1 时直接失败，便于定位） */
    private fun only(text: String) = ops(text).single().item

    private fun dateOf(text: String) = only(text).date

    // ------------------------------------------------------------------ 单条事件

    @Test
    fun timeRangeAndLocation() {
        val item = only("明天下午2点到4点在图书馆报告厅举办人工智能前沿讲座")
        assertEquals(LocalDate.of(2026, 9, 19), item.date)
        assertEquals("14:00", item.startTime)
        assertEquals("16:00", item.endTime)
        assertTrue(item.location.contains("报告厅"))
        assertEquals("lecture", item.type)
        assertFalse(item.isPlan)
    }

    @Test
    fun chineseNumerals() {
        val item = only("本周五 下午两点到四点 在体育馆 举办比赛")
        assertEquals(LocalDate.of(2026, 9, 18), item.date)
        assertEquals("14:00", item.startTime)
        assertEquals("16:00", item.endTime)
        assertTrue(item.location.contains("体育馆"))
        assertEquals("contest", item.type)
    }

    @Test
    fun fullWidthDigitsAndColon() {
        val item = only("下午３点到５点开会")
        assertEquals("15:00", item.startTime)
        assertEquals("17:00", item.endTime)
    }

    @Test
    fun durationInfersEndTime() {
        // 只写时长不写结束时间：9 点开始 + 约 2 小时 → 11:00
        val item = only("9月19日 上午9点开会，预计2小时")
        assertEquals(LocalDate.of(2026, 9, 19), item.date)
        assertEquals("09:00", item.startTime)
        assertEquals("11:00", item.endTime)
    }

    @Test
    fun durationWithHalfHour() {
        val item = only("明天上午10点半部组会 预计1个半小时")
        // “10点半” → 10:30；时长 1.5h → 12:00
        assertEquals("10:30", item.startTime)
        assertEquals("12:00", item.endTime)
    }

    @Test
    fun roomCodeAsLocation() {
        val item = only("周一上午10点 在C1-2003 面试")
        assertEquals(LocalDate.of(2026, 9, 21), item.date)
        assertEquals("C1-2003", item.location)
        assertEquals("interview", item.type)
    }

    // ------------------------------------------------------------------ 日期写法

    @Test
    fun weekendResolvesToSaturday() {
        assertEquals(LocalDate.of(2026, 9, 19), dateOf("周末上午10点 篮球赛"))
    }

    @Test
    fun nextNextWeek() {
        assertEquals(LocalDate.of(2026, 9, 30), dateOf("下下周三 上午9点 补考"))
    }

    @Test
    fun bareWeekdayTakesFutureOccurrence() {
        // 基准日周五：裸“周三”应取下一周的周三（09-23），而不是已经过去的 09-16
        assertEquals(LocalDate.of(2026, 9, 23), dateOf("周三 下午4点 组会"))
    }

    @Test
    fun tomorrowMorningAndTonightAliases() {
        assertEquals(LocalDate.of(2026, 9, 19), dateOf("明早8点体检"))
        val tonight = only("今晚7点半 值班")
        assertEquals(LocalDate.of(2026, 9, 18), tonight.date)
        assertEquals("19:30", tonight.startTime)
    }

    @Test
    fun listMarkerStripsDateKeepsIt() {
        // “1. 9月20日 …” 这类编号行：标记要剥掉，但日期必须保留
        val item = only("1. 9月20日 提交材料")
        assertEquals(LocalDate.of(2026, 9, 20), item.date)
        assertTrue(item.title.contains("提交材料"))
    }

    // ------------------------------------------------------------------ 多事件切分

    @Test
    fun numberedListWithDateInheritance() {
        val list = ops("1. 9月20日 提交材料\n2. 9月21日 面试\n3. 体检")
        assertEquals(3, list.size)
        assertEquals(LocalDate.of(2026, 9, 20), list[0].item.date)
        assertEquals(LocalDate.of(2026, 9, 21), list[1].item.date)
        // 第 3 条没有日期：继承上一条（9月21日）
        assertEquals(LocalDate.of(2026, 9, 21), list[2].item.date)
        assertEquals("interview", list[1].item.type)
    }

    @Test
    fun semicolonSplitsSeveralEvents() {
        val list = ops("周一上午8点例会；周二下午2点评审")
        assertEquals(2, list.size)
        assertEquals(LocalDate.of(2026, 9, 21), list[0].item.date)
        assertEquals(LocalDate.of(2026, 9, 22), list[1].item.date)
        assertEquals("08:00", list[0].item.startTime)
        assertEquals("14:00", list[1].item.startTime)
    }

    @Test
    fun detailLineMergesIntoPreviousEvent() {
        val list = ops("2026-09-20 上午9点新生见面会\n地点：大学生活动中心")
        assertEquals(1, list.size)
        assertEquals(LocalDate.of(2026, 9, 20), list[0].item.date)
        assertTrue(list[0].item.location.contains("大学生活动中心"))
    }

    // ------------------------------------------------------------------ 归类与重复

    @Test
    fun planKeywordsGoToPlan() {
        assertTrue(only("明天开始背单词 每天早上7点").isPlan)
        assertTrue(only("本周日前交作业").isPlan)
        assertFalse(only("明天下午2点开会").isPlan)
    }

    @Test
    fun weeklyRepeatPreserved() {
        val item = only("每周二 下午3点 组会")
        assertTrue(item.repeat.isNotBlank())
        assertEquals(LocalDate.of(2026, 9, 22), item.date)
    }

    // ------------------------------------------------------------------ 标题清洗

    @Test
    fun orgBracketIsNotUsedAsTitle() {
        val item = only("【教务处】关于2026年国庆节放假安排的通知")
        // 标题不该是发文单位，也不该带“的通知”尾巴
        assertNotEquals("教务处", item.title)
        assertEquals("关于2026年国庆节放假安排", item.title)
    }

    // ------------------------------------------------------------------ 课表文本

    @Test
    fun courseBlockParsing() {
        val courses = LocalSmartParser.parseCourses("高等数学 周一第1-2节 1-16周 C1-201 张三老师")
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals(1, c.dayOfWeek)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
        assertTrue(c.room.contains("C1-201"))
        assertTrue(c.teacher.contains("张三"))
        assertTrue(c.weeksRaw.isNotBlank())
        assertTrue(c.title.contains("高等数学"))
    }

    @Test
    fun courseTableParsing() {
        val text = "课程名  教师  周次  节次  教室  星期\n高等数学  张三  1-16  1-2  C1-201  周一"
        val courses = LocalSmartParser.parseCourses(text)
        assertTrue(courses.isNotEmpty())
        assertTrue(courses.first().title.contains("高等数学"))
        assertEquals(1, courses.first().dayOfWeek)
    }

    @Test
    fun coursePeriodFromTimeRange() {
        val courses = LocalSmartParser.parseCourses("线性代数 周三 08:00-09:35 教2-301 李四")
        assertEquals(1, courses.size)
        // 08:00 反查最近节次（应落到第 1 节）
        assertEquals(1, courses[0].startPeriod)
    }

    @Test
    fun courseSingleWeekKeyword() {
        val courses = LocalSmartParser.parseCourses("大学英语 周二 第3-4节 单周 教1-101 王五")
        assertEquals(1, courses.size)
        assertEquals(2, courses[0].dayOfWeek)
        assertEquals(3, courses[0].startPeriod)
        assertTrue(courses[0].weeksRaw.isNotBlank())
    }

    @Test
    fun dateOnlySentenceStillCreatesEvent() {
        // 只写日期、没写时间的短句也要能识别（以当天为准）
        val item = only("今天交材料")
        assertEquals(base, item.date)
        assertTrue(ops("   ").isEmpty())
    }
}
