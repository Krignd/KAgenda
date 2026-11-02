// Derived from WakeUp 3.612, YZune; Apache-2.0.
// https://github.com/Matey-ace/wakeup-course-schedule/blob/43631a54cb70ceb2d983a4d646022d84352c9d85/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/BNUZParser.kt
// KAgenda changes: namespace, null checks, strict week/section checks; Android storage removed.
package com.kstudio.agenda.data.academic.wakeup.parser

import com.kstudio.agenda.data.academic.wakeup.Common
import com.kstudio.agenda.data.academic.wakeup.bean.Course
import org.jsoup.Jsoup
import java.util.regex.Pattern

// 北京师范大学珠海分校
class BNUZParser(source: String) : Parser(source) {

    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()

        val nodePattern = "\\d+"
        val weekPattern1 = Pattern.compile("(\\d+)-(\\d+)")
        val weekPattern2 = Pattern.compile("(\\d+)")

        val doc = Jsoup.parse(source)

        val table1 = doc.getElementById("table1")!!
        val trs = table1.getElementsByTag("tr")

        var node = 0
        var teacher = ""
        var room = ""
        var step = 1
        var startWeek = 0
        var endWeek = 0
        var type = 0
        for (tr in trs) {
            var countFlag = false
            var countDay = 1
            val tds = tr.getElementsByTag("td")
            for (td in tds) {
                val courseValue = td.text().trim()
                if (Common.otherHeader.contains(courseValue)) {
                    //other data
                    continue
                }
                if (courseValue.isEmpty()) {
                    if (countFlag) {
                        countDay++
                    }
                    continue
                }
                if (Pattern.matches(nodePattern, courseValue)) {
                    node = courseValue.toInt()
                    countFlag = true
                    continue
                }

                val infos = td.html().substringAfter("</span>").substringBeforeLast("<br>").split("<br>")

                val courseName = infos[0]

                for (i in 1 until infos.size step 2) {
                    if (i + 1 >= infos.size) continue
                    if (!infos[i].contains('{') || !infos[i].contains('}')) continue
                    teacher = infos[i].substringBefore('{')
                    room = infos[i + 1]
                    step = room.substringAfterLast('(').substringBeforeLast('节').toInt()
                    val weekList = infos[i].substringAfter('{').substringBefore('}').split(',')
                    weekList.forEach {
                        type = 0
                        if (it.contains('-')) {
                            val matcher = weekPattern1.matcher(it)
                            matcher.find()
                            startWeek = requireNotNull(matcher.group(1)).toInt()
                            endWeek = requireNotNull(matcher.group(2)).toInt()

                            type = when {
                                it.contains('单') -> 1
                                it.contains('双') -> 2
                                else -> 0
                            }
                        } else {
                            val matcher = weekPattern2.matcher(it)
                            matcher.find()
                            startWeek = requireNotNull(matcher.group(1)).toInt()
                            endWeek = startWeek
                        }

                        courseList.add(
                                Course(
                                        name = courseName, room = room,
                                        teacher = teacher, day = countDay,
                                        startNode = node, endNode = node + step - 1,
                                        startWeek = startWeek, endWeek = endWeek,
                                        type = type
                                )
                        )
                    }
                }
                countDay++
            }
        }
        return courseList
    }

}