package com.kstudio.agenda.data

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.SemesterSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Keep
internal data class LessonRef(val school: String, val key: String, val day: Long, val title: String) {
    companion object {
        fun of(school: String, course: Course, date: LocalDate, semester: SemesterSchedule?): LessonRef {
            val edits = CourseEditStore.edits.value
            val original = edits.firstOrNull { course.editOriginKey.isNotEmpty() && it.targetKey == course.editOriginKey }?.original ?: course
            val count = semester?.coursesOnDate(date)?.count { it.seriesKey == course.seriesKey }
            val key = if (count == 1) "series/${original.seriesKey}" else "slot/${original.id}"
            return LessonRef(school, key, date.toEpochDay(), course.title)
        }
    }

    val id: String get() = "$school/$day/$key"
}

@Keep
internal data class LessonNote(
    val id: String,
    val lesson: LessonRef,
    val text: String,
    val tags: List<String>,
    val photo: String?,
    val deadline: String?,
    val countdown: Boolean,
)

@Keep
internal data class CountdownLink(
    val id: String,
    val title: String,
    val at: String,
    val agendaId: String? = null,
    val lesson: LessonRef? = null,
)

@Keep
internal data class BillRule(
    val id: String,
    val title: String,
    val first: String,
    val repeat: String,
    val days: Int,
    val category: String? = null,
    val customCategory: String? = null,
    val icon: String? = null,
    val interval: Int? = null,
) {
    fun next(now: LocalDate): LocalDate {
        val start = LocalDate.parse(first)
        if (!now.isAfter(start)) return start
        if (repeat == "month" || repeat == "year") {
            val step = (interval ?: 1).coerceIn(1, 120).toLong() * if (repeat == "year") 12 else 1
            val anchor = YearMonth.from(start)
            val elapsed = ChronoUnit.MONTHS.between(anchor, YearMonth.from(now))
            val month = anchor.plusMonths(elapsed / step * step)
            val due = month.atDay(start.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            if (due >= now) return due
            val following = month.plusMonths(step)
            return following.atDay(start.dayOfMonth.coerceAtMost(following.lengthOfMonth()))
        }
        val step = if (repeat == "week") 7L else days.coerceIn(1, 3650).toLong()
        val elapsed = ChronoUnit.DAYS.between(start, now)
        return start.plusDays(((elapsed + step - 1) / step) * step)
    }
}

@Keep
internal data class CourseTagDefinition(val id: String, val label: String? = null)

@Keep
internal data class PlannerData(
    val tabs: List<String> = listOf("Schedule", "Plan", "School", "Ongoing", "Settings"),
    val countdownPeriod: String = "all",
    val widgetActions: Map<String, String> = emptyMap(),
    val courseTags: Map<String, List<String>> = emptyMap(),
    val notes: List<LessonNote> = emptyList(),
    val links: List<CountdownLink> = emptyList(),
    val bills: List<BillRule> = emptyList(),
    val courseTagDefinitions: List<CourseTagDefinition> = listOf("exam", "lab", "quiz").map { CourseTagDefinition(it) },
    val countdownFormat: String = "auto",
    val countdownsExpanded: Boolean = true,
    val deadlinesExpanded: Boolean = true,
)

internal fun PlannerData.removeCourseTag(id: String): PlannerData = copy(
    courseTagDefinitions = courseTagDefinitions.filterNot { it.id == id },
    courseTags = courseTags.mapValues { (_, tags) -> tags - id }.filterValues { it.isNotEmpty() },
)

internal data class Deadline(val id: String, val title: String, val at: LocalDateTime)

internal fun PlannerData.countdowns(events: List<AgendaEvent>, semester: SemesterSchedule?, school: String): List<Deadline> =
    links.mapNotNull { link ->
        val at = when {
            link.agendaId != null -> {
                val event = events.firstOrNull { it.id == link.agendaId } ?: return@mapNotNull null
                val date = if (!event.isLong && event.repeatRule.isNotBlank()) LocalDateTime.parse(link.at).toLocalDate() else event.date
                if (!event.isLong && !event.occursOn(date)) return@mapNotNull null
                agendaCountdownTime(event, date)
            }
            link.lesson != null -> {
                val lesson = link.lesson
                if (lesson.school != school) return@mapNotNull null
                val date = LocalDate.ofEpochDay(lesson.day)
                val course = semester?.coursesOnDate(date)?.firstOrNull { LessonRef.of(school, it, date, semester).key == lesson.key }
                if (semester != null && course == null) return@mapNotNull null
                course?.let { date.atTime(it.startTime) } ?: LocalDateTime.parse(link.at)
            }
            else -> LocalDateTime.parse(link.at)
        }
        val title = events.firstOrNull { it.id == link.agendaId }?.title ?: link.title
        Deadline(link.id, title, at)
    } + notes.filter { it.countdown }.mapNotNull { it.asDeadline() }

internal fun agendaCountdownTime(event: AgendaEvent, date: LocalDate): LocalDateTime =
    if (event.isLong) event.endDate.atTime(runCatching { LocalTime.parse(event.endTime) }.getOrDefault(LocalTime.of(23, 59)))
    else event.anchorDateTimeOn(date)

internal fun LessonNote.asDeadline(): Deadline? = deadline?.let {
    val label = text.lineSequence().firstOrNull { line -> line.isNotBlank() }?.take(80)
    Deadline(id, listOfNotNull(lesson.title, label).joinToString(" · "), LocalDateTime.parse(it))
}

/** Shared by the foreground UI and RemoteViews providers in the application process. */
internal object PlannerStore {
    const val FILE_NAME = "planner.json"
    private val gson = Gson()
    private val _data = MutableStateFlow(PlannerData())
    val data = _data.asStateFlow()
    @Volatile private var loaded = false

    fun read(context: Context): PlannerData {
        if (!loaded) synchronized(this) {
            if (!loaded) {
                val file = context.filesDir.resolve(FILE_NAME)
                val raw = if (file.isFile) file.readText(Charsets.UTF_8) else prefs(context).getString("planner", null)
                _data.value = raw?.let(::decode) ?: PlannerData()
                loaded = true
            }
        }
        return _data.value
    }

    @Synchronized fun edit(context: Context, change: (PlannerData) -> PlannerData) {
        val next = change(read(context))
        AtomicFileStore.write(context.filesDir.resolve(FILE_NAME), encode(next))
        _data.value = next
    }

    fun encode(data: PlannerData): String = gson.toJson(data)

    fun decode(raw: String): PlannerData = requireNotNull(gson.fromJson(raw, PlannerData::class.java))

    @Synchronized fun <T> withSnapshot(context: Context, action: (PlannerData) -> T): T = action(read(context))

    @Synchronized fun reload(context: Context) {
        loaded = false
        read(context)
    }

    fun photo(context: Context, id: String) = context.filesDir.resolve("lesson-photos").resolve("$id.jpg")

    @Synchronized fun clear(context: Context) {
        edit(context) { PlannerData() }
        context.filesDir.resolve("lesson-photos").listFiles()?.forEach { it.delete() }
        context.cacheDir.resolve("lesson-drafts").listFiles()?.forEach { it.delete() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("planner", Context.MODE_PRIVATE)
}

/** A single current class wins. Overlapping classes require a choice. */
internal fun lessonCaptureCandidates(semester: SemesterSchedule?, now: LocalDateTime): List<Course> {
    val today = semester?.coursesOnDate(now.toLocalDate()).orEmpty()
    val time = now.toLocalTime()
    val current = today.filter { time >= it.startTime && time < it.endTime }
    if (current.isNotEmpty()) return current
    val lastEnd = today.map { it.endTime }.filter { it <= time }.maxOrNull() ?: return emptyList()
    if (ChronoUnit.SECONDS.between(lastEnd, time) > 30 * 60) return emptyList()
    return today.filter { it.endTime == lastEnd }
}
