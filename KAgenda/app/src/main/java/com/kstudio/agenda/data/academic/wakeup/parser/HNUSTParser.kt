// Derived from WakeUp 3.612, YZune; Apache-2.0.
// https://github.com/Matey-ace/wakeup-course-schedule/blob/43631a54cb70ceb2d983a4d646022d84352c9d85/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/HNUSTParser.kt
// KAgenda changes: namespace, null checks, strict week/section checks; Android storage removed.
package com.kstudio.agenda.data.academic.wakeup.parser

import com.kstudio.agenda.data.academic.wakeup.Common
import com.kstudio.agenda.data.academic.wakeup.bean.Course
import org.jsoup.Jsoup

// 东北石油大学
// 湖南科技大学
// 湖南科技大学潇湘学院
class HNUSTParser(source: String, private val oldQzType: Int) : Parser(source) {

    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()
        val doc = Jsoup.parse(source)
        val kbtable = doc.getElementById("kbtable")!!
        val trs = kbtable.getElementsByTag("tr")

        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) {
                continue
            }

            var day = -1

            for (td in tds) {
                day++
                val divs = td.getElementsByTag("div")
                for (div in divs) {
                    if (oldQzType == 0) {
                        if (div.attr("style") != "display: none;" || div.text().isBlank()) continue
                    } else {
                        if (div.attr("style") == "display: none;" || div.text().isBlank()) continue
                    }
                    val split = div.html().split("<br>")
                    var preIndex = -1

                    fun toCourse() {
                        if (preIndex == -1) return
                        val courseName = Jsoup.parse(split[0]).text().trim()
                        val room = Jsoup.parse(split[preIndex + 1]).text().trim()
                        val teacher = Jsoup.parse(split[preIndex - 1]).text().trim()

                        val timeInfo = Jsoup.parse(split[preIndex]).text().trim().split(",")
                        timeInfo.forEach {
                            val weekStr = it.trim().substringBefore('周')
                            val startWeek =
                                    if (weekStr.contains('-')) weekStr.split('-')[0].toInt() else weekStr.toInt()
                            val endWeek = if (weekStr.contains('-')) weekStr.split('-')[1].toInt() else weekStr.toInt()
                            val startNode = div.attr("id").split('-')[0].toInt() * 2 - 1
                            courseList.add(
                                    Course(
                                            name = courseName, teacher = teacher,
                                            room = room, day = day,
                                            startNode = startNode, endNode = startNode + 1,
                                            startWeek = startWeek, endWeek = endWeek,
                                            type = 0
                                    )
                            )
                        }
                    }

                    for (i in split.indices) {
                        if (Common.weekPattern2.containsMatchIn(split[i])) {
                            if (preIndex != -1) {
                                toCourse()
                            }
                            preIndex = i
                        }
                        if (i == split.size - 1) {
                            toCourse()
                        }
                    }
                }
            }
        }
        return courseList
    }

}