package com.kstudio.agenda.data

import com.kstudio.agenda.data.academic.wakeup.parser.*
import com.kstudio.agenda.data.academic.wakeup.parser.qz.*
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Local-only compatibility host for attributed WakeUp parsers. Never saves or performs HTTP. */
object AcademicPortalParser {
    const val REPOSITORY = "https://github.com/Matey-ace/wakeup-course-schedule"
    const val COMMIT = "43631a54cb70ceb2d983a4d646022d84352c9d85"
    const val DIRECTORY_SOURCE = "$REPOSITORY/blob/$COMMIT/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/SchoolListActivity.kt"
    private val types = linkedMapOf(
        "qz" to ("强智 / Qiangzhi" to "qz/QzParser.kt"),
        "qz_with_node" to ("强智 · 明确节次 / With sections" to "qz/QzWithNodeParser.kt"),
        "qz_br" to ("强智 BR" to "qz/QzBrParser.kt"),
        "qz_crazy" to ("强智 Crazy" to "qz/QzCrazyParser.kt"),
        "qz_old" to ("旧强智 / Legacy Qiangzhi" to "OldQzParser.kt"),
        "zf_new" to ("新正方 HTML / Zhengfang HTML" to "NewZFParser.kt"),
        "zf" to ("旧正方 / Legacy Zhengfang" to "ZhengFangParser.kt"),
        "zf_1" to ("旧正方 · 变体 / Variant" to "ZhengFangParser.kt"),
        "urp" to ("URP" to "UrpParser.kt"),
        "urp_new" to ("新 URP / New URP" to "NewUrpParser.kt"),
        "cf" to ("青果 / Kingosoft" to "ChengFangParser.kt"),
        "pku" to ("北京大学 · 旧版 / PKU legacy" to "PekingParser.kt"),
        "bnuz" to ("北师珠 · 旧版 / BNUZ legacy" to "BNUZParser.kt"),
        "hniu" to ("湖南信息职院 · 旧版 / HNIU legacy" to "HNIUParser.kt"),
        "hnust" to ("湖南科技大学 · 旧版 / HNUST legacy" to "HNUSTParser.kt"),
    )
    fun adapters(url: String): List<SchoolCatalog.Adapter> = types.map { (type, info) ->
        SchoolCatalog.Adapter("wakeup_$type", info.first, "GENERAL_LEGACY", url, "", "YZune / Matey-ace",
            "WakeUp 3.612", false, REPOSITORY, COMMIT, "Apache-2.0", "",
            "$REPOSITORY/blob/$COMMIT/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/${info.second}", type)
    }
    /** Require an explicit web scheme; never convert arbitrary strings into active URLs. */
    fun portalUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
        uri.toASCIIString()
    }.getOrNull()

    fun parse(type: String, sources: List<String>): WarehousePayload.Batch {
        require(type in types && sources.isNotEmpty() && sources.size <= 16)
        require(sources.sumOf { it.length } <= 1_500_000)
        val rows = sources.flatMap { source ->
            // Frames without a timetable are expected. A recognized but malformed timetable rejects the batch.
            if (!recognizes(type, source)) emptyList() else when (type) {
                "qz" -> QzParser(source).generateCourseList()
                "qz_with_node" -> QzWithNodeParser(source).generateCourseList()
                "qz_br" -> QzBrParser(source).generateCourseList()
                "qz_crazy" -> QzCrazyParser(source).generateCourseList()
                "qz_old" -> OldQzParser(source).generateCourseList()
                "zf_new" -> NewZFParser(source).generateCourseList()
                "zf" -> ZhengFangParser(source, 0).generateCourseList()
                "zf_1" -> ZhengFangParser(source, 1).generateCourseList()
                "urp" -> UrpParser(source).generateCourseList()
                "urp_new" -> {
                    val groups = JSONObject(source).getJSONArray("dateList")
                    (0 until groups.length()).flatMap { i ->
                        NewUrpParser(JSONObject().put("dateList", JSONArray().put(groups.getJSONObject(i))).toString()).generateCourseList()
                    }
                }
                "cf" -> ChengFangParser(source).generateCourseList()
                "pku" -> PekingParser(source).generateCourseList()
                "bnuz" -> BNUZParser(source).generateCourseList()
                "hniu" -> HNIUParser(source).generateCourseList()
                "hnust" -> HNUSTParser(source, 0).generateCourseList()
                else -> error("Unknown parser")
            }
        }
        require(rows.size in 1..5000)
        val output = JSONArray()
        rows.forEach { c ->
            require(c.name.isNotBlank() && c.day in 1..7)
            require(c.startNode in 1..PeriodTimes.MAX_COUNT && c.endNode in c.startNode..PeriodTimes.MAX_COUNT)
            require(c.startWeek in 1..Course.MAX_WEEK && c.endWeek in c.startWeek..Course.MAX_WEEK && c.type in 0..2)
            val weeks = (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }
            require(weeks.isNotEmpty())
            output.put(JSONObject().put("name", c.name).put("day", c.day).put("position", c.room)
                .put("teacher", c.teacher).put("startSection", c.startNode).put("endSection", c.endNode).put("weeks", JSONArray(weeks)))
        }
        return WarehousePayload.parse(output.toString(), "", "")
    }
    private fun recognizes(type: String, text: String): Boolean = when (type) {
        "urp_new" -> text.trimStart().startsWith("{") && text.contains("\"dateList\"")
        "cf" -> text.contains("var kbxx = ")
        "qz", "qz_br", "qz_with_node", "qz_old", "qz_crazy" -> text.contains("kbtable")
        "zf", "zf_1" -> text.contains("Table1")
        "zf_new", "bnuz" -> text.contains("table1")
        "urp" -> text.contains("displayTag") || text.contains("table-striped")
        "hniu" -> text.contains("bordercolordark")
        "hnust" -> text.contains("kbtable")
        "pku" -> text.contains("datagrid")
        else -> false
    }
}
