package com.kstudio.agenda.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 「粘贴通知文字 → 日程要素」启发式解析器。
 *
 * 支持识别（尽量取文本中最早出现的事件）：
 * - 日期：今天 / 明天 / 后天 / 大后天 / 本周日 / 下周五 / 周三 / 9.18 / 9月19日 / 9/24 / 2026-09-24
 * - 时间：下午3点50分 / 8点20 / 下午2点到4点（缺省半天自动推断） / 晚上7点半 / 十点（中文数字） /
 *   上午10:30-11点30 / 14:00-15:30 / 全角数字与全角冒号自动归一
 * - 地点：地点：xxx / 在·于·前往xxx参加(集合/面试/举办/召幀…) / C1-2003/2004 / B座201室 /
 *   南区操场 / 教学楼2的5002教室 等场馆后缀
 * - 类型：面试·招聘 / 比赛·竞赛 / 讲座·宣讲 / 考试·测验 / 会议·班会 等（按关键词先出现者优先）
 * - 标题：【标题】优先（过滤“【通知】”这类泛化抬头），否则取首个有效句子
 */
object AgendaTextParser {

    data class Parsed(
        val title: String,
        val type: String,           // AgendaTypes 键（interview/…；未识别为空串）
        val dateEpochDay: Long,
        val startTime: String,      // "HH:mm"（未识别为空串）
        val endTime: String,        // "HH:mm"（未识别为空串）
        val location: String,
        val note: String,
        val hasDate: Boolean,
        val hasTime: Boolean,
    )

    // ------------------------------------------------------------ 入口

    fun parse(text: String, baseDate: LocalDate): Parsed {
        val norm = normalize(text)
        val dateMatch = findDate(norm, baseDate)          // Pair(位置, 日期)
        val timeMatch = findTime(norm, dateMatch?.first ?: -1)
        val date = dateMatch?.second ?: baseDate
        return Parsed(
            title = findTitle(text),
            type = findType(norm),
            dateEpochDay = date.toEpochDay(),
            startTime = timeMatch?.first?.let { formatTime(it) } ?: "",
            endTime = timeMatch?.second?.let { formatTime(it) } ?: "",
            location = findLocation(norm),
            note = text.trim().take(400),
            hasDate = dateMatch != null,
            hasTime = timeMatch != null,
        )
    }

