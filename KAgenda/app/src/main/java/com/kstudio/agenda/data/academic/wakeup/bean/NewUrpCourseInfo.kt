// Derived from WakeUp 3.612, YZune; Apache-2.0.
// https://github.com/Matey-ace/wakeup-course-schedule/blob/43631a54cb70ceb2d983a4d646022d84352c9d85/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/bean/NewUrpCourseInfo.kt
// KAgenda changes: namespace, null checks, strict week/section checks; Android storage removed.
package com.kstudio.agenda.data.academic.wakeup.bean

data class NewUrpCourseInfo(
        val attendClassTeacher: String,
        val courseCategoryCode: String,
        val courseCategoryName: String,
        val courseName: String,
        val coursePropertiesCode: String,
        val coursePropertiesName: String,
        val dgFlag: String,
        val examTypeCode: String,
        val examTypeName: String,
        val flag: Any,
        val fsktms: Any,
        val id: Id,
        val programPlanName: String,
        val programPlanNumber: String,
        val restrictedCondition: String,
        val rlFlag: String,
        val selectCourseStatusCode: String,
        val selectCourseStatusName: String,
        val sfczfskt: Any,
        val studyModeCode: String,
        val studyModeName: String,
        val timeAndPlaceList: List<TimeAndPlace>?,
        val unit: Double,
        val xkzy: Any,
        val ywdgFlag: Any,
        val zkxh: Any
) {
    data class Id(
            val coureNumber: String,
            val coureSequenceNumber: String,
            val executiveEducationPlanNumber: String,
            val studentNumber: String
    )

    data class TimeAndPlace(
            val campusName: String?,
            val classDay: Int,
            val classSessions: Int,
            val classWeek: String,
            val classroomName: String,
            val continuingSession: Int,
            val coureName: String,
            val coureNumber: String,
            val coureSequenceNumber: String,
            val coursePropertiesName: String,
            val courseTeacher: Any,
            val executiveEducationPlanNumber: String,
            val id: String,
            val kcm: Any,
            val sksj: Any,
            val studentNumber: String,
            val teachingBuildingName: String?,
            val time: Any,
            val weekDescription: String,
            val xf: Any
    )
}