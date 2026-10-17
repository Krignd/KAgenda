package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.AgendaTypes
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/**
 * AI 助手操作执行器：把 [AiSkills.AiOp]（新增/修改/删除）落到本地日程库。
 * - add：直接新建条目；
 * - update/delete：按「标题（精确→包含）+ 日期就近」匹配本地已有条目。
 */
object AiAssistant {

    /** 执行结果统计 */
    data class ApplyResult(
        val added: Int,
        val updated: Int,
        val deleted: Int,
        val unmatched: Int,
    )

    /** 供 AI 提示词的“现有条目”清单（标题 + 日期时间，最多取一部分） */
    fun contextLines(events: List<AgendaEvent>, query: String = "", today: LocalDate = LocalDate.now()): List<String> =
        events.sortedWith(compareBy<AgendaEvent>(
            { if (query.contains(it.title) && it.title.isNotBlank()) 0 else 1 },
            { if (it.isOngoing(today.atTime(12, 0))) 0 else 1 },
            { if (it.repeatRule.isNotBlank() || !it.endDate.isBefore(today)) 0 else 1 },
            { abs(it.dateEpochDay - today.toEpochDay()) },
        )).map { e ->
            org.json.JSONObject().apply {
                put("id", e.id); put("t", e.title); put("d", e.date.toString()); put("s", e.startTime); put("e", e.endTime)
                put("l", e.location); put("rp", e.repeatRule); put("lg", e.isLong)
                put("ed", e.endDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty())
                put("tg", if (e.isPlan) "plan" else "agenda"); put("tag", e.statusTag)
                put("notify", e.notifyEnabled); put("alarm", e.alarmEnabled); put("global", e.followGlobalReminder)
            }.toString()
        }

    /** 由 AI 解析条目构建本地事件 */
    fun buildEvent(item: AiSkills.AiItem): AgendaEvent {
        val startDay = item.date ?: LocalDate.now()
        // 长条目本身已是时间段，不叠加重复（与编辑器/RepeatRulePicker 口径一致）
        val repeat = if (item.isLong) "" else item.repeat
        // 长条目：ed = 结束日期；重复条目：ed = 重复截止（≤ 开始日则视为“不限”，与编辑器保存口径一致）
        val endDay = item.endDate?.toEpochDay()
        return AgendaEvent(
            id = UUID.randomUUID().toString(),
            title = item.title,
            dateEpochDay = startDay.toEpochDay(),
            startTime = item.startTime,
            endTime = item.endTime,
            location = item.location,
            note = item.note,
            type = item.type.takeIf { key -> key in AgendaTypes.ORDER } ?: "",
            isPlan = item.isPlan,
            // 长日程 / 长计划：AI 解析出的跨天区间必须带上，否则会被压成一天
            isLong = item.isLong,
            endDateEpochDay = when {
                item.isLong -> endDay
                repeat.isNotBlank() -> endDay?.takeIf { it > startDay.toEpochDay() }
                else -> null
            },
            // 重复规则：日程与计划同样支持（“每周二四六”这类周期性描述不能丢）
            repeatRule = repeat,
            followGlobalReminder = item.notify == null && item.alarm == null && item.leadMinutes == null,
            notifyEnabled = item.notify ?: true,
            alarmEnabled = item.alarm ?: false,
            reminderLeadMinutes = item.leadMinutes ?: 0,
            statusTag = item.statusTag,
        )
    }

    /** 应用一批操作；返回统计（unmatched=未匹配到目标的修改/删除） */
    fun apply(context: Context, ops: List<AiSkills.AiOp>): ApplyResult {
        var added = 0
        var updated = 0
        var deleted = 0
        var unmatched = 0
        // 在内存副本上顺序推演匹配（同一批内的先后操作互不干扰），
        // 最后统一批量写盘：避免 N 条操作触发 N 次整文件重写
        val working = AgendaStore.events.value.toMutableList()
        val toUpsert = mutableListOf<AgendaEvent>()
        val toDelete = mutableListOf<String>()
        for (op in ops) {
            when (op) {
                is AiSkills.AiOp.Add -> {
                    val event = buildEvent(op.item)
                    toUpsert += event
                    working += event
                    added++
                }
                is AiSkills.AiOp.Update -> {
                    val target = findMatch(working, op.matchTitle, op.matchDate, op.matchId)
                    if (target == null) {
                        unmatched++
                    } else {
                        val edited = applySet(target, op.set)
                        toUpsert += edited
                        val idx = working.indexOfFirst { it.id == target.id }
                        if (idx >= 0) working[idx] = edited
                        updated++
                    }
                }
                is AiSkills.AiOp.Delete -> {
                    val target = findMatch(working, op.matchTitle, op.matchDate, op.matchId)
                    if (target == null) {
                        unmatched++
                    } else {
                        toDelete += target.id
                        working.removeAll { it.id == target.id }
                        deleted++
                    }
                }
            }
        }
        AgendaStore.upsertAll(context, toUpsert)
        AgendaStore.deleteAll(context, toDelete)
        return ApplyResult(added, updated, deleted, unmatched)
    }

    /** 匹配目标条目：标题完全一致优先，其次“包含”；再按日期（或今天）就近取 */
    fun findMatch(all: List<AgendaEvent>, title: String, date: LocalDate?, id: String? = null): AgendaEvent? {
        if (id != null) return all.firstOrNull { it.id == id }
        val t = title.trim()
        if (t.isBlank()) return null
        val exact = all.filter { it.title.trim() == t }
        val pool = if (exact.isNotEmpty()) {
            exact
        } else {
            all.filter { it.title.contains(t) || t.contains(it.title.trim()) }
        }
        if (pool.isEmpty()) return null
        if (date != null) return pool.filter { it.occursOn(date) }.singleOrNull()
        val pivot = LocalDate.now().toEpochDay()
        val distance = pool.minOf { abs(it.dateEpochDay - pivot) }
        return pool.filter { abs(it.dateEpochDay - pivot) == distance }.singleOrNull()
    }

    /** 把“仅修改字段”应用到事件（null=不变） */
    fun applySet(e: AgendaEvent, s: AiSkills.AiSet): AgendaEvent = e.copy(
        title = s.title ?: e.title,
        followGlobalReminder = if (s.notify != null || s.alarm != null || s.leadMinutes != null) false else e.followGlobalReminder,
        notifyEnabled = s.notify ?: e.notifyEnabled,
        alarmEnabled = s.alarm ?: e.alarmEnabled,
        reminderLeadMinutes = s.leadMinutes ?: e.reminderLeadMinutes,
        statusTag = s.statusTag ?: e.statusTag,
        dateEpochDay = s.date?.toEpochDay() ?: e.dateEpochDay,
        startTime = s.startTime ?: e.startTime,
        endTime = s.endTime ?: e.endTime,
        location = s.location ?: e.location,
        note = s.note ?: e.note,
        // 重复规则：短日程/短计划都生效（长日程本身是时间段，不叠加重复）
        repeatRule = if (e.isLong) e.repeatRule else (s.repeat ?: e.repeatRule),
    )
}
