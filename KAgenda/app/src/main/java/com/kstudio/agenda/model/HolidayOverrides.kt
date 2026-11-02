package com.kstudio.agenda.model

import java.time.LocalDate

enum class HolidayKind { HOLIDAY, WORKDAY, NORMAL }
data class HolidayOverride(val date: LocalDate, val kind: HolidayKind, val name: String = "")

/** A small, portable preference payload. Malformed backups are rejected before writing. */
object HolidayOverrides {
    fun decode(raw: String): Map<Long, HolidayOverride> {
        if (raw.isEmpty()) return emptyMap()
        require(raw.length <= 300_000)
        val lines = raw.lines()
        require(lines.size <= 5000)
        return lines.associate { line ->
            val fields = line.split('|')
            require(fields.size == 3)
            val date = LocalDate.parse(fields[0])
            require(date.year in 2000..2100)
            val kind = HolidayKind.valueOf(fields[1])
            val name = fields[2]
            require(name.length <= 20 && name.none { it.isISOControl() })
            require(kind != HolidayKind.HOLIDAY || name.isNotBlank())
            date.toEpochDay() to HolidayOverride(date, kind, name)
        }.also { require(it.size == lines.size) }
    }
    fun encode(values: Collection<HolidayOverride>): String = values.sortedBy { it.date }.joinToString("\n") {
        "${it.date}|${it.kind}|${it.name.trim()}"
    }.also { decode(it) }
}
