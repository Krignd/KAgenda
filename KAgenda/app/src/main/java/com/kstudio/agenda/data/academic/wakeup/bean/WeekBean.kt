// Derived from WakeUp 3.612, YZune; Apache-2.0.
// https://github.com/Matey-ace/wakeup-course-schedule/blob/43631a54cb70ceb2d983a4d646022d84352c9d85/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/bean/WeekBean.kt
// KAgenda changes: namespace, null checks, strict week/section checks; Android storage removed.
package com.kstudio.agenda.data.academic.wakeup.bean

data class WeekBean(var start: Int, var end: Int, var type: Int) {
    override fun toString(): String {
        val typeString = when (type) {
            1 -> " 单周"
            2 -> " 双周"
            else -> ""
        }
        return "第$start - ${end}周$typeString"
    }
}