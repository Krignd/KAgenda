package com.kstudio.agenda.data

import com.kstudio.agenda.model.School
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AcademicCatalogTest {
    private val assets = File(requireNotNull(System.getProperty("kagenda.assets")))
    @Test fun `whole catalog has optional general import without claiming verification`() {
        fun read(path: String) = File(assets, path).readText()
        val own = School("buaa", "北京航空航天大学", "https://own.invalid", "https://own.invalid", "", true)
        val before = SchoolCatalog.parseSources(read("shiguang/index.json"), read("shiguang/general.json"),
            read("academic/wakeup-schools.json"), read("catalog/schools.json"), read("catalog/logos.json"), listOf(own), read("academic/school-adapters.json"))
            .associateBy { it.name }
        val beforePorts = listOf("安徽大学","安庆师范大学","安徽工业大学","阜阳师范大学","淮北理工学院","淮北师范大学","安徽师范大学")
            .associateWith { SchoolCatalog.adapters(before.getValue(it)).map { adapter -> adapter.id } }
        val rows = SchoolCatalog.parseSources(read("shiguang/index.json"), read("shiguang/general.json"),
            read("academic/wakeup-schools.json"), read("catalog/schools.json"), read("catalog/logos.json"), listOf(own), read("academic/school-adapters.json"), read("academic/school-systems.json"))
        assertTrue(rows.size >= 3196)
        for ((name, type) in listOf("安徽大学" to "eams", "安庆师范大学" to "eams",
            "安徽工业大学" to "cf", "阜阳师范大学" to "cf", "淮北理工学院" to "qz", "淮北师范大学" to "zf_new", "安徽师范大学" to "eams")) {
            val school=rows.single { it.name==name }
            assertTrue(SchoolCatalog.knownSystemTypes(school).contains(type))
            assertEquals(before.getValue(name).supported,school.supported)
            assertEquals(before.getValue(name).warehouseId,school.warehouseId)
            assertEquals(before.getValue(name).homeUrl,school.homeUrl)
            assertEquals(beforePorts.getValue(name),SchoolCatalog.adapters(school).map { it.id })
        }
        assertTrue(SchoolCatalog.knownSystemTypes(rows.single {it.name=="安徽大学"}).contains("zf"))
        assertFalse(SchoolCatalog.knownSystemTypes(rows.single {it.name=="淮北师范大学"}).contains("qz"))
        for (name in listOf("南京航空航天大学","东北大学秦皇岛分校","天津农学院","合肥师范学院","河南科技大学","上海师范大学")) {
            assertTrue(SchoolCatalog.knownSystemTypes(rows.single {it.name==name}).contains("eams"))
        }
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
        val qust=rows.single { it.name=="青岛科技大学" }
        assertTrue(SchoolCatalog.knownSystemTypes(qust).contains("zf_new"))
        assertEquals("ext_qust_personal",SchoolCatalog.adapters(qust).first().id)
        assertFalse(rows.filter { it.name!="青岛科技大学" }.any { school -> SchoolCatalog.adapters(school).any { it.id=="ext_qust_personal" } })
        val sdut=rows.single { it.name=="山东理工大学" }
        val qlu=rows.single { it.name=="齐鲁工业大学" }
        val sdust=rows.single { it.name=="山东科技大学" }
        assertTrue(SchoolCatalog.knownSystemTypes(sdust).contains("qz"))
        assertEquals("ext_sdust_qz_personal",SchoolCatalog.adapters(sdust).first().id)
        assertFalse(rows.filter { it.name!="山东科技大学" }.any { school -> SchoolCatalog.adapters(school).any { it.id=="ext_sdust_qz_personal" } })
        assertTrue(SchoolCatalog.knownSystemTypes(qlu).contains("zhengfang"))
        assertEquals("ext_qlu_personal",SchoolCatalog.adapters(qlu).first().id)
        assertFalse(rows.filter { it.name!="齐鲁工业大学" }.any { school -> SchoolCatalog.adapters(school).any { it.id=="ext_qlu_personal" } })
        assertTrue(SchoolCatalog.knownSystemTypes(sdut).contains("zf_new"))
        assertEquals("ext_sdut_personal",SchoolCatalog.adapters(sdut).first().id)
        assertFalse(rows.filter { it.name!="山东理工大学" }.any { school -> SchoolCatalog.adapters(school).any { it.id=="ext_sdut_personal" } })
        assertEquals(own.homeUrl, rows.first { it.id == own.id }.homeUrl)
        assertFalse(SchoolCatalog.usesBrowserImport(own))
        assertFalse(SchoolCatalog.usesBrowserImport(own.copy(custom = true, supported = false)))
        rows.filter { it.id != own.id }.forEach { school ->
            assertTrue(SchoolCatalog.usesBrowserImport(school))
            val options = SchoolCatalog.adapters(school)
            assertTrue(options.size >= 19)
            assertTrue(options.all { it.sourceUrl.startsWith("https://github.com/") && it.revision.length == 40 &&
                (it.license in setOf("MIT", "Apache-2.0") ||
                    (it.license == "LicenseRef-Dongfang-Permissive" && it.repository == "https://github.com/my832/dongfang-timetable")) })
            if (school.warehouseId.isBlank()) assertFalse(school.supported)
            else assertFalse(options.first().category.startsWith("GENERAL"))
        }
        val swfu = rows.first { it.name == "西南林业大学" }
        assertTrue(SchoolCatalog.adapters(swfu).first().updated.startsWith("2026-10-17"))
        for ((name, id) in listOf("温州大学" to "ext_wzu_api", "浙江工商大学" to "ext_zjgsu_web",
            "浙江农林大学暨阳学院" to "ext_zjyc", "浙江财经大学东方学院" to "ext_zufedfc_html",
            "浙江工业大学" to "ext_zjut_api", "中国计量大学" to "ext_cjlu_api",
            "上海财经大学浙江学院" to "ext_shufezj_api", "浙江万里学院" to "ext_zwu_api")) {
            assertTrue(SchoolCatalog.adapters(rows.single { it.name == name }).any { it.id == id })
        }
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "浙江农林大学" }).any { it.id == "ext_zjyc" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "浙江工业大学" }).any { it.id == "ZJUT_01" })
        val wanli = SchoolCatalog.adapters(rows.single { it.name == "浙江万里学院" })
        assertEquals("ext_zwu_api", wanli.first().id)
        assertTrue(wanli.any { it.id == "ZWU_01" && it.combo })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "苏州科技大学" }).any { it.id == "ext_usts_api" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "江苏科技大学" }).any { it.id == "ext_just_personal" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "江苏科技大学苏州理工学院" }).any { it.id == "ext_just_personal" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "无锡学院" }).any { it.id == "ext_wxxy_api" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "无锡太湖学院" }).any { it.id == "ext_wxxy_api" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "无锡职业技术大学" }).any { it.id == "ext_wxxy_api" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "中国矿业大学" }).any { it.id == "ext_cumt_api" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "南京中医药大学" }).any { it.id == "ext_njucm_dom" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "南京工业大学" }).any { it.id == "ext_njtech_api" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "南京信息工程大学" }).any { it.id == "ext_nuist_weekly" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "南京航空航天大学" }).any { it.id == "ext_nuaa_eams" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "南京理工大学" }).any { it.id == "ext_njust_personal" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "南京理工大学紫金学院" }).any { it.id == "ext_njust_personal" })
        val suda = SchoolCatalog.adapters(rows.single { it.name == "苏州大学" })
        assertTrue(suda.any { it.id == "ext_suda_personal" })
        assertTrue(suda.any { it.path == "SUDA/suda.js" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "苏州大学应用技术学院" }).any { it.id == "ext_suda_personal" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "苏州城市学院" }).any { it.id == "ext_suda_personal" })
        val tongji = SchoolCatalog.adapters(rows.single { it.name == "同济大学" })
        assertTrue(tongji.any { it.id == "ext_tongji_personal" && it.path == "EXT/tongji-personal.js" &&
            it.licenseAsset == "shanghai-tongji-MIT.txt" })
        assertTrue(tongji.any { it.id == "TONGJI_01" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "同济大学浙江学院" }).any { it.id == "ext_tongji_personal" })
        val shu = rows.single { it.name == "上海大学" }
        assertTrue(SchoolCatalog.adapters(shu).any { it.id == "ext_shu_personal" && it.name.contains("正方") &&
            it.url == "https://jwxt.shu.edu.cn/sso/shulogin" && it.licenseAsset == "shanghai-shu-MIT.txt" })
        assertTrue(SchoolCatalog.knownSystemTypes(shu).any { it.startsWith("zf") || it == "zhengfang" })
        assertFalse(rows.filter { it.name != "上海大学" }.any { school ->
            SchoolCatalog.adapters(school).any { it.id == "ext_shu_personal" } })
        val sjtu = SchoolCatalog.adapters(rows.single { it.name == "上海交通大学" })
        assertEquals("ext_sjtu_ug", sjtu.first().id)
        assertTrue(sjtu.any { it.id == "ext_sjtu_grad" && it.category == "POSTGRADUATE" })
        assertTrue(sjtu.first().category == "BACHELOR_AND_ASSOCIATE" && sjtu.first().licenseAsset == "shanghai-sjtu-undergraduate-MIT.txt")
        val ecnu = SchoolCatalog.adapters(rows.single { it.name == "华东师范大学" })
        assertTrue(ecnu.any { it.id == "ext_ecnu_dom" && it.path == "EXT/ecnu-personal.js" && it.licenseAsset == "shanghai-ecnu-MIT.txt" })
        val wuhu = rows.single { it.name == "芜湖学院" }
        assertTrue(SchoolCatalog.adapters(wuhu).any { it.id == "ext_wuhu_personal" &&
            it.name.contains("金智") && it.licenseAsset == "anhui-wuhu-original-MIT.txt" })
        assertTrue(SchoolCatalog.knownSystemTypes(wuhu).contains("jinzhi"))
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "安徽师范大学" }).any { it.id == "ext_wuhu_personal" })
        val hfut = rows.single { it.name == "合肥工业大学" }
        assertTrue(SchoolCatalog.adapters(hfut).any { it.id == "ext_hfut_personal" && it.name.contains("EAMS 5.0") &&
            it.category == "BACHELOR_AND_ASSOCIATE" && it.licenseAsset == "anhui-hfut-bundle.txt" })
        assertTrue(SchoolCatalog.knownSystemTypes(hfut).contains("eams"))
        assertFalse(rows.filter { it.name != "合肥工业大学" }.any { school ->
            SchoolCatalog.adapters(school).any { it.id == "ext_hfut_personal" } })
        assertFalse(rows.filter { it.name != "上海交通大学" }.any { school ->
            SchoolCatalog.adapters(school).any { it.id == "ext_sjtu_ug" } })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "南京航空航天大学金城学院" }).any { it.id == "ext_nuaa_eams" })
        val seu = SchoolCatalog.adapters(rows.single { it.name == "东南大学" })
        assertTrue(seu.any { it.id == "ext_seu_wdkb" })
        assertTrue(seu.any { it.id == "ext_seu" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "东南大学成贤学院" }).any { it.id == "ext_seu_wdkb" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "无锡学院" }).any { it.id == "ext_nuist_weekly" })
        assertTrue(SchoolCatalog.adapters(rows.single { it.name == "中国矿业大学徐海学院" }).any { it.id == "ext_cumtxh_api" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "中国矿业大学徐海学院" }).any { it.id == "ext_cumt_api" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "中国矿业大学" }).any { it.id == "ext_cumtxh_api" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "南京工业大学浦江学院" }).any { it.id == "ext_njtech_api" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "南京中医药大学翰林学院" }).any { it.id == "ext_njucm_dom" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "苏州科技大学天平学院" }).any { it.id == "ext_usts_api" })
        assertFalse(SchoolCatalog.adapters(rows.single { it.name == "中国矿业大学（北京）" }).any { it.id == "ext_cumt_api" })
        val expected = org.json.JSONArray(read("academic/school-adapters.json"))
        for (i in 0 until expected.length()) {
            val group = expected.getJSONObject(i)
            val school = rows.single { it.name == group.getString("name") }
            assertTrue(SchoolCatalog.hasAdditionalSource(school))
            val selected = SchoolCatalog.adapters(school)
            val additions = group.getJSONArray("adapters")
            for (j in 0 until additions.length()) {
                val entry = additions.getJSONObject(j)
                val port = selected.single { it.id == entry.getString("id") }
                assertEquals(entry.getString("repository"), port.repository)
                assertEquals(entry.getString("revision"), port.revision)
                assertTrue(File(assets, "licenses/${port.licenseAsset}").isFile)
                assertTrue(File(assets, "shiguang/scripts/${port.path}").isFile)
                assertEquals("", port.parser)
            }
            // Existing school entries survive merging, and all schools keep
            // their explicit general fallback options.
            assertTrue(selected.any { it.category.startsWith("GENERAL") })
            assertTrue(selected.any { it.parser == "qz" })
        }
    }
}
