package com.kstudio.agenda.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
internal fun isPlannerPhotoId(value: String) = uuid.matches(value)

/** Validates everything the planner will read before staging a restore. */
internal fun plannerBackupData(json: JSONObject, photoIds: Set<String>): PlannerData {
    val data = PlannerStore.decode(json.toString())
    require(data.tabs.distinct() == data.tabs &&
        "Settings" in data.tabs && data.tabs.all { it in setOf("Schedule", "Plan", "School", "Ongoing", "Settings", "Countdown", "Bills") })
    require(data.countdownPeriod in setOf("all", "day", "week", "month"))
    require(data.widgetActions.all { (key, value) ->
        key in setOf("next", "overview", "countdown") && value in setOf("", "course", "camera", "countdown", "agenda")
    })
    require(data.countdownFormat in setOf("auto", "days_clock", "hours"))
    val definitionIds = data.courseTagDefinitions.map { definition ->
        if (definition.id !in setOf("exam", "lab", "quiz")) {
            require(uuid.matches(definition.id) && !definition.label.isNullOrBlank())
        }
        require(definition.label == null || definition.label.length in 1..30 && definition.label.isNotBlank())
        definition.id
    }.toSet()
    require(definitionIds.size == data.courseTagDefinitions.size)
    require(data.courseTags.all { (key, tags) -> key.isNotBlank() && tags.all { it in definitionIds } })
    require(data.notes.map { it.id }.toSet().size == data.notes.size)
    require((data.links.map { it.id } + data.notes.map { it.id }).toSet().size == data.links.size + data.notes.size)
    require(data.bills.map { it.id }.toSet().size == data.bills.size)
    data.notes.forEach { note ->
        validateLesson(note.lesson)
        require(uuid.matches(note.id) && (note.text.isNotBlank() || note.photo != null))
        require(!note.countdown || note.deadline != null)
        note.deadline?.let(LocalDateTime::parse)
        require(note.tags.all { it.isNotBlank() })
    }
    val referencedPhotos = data.notes.mapNotNull { it.photo }
    require(referencedPhotos.size == photoIds.size && referencedPhotos.toSet() == photoIds && photoIds.all(::isPlannerPhotoId)) {
        "Backup lesson photos do not match the planner"
    }
    data.links.forEach { link ->
        require(link.id.isNotBlank() && link.title.isNotBlank() && (link.agendaId == null || link.lesson == null))
        LocalDateTime.parse(link.at)
        link.lesson?.let(::validateLesson)
    }
    data.bills.forEach { bill ->
        require(uuid.matches(bill.id) && bill.title.isNotBlank())
        LocalDate.parse(bill.first)
        require(bill.repeat in setOf("year", "month", "week", "days") && bill.days in 1..3650)
        require(bill.interval == null || bill.interval in 1..120)
        require(bill.customCategory.orEmpty().length <= 30)
    }
    return data
}

private fun validateLesson(lesson: LessonRef) {
    require(lesson.school.matches(Regex("[a-zA-Z0-9_-]{1,100}")) && lesson.key.isNotBlank() && lesson.title.isNotBlank())
    LocalDate.ofEpochDay(lesson.day)
}
