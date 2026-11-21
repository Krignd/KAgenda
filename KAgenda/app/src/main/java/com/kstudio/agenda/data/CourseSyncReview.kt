package com.kstudio.agenda.data

import com.kstudio.agenda.model.SemesterSchedule
import java.time.LocalDate
import java.time.LocalTime
import com.kstudio.agenda.model.PeriodTimes

internal data class SyncedLesson(
    val date: LocalDate,
    val title: String,
    val code: String,
    val teacher: String,
    val room: String,
    val time: String,
    val periods: IntRange,
)

internal data class CourseSyncReview(
    val semesterChanged: Boolean,
    val removed: List<SyncedLesson>,
    val added: List<SyncedLesson>,
)

internal fun compareCourseSync(
    previous: SemesterSchedule,
    incoming: SemesterSchedule,
    incomingTimes: List<Pair<LocalTime, LocalTime>> = emptyList(),
): CourseSyncReview? {
    fun lessons(schedule: SemesterSchedule, times: List<Pair<LocalTime, LocalTime>>) = schedule.weeks.toSortedMap().flatMap { (week, courses) ->
        courses.filter { it.occursInWeek(week) }.map { course ->
            val time = if (course.hasCustomTime || times.isEmpty()) course.timeRange else {
                val start = times.getOrNull(course.startPeriod - 1)?.first ?: course.startTime
                val end = times.getOrNull(course.endPeriod - 1)?.second ?: course.endTime
                PeriodTimes.format(start) + "-" + PeriodTimes.format(end)
            }
            SyncedLesson(schedule.mondayOf(week).plusDays((course.dayOfWeek - 1).toLong()), course.title.trim(),
                course.code.trim(), course.teacher.trim(), course.room.trim(), time, course.startPeriod..course.endPeriod)
        }
    }
    val before = lessons(previous, emptyList())
    val after = lessons(incoming, incomingTimes)
    fun subtract(from: List<SyncedLesson>, matching: List<SyncedLesson>): List<SyncedLesson> {
        val counts = matching.groupingBy { it }.eachCount().toMutableMap()
        return from.filter { row ->
            val count = counts[row] ?: 0
            if (count > 0) counts[row] = count - 1
            count == 0
        }.sortedWith(compareBy({ it.date }, { it.time }, { it.title }))
    }
    val term = previous.semesterLabel != incoming.semesterLabel || previous.anchorEpochDay != incoming.anchorEpochDay
    val removed = subtract(before, after)
    val added = subtract(after, before)
    return CourseSyncReview(term, removed, added).takeIf { term || removed.isNotEmpty() || added.isNotEmpty() }
}
