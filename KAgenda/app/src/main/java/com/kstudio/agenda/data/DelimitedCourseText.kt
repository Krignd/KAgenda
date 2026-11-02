package com.kstudio.agenda.data

/** RFC-style quoting, embedded newlines and empty cells; also accepts escaped Markdown pipes. */
internal object DelimitedCourseText {
    fun rows(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row += cell.toString().trim(); cell.clear() }
        fun endRow() {
            endCell()
            val values = if (separator == '|') row.drop(if (row.firstOrNull().isNullOrEmpty()) 1 else 0)
                .dropLast(if (row.lastOrNull().isNullOrEmpty()) 1 else 0) else row.toList()
            if (values.any { it.isNotBlank() }) rows += values
            row.clear()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                separator == '|' && ch == '\\' && text.getOrNull(i + 1) == '|' -> { cell.append('|'); i++ }
                separator == ',' && ch == '"' -> {
                    if (quoted && text.getOrNull(i + 1) == '"') { cell.append('"'); i++ }
                    else if (quoted || cell.isBlank()) quoted = !quoted else cell.append(ch)
                }
                ch == separator && !quoted -> endCell()
                ch == '\n' && !quoted -> endRow()
                ch != '\r' -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
