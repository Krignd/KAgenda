package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.HolidayNoticeParser
import com.kstudio.agenda.model.HolidayTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object OfficialHolidayStore {
    const val FILE_NAME = "official_holidays.json"
    private const val NOTICE_2026 = "https://www.gov.cn/zhengce/content/202511/content_7047090.htm"
    private const val MIRROR_2026 = "https://www.beijing.gov.cn/zhengce/zhengcefagui/202511/t20251104_4258873.html"
    private fun official(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && (u.host == "gov.cn" || u.host.endsWith(".gov.cn")) && u.userInfo == null
    }.getOrDefault(false)

    private fun download(url: String): String {
        require(official(url))
        var target = url
        repeat(5) {
            val connection = URL(target).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 KAgenda")
            try {
                if (connection.responseCode in 300..399) {
                    target = URL(URL(target), connection.getHeaderField("Location")).toString()
                    require(official(target))
                } else {
                    check(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 2_000_000) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 2_000_000)
                    return bytes.toString(Charsets.UTF_8)
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }

    suspend fun fetch(context: Context, year: Int, suppliedUrl: String = ""): String = withContext(Dispatchers.IO) {
        require(year in 2000..2100)
        val urls = if (suppliedUrl.isNotBlank()) listOf(suppliedUrl.trim()) else discover(year)
        var last: Exception? = null
        for (url in urls) {
            try {
                val notice = HolidayNoticeParser.parse(download(url), year)
                val file = File(context.filesDir, FILE_NAME)
                val all = if (file.exists()) JSONObject(file.readText()) else JSONObject()
                val marks = JSONObject()
                notice.holidays.forEach { (day, name) -> marks.put(day.toString(), name) }
                all.put(year.toString(), JSONObject().put("source", url).put("fetchedAt", System.currentTimeMillis())
                    .put("holidays", marks).put("workdays", JSONArray(notice.workdays.toList())))
                val temp = File(context.filesDir, "$FILE_NAME.tmp")
                temp.writeText(all.toString())
                check(temp.renameTo(file))
                load(context)
                return@withContext url
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("Official notice not found for $year")
    }

    private fun discover(year: Int): List<String> {
        val urls = mutableListOf<String>()
        if (year == 2026) return listOf(NOTICE_2026, MIRROR_2026)
        // Discovery is best-effort: future notices can always be supplied explicitly in settings.
        runCatching {
            val library = download("https://sousuo.www.gov.cn/zcwjk/policyDocumentLibrary?q=${year}%E5%B9%B4%E9%83%A8%E5%88%86%E8%8A%82%E5%81%87%E6%97%A5&t=zhengcelibrary")
            Regex("https://www\\.gov\\.cn/zhengce/content/[^\"'<> ]+\\.htm").findAll(library).forEach { urls += it.value }
        }
        return urls.distinct()
    }

    fun load(context: Context) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return
            val all = JSONObject(file.readText())
            all.keys().forEach { key ->
                val year = key.toIntOrNull() ?: return@forEach
                val data = all.getJSONObject(key)
                val marks = data.getJSONObject("holidays")
                val holidays = marks.keys().asSequence().associate { it.toLong() to marks.getString(it) }
                val arr = data.optJSONArray("workdays") ?: JSONArray()
                HolidayTable.replaceYear(year, holidays, (0 until arr.length()).map { arr.getLong(it) }.toSet())
            }
        }
    }
}
