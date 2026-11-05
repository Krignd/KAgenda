package com.kstudio.agenda.data

import android.content.Context
import android.net.Uri
import com.kstudio.agenda.data.engines.EngineFeatures
import com.kstudio.agenda.data.engines.EngineId
import com.kstudio.agenda.data.engines.OcrEngineContract

object ScreenshotTextExtractor {
    suspend fun extract(context: Context, uri: Uri): String =
        EngineFeatures.instance(context, EngineId.OCR, OcrEngineContract::class.java).extract(context, uri)
}

/** Preserve coordinates when a grid cannot be reconstructed safely. No invented weeks or titles. */
object OcrTimetableLayout {
    data class Cell(val weekday: Int, val start: Int, val end: Int, val lines: List<String>)
    fun cells(source: String): List<Cell> = Regex("Cell weekday=(\\d), startPeriod=(\\d+), endPeriod=(\\d+):\\n(.*?)(?=\\n\\nCell|\\nUnassigned:|$)", RegexOption.DOT_MATCHES_ALL)
        .findAll(source).map { m -> Cell(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
            m.groupValues[4].lines().map { it.replaceFirst(Regex("^\\d+,\\d+:\\s*"), "") }.filter { it.isNotBlank() }) }.toList()
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val x get() = (left + right) / 2
        val y get() = (top + bottom) / 2
    }
    fun extract(lines: List<Line>): String {
        val sorted = lines.sortedWith(compareBy({ it.top }, { it.left }))
        if (sorted.isEmpty()) return ""
        val dayChars = "一二三四五六日"
        val days = sorted.mapNotNull { line ->
            val m = Regex("^(?:星期|周)([一二三四五六日天])$").find(line.text.replace(" ", "").trim('|', '·', '_'))
            m?.let { line to if (it.groupValues[1] == "天") 7 else dayChars.indexOf(it.groupValues[1]) + 1 }
        }.distinctBy { it.second }.sortedBy { it.first.x }
        if (days.size < 2) return "OCR positions (x,y,text):\n" + sorted.joinToString("\n") { "${it.x},${it.y}: ${it.text}" }
        val gap = days.zipWithNext { a,b -> b.first.x - a.first.x }.minOrNull() ?: 1
        val periods = sorted.mapNotNull { line ->
            if (line.x > days.first().first.x - gap / 2) return@mapNotNull null
            val m = Regex("^第?(\\d{1,2})(?:[-—~至](\\d{1,2}))?节?$").find(line.text.replace(" ", "").trim('|', '·', '_')) ?: return@mapNotNull null
            val start = m.groupValues[1].toInt(); val end = m.groupValues[2].toIntOrNull() ?: start
            if (start !in 1..24 || end !in start..24) null else Triple(line, start, end)
        }.sortedBy { it.first.y }
        if (periods.isEmpty()) return "OCR positions (x,y,text):\n" + sorted.joinToString("\n") { "${it.x},${it.y}: ${it.text}" }
        val cells = linkedMapOf<Triple<Int,Int,Int>, MutableList<Line>>()
        val unassigned = mutableListOf<Line>()
        for (line in sorted) {
            if (days.any { it.first == line } || periods.any { it.first == line }) continue
            val day = days.minByOrNull { kotlin.math.abs(it.first.x - line.x) }
            val period = periods.lastOrNull { line.y >= it.first.top - 15 }
            if (day == null || period == null || line.x < days.first().first.x - gap / 2 || line.y <= days.maxOf { it.first.bottom }) {
                unassigned += line; continue
            }
            cells.getOrPut(Triple(day.second, period.second, period.third)) { mutableListOf() } += line
        }
        return "OCR positions (x,y,text); cells inferred from headers:\n" + cells.entries.joinToString("\n\n") { (key, values) ->
            "Cell weekday=${key.first}, startPeriod=${key.second}, endPeriod=${key.third}:\n" + values.joinToString("\n") { "${it.x},${it.y}: ${it.text}" }
        } + if (unassigned.isEmpty()) "" else "\nUnassigned:\n" + unassigned.joinToString("\n") { "${it.x},${it.y}: ${it.text}" }
    }
}
