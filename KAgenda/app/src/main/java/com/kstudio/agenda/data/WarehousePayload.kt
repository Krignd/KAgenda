package com.kstudio.agenda.data

import com.kstudio.agenda.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** 仅读取公开桥接协议的字段；全部暂存、校验、预览，确认后才落盘。 */
object WarehousePayload {
    data class Batch(val courses: List<Course>, val times: List<Pair<LocalTime, LocalTime>>, val firstMonday: LocalDate?, val totalWeeks: Int)
    class UnsupportedTime : IllegalArgumentException()
    fun mergeConfig(previous: String, update: String): String {
        val merged = if (previous.isBlank()) JSONObject() else JSONObject(previous)
        val changes = JSONObject(update)
        changes.keys().forEach { key -> merged.put(key, changes.get(key)) }
        if (changes.has("totalWeeks") && !changes.has("semesterTotalWeeks")) merged.put("semesterTotalWeeks", changes.get("totalWeeks"))
        return merged.toString()
    }
    fun timeSlots(raw: String): List<Pair<LocalTime, LocalTime>> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        require(array.length() in 1..PeriodTimes.MAX_COUNT)
        val slots = (0 until array.length()).map { array.getJSONObject(it) }.sortedBy { it.getInt("number") }
        val result = slots.mapIndexed { index, o ->
            require(o.getInt("number") == index + 1)
            LocalTime.parse(o.getString("startTime")) to LocalTime.parse(o.getString("endTime"))
        }
        require(result.all { it.second.isAfter(it.first) })
        require(result.zipWithNext().all { (a, c) -> !c.first.isBefore(a.second) })
        return result
    }
    fun parse(coursesRaw: String, timesRaw: String, configRaw: String): Batch {
        val slots = timeSlots(timesRaw)
        val config = if (configRaw.isBlank()) JSONObject() else JSONObject(configRaw)
        val weeks = if (config.has("semesterTotalWeeks")) config.optInt("semesterTotalWeeks", 20) else config.optInt("totalWeeks", 20)
        require(weeks in 1..Course.MAX_WEEK)
        val start = config.optString("semesterStartDate").takeIf { it.isNotBlank() && it != "null" }?.let { LocalDate.parse(it) }
        val monday = start?.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val arr = JSONArray(coursesRaw)
        require(arr.length() in 1..5000)
        val courses = (0 until arr.length()).map { arr.getJSONObject(it) }.map { row ->
            require(!row.isNull("name"))
            val name = row.getString("name").trim(); require(name.isNotBlank())
            val day = row.getInt("day"); require(day in 1..7)
            var first = row.optInt("startSection"); var last = row.optInt("endSection")
            var customStart = ""; var customEnd = ""
            if (row.optBoolean("isCustomTime", false)) {
                customStart = row.getString("customStartTime").trim()
                customEnd = row.getString("customEndTime").trim()
                val exact = requireNotNull(Course.validCustomTimes(customStart, customEnd)) { "Invalid course times" }
                val hasFirst = row.has("startSection"); val hasLast = row.has("endSection")
                require(hasFirst == hasLast) { "Incomplete course sections" }
                if (hasFirst) {
                    first = requireNotNull(row.get("startSection").toString().toIntOrNull()) { "Invalid start section" }
                    last = requireNotNull(row.get("endSection").toString().toIntOrNull()) { "Invalid end section" }
                }
                // Keep explicit school periods and actual clocks independently.
                // Time-only legacy records still need an exact slot mapping;
                // never invent a period for an otherwise unmappable time.
                if (!hasFirst || (first == 0 && last == 0)) {
                    first = slots.indexOfFirst { it.first == exact.first } + 1
                    last = slots.indexOfLast { it.second == exact.second } + 1
                    if (first < 1 || last < first) throw UnsupportedTime()
                }
            }
            require(first in 1..PeriodTimes.MAX_COUNT && last in first..PeriodTimes.MAX_COUNT)
            if (slots.isNotEmpty()) require(last <= slots.size)
            val ws = row.getJSONArray("weeks")
            val active = (0 until ws.length()).map { ws.getInt(it) }.distinct().sorted()
            require(active.isNotEmpty() && active.all { it in 1..Course.MAX_WEEK })
            val importedInfo = if (row.isNull("extraInfo")) "" else {
                require(row.get("extraInfo") is String) { "Course details must be text" }
                row.getString("extraInfo").trim().also { require(it.length <= 4000) }
            }
            val creditInfo = row.optString("credit").takeIf { it.isNotBlank() && it != "null" }
                ?.let { "${com.kstudio.agenda.i18n.AppText.current.importCreditLabel}: $it" }.orEmpty()
            Course(name, "", if (row.isNull("teacher")) "" else row.optString("teacher"), Course.encodeWeeks(active), if (row.isNull("position")) "" else row.optString("position"), first, last, day,
                extraInfo = listOf(importedInfo, creditInfo).filter { it.isNotBlank() }.distinct().joinToString("\n"),
                customStartTime = customStart, customEndTime = customEnd)
        }.groupBy { listOf(it.title, it.code, it.teacher, it.weeksRaw, it.room, it.startPeriod, it.endPeriod, it.dayOfWeek, it.customStartTime, it.customEndTime) }
            .values.map { equivalent ->
                // Identical timetable facts can carry multiple teaching-class
                // descriptions. Keep every distinct block in one occurrence.
                val details = equivalent.map { it.extraInfo }.filter { it.isNotBlank() }.distinct().joinToString("\n\n")
                require(details.length <= 4000) { "Combined course details exceed supported size" }
                equivalent.first().copy(extraInfo = details)
            }
        return Batch(courses, slots, monday, maxOf(weeks, courses.maxOf { it.weeksSet().maxOrNull() ?: 1 }))
    }
}
