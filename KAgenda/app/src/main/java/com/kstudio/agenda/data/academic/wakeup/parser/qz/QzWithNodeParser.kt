// Derived from WakeUp 3.612, YZune; Apache-2.0.
// https://github.com/Matey-ace/wakeup-course-schedule/blob/43631a54cb70ceb2d983a4d646022d84352c9d85/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/qz/QzWithNodeParser.kt
// KAgenda changes: namespace, null checks, strict week/section checks; Android storage removed.
package com.kstudio.agenda.data.academic.wakeup.parser.qz

import com.kstudio.agenda.data.academic.wakeup.bean.Course
import org.jsoup.Jsoup

class QzWithNodeParser(source: String) : QzParser(source) {

    override fun convert(day: Int, nodeCount: Int, infoStr: String, courseList: MutableList<Course>) {
        val courseHtml = Jsoup.parse(infoStr)
        val courseName = infoStr.substringBefore("<br>").trim()
        val teacher = courseHtml.getElementsByAttributeValue("title", "老师").text().trim()
        val room = courseHtml.getElementsByAttributeValue(
                "title",
                "教室"
        ).text().trim() + courseHtml.getElementsByAttributeValue("title", "分组").text().trim()
        val tempStr = courseHtml.getElementsByAttributeValue("title", "周次(节次)").text()
        val weekStr = when {
            tempStr.contains(' ') -> courseHtml.getElementsByAttributeValue("title", "周次(节次)").text().split(' ')[0]
            tempStr.isBlank() -> courseHtml.getElementsByAttributeValue("title", "周次").text()
            else -> courseHtml.getElementsByAttributeValue("title", "周次(节次)").text().substringBefore(')')
        }
        val nodeList = when {
            tempStr.contains(' ') -> courseHtml.getElementsByAttributeValue(
                    "title",
                    "周次(节次)"
            ).text().split(' ')[1].removeSurrounding("[", "]").split('-')
            tempStr.isBlank() -> courseHtml.getElementsByAttributeValue(
                    "title",
                    "节次"
            ).text().substringAfter(')').removeSurrounding("[", "]").split('-')
            else -> courseHtml.getElementsByAttributeValue(
                    "title",
                    "周次(节次)"
            ).text().substringAfter(')').removeSurrounding("[", "]").split('-')
        }
        val weekList = weekStr.split(',')
        var startWeek = 0
        var endWeek = 0
        var type = 0
        weekList.forEach {
            type = 0
            if (it.contains('-')) {
                val weeks = it.split('-')
                if (weeks.isNotEmpty()) {
                    startWeek = weeks[0].toInt()
                }
                if (weeks.size > 1) {
                    type = when {
                        weeks[1].contains('单') -> 1
                        weeks[1].contains('双') -> 2
                        else -> 0
                    }
                    endWeek = weeks[1].substringBefore('(').toInt()
                }
            } else {
                startWeek = it.substringBefore('(').toInt()
                endWeek = it.substringBefore('(').toInt()
            }
            courseList.add(
                    Course(
                            name = courseName, teacher = teacher,
                            room = room, day = day,
                            startNode = nodeList.first()!!.substringBefore('节').toInt(),
                            endNode = nodeList.last()!!.substringBefore('节').toInt(),
                            startWeek = startWeek, endWeek = endWeek,
                            type = type
                    )
            )
        }
    }

}