package com.kstudio.agenda.model

/** 优先同一行；只在维持可读字号仍放不下时换行，时间永不省略。 */
object TimeRangeLayout {
    data class Line(val text: String, val fontSize: Float)
    fun lines(start: String, end: String, width: Float, preferred: Float = 20f, minimum: Float = 16f,
        measure: (String, Float) -> Float): List<Line> {
        require(width > 0f && preferred >= minimum && minimum > 0f)
        fun clean(text: String) = text.trim().replace(Regex("\\s+"), " ")
        val first = clean(start); val last = clean(end)
        if (first.isEmpty() && last.isEmpty()) return emptyList()
        val combined = if (last.isEmpty()) first else "$first–$last"
        fun fitted(text: String): Line? {
            if (measure(text, preferred) <= width) return Line(text, preferred)
            val size = (preferred * width / measure(text, preferred)).coerceAtMost(preferred)
            return if (size >= minimum && measure(text, size) <= width + .01f) Line(text, size) else null
        }
        fitted(combined)?.let { return listOf(it) }
        val pieces = listOf(first, if (last.isEmpty()) "" else "–$last").filter(String::isNotEmpty)
        return pieces.flatMap { text ->
            fitted(text)?.let { return@flatMap listOf(it) }
            val result = mutableListOf<Line>()
            val line = StringBuilder()
            text.codePoints().toArray().forEach { code ->
                val char = String(Character.toChars(code))
                if (line.isNotEmpty() && measure(line.toString() + char, minimum) > width) {
                    result += Line(line.toString(), minimum); line.clear()
                }
                line.append(char)
            }
            if (line.isNotEmpty()) {
                val raw = line.toString()
                result += Line(raw, minOf(minimum, minimum * width / measure(raw, minimum).coerceAtLeast(1f)))
            }
            result
        }
    }
}
