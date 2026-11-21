package com.kstudio.agenda.i18n

object CourseTimeText {
    private fun text(zh: String, en: String, fr: String) = when (AppText.resolve(AppText.state.value)) {
        AppLang.ZH -> zh
        AppLang.FR -> fr
        else -> en
    }
    val periods get() = text("按节次时间", "Period times", "Horaires des périodes")
    val custom get() = text("自定义时间", "Custom times", "Horaires personnalisés")
    val start get() = text("开始", "Start", "Début")
    val end get() = text("结束", "End", "Fin")
    val hint get() = text("显示和提醒使用自定义时间；节次用于节次课表中的位置。", "Display and reminders use these times. Periods determine placement in the period grid.", "L’affichage et les rappels utilisent ces horaires. Les périodes déterminent la position dans la grille.")
    val invalid get() = text("结束时间必须晚于开始时间（同一天内）", "End must be later than start on the same day", "La fin doit suivre le début le même jour")
}
