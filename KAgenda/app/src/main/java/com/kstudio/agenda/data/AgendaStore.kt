package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.AgendaTypes
import com.kstudio.agenda.model.FuzzyTime
import com.kstudio.agenda.model.RepeatRules
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 日程存储：本地 JSON 文件（filesDir/agenda.json），进程内通过 StateFlow 分发。
 * 增删改均为“读改写”整个小文件，简单可靠；接口设计为同步方法，
 * 由调用方（ViewModel）切到 IO 线程执行。
 */
object AgendaStore {

    private const val FILE_NAME = "agenda.json"

    private val _events = MutableStateFlow<List<AgendaEvent>>(emptyList())
    val events: StateFlow<List<AgendaEvent>> = _events.asStateFlow()

    @Volatile
    private var loaded = false

    /** 应用启动时调用一次：加载磁盘中的日程 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            val list = runCatching {
                val f = File(context.filesDir, FILE_NAME)
                if (!f.exists()) emptyList() else parse(f.readText(Charsets.UTF_8))
            }.getOrDefault(emptyList())
            _events.value = list.sortedWith(compareBy({ it.dateEpochDay }, { FuzzyTime.sortKey(it.startTime) }))
        }
    }

    /** 新增或更新（按 id 匹配） */
    fun upsert(context: Context, event: AgendaEvent) {
        ensureLoaded(context)
        synchronized(this) {
            val list = _events.value.toMutableList()
            val idx = list.indexOfFirst { it.id == event.id }
            if (idx >= 0) list[idx] = event else list.add(event)
            val sorted = list.sortedWith(compareBy({ it.dateEpochDay }, { FuzzyTime.sortKey(it.startTime) }))
            _events.value = sorted
            persist(context, sorted)
        }
    }

    fun delete(context: Context, id: String) {
        ensureLoaded(context)
        synchronized(this) {
            val list = _events.value.filterNot { it.id == id }
            _events.value = list
            persist(context, list)
        }
    }

    /** 批量新增或更新：一次排序 + 一次写盘（AI 批量添加等场景不再逐条重写整文件） */
    fun upsertAll(context: Context, events: List<AgendaEvent>) {
        if (events.isEmpty()) return
        ensureLoaded(context)
        synchronized(this) {
            val map = LinkedHashMap<String, AgendaEvent>(_events.value.size + events.size)
            _events.value.forEach { map[it.id] = it }
            events.forEach { map[it.id] = it }
            val sorted = map.values.sortedWith(compareBy({ it.dateEpochDay }, { FuzzyTime.sortKey(it.startTime) }))
            _events.value = sorted
            persist(context, sorted)
        }
    }

    /** 批量删除：一次写盘 */
    fun deleteAll(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        ensureLoaded(context)
        synchronized(this) {
            val idSet = ids.toHashSet()
            val list = _events.value.filterNot { it.id in idSet }
            if (list.size == _events.value.size) return
            _events.value = list
            persist(context, list)
        }
    }

    /** 清空全部日程与计划（重置应用 / 清除日程数据时调用） */
    fun clear(context: Context) {
        synchronized(this) {
            _events.value = emptyList()
            runCatching { File(context.filesDir, FILE_NAME).delete() }
        }
    }

    private fun persist(context: Context, list: List<AgendaEvent>) {
        // 写盘失败不再静默：提示性日志（内存状态仍已更新，但重启后可能丢失该次改动）
        runCatching {
            File(context.filesDir, FILE_NAME).writeText(toJson(list), Charsets.UTF_8)
        }.onFailure {
            AppLog.e("AgendaStore", "日程写盘失败（改动仅在内存中，重启后可能丢失）", it)
        }
    }

    private fun parse(text: String): List<AgendaEvent> = try {
        val arr = JSONArray(text)
        val result = mutableListOf<AgendaEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val title = o.optString("title")
            val day = o.optLong("dateEpochDay", Long.MIN_VALUE)
            if (id.isBlank() || title.isBlank() || day == Long.MIN_VALUE) continue
            result.add(
                AgendaEvent(
                    id = id,
                    title = title,
                    dateEpochDay = day,
                    startTime = o.optString("startTime"),
                    endTime = o.optString("endTime"),
                    location = o.optString("location"),
                    note = o.optString("note"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    isLong = o.optBoolean("isLong", false),
                    endDateEpochDay = if (o.has("endDateEpochDay")) o.optLong("endDateEpochDay") else null,
                    type = AgendaTypes.fromLegacy(o.optString("type")),
                    colorArgb = o.optInt("colorArgb", 0),
                    isPlan = o.optBoolean("isPlan", false),
                    repeatRule = o.optString("repeat").takeIf { RepeatRules.isValid(it) } ?: "",
                )
            )
        }
        result
    } catch (_: Throwable) {
        emptyList()
    }

    private fun toJson(list: List<AgendaEvent>): String {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    put("dateEpochDay", e.dateEpochDay)
                    put("startTime", e.startTime)
                    put("endTime", e.endTime)
                    put("location", e.location)
                    put("note", e.note)
                    put("createdAt", e.createdAt)
                    put("isLong", e.isLong)
                    if (e.endDateEpochDay != null) put("endDateEpochDay", e.endDateEpochDay)
                    put("type", e.type)
                    put("colorArgb", e.colorArgb)
                    put("isPlan", e.isPlan)
                    if (e.repeatRule.isNotBlank()) put("repeat", e.repeatRule)
                }
            )
        }
        return arr.toString()
    }
}
