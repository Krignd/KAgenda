package com.kstudio.agenda.i18n

object CourseScopeText {
    private fun text(zh: String, en: String, fr: String) = when (AppText.resolve(AppText.state.value)) {
        AppLang.ZH -> zh
        AppLang.FR -> fr
        else -> en
    }
    val only get() = text("仅修改当前周这一次", "Only this occurrence in the selected week", "Seulement cette occurrence de la semaine sélectionnée")
    val unknownWeek get() = text("无法确定当前教学周，请先确认学期起始日期", "Cannot determine the teaching week. Check the semester start date.", "Semaine indéterminée. Vérifiez la date de début du semestre.")
    fun week(n: Int) = text("仅影响第 $n 周，其他周保持原样", "Only week $n is affected; other weeks stay unchanged", "Seule la semaine $n est modifiée ; les autres restent inchangées")
}
