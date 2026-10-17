package com.kstudio.agenda.model

import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** 离线拼音；完整校名包含多音词读音，自定义名称用上下文前缀与单字表兜底。 */
class SchoolNameIndex private constructor(
    private val names: Map<String, List<String>>,
    private val characters: Map<String, String>,
    private val prefixes: List<Pair<String, List<String>>>,
) {
    data class Spelling(val full: String, val initials: String) {
        val letter: String get() = full.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
    }
    fun spelling(name: String): Spelling {
        val normalized = normalizeName(name)
        val parts = names[normalized] ?: run {
            val prefix = prefixes.firstOrNull { normalized.startsWith(it.first) }
            prefix?.second.orEmpty() + normalized.substring(prefix?.first?.length ?: 0).codePoints().toArray().map { code ->
                val char = String(Character.toChars(code))
                characters[char] ?: latin(char)
            }
        }
        return Spelling(parts.joinToString(""), parts.joinToString("") { it.take(1) })
    }
    companion object {
        private fun normalizeName(name: String) = name.trim().replace('(', '（').replace(')', '）').replace(" ", "")
        private fun latin(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        fun parse(raw: String): SchoolNameIndex {
            val root = JSONObject(raw)
            fun words(json: JSONObject): Map<String, List<String>> = json.keys().asSequence().associate { name ->
                val array = json.getJSONArray(name)
                normalizeName(name) to (0 until array.length()).map { array.getString(it) }
            }
            val chars = root.getJSONObject("characters")
            return SchoolNameIndex(words(root.getJSONObject("names")), chars.keys().asSequence().associateWith { chars.getString(it) },
                words(root.getJSONObject("prefixes")).toList().sortedByDescending { it.first.length })
        }
        val empty = SchoolNameIndex(emptyMap(), emptyMap(), emptyList())
    }
}

object SchoolDirectory {
    val alphabet = ('A'..'Z').map(Char::toString) + "#"
    data class Entry(val school: School, val spelling: SchoolNameIndex.Spelling)
    sealed interface Row {
        data class Header(val letter: String) : Row
        data class Item(val entry: Entry) : Row
    }
    data class Listing(val rows: List<Row>, val positions: Map<String, Int>)
    fun sorted(schools: List<School>, index: SchoolNameIndex): List<Entry> = schools.map { Entry(it, index.spelling(it.name)) }
        .sortedWith(compareBy<Entry> { alphabet.indexOf(it.spelling.letter) }.thenBy { it.spelling.full }.thenBy { it.school.name }.thenBy { it.school.id })
    fun filter(entries: List<Entry>, query: String): Listing {
        val q = query.trim().lowercase(Locale.ROOT)
        val roman = q.filter { !it.isWhitespace() }
        val matched = entries.filter { entry -> q.isBlank() || entry.school.name.contains(q, true) || entry.school.province.contains(q, true) ||
            entry.school.id.contains(q, true) || entry.spelling.full.contains(roman) || entry.spelling.initials.contains(roman) }
        val rows = mutableListOf<Row>()
        val positions = linkedMapOf<String, Int>()
        matched.forEach { entry ->
            val letter = entry.spelling.letter
            if (letter !in positions) { positions[letter] = rows.size; rows += Row.Header(letter) }
            rows += Row.Item(entry)
        }
        return Listing(rows, positions)
    }
}
