package com.kstudio.agenda.data

import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** Versioned interchange format shared by pasted text, page extraction and OCR. Never writes data. */
object CourseImportCodec {
    const val FORMAT = "kagenda.schedule"
    const val MAX_TEXT = 120_000
    data class Preview(val courses: List<Course>, val firstMonday: LocalDate?, val issues: List<String>)

    fun decode(text: String): Preview {
        require(text.length <= MAX_TEXT) { "input_too_long" }
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = JSONObject(clean)
        require(root.optString("format") == FORMAT && integer(root, "version") == 1) { "invalid_format" }
        val date = root.optString("firstWeekMonday").takeUnless { it.isBlank() || it == "null" }
            ?.let { LocalDate.parse(it) }
        require(date == null || date.dayOfWeek == DayOfWeek.MONDAY) { "invalid_monday" }
        val rows = root.getJSONArray("courses")
        require(rows.length() in 1..500) { "invalid_count" }
        val courses = mutableListOf<Course>()
        val issues = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            runCatching {
                val o = rows.getJSONObject(i)
                val title = o.getString("title").trim()
                val day = integer(o, "weekday")
                val start = integer(o, "startPeriod")
                val end = integer(o, "endPeriod")
                require(title.isNotBlank() && title.length <= 200) { "invalid_title" }
                require(day in 1..7 && start in 1..PeriodTimes.MAX_COUNT && end in start..PeriodTimes.MAX_COUNT) { "invalid_slot" }
                val rawWeeks = o.optString("weeksRaw").takeUnless { it.isBlank() || it == "null" }
                val normalized = rawWeeks?.let { LocalSmartParser.normalizeImportWeeks(it) }
                require(normalized == null || normalized.isNotBlank()) { "missing_weeks" }
                val weeks = if (normalized != null) JSONArray(Course.parseWeeks(normalized).flatMap { it.toList() })
                    else o.getJSONArray("weeks")
                require(weeks.length() in 1..40) { "missing_weeks" }
                val nums = (0 until weeks.length()).map { j ->
                    val n = weeks.get(j)
                    require(n is Number && n.toDouble() == n.toInt().toDouble() && n.toInt() in 1..40) { "invalid_weeks" }
                    n.toInt()
                }
                courses += Course(title, o.optString("code", ""), o.optString("teacher", ""),
                    Course.encodeWeeks(nums), o.optString("room", ""), start, end, day)
            }.onFailure { issues += "${i + 1}: ${it.message ?: "invalid_course"}" }
        }
        if (hasConflicts(courses)) issues += "conflicting_courses"
        return Preview(merge(courses), date, issues)
    }

    private fun integer(o: JSONObject, field: String): Int {
        val n = o.get(field)
        require(n is Number && n.toDouble() == n.toInt().toDouble()) { "invalid_slot" }
        return n.toInt()
    }

    fun encode(courses: List<Course>, firstMonday: LocalDate? = null): String = JSONObject()
        .put("format", FORMAT).put("version", 1)
        .put("firstWeekMonday", firstMonday?.toString() ?: JSONObject.NULL)
        .put("courses", JSONArray().apply {
            courses.forEach { c -> put(JSONObject().put("title", c.title).put("weekday", c.dayOfWeek)
                .put("startPeriod", c.startPeriod).put("endPeriod", c.endPeriod)
                .put("weeks", if (c.weeksRaw.isBlank()) JSONObject.NULL else JSONArray(c.weeksSet().sorted()))
                .put("room", c.room).put("teacher", c.teacher).put("code", c.code)) }
        }).toString(2)

    /** Storage IDs predate imports. Reject incompatible records instead of silently overwriting them. */
    fun hasConflicts(courses: List<Course>): Boolean = courses.groupBy { it.id }.values.any { group ->
        group.map { it.title.trim() to it.teacher.trim() }.distinct().size > 1
    }

    fun merge(courses: List<Course>): List<Course> = courses.groupBy { Triple(it.id, it.title, it.teacher) }
        .values.map { rows ->
            rows.first().copy(weeksRaw = if (rows.any { it.weeksRaw.isBlank() }) ""
                else Course.encodeWeeks(rows.flatMap { it.weeksSet() }))
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.title }))
}
