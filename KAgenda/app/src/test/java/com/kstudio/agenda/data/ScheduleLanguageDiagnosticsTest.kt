package com.kstudio.agenda.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScheduleLanguageDiagnosticsTest {
    private fun row() = JSONObject().put("courseName", "SYNTHETIC_PRIVATE_TITLE")
        .put("dayOfWeek", 2).put("beginSection", 1).put("endSection", 2)
        .put("studentId", "SYNTHETIC_ACCOUNT")
        .put("cellDetail", JSONArray().put(JSONObject().put("text", "（本）合成课程")))

    @Test fun `diagnostics distinguish original API DOM and parsed titles without emitting values`() {
        val response = JSONObject().put("datas", JSONObject().put("arrangedList", JSONArray().put(row())))
        val envelope = JSONObject().put("url", "https://synthetic.invalid/getMyScheduleDetail.do?token=SYNTHETIC_SECRET")
            .put("response", response.toString()).toString()
        val dom = JSONObject().put("courses", JSONArray().put(JSONObject().put("title", "合成课程"))).toString()
        val courses = ScheduleParser.parseApiEnvelopes(listOf(envelope))
        val text = ScheduleLanguageDiagnostics.current(dom, listOf(envelope), courses)
        val result = JSONObject(text)
        assertEquals(1, result.getJSONObject("DOM").getInt("含汉字"))
        assertEquals(1, result.getJSONObject("API").getInt("含拉丁字母且无汉字"))
        assertEquals(1, result.getJSONObject("最终当前周").getInt("含拉丁字母且无汉字"))
        assertEquals(1, result.getInt("API非中文且首项另有带标记中文课名"))
        assertEquals("SYNTHETIC_PRIVATE_TITLE", courses.single().title) // Original parser is unchanged.
        for (privateValue in listOf("SYNTHETIC_PRIVATE_TITLE", "SYNTHETIC_ACCOUNT", "SYNTHETIC_SECRET", "合成课程")) {
            assertFalse(privateValue, text.contains(privateValue))
        }
    }

    @Test fun `semester diagnostics keep repeats and invalid responses distinct from empty course lists`() {
        val raw = JSONObject().put("ok", true).put("debug", JSONObject().put("requestLanguage", "zh-CN")).put("weeks", JSONObject()
            .put("1", JSONArray().put(row())).put("2", JSONArray().put(row()))).toString()
        val parsed = ScheduleParser.parseSemesterFetchResult(raw)
        val text = ScheduleLanguageDiagnostics.semester(raw, parsed)
        val result = JSONObject(text)
        assertEquals(2, result.getInt("原始周数"))
        assertEquals("zh-CN", result.getString("重放请求语言"))
        val unknownLanguage = JSONObject(raw).put("debug", JSONObject().put("requestLanguage", "SYNTHETIC_SECRET_LANGUAGE"))
        val safe = ScheduleLanguageDiagnostics.semester(unknownLanguage.toString(), parsed)
        assertEquals("未提供", JSONObject(safe).getString("重放请求语言"))
        assertFalse(safe.contains("SYNTHETIC_SECRET_LANGUAGE"))
        assertEquals(2, result.getJSONObject("API").getInt("total"))
        assertEquals(2, result.getJSONObject("最终整学期").getInt("含拉丁字母且无汉字"))
        assertFalse(text.contains("SYNTHETIC_PRIVATE_TITLE"))
        val bad = JSONObject().put("url", "getMyScheduleDetail").put("response", "<html>login</html>").toString()
        val failure = JSONObject(ScheduleLanguageDiagnostics.current("", listOf(bad), emptyList()))
        assertFalse(failure.getBoolean("DOM可读取"))
        assertEquals(1, failure.getInt("无排课数组响应"))
        assertEquals(0, failure.getJSONObject("API").getInt("total"))
    }
}
