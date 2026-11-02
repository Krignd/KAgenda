package com.kstudio.agenda.data

import com.kstudio.agenda.model.Course
import org.json.JSONArray
import org.json.JSONObject

/** Read-only aggregate diagnostics. Never emits names, account data, cookies or response bodies. */
object ScheduleLanguageDiagnostics {
    private val han = Regex("\\p{IsHan}")
    private val latin = Regex("[A-Za-z]")

    private class Titles {
        var total = 0; var chinese = 0; var latinOnly = 0; var other = 0; var blank = 0
        fun add(value: String) {
            total++
            when {
                value.isBlank() -> blank++
                han.containsMatchIn(value) -> chinese++
                latin.containsMatchIn(value) -> latinOnly++
                else -> other++
            }
        }
        fun json() = JSONObject().put("total", total).put("含汉字", chinese)
            .put("含拉丁字母且无汉字", latinOnly).put("其他", other).put("空", blank)
    }

    private class ApiTitles {
        val titles = Titles()
        var taggedChineseAlternative = 0
        fun add(rows: JSONArray?) {
            if (rows == null) return
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val name = row.opt("courseName") as? String ?: ""
                titles.add(name)
                val raw = row.optJSONArray("cellDetail")?.optJSONObject(0)?.opt("text") as? String ?: ""
                val cleaned = ScheduleParser.cleanTitle(raw)
                if (raw != cleaned && han.containsMatchIn(cleaned) && !han.containsMatchIn(name)) taggedChineseAlternative++
            }
        }
    }

    fun current(dom: String, envelopes: List<String>, parsed: List<Course>): String {
        val domTitles = Titles()
        val domObject = runCatching { JSONObject(dom) }.getOrNull()
        val domRows = domObject?.optJSONArray("courses")
        if (domRows != null) for (i in 0 until domRows.length()) {
            domRows.optJSONObject(i)?.let { domTitles.add(it.opt("title") as? String ?: "") }
        }
        val api = ApiTitles()
        var responses = 0
        var invalidResponses = 0
        for (raw in envelopes) {
            val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            if (!envelope.optString("url").contains("getMyScheduleDetail")) continue
            responses++
            val response = runCatching { JSONObject(envelope.optString("response")) }.getOrNull()
            val rows = response?.optJSONObject("datas")?.optJSONArray("arrangedList")
            if (rows == null) invalidResponses++ else api.add(rows)
        }
        val parsedTitles = Titles().also { p -> parsed.forEach { p.add(it.title) } }
        return JSONObject().put("DOM可读取", domObject != null).put("DOM", domTitles.json())
            .put("接口响应", responses).put("无排课数组响应", invalidResponses).put("API", api.titles.json())
            .put("API非中文且首项另有带标记中文课名", api.taggedChineseAlternative)
            .put("最终当前周", parsedTitles.json()).toString()
    }

    fun semester(raw: String, parsed: Map<Int, List<Course>>): String {
        val root = runCatching { JSONObject(raw) }.getOrNull()
        val weeks = root?.optJSONObject("weeks")
        val api = ApiTitles()
        if (weeks != null) weeks.keys().forEach { api.add(weeks.optJSONArray(it)) }
        val parsedTitles = Titles().also { p -> parsed.values.forEach { rows -> rows.forEach { p.add(it.title) } } }
        return JSONObject().put("原始周数", weeks?.length() ?: 0).put("API", api.titles.json())
            .put("重放请求语言", root?.optJSONObject("debug")?.optString("requestLanguage")
                ?.takeIf { it == "zh-CN" || it == "en-US" } ?: "未提供")
            .put("API非中文且首项另有带标记中文课名", api.taggedChineseAlternative)
            .put("解析周数", parsed.size).put("最终整学期", parsedTitles.json()).toString()
    }
}
