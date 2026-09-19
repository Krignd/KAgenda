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
    fun contextLines(events: List<AgendaEvent>): List<String> =
        events
            .sortedWith(compareBy({ it.dateEpochDay }, { com.kstudio.agenda.model.FuzzyTime.sortKey(it.startTime) }))
            .map { e ->
                buildString {
                    append(e.date.monthValue).append('/').append(e.date.dayOfMonth)
                    if (e.startTime.isNotBlank()) append(' ').append(e.startTime)
                    append(' ').append(e.title)
                    if (e.isPlan) append("（计划）")
                }
            }

    /** 由 AI 解析条目构建本地事件 */
    fun buildEvent(item: AiSkills.AiItem): AgendaEvent = AgendaEvent(
        id = UUID.randomUUID().toString(),
        title = item.title,
        dateEpochDay = (item.date ?: LocalDate.now()).toEpochDay(),
        startTime = item.startTime,
        endTime = item.endTime,
        location = item.location,
        note = item.note,
        type = item.type.takeIf { key -> key in AgendaTypes.ORDER } ?: "",
        isPlan = item.isPlan,
        repeatRule = if (item.isPlan) item.repeat else "",
    )

    /** 应用一批操作；返回统计（unmatched=未匹配到目标的修改/删除） */
    fun apply(context: Context, ops: List<AiSkills.AiOp>): ApplyResult {
        var added = 0
        var updated = 0
        var deleted = 0
        var unmatched = 0
        for (op in ops) {
            when (op) {
                is AiSkills.AiOp.Add -> {
                    AgendaStore.upsert(context, buildEvent(op.item))
                    added++
                }
                is AiSkills.AiOp.Update -> {
                    val target = findMatch(AgendaStore.events.value, op.matchTitle, op.matchDate)
                    if (target == null) {
                        unmatched++
                    } else {
                        AgendaStore.upsert(context, applySet(target, op.set))
                        updated++
                    }
                }
                is AiSkills.AiOp.Delete -> {
                    val target = findMatch(AgendaStore.events.value, op.matchTitle, op.matchDate)
                    if (target == null) {
                        unmatched++
                    } else {
                        AgendaStore.delete(context, target.id)
                        deleted++
                    }
                }
            }
        }
        return ApplyResult(added, updated, deleted, unmatched)
    }

    /** 匹配目标条目：标题完全一致优先，其次“包含”；再按日期（或今天）就近取 */
    fun findMatch(all: List<AgendaEvent>, title: String, date: LocalDate?): AgendaEvent? {
        val t = title.trim()
        if (t.isBlank()) return null
        val exact = all.filter { it.title.trim() == t }
        val pool = if (exact.isNotEmpty()) {
            exact
        } else {
            all.filter { it.title.contains(t) || t.contains(it.title.trim()) }
        }
        if (pool.isEmpty()) return null
        date?.let { d -> pool.firstOrNull { it.date == d }?.let { return it } }
        val pivot = (date ?: LocalDate.now()).toEpochDay()
        return pool.minByOrNull { abs(it.date.toEpochDay() - pivot) }
    }

    /** 把“仅修改字段”应用到事件（null=不变） */
    fun applySet(e: AgendaEvent, s: AiSkills.AiSet): AgendaEvent = e.copy(
        title = s.title ?: e.title,
        dateEpochDay = s.date?.toEpochDay() ?: e.dateEpochDay,
        startTime = s.startTime ?: e.startTime,
        endTime = s.endTime ?: e.endTime,
        location = s.location ?: e.location,
        note = s.note ?: e.note,
        repeatRule = s.repeat ?: e.repeatRule,
    )
}
