package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class AcademicPortalParserTest {
    private fun parse(type: String, source: String) = AcademicPortalParser.parse(type, listOf("<html>Portal</html>", source))
    private fun qz(info: String, css: String = "kbcontent", day: Int = 3) = """
        <table id="kbtable"><tr><th>星期</th></tr><tr>${"<td></td>".repeat(day - 1)}
        <td><div class="$css">数学<br><font title="老师">张老师</font><br>
        <font title="周次(节次)">$info</font><br><font title="教室">A201</font></div></td></tr></table>
    """.trimIndent()

    @Test fun `Qiangzhi variants preserve odd weeks and weekday`() {
        for ((type, css) in listOf("qz" to "kbcontent", "qz_br" to "kbcontent", "qz_crazy" to "kbcontent1")) {
            val c = parse(type, qz("1-5(单)(周)", css)).courses.single()
            assertEquals("数学", c.title); assertEquals("张老师", c.teacher)
            assertEquals("A201", c.room); assertEquals(3, c.dayOfWeek)
            assertEquals(setOf(1, 3, 5), c.weeksSet())
            assertEquals(1, c.startPeriod); assertEquals(2, c.endPeriod)
        }
    }
    @Test fun `Qiangzhi explicit sections and disjoint weeks survive`() {
        val result = parse("qz_with_node", qz("1-3,7-9(周)[5-6节]")).courses
        assertEquals(setOf(1, 2, 3, 7, 8, 9), result.flatMap { it.weeksSet() }.toSet())
        assertTrue(result.all { it.startPeriod == 5 && it.endPeriod == 6 })
    }
    @Test fun `old Qiangzhi retains explicit sections`() {
        val html = """<table id="kbtable"><tr><td>节次</td><td><div>物理<br>王老师<br>1-5周[3-4节]<br>B101</div></td></tr></table>"""
        val c = parse("qz_old", html).courses.single()
        assertEquals(1, c.dayOfWeek); assertEquals(3, c.startPeriod); assertEquals(4, c.endPeriod)
    }
    @Test fun `new Zhengfang HTML preserves split parity ranges`() {
        val html = """<table id="table1"><tr><td class="festival">3</td><td id="2-3"><div><span class="title">线代</span>
        <p><span title="教师">李老师</span></p><p><span title="上课地点">B202</span></p>
        <p><span title="节/周">(3-4节)1-5周(单),8-10周(双)</span></p></div></td></tr></table>"""
        val rows = parse("zf_new", html).courses
        assertEquals(setOf(1, 3, 5, 8, 10), rows.flatMap { it.weeksSet() }.toSet())
        assertTrue(rows.all { it.dayOfWeek == 2 && it.startPeriod == 3 && it.endPeriod == 4 })
    }
    private fun urpGroup(name: String, bits: String): JSONObject = JSONObject().put("selectCourseList", JSONArray().put(
        JSONObject().put("courseName", name).put("attendClassTeacher", "老师").put("timeAndPlaceList", JSONArray().put(
            JSONObject().put("classDay", 4).put("classSessions", 5).put("continuingSession", 2)
                .put("classWeek", bits).put("classroomName", "C301")))))
    @Test fun `new URP processes every response group and exact week bits`() {
        val raw = JSONObject().put("dateList", JSONArray().put(urpGroup("第一组", "10101")).put(urpGroup("第二组", "01010")))
        val rows = parse("urp_new", raw.toString()).courses
        assertEquals(setOf("第一组", "第二组"), rows.map { it.title }.toSet())
        assertEquals(setOf(1, 3, 5), rows.filter { it.title == "第一组" }.flatMap { it.weeksSet() }.toSet())
        assertEquals(setOf(2, 4), rows.filter { it.title == "第二组" }.flatMap { it.weeksSet() }.toSet())
        assertTrue(rows.all { it.startPeriod == 5 && it.endPeriod == 6 && it.dayOfWeek == 4 })
    }
    @Test fun `Kingosoft embedded data preserves nonconsecutive weeks`() {
        val html = """<script>var kbxx = [{"kcmc":"程序设计","xq":"5","jcdm2":"7,8","zcs":"1,3,6","jxcdmcs":"机房","teaxms":"老师"}];</script>"""
        val rows = parse("cf", html).courses
        assertEquals(setOf(1, 3, 6), rows.flatMap { it.weeksSet() }.toSet())
        assertTrue(rows.all { it.startPeriod == 7 && it.endPeriod == 8 && it.dayOfWeek == 5 })
    }
    @Test fun `URP table derives columns by headers and retains teacher`() {
        val html = """<table class="displayTag"><thead><tr>${listOf("课程名", "教师", "周次", "星期", "节次", "节数", "教学楼", "教室").joinToString("") { "<th>$it</th>" }}</tr></thead>
        <tbody><tr><td>英语</td><td>王老师</td><td>2-6周(双)</td><td>4</td><td>3-4</td><td>2</td><td>A</td><td>201</td></tr></tbody></table>"""
        val c = parse("urp", html).courses.single()
        assertEquals(setOf(2, 4, 6), c.weeksSet()); assertEquals(4, c.dayOfWeek)
        assertEquals("A201", c.room); assertEquals("王老师", c.teacher)
    }
    @Test fun `school specific legacy formats require explicit weeks and sections`() {
        val pku = """<table class="datagrid"><tbody><tr>${listOf("北大课程", "", "", "", "老师", "", "", "1~5周 周二3~4节 A201", "已排", "", "").joinToString("") { "<td>$it</td>" }}</tr></tbody></table>"""
        val pc = parse("pku", pku).courses.single()
        assertEquals(2, pc.dayOfWeek); assertEquals(3, pc.startPeriod); assertEquals(setOf(1,2,3,4,5), pc.weeksSet())
        val bnuz = """<table id="table1"><tr><td>3</td><td><span></span>珠海课程<br>老师{1-5周(单),8周}<br>A201(2节)<br></td></tr></table>"""
        assertEquals(setOf(1,3,5,8), parse("bnuz", bnuz).courses.flatMap { it.weeksSet() }.toSet())
        val hniu = """<table bordercolordark="#FFFFFF"><tr><td align="center">节次</td><td valign="top">信息课程<br>老师 [1-3,7周][5-6节]<br>A201</td></tr></table>"""
        val hc = parse("hniu", hniu).courses
        assertEquals(setOf(1,2,3,7), hc.flatMap { it.weeksSet() }.toSet())
        assertTrue(hc.all { it.startPeriod == 5 && it.endPeriod == 6 })
        val hnust = """<table id="kbtable"><tr><td></td><td><div id="2-1" style="display: none;">科大课程<br>老师<br>1-3周<br>A201</div></td></tr></table>"""
        assertEquals(3, parse("hnust", hnust).courses.single().startPeriod)
    }
    @Test fun `legacy Zhengfang variants preserve explicit time information`() {
        val normal = """<table id="Table1"><tr><td>第1节</td><td align="Center"><a href="#">旧正方课程<br>周二第1,2节{第1-5周}<br>老师<br>A201</a></td></tr></table>"""
        for (kind in listOf("zf", "zf_1")) {
            val c = parse(kind, normal).courses.single()
            assertEquals("旧正方课程", c.title); assertEquals(2, c.dayOfWeek)
            assertEquals(1, c.startPeriod); assertEquals(2, c.endPeriod)
        }
    }
    @Test fun `single sections and missing weeks never inherit the preceding course`() {
        val source = """<table id="table1"><tr><td class="festival">3</td><td id="2-3"><div><span class="title">单节课</span>
        <p><span title="节/周">(3节)4周</span></p></div></td></tr></table>"""
        val c = parse("zf_new", source).courses.single()
        assertEquals(3, c.startPeriod); assertEquals(3, c.endPeriod); assertEquals(setOf(4), c.weeksSet())
        assertTrue(runCatching { parse("zf_new", source.replace("4周", "未知周次")) }.isFailure)
        assertTrue(runCatching { parse("qz", qz("1-3(单),8(周)")) }.isSuccess)
    }
    @Test fun `empty and malformed pages never produce a writable draft`() {
        AcademicPortalParser.adapters("").forEach { adapter ->
            assertTrue(runCatching { parse(adapter.parser, "<html><form>请登录</form></html>") }.isFailure)
        }
        assertTrue(runCatching { parse("qz", qz("not a week")) }.isFailure)
        assertTrue(runCatching { parse("qz", qz("1-5(周)", day = 8)) }.isFailure)
        assertTrue(runCatching { parse("qz_with_node", qz("1-3(周)[0-2节]")) }.isFailure)
    }
    @Test fun `URLs must have explicit web schemes and cannot embed credentials`() {
        for (bad in listOf("example.edu.cn", "javascript:alert(1)", "file:///etc/passwd", "https://user:pass@example.edu.cn", "https://")) {
            assertNull(AcademicPortalParser.portalUrl(bad))
        }
        assertEquals("https://jw.example.edu.cn/path?q=1", AcademicPortalParser.portalUrl(" https://jw.example.edu.cn/path?q=1 "))
    }
}
