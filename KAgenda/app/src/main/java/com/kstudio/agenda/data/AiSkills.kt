package com.kstudio.agenda.data

import org.json.JSONObject
import java.time.LocalDate

/**
 * App 内置 AI 技能：定义可被 DeepSeek 执行的“技能”（提示词 + 输出解析）。
 * 目前包含：
 * - parse_event：把一段自然语言（通知文字 / 修改指令）解析为日程字段（新建或修改日程与计划）
 */
object AiSkills {

    /** AI 解析结果（字段缺省为空串 / null；action = create | update） */
    data class AiEvent(
        val action: String,
        val title: String,
        val type: String,
        val date: LocalDate?,
        val startTime: String,
        val endTime: String,
        val location: String,
        val note: String,
    )

    val parseEventSystemPrompt: String = """
        你是日程解析助手。把用户的中文文字（通知或修改指令）解析为日程要素，只输出一个 json 对象，无其他文字：
        {"action":"create|update","title":"≤20字","type":"interview|contest|lecture|exam|meeting|other 或空","date":"YYYY-MM-DD 或空","startTime":"HH:mm 或空","endTime":"HH:mm 或空","location":"或空","note":"或空"}
        规则：相对日期（今天/明天/后天/大后天/本周X/下周X）与“9.18”按基准日期推算；指令式（如“把体检改到明早10点”）用 action=update；全角转半角；不确定的字段留空串。
    """.trimIndent()

    /** 多条目解析（AI 快速添加）：输入可含多个日程/计划 */
    val parseItemsSystemPrompt: String = """
        你是日程解析助手。用户文字中可能包含多个日程或计划，请全部提取（一个也不要遗漏），只输出一个 json 对象：
        {"items":[{"tg":"agenda|plan","t":"标题≤20字","tp":"interview|contest|lecture|exam|meeting|other 或空","d":"YYYY-MM-DD 或空","s":"HH:mm 或空","e":"HH:mm 或空","l":"地点或空","n":"备注≤24字或空"}]}
        规则：课程/通知/活动→agenda；个人计划、目标、习惯、锻炼→plan；相对日期与“9.18”按基准日期推算；时间不明确时 s/e 留空；全角转半角；无关内容忽略；确保输出完整合法的 json（数组内所有条目都要输出，不要省略或使用省略号）。
    """.trimIndent()

    /** 学校适配代码生成（KagendaSchoolAdapter/2：可容纳不同学校的登录/探测/提取逻辑） */
    val adapterAuthorSystemPrompt: String = """
        你是学校课表系统适配助手。请根据用户提供的学校名称/官网，输出一个尽量完整的 json 适配代码（只输出 json 对象，不要注释与多余文字）：
        {
          "format":"KagendaSchoolAdapter/2",
          "id":"小写英文标识",
          "name":"学校名称",
          "homeUrl":"教务系统/课表页完整 https:// 地址",
          "ssoUrl":"统一身份认证登录页 https:// 地址（不确定留空字符串）",
          "serviceApi":"CAS service/接口地址（不确定留空字符串）",
          "probeJs":"页面状态探针（可选）：return JSON.stringify({ready:是否已显示课表,login:是否出现登录表单,captcha:是否有验证码,error:'错误文本或空',netError:是否网络异常})",
          "loginJs":"登录页自动登录（可选）：用 window.__kagendaUser / window.__kagendaPass 读取学号密码，填充并提交表单；提交后 return 'ok'",
          "waitSelector":"课表就绪后会出现的关键元素 CSS 选择器（可选）",
          "extractJs":"在课表页执行的提取脚本（必需）：return JSON.stringify([{title,day(1-7或中文),start(节次),end(节次),room,teacher,weeks('2-16'可省略)}]) 或 return JSON.stringify({weekNo:当前周,courses:[...]})",
          "note":"需要用户手动确认/补充的说明（可选）"
        }
        要求：
        1. 各校教务系统差异很大，请按该学校最常见的技术栈（正方/强智/URP/本科教务等）认真编写各脚本，而非全部留空；
        2. extractJs 优先用 fetch 调接口（若知道接口）或解析 DOM；脚本必须能独立执行、不依赖外部库；
        3. 无法确定的字段留空字符串；URL 必须带 https://；不要输出 Markdown 代码块。
    """.trimIndent()

    /** 生成适配代码的用户提示词；[extra] 为用户提供的补充线索（接口地址/页面片段/说明等） */
    fun adapterUserPrompt(name: String, site: String, extra: String = ""): String = buildString {
        append("学校名称：$name\n")
        append("官网/教务地址：${site.ifBlank { "（未提供，请按校名推测常见域名）" }}\n")
        if (extra.isNotBlank()) {
            append("补充线索（用户提供，请优先参考；可能是接口地址、页面片段或说明）：\n")
            append(extra.trim().take(4_000)).append("\n")
        }
        append("请输出完整的适配 json 代码（脚本尽量完整可用）。")
    }