    // ------------------------------------------------------------ 规范化

    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when {
                    ch in '０'..'９' -> '0' + (ch - '０')        // 全角数字 → 半角
                    ch == '：' -> ':'
                    ch == '．' -> '.'
                    ch == '～' -> '~'
                    ch == '－' || ch == '—' || ch == '–' -> '-'
                    ch == '\u3000' -> ' '
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    // ------------------------------------------------------------ 日期

    private fun findDate(norm: String, base: LocalDate): Pair<Int, LocalDate>? {
        val found = mutableListOf<Pair<Int, LocalDate>>()

        // 相对日期：今天 / 明天 / 后天 / 大后天（“后天”排除前面带“大”的误配）
        for ((kw, offset) in listOf("大后天" to 3L, "今天" to 0L, "明天" to 1L, "后天" to 2L)) {
            val idx = norm.indexOf(kw)
            if (idx >= 0) {
                if (kw == "后天" && idx > 0 && norm[idx - 1] == '大') continue
                found.add(idx to base.plusDays(offset))
            }
        }

        // 星期：本周日 / 这周日 / 下周五 / 周三
        val weekdayRegex = Regex("(本周|这周|下周|周|星期|礼拜)([一二三四五六日天])")
        for (m in weekdayRegex.findAll(norm)) {
            val prefix = m.groupValues[1]
            val cn = m.groupValues[2]
            val dow = when (cn) {
                "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
            }
            val monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var date = monday.plusDays((dow - 1).toLong())
            when (prefix) {
                "下周" -> date = date.plusWeeks(1)
                "本周", "这周" -> Unit
                else -> if (date.isBefore(base)) date = date.plusWeeks(1)   // 裸“周三”：取最近的将来
            }
            found.add(m.range.first to date)
        }

        // 显式年份：2026-09-24 / 2026年9月24日 / 2026/9/24
        val fullDateRegex = Regex("(20\\d{2})\\s*[-年/]\\s*(\\d{1,2})\\s*[-月/]\\s*(\\d{1,2})\\s*[日号]?")
        for (m in fullDateRegex.findAll(norm)) {
            val y = m.groupValues[1].toIntOrNull() ?: continue
            val month = m.groupValues[2].toIntOrNull() ?: continue
            val day = m.groupValues[3].toIntOrNull() ?: continue
            val date = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: continue
            found.add(m.range.first to date)
        }

        // 绝对日期：9.18 / 9月19日 / 10.10 / 9/24
        val dateRegex = Regex("(\\d{1,2})\\s*[.月/]\\s*(\\d{1,2})\\s*[日号]?")
        for (m in dateRegex.findAll(norm)) {
            val month = m.groupValues[1].toIntOrNull() ?: continue
            val day = m.groupValues[2].toIntOrNull() ?: continue
            if (month !in 1..12 || day !in 1..31) continue
            // 防误配：如 "3.50"（时间）因 month=3 day=50 被上面的范围检查排除；"2026.9" 里 "26.9" 月=26 也被排除
            var date = runCatching { LocalDate.of(base.year, month, day) }.getOrNull() ?: continue
            if (date.isBefore(base.minusDays(180))) {
                date = runCatching { LocalDate.of(base.year + 1, month, day) }.getOrNull() ?: continue
            }
            found.add(m.range.first to date)
        }

        return found.minByOrNull { it.first }
    }

    // ------------------------------------------------------------ 时间

    private val timeToken = Regex(
        "(上午|下午|晚上|傍晚|中午|早上|早晨|凌晨|晚)?\\s*(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*[点时:]\\s*(半|\\d{1,2}\\s*分?)?"
    )

    private fun findTime(norm: String, dateIndex: Int): Pair<LocalTime, LocalTime?>? {
        data class Token(val index: Int, val time: LocalTime)

        val tokens = mutableListOf<Token>()
        for (m in timeToken.findAll(norm)) {
            val marker = m.groupValues[1].ifBlank { null }
            val h = parseHour(m.groupValues[2]) ?: continue
            val min = parseMinute(m.groupValues[3]) ?: continue
            val t = toTime(marker, h, min) ?: continue
            tokens.add(Token(m.range.first, t))
        }
        if (tokens.isEmpty()) return null

        // 优先取离日期最近的 token（相隔 20 字符内），否则取最早出现的
        val anchor = if (dateIndex >= 0) {
            tokens.filter { kotlin.math.abs(it.index - dateIndex) <= 20 }
                .minByOrNull { kotlin.math.abs(it.index - dateIndex) }
        } else null
        val first = anchor ?: tokens.minByOrNull { it.index } ?: return null

        // 区间结束时间：紧随其后的 “- 11点30 / ~15:30 / 至16:00 / 到17:00”
        val tail = norm.substring(first.index)
        val rangeRegex = Regex(
            ".{1,24}?[-~至到]\\s*(上午|下午|晚上|傍晚|中午|早上|早晨|凌晨|晚)?\\s*(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*[点时:]\\s*(半|\\d{1,2}\\s*分?)?"
        )
        for (m in rangeRegex.findAll(tail)) {
            // 不得跨句号/换行
            val between = m.value
            if (between.contains('。') || between.contains('\n')) continue
            val marker = m.groupValues[1].ifBlank { null }
            val h = parseHour(m.groupValues[2]) ?: continue
            val min = parseMinute(m.groupValues[3]) ?: continue
            var end = toTime(marker, h, min) ?: continue
            // 缺省半天推断：如下午2点到4点 → 16:00（结束早于开始且起始在下午时 +12h）
            if (!end.isAfter(first.time) && marker == null && first.time.hour >= 12 && end.hour < 12) {
                val bumped = end.plusHours(12)
                if (bumped.hour in 12..23) end = bumped
            }
            if (end.isAfter(first.time)) return first.time to end
        }
        return first.time to null
    }

    /** 小时：阿拉伯数字或中文数字（常见 1~24 写法） */
    private fun parseHour(raw: String): Int? {
        val s = raw.trim()
        s.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        return when {
            s == "十" -> 10
            s.startsWith("十") -> digits[s.getOrNull(1)]?.let { 10 + it }
            s.endsWith("十") -> digits[s.firstOrNull()]?.let { it * 10 }
            s.contains('十') -> {
                val i = s.indexOf('十')
                val a = digits[s.getOrNull(i - 1)] ?: 1
                val b = digits[s.getOrNull(i + 1)] ?: 0
                a * 10 + b
            }
            s.length == 1 -> digits[s[0]]
            else -> null
        }
    }

    /** 分钟：“半”=30；空白=0；否则取数字 */
    private fun parseMinute(raw: String): Int? {
        val s = raw.trim()
        return when {
            s == "半" -> 30
            s.isBlank() -> 0
            else -> s.filter { it.isDigit() }.toIntOrNull() ?: 0
        }.takeIf { it in 0..59 }
    }

    private fun toTime(marker: String?, h: Int, min: Int): LocalTime? {
        if (h !in 0..23 || min !in 0..59) return null
        var hh = h
        when (marker) {
            "下午", "晚上", "傍晚", "晚" -> if (hh < 12) hh += 12
            "中午" -> if (hh <= 6) hh += 12
            "凌晨" -> if (hh == 12) hh = 0
        }
        return runCatching { LocalTime.of(hh, min) }.getOrNull()
    }

    private fun formatTime(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    // ------------------------------------------------------------ 类型

    private val typeKeywords: List<Pair<String, List<String>>> = listOf(
        "interview" to listOf("面试", "招聘", "双选", "笔试"),
        "contest" to listOf("比赛", "大赛", "竞赛", "选拔赛", "挑战赛", "选拔", "初赛", "复赛", "决赛"),
        "lecture" to listOf("讲座", "宣讲", "大师课", "报告会", "论坛", "公开课", "分享会", "研讨会"),
        "exam" to listOf("考试", "测试", "测验", "考核", "测评", "模拟考", "期中考", "期末考", "答辩"),
        "meeting" to listOf("会议", "开会", "见面会", "例会", "班会", "班委", "座谈会", "组会", "研讨"),
    )

    /** 按关键词在正文中出现的先后顺序判定类型（先出现者优先，同位置时按上表顺序） */
    private fun findType(norm: String): String {
        var bestIndex = Int.MAX_VALUE
        var bestOrder = Int.MAX_VALUE
        var bestKey = ""
        for ((order, entry) in typeKeywords.withIndex()) {
            for (word in entry.second) {
                val i = norm.indexOf(word)
                if (i >= 0 && (i < bestIndex || (i == bestIndex && order < bestOrder))) {
                    bestIndex = i
                    bestOrder = order
                    bestKey = entry.first
                }
            }
        }
        return bestKey
    }

    // ------------------------------------------------------------ 标题

    /** 泛化抬头：这类括号标题不单独作为标题，回退到正文首句 */
    private val genericBrackets = setOf("通知", "公告", "温馨提示", "提醒", "重要提醒", "重要通知", "紧急通知")

    private fun findTitle(text: String): String {
        for (m in Regex("【([^】]{2,30})】").findAll(text)) {
            val v = m.groupValues[1].trim()
            if (v !in genericBrackets) return v
        }

        val firstLine = text.trim().lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.isBlank()) return ""
        for (raw in firstLine.split('。', '！', '？', '\n', '；', '，', ',', '、')) {
            var seg = raw.trim()
            // 去掉开头的 【xx】 括号块（保留其后的正文）
            if (seg.startsWith("【")) {
                val e = seg.indexOf('】')
                if (e in 1..30) seg = seg.substring(e + 1).trim()
            }
            if (seg.length >= 4 &&
                !seg.startsWith("各位") && !seg.startsWith("亲爱的") && !seg.startsWith("尊敬的") &&
                !seg.startsWith("大家好") && !seg.startsWith("同学们") && !seg.startsWith("老师们")
            ) {
                return seg.take(24)
            }
        }
        return firstLine.take(24)
    }

    // ------------------------------------------------------------ 地点

    private fun findLocation(norm: String): String {
        // 1) “地点：xxx” / “活动地点 xxx”
        Regex("地点\\s*[:]?\\s*([^，,。；;、（）(){}\\n]{2,24})").find(norm)?.let {
            val v = cleanLocation(it.groupValues[1])
            if (v.isNotBlank()) return v
        }
        // 2) “在 / 于 / 前往 / 到达 xxx 参加(集合/面试/举办/召开…)”：动词候选尽量多
        Regex(
            "(?:前往|到达|抵达|在|到|于)\\s*([^，,。；;、（）(){}\\n]{2,20}?)\\s*" +
                "(?:参加|集合|列队|就坐|进行|完成|上课|召开|举行|举办|开展|办理|领取|面试|笔试|报到|签到|候场|体检|考试|测试)"
        ).find(norm)?.let {
            val v = cleanLocation(it.groupValues[1])
            if (v.isNotBlank()) return v
        }
        // 3) 房间号形态：C1-2003 / C1-2003/2004 / A-305
        Regex("([A-Za-z]\\d{1,2}-\\d{2,4}(?:/\\d{2,4})?)").find(norm)?.let {
            return it.groupValues[1]
        }
        // 3b) 座/栋形态：B座201室 / 2号楼301 / 301室
        Regex("((?:[A-Za-z]?\\d{1,2}(?:号楼|号馆|栋|座)[A-Za-z]?\\d{0,4}室?)|(?:[A-Za-z]?\\d{3,4}室))").find(norm)?.let {
            return it.groupValues[1]
        }
        // 4) 场馆后缀形态（允许数字与“之/的”）：南区操场 / 音乐厅 / 教学楼2的5002教室
        Regex(
            "([\\u4e00-\\u9fa5A-Za-z0-9的]{2,18}(?:教室|音乐厅|报告厅|大礼堂|礼堂|操场|广场|大厅|排练室|活动室|会议室|体育馆|游泳馆|田径场|篮球场|足球场|图书馆|食堂|活动中心|学生中心|多功能厅|演艺厅|机房|实验室|实验楼|教学楼|宿舍楼|服务大厅|场馆))"
        ).find(norm)?.let {
            val v = cleanLocation(it.groupValues[1])
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun cleanLocation(raw: String): String {
        var v = raw.trim()
        for (p in listOf("到达", "前往", "位于", "在", "去", "到")) {
            if (v.startsWith(p) && v.length > p.length + 2) v = v.removePrefix(p)
        }
        return v.trim().trim('（', '）', '(', ')').take(24)
    }
}
