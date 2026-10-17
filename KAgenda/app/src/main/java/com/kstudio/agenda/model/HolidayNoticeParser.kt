package com.kstudio.agenda.model

import java.time.LocalDate

/** Parser for the numbered holiday paragraphs in State Council notices. No guessed dates. */
object HolidayNoticeParser {
    data class Notice(val year: Int, val holidays: Map<Long, String>, val workdays: Set<Long>)
    fun parse(html: String, year: Int): Notice {
        require(year in 2000..2100)
        val text = html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
        require(Regex("${year}年\\s*部分节假日安排").containsMatchIn(text))
        val labels = Regex("(元旦|春节|清明节|劳动节|端午节|中秋节[、和]国庆节|中秋节|国庆节)[：:]").findAll(text).toList()
        require(labels.size in 6..7)
        require(listOf("元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节").all { name -> labels.any { it.groupValues[1].contains(name) } })
        val holidays = linkedMapOf<Long, String>()
        val workdays = linkedSetOf<Long>()
        val date = Regex("(\\d{1,2})月(\\d{1,2})日")
        val range = Regex("(\\d{1,2})月(\\d{1,2})日[^至。]*至(?:(\\d{1,2})月)?(\\d{1,2})日")
        labels.forEachIndexed { index, label ->
            val section = text.substring(label.range.last + 1, labels.getOrNull(index + 1)?.range?.first ?: text.length)
            val vacation = section.substringBefore("放假")
            require(section.contains("放假"))
            val r = range.find(vacation)
            val first = date.find(vacation) ?: error("Missing holiday date")
            val start = LocalDate.of(year, first.groupValues[1].toInt(), first.groupValues[2].toInt())
            val end = if (r == null) start else LocalDate.of(year,
                r.groupValues[3].toIntOrNull() ?: start.monthValue, r.groupValues[4].toInt())
            require(!end.isBefore(start) && end.toEpochDay() - start.toEpochDay() <= 14)
            var d = start
            while (!d.isAfter(end)) {
                holidays[d.toEpochDay()] = label.groupValues[1]
                d = d.plusDays(1)
            }
            section.split('。').filter { it.contains("上班") }.forEach { sentence ->
                date.findAll(sentence.substringAfterLast("天", sentence)).forEach { match ->
                    workdays += LocalDate.of(year, match.groupValues[1].toInt(), match.groupValues[2].toInt()).toEpochDay()
                }
            }
        }
        require(holidays.size in 20..50 && workdays.size <= 12 && workdays.none { it in holidays })
        return Notice(year, holidays, workdays)
    }
}
