package com.kstudio.agenda.data

import com.kstudio.agenda.model.FuzzyTime
import com.kstudio.agenda.model.RepeatRules
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

    /**
     * AI 助手解析（支持新增/修改/删除）：
     * 输入一段文字 → 输出 ops 数组（add / update / delete）。
     */
    val assistantSystemPrompt: String = """
        你是日程助手。用户会输入一段文字，可能是新日程/计划的描述，也可能是要求修改或删除已有日程/计划的指令。请解析为一个 json 对象（只输出 json，无其他文字）：
        {"ops":[
          {"op":"add","tg":"agenda|plan","t":"标题≤20字","tp":"interview|contest|lecture|exam|meeting|other 或空","d":"YYYY-MM-DD 或空","s":"时间或空","e":"时间或空","l":"地点或空","n":"备注≤24字或空","rp":"重复规则或空"},
          {"op":"update","match":{"t":"已有条目标题","d":"YYYY-MM-DD 或空"},"set":{"t":"新标题或空","d":"新日期或空","s":"新开始或空","e":"新结束或空","l":"新地点或空","n":"新备注或空","rp":"新重复规则或空"}},
          {"op":"delete","match":{"t":"已有条目标题","d":"YYYY-MM-DD 或空"}}
        ]}
        规则：
        1. 新建内容用 add：课程/通知/活动→agenda；个人计划、目标、习惯、锻炼→plan；
        2. “把X改到/推迟/提前/改成/换地点”等修改指令用 update，match.t 填用户提到的原条目标题（尽量与“现有条目”一致），set 只填需要修改的字段，未修改的字段留空串；
        3. “删除/取消X”用 delete；
        4. s/e 格式：精确时间用 24 小时制 "HH:mm"；时间段（“14:00-16:00”或“下午2点到4点”）必须拆分为 s=开始、e=结束；“下午3点半”=15:30、“晚上8点”=20:00、“上午9点”=09:00；只知道大概时段时 s 用 凌晨|早晨|上午|下午|晚上|午夜 之一；
        5. 相对日期（今天/明天/后天/本周X/下周X）与“9.18”按基准日期推算；全角转半角；无关内容忽略；确保输出完整合法的 json。
        6. 重复规则 rp（短日程与短计划同样支持，长日程不用）：用户描述“每周二/四/六”→weekly:2,4,6（1=周一…7=周日）；“隔周周二/双周二”→biweekly:2；“每3天”→daily:3；“每天”→daily:1；“每月X日”→monthly；不重复留空串。
           凡是出现“每周…/每周几/隔周/双周/每N天/每天/每月”这类周期性说法，rp 必须填写，不能只给日期；
        7. 修改重复（如“改成每周一三五”）用 update，set.rp 填新规则；“不再重复”时 set.rp 填 "none"。
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

    /** AI 助手用户提示词：附上现有条目清单，便于匹配“修改/删除”目标 */
    fun assistantUserPrompt(text: String, baseDate: LocalDate, existingLines: List<String>): String = buildString {
        append("基准日期：$baseDate（${weekdayCn(baseDate)}）\n")
        if (existingLines.isNotEmpty()) {
            append("现有日程/计划（供修改/删除匹配）：\n")
            existingLines.take(60).forEach { append("- ").append(it).append('\n') }
        }
        append("用户输入：\n").append(text)
    }

    private fun weekdayCn(date: LocalDate): String =
        "周" + "一二三四五六日"[date.dayOfWeek.value - 1]

    /** 解析模型回复（容忍 ```json 包裹与前后噪声），失败返回 null */
    fun parseReply(content: String): AiEvent? {
        val json = extractJson(content) ?: return null
        return runCatching {
            val o = JSONObject(json)
            val (start, end) = normalizeTimePair(o.optString("startTime"), o.optString("endTime"))
            AiEvent(
                action = o.optString("action", "create"),
                title = o.optString("title").trim(),
                type = o.optString("type").trim().lowercase(),
                date = parseAiDate(o.optString("date")),
                startTime = start,
                endTime = end,
                location = o.optString("location").trim(),
                note = o.optString("note").trim(),
            )
        }.getOrNull()
    }

    /** AI 助手单条目（add / set 共用字段解析结果） */
    data class AiItem(
        val isPlan: Boolean,
        val title: String,
        val type: String,
        val date: LocalDate?,
        val startTime: String,
        val endTime: String,
        val location: String,
        val note: String,
        /** 重复规则 token（见 RepeatRules；空串=不重复） */
        val repeat: String = "",
    )

    /** update 的“仅修改字段”（null=不变） */
    data class AiSet(
        val title: String?,
        val date: LocalDate?,
        val startTime: String?,
        val endTime: String?,
        val location: String?,
        val note: String?,
        /** 新重复规则（null=不变；""=取消重复） */
        val repeat: String? = null,
    )

    /** AI 助手操作 */
    sealed interface AiOp {
        data class Add(val item: AiItem) : AiOp
        data class Update(val matchTitle: String, val matchDate: LocalDate?, val set: AiSet) : AiOp
        data class Delete(val matchTitle: String, val matchDate: LocalDate?) : AiOp
    }

    /** ops 回复解析（AI 助手）：支持新增/修改/删除 */
    fun parseOpsReply(content: String): List<AiOp> {
        val json = extractJson(content) ?: return emptyList()
        val arr = runCatching { JSONObject(json).optJSONArray("ops") }.getOrNull()
            ?: runCatching { JSONObject(json).optJSONArray("items") }.getOrNull()  // 兼容旧格式
            ?: return emptyList()
        val out = mutableListOf<AiOp>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            when (o.optString("op").trim().lowercase()) {
                "update" -> {
                    val match = o.optJSONObject("match") ?: continue
                    val title = match.optString("t").trim()
                    if (title.isBlank()) continue
                    val setObj = o.optJSONObject("set")
                    out.add(
                        AiOp.Update(
                            matchTitle = title,
                            matchDate = parseAiDate(match.optString("d")),
                            set = AiSet(
                                title = setObj?.optString("t")?.trim()?.takeIf { it.isNotBlank() },
                                date = setObj?.optString("d")?.let { parseAiDate(it) },
                                startTime = setObj?.optString("s")?.let { fuzzyOrTime(it) }?.takeIf { it.isNotBlank() },
                                endTime = setObj?.optString("e")?.let { fuzzyOrTime(it) }?.takeIf { it.isNotBlank() },
                                location = setObj?.optString("l")?.trim()?.takeIf { it.isNotBlank() },
                                note = setObj?.optString("n")?.trim()?.takeIf { it.isNotBlank() },
                                repeat = if (setObj != null && setObj.has("rp")) normalizeRepeat(setObj.optString("rp")) else null,
                            ),
                        )
                    )
                }
                "delete" -> {
                    val match = o.optJSONObject("match") ?: continue
                    val title = match.optString("t").trim()
                    if (title.isBlank()) continue
                    out.add(AiOp.Delete(matchTitle = title, matchDate = parseAiDate(match.optString("d"))))
                }
                else -> parseAiItem(o)?.let { out.add(AiOp.Add(it)) }
            }
        }
        return out
    }

    /** 单条目解析（add / 旧版 items 兼容） */
    private fun parseAiItem(o: JSONObject): AiItem? {
        val title = o.optString("t").trim()
        if (title.isBlank()) return null
        val (s, e) = normalizeTimePair(o.optString("s"), o.optString("e"))
        return AiItem(
            isPlan = o.optString("tg").trim() == "plan",
            title = title,
            type = o.optString("tp").trim().lowercase(),
            date = parseAiDate(o.optString("d")),
            startTime = s,
            endTime = e,
            location = o.optString("l").trim(),
            note = o.optString("n").trim(),
            repeat = normalizeRepeat(o.optString("rp")),
        )
    }

    /** 重复字段归一化：空/none/不重复 → ""；合法 token 原样；中文描述→宽松解析；解析失败→"" */
    private fun normalizeRepeat(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty() || s == "none" || s == "无" || s.contains("不重复")) return ""
        if (s == RepeatRules.MONTHLY || s.contains(':') && RepeatRules.isValid(s)) return s
        return RepeatRules.parse(s) ?: ""
    }

    /** 模糊时刻词（凌晨/早晨/上午/下午/晚上/午夜）→ 原样返回；否则按 "HH:mm" 规整 */
    private fun fuzzyOrTime(raw: String): String {
        val t = raw.trim()
        FuzzyTime.ORDER.firstOrNull { t.contains(it) }?.let { return it }
        return normalizeTime(t)
    }

    /** s/e 规整：支持“时间段”自动拆分（如 s="14:00-16:00" 或 s="下午2点到4点"） */
    private fun normalizeTimePair(sRaw: String, eRaw: String): Pair<String, String> {
        val sList = collectTimes(sRaw)
        if (sList.size >= 2) return sList[0] to sList[1]
        val s = sList.firstOrNull() ?: fuzzyOrTime(sRaw)
        val e = collectTimes(eRaw).firstOrNull() ?: fuzzyOrTime(eRaw)
        return s to e
    }

    /** 从文本中收集所有可解析的时刻（带“下午/晚上”提示时自动 +12） */
    private fun collectTimes(raw: String): List<String> {
        val pm = raw.contains("下午") || raw.contains("晚上") || raw.contains("傍晚")
        return Regex("(\\d{1,2})\\s*[:：点时]\\s*(\\d{1,2})?").findAll(raw).mapNotNull { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (pm && h in 1..11) h += 12
            if (h !in 0..23 || min !in 0..59) null else "%02d:%02d".format(h, min)
        }.toList()
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