    fun userPrompt(text: String, baseDate: LocalDate): String =
        "基准日期：$baseDate（${weekdayCn(baseDate)}）\n用户输入：\n$text"

    private fun weekdayCn(date: LocalDate): String =
        "周" + "一二三四五六日"[date.dayOfWeek.value - 1]

    /** 解析模型回复（容忍 ```json 包裹与前后噪声），失败返回 null */
    fun parseReply(content: String): AiEvent? {
        val json = extractJson(content) ?: return null
        return runCatching {
            val o = JSONObject(json)
            AiEvent(
                action = o.optString("action", "create"),
                title = o.optString("title").trim(),
                type = o.optString("type").trim().lowercase(),
                date = parseAiDate(o.optString("date")),
                startTime = normalizeTime(o.optString("startTime")),
                endTime = normalizeTime(o.optString("endTime")),
                location = o.optString("location").trim(),
                note = o.optString("note").trim(),
            )
        }.getOrNull()
    }

    /** 多条目回复解析（AI 快速添加） */
    data class AiItem(
        val isPlan: Boolean,
        val title: String,
        val type: String,
        val date: LocalDate?,
        val startTime: String,
        val endTime: String,
        val location: String,
        val note: String,
    )

    fun parseItemsReply(content: String): List<AiItem> {
        val json = extractJson(content) ?: return emptyList()
        val arr = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull() ?: return emptyList()
        val out = mutableListOf<AiItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("t").trim()
            if (title.isBlank()) continue
            out.add(
                AiItem(
                    isPlan = o.optString("tg").trim() == "plan",
                    title = title,
                    type = o.optString("tp").trim().lowercase(),
                    date = parseAiDate(o.optString("d")),
                    startTime = normalizeTime(o.optString("s")),
                    endTime = normalizeTime(o.optString("e")),
                    location = o.optString("l").trim(),
                    note = o.optString("n").trim(),
                )
            )
        }
        return out
    }

    /** 供 UI 使用：从回复中取出第一个 JSON 对象（如适配代码生成） */
    fun extractFirstJson(content: String): String? = extractJson(content)

    private fun normalizeTime(raw: String): String {
        val m = Regex("(\\d{1,2})\\s*[:：点时]\\s*(\\d{1,2})?").find(raw.trim()) ?: return ""
        val h = m.groupValues[1].toIntOrNull() ?: return ""
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h !in 0..23 || min !in 0..59) return ""
        return "%02d:%02d".format(h, min)
    }

    /** 宽松解析 AI 返回的日期：2026-09-18 / 2026.9.18 / 2026年9月18日 / 9-18（同/次年就近推断） */
    private fun parseAiDate(raw: String): LocalDate? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val full = Regex("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})").find(s)
        if (full != null) {
            return runCatching {
                LocalDate.of(full.groupValues[1].toInt(), full.groupValues[2].toInt(), full.groupValues[3].toInt())
            }.getOrNull()
        }
        val short = Regex("^(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})日?").find(s)
        if (short != null) {
            val now = LocalDate.now()
            val md = runCatching {
                LocalDate.of(now.year, short.groupValues[1].toInt(), short.groupValues[2].toInt())
            }.getOrNull() ?: return null
            // 明显早于今天 3 个月的，视为明年（如 1 月时收到 12 月）
            return if (md.isBefore(now.minusMonths(3))) md.plusYears(1) else md
        }
        return null
    }

    /** 提取回复中的第一个完整 json 对象（正确处理字符串与转义；被截断时自动补全括号） */
    private fun extractJson(content: String): String? {
        val start = content.indexOf('{')
        if (start < 0) return null
        val stack = ArrayDeque<Char>()
        var inStr = false
        var esc = false
        for (i in start until content.length) {
            val c = content[i]
            when {
                esc -> esc = false
                inStr && c == '\\' -> esc = true
                c == '"' -> inStr = !inStr
                !inStr && (c == '{' || c == '[') -> stack.addLast(c)
                !inStr && (c == '}' || c == ']') -> {
                    if (stack.isNotEmpty()) stack.removeLast()
                    if (stack.isEmpty()) return content.substring(start, i + 1)
                }
            }
        }
        // 到达末尾仍未闭合：多为 max_tokens 截断 → 补齐括号后再尝试解析
        if (stack.isNotEmpty()) {
            val sb = StringBuilder(content.substring(start))
            if (inStr) sb.append('"')
            while (stack.isNotEmpty()) {
                sb.append(if (stack.removeLast() == '{') '}' else ']')
            }
            return sb.toString()
        }
        return null
    }
}
