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
    data class Listing(val rows: List<Row>, val positions: Map<String, Int>, val searching: Boolean = false)
    // Administrative order groups equal search matches without restoring a pinyin sort.
    private val provinces = listOf("北京市", "天津市", "河北省", "山西省", "内蒙古自治区", "辽宁省", "吉林省", "黑龙江省",
        "上海市", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省", "湖北省", "湖南省",
        "广东省", "广西壮族自治区", "海南省", "重庆市", "四川省", "贵州省", "云南省", "西藏自治区",
        "陕西省", "甘肃省", "青海省", "宁夏回族自治区", "新疆维吾尔自治区")
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    private fun orderedMatch(name: String, query: String): Boolean {
        var next = 0
        for (char in name) if (next < query.length && char == query[next]) next++
        return next == query.length
    }
    private data class Match(val entry: Entry, val tier: Int, val overlap: Double)
    private fun match(entry: Entry, query: String): Match? {
        val name = normalized(entry.school.name)
        val han = query.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val frequencies = name.groupingBy { it }.eachCount()
        val overlap = query.groupingBy { it }.eachCount().entries.sumOf { (char, count) -> minOf(count, frequencies[char] ?: 0) }
        val tier = when {
            name == query || entry.spelling.full == query -> 0
            name.startsWith(query) -> 1
            name.contains(query) -> 2
            han && query.length > 1 && orderedMatch(name, query) -> 3
            entry.spelling.initials == query -> 3
            entry.spelling.full.contains(query) || entry.spelling.initials.contains(query) -> 4
            han && query.length > 2 && overlap >= 2 && overlap.toDouble() / query.length >= .6 -> 5
            normalized(entry.school.province).contains(query) -> 6
            normalized(entry.school.id).contains(query) -> 7
            else -> return null
        }
        return Match(entry, tier, 2.0 * overlap / (name.length + query.length).coerceAtLeast(1))
    }
    fun sorted(schools: List<School>, index: SchoolNameIndex): List<Entry> = schools.map { Entry(it, index.spelling(it.name)) }
        .sortedWith(compareBy<Entry> { alphabet.indexOf(it.spelling.letter) }.thenBy { it.spelling.full }.thenBy { it.school.name }.thenBy { it.school.id })
    fun filter(entries: List<Entry>, query: String): Listing {
        val q = normalized(query)
        if (q.isNotEmpty()) {
            val matched = entries.mapNotNull { match(it, q) }.sortedWith(compareBy<Match> { it.tier }
                .thenByDescending { it.overlap }
                .thenBy { provinces.indexOf(it.entry.school.province).takeIf { i -> i >= 0 } ?: provinces.size }
                .thenBy { it.entry.school.province }.thenBy { it.entry.school.name }.thenBy { it.entry.school.id })
            return Listing(matched.map { Row.Item(it.entry) }, emptyMap(), searching = true)
        }
        val rows = mutableListOf<Row>()
        val positions = linkedMapOf<String, Int>()
        entries.forEach { entry ->
            val letter = entry.spelling.letter
            if (letter !in positions) { positions[letter] = rows.size; rows += Row.Header(letter) }
            rows += Row.Item(entry)
        }
        return Listing(rows, positions)
    }
}
