package com.kstudio.agenda.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 内置浏览器的一条访问记录 */
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val atMillis: Long,
)

/**
 * 内置浏览器（开发者工具里的那个）的访问历史。
 *
 * - 存 `filesDir/dev_browser_history.json`，最多保留 [MAX] 条，最新的在最前；
 * - 同一地址重复访问只保留最新一条（不刷屏）；
 * - 与登录流程的 WebView 无关：那个窗口不写历史，这里也不读它的状态。
 */
object BrowserHistoryStore {

    private const val FILE_NAME = "dev_browser_history.json"
    private const val MAX = 200

    fun load(context: Context): List<BrowserHistoryEntry> = runCatching {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        val out = mutableListOf<BrowserHistoryEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(
                BrowserHistoryEntry(
                    url = url,
                    title = o.optString("title"),
                    atMillis = o.optLong("at", 0L),
                )
            )
        }
        out
    }.getOrDefault(emptyList())

    /** 记录一次访问（页面开始加载时调用；标题稍后由 [updateTitle] 补上） */
    fun record(context: Context, url: String) {
        if (!isRecordable(url)) return
        val list = load(context).toMutableList()
        list.removeAll { it.url == url }
        list.add(0, BrowserHistoryEntry(url, "", System.currentTimeMillis()))
        trim(list)
        save(context, list)
    }

    /** 页面加载完成后回填标题（同一地址若已存在则就地更新，不改变顺序） */
    fun updateTitle(context: Context, url: String, title: String) {
        if (!isRecordable(url) || title.isBlank()) return
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.url == url }
        if (idx < 0) return
        if (list[idx].title == title) return
        list[idx] = list[idx].copy(title = title)
        save(context, list)
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    /** about:blank / data: 之类的placeholder 不记入历史 */
    private fun isRecordable(url: String): Boolean =
        url.isNotBlank() && !url.startsWith("about:") && !url.startsWith("data:")

    private fun trim(list: MutableList<BrowserHistoryEntry>) {
        while (list.size > MAX) list.removeAt(list.size - 1)
    }

    private fun save(context: Context, list: List<BrowserHistoryEntry>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("url", e.url)
                        put("title", e.title)
                        put("at", e.atMillis)
                    }
                )
            }
            File(context.filesDir, FILE_NAME).writeText(arr.toString(), Charsets.UTF_8)
        }
    }
}
