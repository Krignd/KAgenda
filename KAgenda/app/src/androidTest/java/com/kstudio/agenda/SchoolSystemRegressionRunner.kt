package com.kstudio.agenda

import android.app.Instrumentation
import android.os.Bundle
import com.kstudio.agenda.data.SchoolCatalog
import com.kstudio.agenda.i18n.AcademicText
import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.model.SchoolDirectory
import com.kstudio.agenda.data.SettingsStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/** Public catalog and picker preference; emulator only, no school requests or course-data rewriting. */
class SchoolSystemRegressionRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        val previous = AppText.state.value
        try {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
            val rows = SchoolCatalog.load(targetContext, Schools.ALL)
            val wmu = rows.single { it.name == "温州医科大学" }
            AppText.state.value = AppLang.ZH
            check(AcademicText.schoolSupportLabel(wmu) == "新正方")
            check(SchoolCatalog.adapters(wmu).first().parser == "zf_new")
            check(AcademicText.schoolSupportLabel(rows.single { it.id == "buaa" }) == "独立适配")
            check(AcademicText.schoolSupportLabel(rows.single { it.name == "南京师范大学" }) == "金智")
            val jiangxia = rows.single { it.name == "福建江夏学院" }
            check("zf_new" in SchoolCatalog.knownSystemTypes(jiangxia))
            check(AcademicText.schoolSupportLabel(jiangxia).contains("新正方"))
            check(!AcademicText.isUndetermined(jiangxia))
            val fzfu = rows.single { it.name == "福州外语外贸学院" }
            check(AcademicText.schoolSupportLabel(fzfu) == "新正方")
            check("zf_new" in SchoolCatalog.knownSystemTypes(fzfu))
            check(!AcademicText.isUndetermined(fzfu) && fzfu.supported)
            check(SchoolCatalog.adapters(fzfu).any { it.id == "ext_fzfu_personal" })
            val smu = rows.single { it.name == "三明学院" }
            check(AcademicText.schoolSupportLabel(smu) == "新正方" && smu.supported)
            check(SchoolCatalog.adapters(smu).any { it.id == "ext_smu_personal" })
            check(AcademicText.schoolSupportLabel(rows.first { !it.supported && it.homeUrl.isBlank() }) == "未确定")
            val entries = SchoolDirectory.sorted(rows, SchoolCatalog.nameIndex(targetContext))
            val search = SchoolDirectory.filter(entries, "北航")
            check(search.positions.isEmpty() && search.rows.all { it is SchoolDirectory.Row.Item })
            check((search.rows.first() as SchoolDirectory.Row.Item).entry.school.id == "buaa")
            check(!AcademicText.isUndetermined(wmu))
            val visible = rows.filterNot(AcademicText::isUndetermined)
            check(visible.any { it.id == "buaa" } && visible.any { it.id == wmu.id })
            check(visible.none { AcademicText.schoolSupportLabel(it) == AcademicText.unknownSystem })
            runBlocking {
                val previousFilter = SettingsStore.read(targetContext).hideUndeterminedSchools
                try {
                    SettingsStore.setHideUndeterminedSchools(targetContext, true)
                    check(SettingsStore.read(targetContext).hideUndeterminedSchools)
                    check(SettingsStore.exportPreferences(targetContext)["hide_undetermined_schools"] == true)
                    SettingsStore.importPreferences(targetContext, mapOf("hide_undetermined_schools" to false))
                    check(!SettingsStore.read(targetContext).hideUndeterminedSchools)
                } finally { SettingsStore.setHideUndeterminedSchools(targetContext, previousFilter) }
            }
            val hints = JSONArray(targetContext.assets.open("academic/wakeup-schools.json").bufferedReader().use { it.readText() })
            var checked = 0
            for (i in 0 until hints.length()) {
                val hint = hints.getJSONObject(i)
                if (AcademicText.systemName(hint.getString("type")) == null) continue
                val school = rows.singleOrNull { it.name == hint.getString("name") } ?: continue
                check(hint.getString("type") in SchoolCatalog.knownSystemTypes(school)) { school.name }
                check(AcademicText.schoolSupportLabel(school) != "未确定") { school.name }
                checked++
            }
            result.putString("stream", "PASS: $checked school-bound system records; ranked BUAA search, unknown filter, preference and backup roundtrip. Preference restored; no school requests or course-data writes.")
            finish(android.app.Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}"); finish(android.app.Activity.RESULT_CANCELED, result)
        } finally { AppText.state.value = previous }
    }
}
