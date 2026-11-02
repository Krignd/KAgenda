package com.kstudio.agenda.ui

import com.kstudio.agenda.data.CourseImportCodec
import com.kstudio.agenda.data.LocalSmartParser
import org.junit.Assert.*
import org.junit.Test

class CourseImportReviewTest {
    @Test fun partialInvalidTableCannotBeImported() {
        val review = CourseImportReview.read("课程名\t星期\t节次\t周次\n数学\t1\t1-2\t1-16\n英语\t9\t3-4\t1-16", false)
        assertEquals(1, review.courses.size)
        assertFalse(review.valid)
    }
    @Test fun rawOcrIsNotMisinterpretedAsCoursesOrEvents() {
        val review = CourseImportReview.read("高等数学 周一 第1-2节 1-16周\nOCR positions (x,y,text):\n100,50: 高等数学", true)
        assertTrue(review.courses.isEmpty()); assertTrue(review.events.isEmpty())
    }
    @Test fun completedStandardTextCanBeUsedAfterOcr() {
        val cs = LocalSmartParser.parseCourses("高等数学 周一 第1-2节 1-16周")
        val review = CourseImportReview.read(CourseImportCodec.encode(cs), true)
        assertTrue(review.valid); assertEquals(cs, review.courses)
    }
    @Test fun csvExportCanBeImportedWithoutChangingCourses() {
        val cs = LocalSmartParser.parseCourses("课程名\t星期\t节次\t周次\t教室\t教师\t课程号\n数学,实验\t1\t1-2\t1,3,5\t\t张三\tABC123")
        val parsed = LocalSmartParser.parseCourses(buildCsvExport(null, cs))
        assertEquals(cs, parsed)
    }
    @Test fun markdownExportWithEventsCanBeImportedWithoutChangingCourses() {
        val cs = LocalSmartParser.parseCourses("课程名\t星期\t节次\t周次\t教室\t教师\n数学|实验\t周一\t1-2\t1,3,5\t\t张三")
        assertEquals(cs, LocalSmartParser.parseCourses(buildMarkdownExport(null, cs, emptyList())))
    }
    @Test fun invalidJsonNeverFallsBackToGuessing() {
        val review = CourseImportReview.read("{\"courses\":\"高等数学 周一 第1-2节 1-16周\"}", false)
        assertFalse(review.valid); assertTrue(review.courses.isEmpty())
    }
}
