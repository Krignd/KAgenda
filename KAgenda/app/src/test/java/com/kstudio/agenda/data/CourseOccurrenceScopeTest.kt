package com.kstudio.agenda.data

import com.kstudio.agenda.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CourseOccurrenceScopeTest {
    private val base = Course("Course", "C1", "Teacher", "1-16", "A", 1, 2, 1)
    private val changed = base.copy(weeksRaw = "11", customStartTime = "09:17", customEndTime = "10:43")
    private val anchor = 20000L
    private fun raw() = SemesterSchedule("Term", anchor, (10..12).associateWith { listOf(base) }, 0)
    private fun edit(week: Int = 11) = CourseEdit(false, base, changed.copy(weeksRaw = week.toString()), 0,
        sourceWeek = week, sourceAnchorEpochDay = anchor)

    @Test fun selectedOccurrenceDoesNotChangeTheSameSlotInOtherWeeks() {
        val source = raw()
        val result = CourseEditStore.apply(source, listOf(edit()))!!
        assertEquals(source.weeks[10], result.weeks[10])
        assertEquals(source.weeks[12], result.weeks[12])
        assertEquals("09:17-10:43", result.weeks.getValue(11).single().timeRange)
        assertEquals(listOf(base), source.weeks[11])
    }

    @Test fun differentWeeksKeepIndependentEdits() {
        val first = edit(10)
        val second = edit(11).copy(edited = changed.copy(customStartTime = "14:13", customEndTime = "15:37"))
        val edits = CourseEditStore.merge(listOf(first), second)
        assertEquals(2, edits.size)
        assertNotEquals(first.targetKey, second.targetKey)
        val result = CourseEditStore.apply(raw(), edits)!!
        assertEquals("09:17-10:43", result.weeks.getValue(10).single().timeRange)
        assertEquals("14:13-15:37", result.weeks.getValue(11).single().timeRange)
        assertEquals(listOf(base), result.weeks[12])
    }

    @Test fun resavingLegacyWideEditNarrowsItAndRestoresOtherWeeks() {
        val old = CourseEdit(false, base, changed.copy(weeksRaw = "1-16"), 1)
        val (_, edits) = CourseEditStore.prepareSave(listOf(old), edit(), old.targetKey)
        assertEquals(1, edits.size)
        assertEquals(base, edits.single().original)
        val result = CourseEditStore.apply(raw(), edits)!!
        assertEquals(listOf(base), result.weeks[10])
        assertEquals(listOf(base), result.weeks[12])
        assertEquals("09:17-10:43", result.weeks.getValue(11).single().timeRange)
    }

    @Test fun editingOneOccurrenceOfLocalAdditionKeepsItsOtherWeeks() {
        val add = CourseEdit(false, base, base, 0, kind = EDIT_KIND_ADD)
        val (_, edits) = CourseEditStore.prepareSave(listOf(add), edit(), add.targetKey)
        assertEquals(setOf(EDIT_KIND_ADD, EDIT_KIND_MODIFY), edits.map { it.kind }.toSet())
        val source = raw().copy(weeks = (10..12).associateWith { emptyList<Course>() })
        val result = CourseEditStore.apply(source, edits)!!
        assertEquals(base.timeRange, result.weeks.getValue(10).single().timeRange)
        assertEquals("09:17-10:43", result.weeks.getValue(11).single().timeRange)
        assertEquals(base.timeRange, result.weeks.getValue(12).single().timeRange)
    }

    @Test fun occurrenceDoesNotLeakIntoAnotherSemester() {
        val source = raw().copy(anchorEpochDay = anchor + 180)
        assertEquals(source, CourseEditStore.apply(source, listOf(edit())))
        assertNotEquals(edit().targetKey, edit().copy(sourceAnchorEpochDay = anchor + 180).targetKey)
    }

    @Test fun singleEditOnTopOfWholeCourseChangePreservesOthersAndMatchesTheViewedSlot() {
        val moved = base.copy(dayOfWeek = 3, startPeriod = 5, endPeriod = 6, room = "B")
        val whole = CourseEdit(true, base, moved, 0)
        val candidate = CourseEdit(false, moved, moved.copy(weeksRaw = "11", customStartTime = "14:13", customEndTime = "15:37"), 1,
            sourceWeek = 11, sourceAnchorEpochDay = anchor)
        val (record, edits) = CourseEditStore.prepareSave(listOf(whole), candidate, whole.targetKey)
        assertEquals(2, edits.size)
        val result = CourseEditStore.apply(raw(), edits)!!
        assertEquals(moved.timeRange, result.weeks.getValue(10).single().timeRange)
        assertEquals(moved.timeRange, result.weeks.getValue(12).single().timeRange)
        assertEquals(3, result.weeks.getValue(11).single().dayOfWeek)
        assertEquals("14:13-15:37", result.weeks.getValue(11).single().timeRange)
        assertTrue(CourseEditStore.comparisonCourses(raw(), edits, record).single().sameEditableContent(record.original))
    }

    @Test fun explicitWholeCourseEditStillChangesAllWeeks() {
        val all = CourseEdit(true, base, base.copy(room = "B"), 0)
        val result = CourseEditStore.apply(raw(), listOf(all))!!
        assertTrue(result.weeks.values.flatten().all { it.room == "B" })
    }

    @Test fun storedWeekAndSemesterAreReadBackAndInvalidScopeDoesNotBecomeGlobal() {
        val encode = CourseEditStore.javaClass.getDeclaredMethod("courseToJson", Course::class.java).apply { isAccessible = true }
        val parse = CourseEditStore.javaClass.getDeclaredMethod("parse", JSONArray::class.java).apply { isAccessible = true }
        val json = JSONObject().put("applyAll", false).put("sourceWeek", 11).put("sourceAnchorEpochDay", anchor)
            .put("original", encode.invoke(CourseEditStore, base)).put("edited", encode.invoke(CourseEditStore, changed))
        @Suppress("UNCHECKED_CAST")
        fun read() = parse.invoke(CourseEditStore, JSONArray().put(json)) as List<CourseEdit>
        assertEquals(edit().targetKey, read().single().targetKey)
        json.put("sourceWeek", 99)
        assertTrue(read().isEmpty())
        json.remove("sourceWeek"); json.remove("sourceAnchorEpochDay")
        assertNull(read().single().sourceWeek) // 旧记录兼容，用户重新保存时收拢，不擅自猜周次。
    }
}
