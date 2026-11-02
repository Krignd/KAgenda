package com.kstudio.agenda.data

import com.kstudio.agenda.model.Course
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CourseImportRegressionTest {
    private fun only(s: String) = LocalSmartParser.parseCourses(s).single()
    private val header = "课程名\t星期\t节次\t周次\t教室\t教师\t课程号"
    private fun row(title: String = "高等数学", day: String = "周一", weeks: String = "1-16", room: String = "A101", teacher: String = "张三") =
        "$title\t$day\t1-2\t$weeks\t$room\t$teacher\tMATH101"

    @Test fun numericWeekdayAndBareWeeks() {
        val c = only("$header\n${row(day = "1")}")
        assertEquals(1, c.dayOfWeek); assertEquals((1..16).toSet(), c.weeksSet())
    }
    @Test fun emptyRoomDoesNotShiftTeacherAndCode() {
        val c = only("$header\n${row(room = "")}")
        assertEquals("", c.room); assertEquals("张三", c.teacher); assertEquals("MATH101", c.code)
    }
    @Test fun quotedCsvCommaQuoteAndNewline() {
        val c = only("课程名,星期,节次,周次,教室,教师\n\"数学,\"\"实验\"\"\n课程\",1,1-2,\"1,3,5\",A101,张三")
        assertEquals("数学,\"实验\"\n课程", c.title); assertEquals(setOf(1,3,5), c.weeksSet())
    }
    @Test fun markdownHeadingAndEmptyCells() {
        val c = only("# K日程导出\n生成时间：2026-10-20\n## 课程\n| 课程 | 星期 | 节次 | 时间 | 周次 | 教室 | 教师 |\n| --- | --- | --- | --- | --- | --- | --- |\n| 高等数学 | 1 | 第1-2节 | 08:00-09:35 | 1-16 | | 张三 |")
        assertEquals("高等数学", c.title); assertEquals("", c.room); assertEquals("张三", c.teacher); assertEquals("1-16", c.weeksRaw)
    }
    @Test fun escapedMarkdownPipe() {
        assertEquals("数学|实验", only("课程|星期|节次|周次\n数学\\|实验|1|1-2|1-16").title)
    }
    @Test fun oddRangeAndDisjointWeeks() {
        assertEquals(setOf(1,3,5,7,9,11,13,15), only("大学英语 周二 第3-4节 1-16周(单) 教1-101").weeksSet())
        assertEquals(setOf(2,4,8,10,12), only("大学英语 周二 第3-4节 1-4,8-12周（双） 教1-101").weeksSet())
    }
    @Test fun clockEndMatchesPeriodEndAndIsNotRoom() {
        val c = only("线性代数 周三 08:00-09:35 教2-301")
        assertEquals(1, c.startPeriod); assertEquals(2, c.endPeriod); assertEquals("教2-301", c.room)
    }
    @Test fun englishFullWeekday() {
        val c = only("Course\tDay\tPeriod\tWeeks\nMath\tMonday\t1-2\t1-16")
        assertEquals(1, c.dayOfWeek); assertEquals("Math", c.title)
    }
    @Test fun separateTitleAndDetails() {
        assertEquals("高等数学", only("高等数学\n周一 第1-2节 1-16周 教1-101").title)
    }
    @Test fun gridWeekdayAndPeriodAreInherited() {
        val cs = LocalSmartParser.parseCourses("节次\t周一\t周二\n1-2\t高等数学 1-16周\t大学英语 2-12周")
        assertEquals(2, cs.size); assertEquals(listOf(1,2), cs.map { it.dayOfWeek })
        assertEquals(listOf("高等数学", "大学英语"), cs.map { it.title })
    }
    @Test fun splitWeeksMergeInsteadOfOverwrite() {
        assertEquals(setOf(1,2,3,4,8,9,10,11,12), only("$header\n${row(weeks="1-4")}\n${row(weeks="8-12")}").weeksSet())
    }
    @Test fun jsonRoundTrip() {
        val original = only("$header\n${row()}")
        val date = LocalDate.of(2026,9,7)
        val decoded = CourseImportCodec.decode(CourseImportCodec.encode(listOf(original), date))
        assertTrue(decoded.issues.isEmpty()); assertEquals(date, decoded.firstMonday); assertEquals(original, decoded.courses.single())
    }
    @Test fun missingJsonWeeksDoNotBecomeEveryWeek() {
        val text = CourseImportCodec.encode(listOf(only("高等数学 周一第1-2节")))
        assertTrue(CourseImportCodec.decode(text).issues.isNotEmpty())
        assertTrue(LocalSmartParser.parseCourses(text).isEmpty())
    }
    @Test fun jsonFractionAndOutOfBoundsAreRejected() {
        val good = CourseImportCodec.encode(listOf(only("$header\n${row()}")))
        assertTrue(CourseImportCodec.decode(good.replace("\"weekday\": 1", "\"weekday\": 1.5")).issues.isNotEmpty())
        assertTrue(CourseImportCodec.decode(good.replace("\"startPeriod\": 1", "\"startPeriod\": 0")).issues.isNotEmpty())
    }
    @Test fun formatVersionAndMondayAreChecked() {
        val good = CourseImportCodec.encode(listOf(only("$header\n${row()}")), LocalDate.of(2026,9,7))
        assertTrue(runCatching { CourseImportCodec.decode(good.replace("\"version\": 1", "\"version\": 2")) }.isFailure)
        assertTrue(runCatching { CourseImportCodec.decode(good.replace("2026-09-07", "2026-09-08")) }.isFailure)
    }
    @Test fun conflictingMetadataIsNotSilentlyLost() {
        val cs = LocalSmartParser.parseCourses("$header\n${row(teacher="张三")}\n${row(teacher="李四")}")
        assertEquals(2, cs.size); assertTrue(CourseImportCodec.hasConflicts(cs))
    }
    @Test fun utfBomAndGbk() {
        val line = "高等数学 周一第1-2节 1-16周 A101"
        for (encoding in listOf("UTF-8", "UTF-16", "UTF-16BE", "GBK")) {
            var bytes = line.toByteArray(charset(encoding))
            if (encoding == "UTF-8") bytes = byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()) + bytes
            if (encoding == "UTF-16BE") bytes = byteArrayOf(0xfe.toByte(),0xff.toByte()) + bytes
            assertEquals("高等数学", only(DocumentTextExtractor.extractBytes(bytes, "txt").getOrThrow()).title)
        }
    }
    private fun zip(vararg entries: Pair<String,String>): ByteArray = ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { z -> entries.forEach { (path, text) -> z.putNextEntry(ZipEntry(path)); z.write(text.toByteArray()); z.closeEntry() } }
    }.toByteArray()
    private val fields = listOf("课程名","星期","节次","周次","教室","教师")
    private val values = listOf("高等数学","周一","第1-2节","1-16周","","张三")
    @Test fun wordAndPowerPointTablesKeepRowsColumnsAndEmptyRoom() {
        for ((ext, prefix, name) in listOf(Triple("docx","w","word/document.xml"),Triple("pptx","a","ppt/slides/slide1.xml"))) {
            val xml = "<$prefix:document><$prefix:tbl>" + listOf(fields,values).joinToString("") { cells ->
                "<$prefix:tr>" + cells.joinToString("") { "<$prefix:tc><$prefix:p><$prefix:r><$prefix:t>$it</$prefix:t></$prefix:r></$prefix:p></$prefix:tc>" } + "</$prefix:tr>"
            } + "</$prefix:tbl></$prefix:document>"
            val c = only(DocumentTextExtractor.extractBytes(zip(name to xml),ext).getOrThrow())
            assertEquals("高等数学", c.title); assertEquals("张三", c.teacher); assertEquals("", c.room)
        }
    }
    @Test fun htmlTableKeepsEmptyCellAndDecodesEntities() {
        val html = "<table>" + listOf(fields,values).joinToString("") { cells -> "<tr>" + cells.joinToString("") { "<td>$it</td>" } + "</tr>" } + "</table>"
        val c = only(DocumentTextExtractor.extractBytes(html.toByteArray(),"html").getOrThrow())
        assertEquals("高等数学",c.title); assertEquals("张三",c.teacher); assertEquals("",c.room)
    }
    @Test fun xlsxWithMimeOnlyAndSharedStringsAfterWorksheet() {
        val all = fields+values
        val xml = "<worksheet><sheetData>" + listOf(fields.indices.toList(),values.indices.map { it+fields.size }).mapIndexed { i, indices ->
            "<row>" + indices.mapIndexed { j, n -> "<c r=\"${('A'+j)}${i+1}\" t=\"s\"><v>$n</v></c>" }.joinToString("") + "</row>"
        }.joinToString("") + "</sheetData></worksheet>"
        val shared = "<sst>" + all.joinToString("") { "<si><t>$it</t></si>" } + "</sst>"
        val c = only(DocumentTextExtractor.extractBytes(zip("xl/worksheets/sheet1.xml" to xml,"xl/sharedStrings.xml" to shared),"","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").getOrThrow())
        assertEquals("高等数学",c.title); assertEquals("张三",c.teacher); assertEquals("",c.room)
    }
    @Test fun oversizedAndOldBinaryFormatsFail() {
        assertTrue(DocumentTextExtractor.extractBytes(ByteArray(8*1024*1024+1),"txt").isFailure)
        assertTrue(DocumentTextExtractor.extractBytes(byteArrayOf(0,1,2),"xls").isFailure)
    }
    @Test fun rawAiWeeksAreNormalizedByCodeRatherThanModelArithmetic() {
        val raw = """{"format":"kagenda.schedule","version":1,"firstWeekMonday":null,"courses":[{"title":"数学","weekday":1,"startPeriod":1,"endPeriod":2,"weeksRaw":"1-4,8-12周（单）","room":"A101","teacher":"张三"}]}"""
        assertEquals(setOf(1,3,9,11), CourseImportCodec.decode(raw).courses.single().weeksSet())
        assertTrue(CourseImportCodec.decode(raw.replace("1-4,8-12周（单）", "单周")).issues.isNotEmpty())
    }
    @Test fun invalidPeriodRangeDoesNotDegradeToItsLastPeriod() {
        assertTrue(LocalSmartParser.parseCourses("高等数学 周一 第9-1节 1-16周").isEmpty())
        assertTrue(LocalSmartParser.parseCourses("高等数学 周一 第1-25节 1-16周").isEmpty())
    }
    @Test fun commonUnicodeTimeSeparator() {
        val c = only("高等数学 周一 08:00–09:35 1-16周 A101")
        assertEquals(1,c.startPeriod); assertEquals(2,c.endPeriod)
    }
    @Test fun ocrCellsKeepColumnAndRowIdentity() {
        fun line(t: String, x: Int, y: Int) = OcrTimetableLayout.Line(t,x,y,x+80,y+20)
        val text = OcrTimetableLayout.extract(listOf(line("星期一",200,10),line("星期二",600,10),line("第1-2节",0,100),line("高等数学",200,100),line("大学英语",600,100),line("1-16周",200,140)))
        assertTrue(text.contains("weekday=1, startPeriod=1, endPeriod=2:\n240,110: 高等数学"))
        assertTrue(text.contains("weekday=2, startPeriod=1, endPeriod=2:\n640,110: 大学英语"))
    }
    @Test fun ocrEvidenceCorrectsModelWeekdayAndRejectsInventedMetadata() {
        val source = "OCR positions:\nCell weekday=1, startPeriod=1, endPeriod=2:\n240,110: 高等数学\n240,150: 周次：1-4\n240,180: 教室A101"
        val guess = """{"format":"kagenda.schedule","version":1,"firstWeekMonday":null,"courses":[{"title":"高等数学","weekday":7,"startPeriod":9,"endPeriod":10,"weeks":[1,3,5],"teacher":"虚构教师","code":"FAKE101"}]}"""
        val decoded = CourseImportCodec.decode(CourseAiParser.groundOcr(source,guess))
        assertTrue(decoded.issues.isEmpty())
        val c = decoded.courses.single()
        assertEquals(1,c.dayOfWeek); assertEquals(1,c.startPeriod); assertEquals(2,c.endPeriod)
        assertEquals(setOf(1,2,3,4),c.weeksSet()); assertEquals("A101",c.room)
        assertEquals("",c.teacher); assertEquals("",c.code)
    }
    @Test fun unlocatedOcrCannotSaveModelGuessedWeekday() {
        val guess = """{"format":"kagenda.schedule","version":1,"courses":[{"title":"数学","weekday":1,"startPeriod":1,"endPeriod":2,"weeks":[1,2]}]}"""
        assertTrue(CourseImportCodec.decode(CourseAiParser.groundOcr("OCR positions:\n240,110: 数学",guess)).issues.isNotEmpty())
    }
}
