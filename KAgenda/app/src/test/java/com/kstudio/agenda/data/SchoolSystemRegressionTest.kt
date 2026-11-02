package com.kstudio.agenda.data

import com.kstudio.agenda.i18n.AcademicText
import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.Schools
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SchoolSystemRegressionTest {
    private val assets = File(requireNotNull(System.getProperty("kagenda.assets")))
    private fun load(extraRaw: String = File(assets, "academic/school-adapters.json").readText(), indexRaw: String = File(assets, "shiguang/index.json").readText()) = SchoolCatalog.parseSources(
        indexRaw, File(assets, "shiguang/general.json").readText(),
        File(assets, "academic/wakeup-schools.json").readText(), File(assets, "catalog/schools.json").readText(),
        File(assets, "catalog/logos.json").readText(), Schools.ALL, extraRaw,
        File(assets, "academic/school-systems.json").readText())

    @Test fun `school directory types survive without dedicated scripts and do not imply verification`() {
        val previous = AppText.state.value
        try {
            AppText.state.value = AppLang.ZH
            val rows = load()
            val hints = JSONArray(File(assets, "academic/wakeup-schools.json").readText())
            for (i in 0 until hints.length()) {
                val hint = hints.getJSONObject(i)
                if (AcademicText.systemName(hint.getString("type")) == null) continue
                val school = rows.singleOrNull { it.name == hint.getString("name") } ?: continue
                assertTrue(school.name, hint.getString("type") in SchoolCatalog.knownSystemTypes(school))
                assertNotEquals(school.name, "未确定", AcademicText.schoolSupportLabel(school))
            }
            val wmu = rows.single { it.name == "温州医科大学" }
            assertEquals(listOf("zf_new"), SchoolCatalog.knownSystemTypes(wmu))
            assertEquals("新正方", AcademicText.schoolSupportLabel(wmu))
            assertFalse(AcademicText.isUndetermined(wmu))
            assertFalse(wmu.supported) // Type evidence is independent of a dedicated/validated importer.
            assertEquals("zf_new", SchoolCatalog.adapters(wmu).first().parser)
            assertTrue(AcademicText.adapterName(SchoolCatalog.adapters(wmu).first()).startsWith("新正方"))
            assertEquals("金智", AcademicText.schoolSupportLabel(rows.single { it.name == "南京师范大学" }))
            assertTrue("urp" in SchoolCatalog.knownSystemTypes(rows.single { it.name == "天津城建大学" }))
            assertTrue("eams" in SchoolCatalog.knownSystemTypes(rows.single { it.name == "电子科技大学" }))
            assertEquals("独立适配", AcademicText.schoolSupportLabel(rows.single { it.id == "buaa" }))
            val fzfu = rows.single { it.name == "福州外语外贸学院" }
            assertEquals("新正方", AcademicText.schoolSupportLabel(fzfu))
            assertEquals(listOf("zf_new"), SchoolCatalog.knownSystemTypes(fzfu))
            assertFalse(AcademicText.isUndetermined(fzfu))
            assertTrue(fzfu.supported)
            assertTrue(SchoolCatalog.adapters(fzfu).any { it.id == "ext_fzfu_personal" })
            val smu = rows.single { it.name == "三明学院" }
            assertEquals("新正方", AcademicText.schoolSupportLabel(smu))
            assertTrue(smu.supported && SchoolCatalog.adapters(smu).any { it.id == "ext_smu_personal" })
            assertEquals("ext_smu_personal", SchoolCatalog.adapters(smu).first().id)
            assertTrue(SchoolCatalog.adapters(smu).any { it.id == "SMXY" })
            val fit = rows.single { it.name == "福州理工学院" }
            assertEquals("正方", AcademicText.schoolSupportLabel(fit))
            assertFalse(AcademicText.isUndetermined(fit))
            assertTrue(fit.supported)
            assertEquals("ext_fit_personal", SchoolCatalog.adapters(fit).first().id)
            assertTrue(SchoolCatalog.adapters(fit).any { it.id == "FIT" })
            val lmu = rows.single { it.name == "黎明职业大学" }
            assertEquals("超星", AcademicText.schoolSupportLabel(lmu))
            assertFalse(AcademicText.isUndetermined(lmu))
            assertTrue(lmu.supported)
            assertEquals("ext_lmu_personal", SchoolCatalog.adapters(lmu).first().id)
            val mku = rows.single { it.name == "闽南科技学院" }
            assertTrue("qz" in SchoolCatalog.knownSystemTypes(mku))
            assertFalse(AcademicText.isUndetermined(mku))
            assertEquals("ext_mku_personal", SchoolCatalog.adapters(mku).first().id)
            assertTrue(SchoolCatalog.adapters(mku).any { it.id == "MKU_01" })
            val ndnu = rows.single { it.name == "宁德师范学院" }
            assertEquals("正方", AcademicText.schoolSupportLabel(ndnu))
            assertFalse(AcademicText.isUndetermined(ndnu))
            assertEquals("ext_ndnu_personal", SchoolCatalog.adapters(ndnu).first().id)
            assertTrue(ndnu.supported)
            val vendorOnlyRows = load(extraRaw = "[]", indexRaw = "[]")
            assertFalse(vendorOnlyRows.single { it.name == "福州外语外贸学院" }.supported)
            assertFalse(vendorOnlyRows.single { it.name == "三明学院" }.supported)
            load() // Restore the full catalog after the metadata-only fixture.
            assertEquals("独立适配", AcademicText.schoolSupportLabel(rows.single { it.name == "江苏大学" }))
            val unknown = rows.first { !it.supported && it.homeUrl.isBlank() }
            assertTrue(SchoolCatalog.knownSystemTypes(unknown).isEmpty())
            assertEquals("未确定", AcademicText.schoolSupportLabel(unknown))
            assertTrue(AcademicText.isUndetermined(unknown))
            assertTrue(SchoolCatalog.adapters(unknown).any { it.parser == "zf_new" }) // Global options are not evidence.
            assertTrue(SchoolCatalog.knownSystemTypes(wmu.copy(name = "温州医科大学仁济学院")).isEmpty())
            AppText.state.value = AppLang.EN
            assertEquals("New Zhengfang", AcademicText.schoolSupportLabel(wmu))
            AppText.state.value = AppLang.FR
            assertEquals("Nouveau Zhengfang", AcademicText.schoolSupportLabel(wmu))
            for (language in listOf(AppLang.ZH, AppLang.EN, AppLang.FR)) {
                AppText.state.value = language
                for (school in rows) assertEquals(school.name,
                    AcademicText.schoolSupportLabel(school) == AcademicText.unknownSystem,
                    AcademicText.isUndetermined(school))
            }
            assertTrue(BackupSettings.validate("hide_undetermined_schools", true))
            assertFalse(BackupSettings.validate("hide_undetermined_schools", "true"))
        } finally { AppText.state.value = previous }
    }

}
