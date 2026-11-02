// Derived from WakeUp 3.612, YZune; Apache-2.0.
// https://github.com/Matey-ace/wakeup-course-schedule/blob/43631a54cb70ceb2d983a4d646022d84352c9d85/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/bean/ChengFangInfo.kt
// KAgenda changes: namespace, null checks, strict week/section checks; Android storage removed.
package com.kstudio.agenda.data.academic.wakeup.bean

data class ChengFangInfo(
        val kcmc: String,
        val jcdm2: String,
        val zcs: String,
        val xq: String,
        val jxcdmcs: String,
        val teaxms: String
)