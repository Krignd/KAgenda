// Derived from WakeUp 3.612, YZune; Apache-2.0.
// https://github.com/Matey-ace/wakeup-course-schedule/blob/43631a54cb70ceb2d983a4d646022d84352c9d85/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/ChengFangParser.kt
// KAgenda changes: namespace, null checks, strict week/section checks; Android storage removed.
package com.kstudio.agenda.data.academic.wakeup.parser

import com.google.gson.Gson
import com.kstudio.agenda.data.academic.wakeup.Common
import com.kstudio.agenda.data.academic.wakeup.bean.ChengFangInfo
import com.kstudio.agenda.data.academic.wakeup.bean.Course

class ChengFangParser(source: String) : Parser(source) {

    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()
        val json = source.substringAfter("var kbxx = ").substringBefore(';')
        val gson = Gson()
        val weekList = arrayListOf<Int>()
        gson.fromJson(json, Array<ChengFangInfo>::class.java).forEach {
            weekList.clear()
            it.zcs.split(',').forEach { str ->
                weekList.add(str.toInt())
            }
            weekList.sort()
            val day = it.xq.toInt()
            val startNode = it.jcdm2.split(',')[0].toInt()
            val endNode =
                    if (it.jcdm2.contains(',')) it.jcdm2.split(',').last()!!.toInt() else it.jcdm2.split(',')[0].toInt()
            val step = endNode - startNode + 1
            Common.weekIntList2WeekBeanList(weekList).forEach { weekBean ->
                courseList.add(
                        Course(
                                name = it.kcmc, room = it.jxcdmcs,
                                teacher = it.teaxms, day = day,
                                startWeek = weekBean.start, endWeek = weekBean.end,
                                type = weekBean.type, startNode = startNode,
                                endNode = startNode + step - 1
                        )
                )
            }
        }
        return courseList
    }

}