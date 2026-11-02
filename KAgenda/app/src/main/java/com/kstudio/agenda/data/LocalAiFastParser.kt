package com.kstudio.agenda.data

import com.kstudio.agenda.model.AgendaEvent
import java.time.LocalDate

/** Null means the model must decide. Never resolve ambiguous titles. */
object LocalAiFastParser {
    private val mutation = Regex("修改|删除|取消|推迟|提前|延后|改到|改成|改为|换成|换到|换地点|挪到|移到|不再重复|不要|别|不去|不开|\\b(update|delete|cancel|move|reschedule|remove)\\b|supprim|annul|déplac|modifi", RegexOption.IGNORE_CASE)
    private const val DAYS = "(?:今天|明天|后天|大后天)"
    private const val CLOCK = "(?:(?:上午|下午|晚上|早上|早晨|中午|凌晨)?(?:[0-9０-９]{1,2}|[一二三四五六七八九十两]{1,3})(?:[:：][0-9０-９]{2}|点(?:半|[0-9０-９]{1,2}分?)?))"
    private val simpleAdd = Regex("^$DAYS$CLOCK\\s*[^，,；;。！？!?\\n]{1,30}$")
    private val complex = Regex("每|班|到|至|如果|或|否则|分别|然后|同时|以及|闹钟|闹铃|叫醒|提醒|[；;、/／\\n]")
    private val move = Regex("^把(.+?)(?:改到|移到|挪到)($DAYS(?:$CLOCK)?)$")
    private val delete = Regex("^删除(.+)$")

    fun isMutation(text: String) = mutation.containsMatchIn(text)
    private fun clean(text: String) = text.trim().trimEnd('。', '！', '!')
    fun simpleAddition(text: String): Boolean {
        val value = clean(text)
        if (isMutation(value) || complex.containsMatchIn(value) || !simpleAdd.matches(value)) return false
        if (Regex(DAYS).findAll(value).count() != 1 || Regex(CLOCK).findAll(value).count() != 1) return false
        val parsed = AgendaTextParser.parse(value, LocalDate.of(2026, 1, 1))
        return parsed.hasDate && parsed.hasTime
    }

    fun mutation(text: String, events: List<AgendaEvent>, date: LocalDate): List<AiSkills.AiOp>? {
        val value = clean(text)
        delete.matchEntire(value)?.let { match ->
            val event = unique(events, match.groupValues[1]) ?: return null
            return listOf(AiSkills.AiOp.Delete(event.title, null, event.id))
        }
        val match = move.matchEntire(value) ?: return null
        val event = unique(events, match.groupValues[1]) ?: return null
        val destination = match.groupValues[2]
        val parsed = AgendaTextParser.parse(destination, date)
        if (!parsed.hasDate || (destination !in setOf("今天", "明天", "后天", "大后天") && !parsed.hasTime)) return null
        // Moving a timed interval also requires deciding how its end should move.
        if (parsed.hasTime && event.endTime.isNotBlank()) return null
        return listOf(AiSkills.AiOp.Update(event.title, null, AiSkills.AiSet(
            title = null, date = LocalDate.ofEpochDay(parsed.dateEpochDay),
            startTime = parsed.startTime.takeIf { parsed.hasTime }, endTime = null, location = null, note = null,
        ), event.id))
    }

    private fun unique(events: List<AgendaEvent>, raw: String): AgendaEvent? {
        val title = raw.trim().removeSurrounding("“", "”").removeSurrounding("\"", "\"")
        // Recurring/long events need clarification of occurrence vs series.
        return events.filter { it.title == title }.singleOrNull()?.takeIf { !it.isLong && it.repeatRule.isBlank() }
    }
}
