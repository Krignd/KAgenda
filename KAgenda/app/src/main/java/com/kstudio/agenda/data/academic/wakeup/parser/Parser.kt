package com.kstudio.agenda.data.academic.wakeup.parser

import com.kstudio.agenda.data.academic.wakeup.bean.Course

/** KAgenda host: parsing only, no upstream Android storage or network. */
abstract class Parser(val source: String) {
    abstract fun generateCourseList(): List<Course>
}
