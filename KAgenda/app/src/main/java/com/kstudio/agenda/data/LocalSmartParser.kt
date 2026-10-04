package com.kstudio.agenda.data

import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.RepeatRules
import java.time.LocalDate
import java.time.LocalTime

/**
 * 本地智能识别（完全离线，不依赖任何 AI 接口）。
 *
 * 目标：让「粘贴通知文字 / 导入文档 / 手动录入」在绝大多数情况下直接可用，
 * 只有复杂或高度口语化的指令才需要 DeepSeek 兜底。
 *
 * 能力：
 * 1. [parseEvents]：把一段文字拆成多条日程/计划（支持编号列表、多行详情、一行多个时间段、
 *    日期继承、重复规则、日程/计划自动判别）；
 * 2. [parseCourses]：把课程表文字（表格 / 逐行 / 混合格式）解析为课程，供文档导入与手动添加使用。
 *
 * 单条文本的字段识别（日期/时间/地点/类型）复用 [AgendaTextParser]，此处只做「切分与归类」。
 */
object LocalSmartParser {

    // ------------------------------------------------------------ 事件解析

    /** 计划类关键词（命中则默认归到「计划」页） */
    private val PLAN_WORDS = listOf(
        "计划", "待办", "任务", "目标", "打卡", "习惯", "todo", "Todo", "TODO",
        "减肥", "健身", "跑步", "背单词", "复习计划", "学习计划",
        "作业", "复习", "预习", "刷题", "错题", "练琴", "练字", "读文献", "看论文",
    )

    /** 详情行关键词：这类行并入上一条（避免把 “地点：xxx” 拆成独立事件） */
    private val DETAIL_PREFIXES = listOf(
        "时间", "地点", "地址", "备注", "内容", "参加", "对象", "要求", "联系人", "联系电话",
        "主办", "承办", "方式", "议程", "说明", "费用", "签到",
        "主讲", "面向", "报名", "截止", "注意", "授课", "教室", "学分", "考试范围",
    )

    private val MARKER_REGEX = Regex(
        // 注意：这里**不**把 “9月20日” 这类日期当列表标记——旧写法会把它从行首删掉，导致该条丢失日期
        "^\\s*(?:[-*•·▪▫●○◆◇–—>]+|\\d{1,2}\\s*[.、)）]|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫]|\\(\\d{1,2}\\)|\\[\\d{1,2}])\\s*"
    )

    /** 重复规则匹配（常量：批量解析时逐行调用，避免反复编译正则） */
    private val REPEAT_BIWEEKLY = Regex("(隔周|每隔一周)[^，。；\\n]{0,6}")
    private val REPEAT_PATTERNS = listOf(
        Regex("每(天|日)"),
        Regex("每\\s*[0-9一两二三四五六七八九十]+\\s*(天|日)"),
        Regex("每(周|星期|礼拜)[一二三四五六日天1-7]"),
        Regex("每(周|星期|礼拜)"),
        Regex("隔周[一二三四五六日天1-7]?"),
        Regex("每(个)?月[0-9一二三四五六七八九十]{1,3}\\s*[日号]?"),
    )

    /** 课程字段匹配（常量） */
    private val DAY_CN_REGEX = Regex("(周|星期|礼拜)\\s*([一二三四五六日天1-7])")
    private val DAY_EN_REGEX = Regex("\\b(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\.?")
    private val PERIOD_RANGE_REGEX = Regex("第?\\s*(\\d{1,2})\\s*[-~—到至,，、]\\s*(\\d{1,2})\\s*节")
    private val PERIOD_SINGLE_REGEX = Regex("第?\\s*(\\d{1,2})\\s*节")
    private val PERIOD_TIME_REGEX =
        Regex("(\\d{1,2})[:：](\\d{2})\\s*[-~—到至]\\s*(\\d{1,2})[:：](\\d{2})")
    /** 表格里光写 “1-2” 的节次格（没有“节”字） */
    private val BARE_PERIOD_RANGE_REGEX =
        Regex("(?:^|[^\\d:：])(\\d{1,2})\\s*[-~—–]\\s*(\\d{1,2})\\s*$")
    private val WEEKS_RANGE_REGEX = Regex("第?\\s*(\\d{1,2})\\s*[-~—到至]\\s*(\\d{1,2})\\s*周")
    private val WEEKS_LIST_REGEX = Regex("((?:\\d{1,2}\\s*[,，、]\\s*)+\\d{1,2})\\s*周")
    private val WEEKS_SINGLE_REGEX = Regex("第?\\s*(\\d{1,2})\\s*周")
    private val CODE_REGEX = Regex("[A-Za-z]{2,6}\\d{3,6}[A-Za-z]?")
    private val TEACHER_LABEL_REGEX =
        Regex("(?:教师|老师|授课教师|任课教师)\\s*[:：]?\\s*([\\u4e00-\\u9fa5]{2,4}(?:[,，、][\\u4e00-\\u9fa5]{2,4})*)")
    private val TEACHER_SUFFIX_REGEX = Regex("([\\u4e00-\\u9fa5]{2,4})\\s*(?:老师|教授|讲师|教师)")
    private val ROOM_LABEL_REGEX = Regex("(?:教室|地点|上课地点|场地)\\s*[:：]?\\s*([^\\s,，;；|]{2,20})")
    private val ROOM_CODE_REGEX =
        Regex("([\\u4e00-\\u9fa5]{0,4}[A-Za-z]?\\d{1,2}\\s*[-#]?\\s*\\d{2,4}|[A-Za-z]\\d{2,4})")
    private val CELLS_SPLIT_REGEX = Regex("\\s{2,}")

    /** 场馆关键词（用于从行里摘出地点） */
    private val ROOM_KEYWORDS = listOf(
        "教室", "教学楼", "实验楼", "机房", "实验室", "报告厅", "体育馆", "操场",
        "楼", "室", "馆", "中心", "场地", "校区",
    )

    /** 课程名清理（常量） */
    private val TITLE_PERIOD_REGEX = Regex("第?\\s*\\d{1,2}\\s*[-~—到至,，、]?\\s*\\d{0,2}\\s*节")
    private val TITLE_TIME_REGEX = Regex("\\d{1,2}[:：]\\d{2}\\s*[-~—到至]?\\s*\\d{0,2}[:：]?\\d{0,2}")
    private val TITLE_UNIT_REGEX = Regex("(单周|双周)")
    private val TITLE_SEP_REGEX = Regex("[|\\t]")
    private val TITLE_WS_REGEX = Regex("\\s+")

    /**
     * 多事件解析：返回可直接执行的「新增」操作列表（每条一个日程或计划）。
     * @param forcePlan 强制归类（导入为计划时传 true；自动判别传 null）
     */
    fun parseEvents(
        text: String,
        baseDate: LocalDate = LocalDate.now(),
        forcePlan: Boolean? = null,
    ): List<AiSkills.AiOp.Add> {
        val blocks = splitBlocks(text)
        if (blocks.isEmpty()) return emptyList()

        val out = mutableListOf<AiSkills.AiOp.Add>()
        var lastDate: LocalDate? = null
        val seen = HashSet<String>()

        for (block in blocks) {
            val p = AgendaTextParser.parse(block, baseDate)
            val title = cleanTitle(p.title.ifBlank { firstSentence(block) })
            if (title.isBlank()) continue
            val parsedDate = LocalDate.ofEpochDay(p.dateEpochDay)
            // 日期继承：编号列表常见“9月20日 安排”+ 后续无日期的条目
            val date = if (p.hasDate) parsedDate else lastDate ?: baseDate
            if (p.hasDate) lastDate = parsedDate

            val repeat = findRepeat(block)
            val isPlan = forcePlan ?: PLAN_WORDS.any { block.contains(it, ignoreCase = true) }
            val key = "$title|$date|${p.startTime}|${p.endTime}"
            if (!seen.add(key)) continue

            out.add(
                AiSkills.AiOp.Add(
                    AiSkills.AiItem(
                        isPlan = isPlan,
                        title = title,
                        type = p.type,
                        date = date,
                        startTime = p.startTime,
                        endTime = p.endTime,
                        location = p.location,
                        note = block.trim().take(400),
                        repeat = repeat,
                    )
                )
            )
        }
        return out
    }

    /** 把文本切成「一条事件 = 一个块」 */
    private fun splitBlocks(text: String): List<String> {
        val blocks = mutableListOf<StringBuilder>()

        // 先按行拆分；行内若含多个并列分句（；/;）再细分，覆盖“周一8:00例会；周二9:00评审”
        val candidates = mutableListOf<String>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parts = line.split('；', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1) candidates.addAll(parts) else candidates.add(line)
        }

        for (raw in candidates) {
            val marker = MARKER_REGEX.find(raw)?.takeIf { it.range.first == 0 }
            val bare = raw.replaceFirst(MARKER_REGEX, "").trim()
            if (bare.isEmpty()) continue
            val parsed = AgendaTextParser.parse(bare, LocalDate.now())
            val hasAnchor = parsed.hasDate || parsed.hasTime
            val isDetail = DETAIL_PREFIXES.any { bare.startsWith(it) }
            when {
                blocks.isEmpty() || marker != null -> blocks.add(StringBuilder(bare))
                isDetail || !hasAnchor -> {
                    // 详情行 / 无日期时间的信息行：并入上一条
                    if (blocks.last().isNotEmpty()) blocks.last().append('\n')
                    blocks.last().append(bare)
                }
                else -> blocks.add(StringBuilder(bare))
            }
        }
        return blocks.map { it.toString() }.filter { it.isNotBlank() }
    }

    private fun firstSentence(text: String): String =
        text.trim().lines().firstOrNull { it.isNotBlank() }?.trim()?.take(24).orEmpty()

    /**
     * 标题清洗（通知类文本的通用噪声）：
     * - 去掉开头的来源标注：`【教务处】…`、`（学工部）…`、`[通知]…`；
     * - 去掉结尾的“的通知/的公告/通知/公告”等公文尾巴（保留主体，如“关于放假的安排”）。
     */
    private fun cleanTitle(raw: String): String {
        var s = raw.trim()
        val openers = charArrayOf('【', '［', '[', '（', '(')
        if (s.isNotEmpty() && openers.contains(s[0])) {
            val close = s.indexOfFirst {
                it == '】' || it == '］' || it == ']' || it == '）' || it == ')'
            }
            if (close in 2..12) s = s.substring(close + 1).trim()
        }
        s = s.trimStart('-', '—', '·', '：', ':', ' ').trim()
        for (suffix in listOf("的通知", "的公告", "通知", "公告")) {
            if (s.length > suffix.length + 3 && s.endsWith(suffix)) {
                s = s.removeSuffix(suffix).trim().trimEnd('的', '：', ':', ' ')
                break
            }
        }
        return s.take(40)
    }

    /** 从文本中提取重复规则（“每周二/隔周三/每3天/每月5日”） */
    private fun findRepeat(text: String): String {
        REPEAT_BIWEEKLY.find(text)?.let { m ->
            RepeatRules.parse("隔周" + m.value.removePrefix("隔周").removePrefix("每隔一周"))
                ?.let { if (it.isNotBlank()) return it }
        }
        for (r in REPEAT_PATTERNS) {
            val hit = r.find(text) ?: continue
            // 取该短语及其后最多 6 个汉字/数字（“每周二/四”这类）
            val tail = text.substring(hit.range.first, minOf(text.length, hit.range.last + 6))
                .takeWhile { it.isLetterOrDigit() || it in "周星期礼拜天日月号每/、,，" }
            RepeatRules.parse(tail.ifBlank { hit.value })?.let { if (it.isNotBlank()) return it }
        }
        return ""
    }

    // ------------------------------------------------------------ 课程表解析

    /** 星期关键词 → 1..7 */
    private val DAY_MAP: Map<String, Int> = buildMap {
        val cn = listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
        for ((c, n) in cn) {
            put("周$c", n); put("星期$c", n); put("礼拜$c", n)
        }
        for (n in 1..7) {
            put("周$n", n); put("星期$n", n); put("礼拜$n", n)
        }
        put("mon", 1); put("tue", 2); put("wed", 3); put("thu", 4); put("fri", 5); put("sat", 6); put("sun", 7)
    }

    /** 表头关键词 → 字段 */
    private fun headerField(cell: String): String? {
        val c = cell.trim().lowercase()
        return when {
            c.isBlank() -> null
            listOf("星期", "周几", "weekday", "day").any { c.contains(it) } -> "day"
            listOf("节次", "节", "period", "时间", "时段", "上课时间").any { c == it || c.contains(it) } -> "period"
            listOf("周次", "周数", "weeks", "week").any { c.contains(it) } -> "weeks"
            listOf("教室", "地点", "上课地点", "room", "location", "场地").any { c.contains(it) } -> "room"
            listOf("教师", "老师", "任课", "授课", "teacher").any { c.contains(it) } -> "teacher"
            listOf("课程号", "课号", "代码", "编号", "code").any { c.contains(it) } -> "code"
            listOf("课程", "科目", "课名", "course", "subject", "name").any { c.contains(it) } -> "title"
            else -> null
        }
    }

    /**
     * 课程表文字 → 课程列表。
     * 支持三种常见形态：
     * 1. 分隔符表格（Tab / 逗号 / 竖线 / 多空格），首行为表头；
     * 2. 逐行“课程名 周一 第1-2节 1-16周 教室 教师”；
     * 3. 每门课一块（课程名一行 + 细节行）。
     */
    fun parseCourses(text: String): List<Course> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val out = mutableListOf<Course>()
        // 1) 表格形态
        val header = splitCells(lines[0])
        val mapping = header.map { headerField(it) }
        if (mapping.count { it != null } >= 3 && mapping.contains("title")) {
            for (i in 1 until lines.size) {
                val cells = splitCells(lines[i])
                if (cells.size < 2) continue
                fun col(f: String): String {
                    val idx = mapping.indexOf(f)
                    return if (idx >= 0 && idx < cells.size) cells[idx] else ""
                }
                out += buildCourse(
                    title = col("title"),
                    day = col("day"),
                    period = col("period"),
                    weeks = col("weeks"),
                    room = col("room"),
                    teacher = col("teacher"),
                    code = col("code"),
                ) ?: continue
                continue
            }
            if (out.isNotEmpty()) return dedupe(out)
        }

        // 2/3) 逐行 / 分块
        // 先按“含星期信息”的行切块：不含星期的行作为上一块的补充（例如课程名行 + 时间行）
        val blocks = mutableListOf<StringBuilder>()
        for (line in lines) {
            val hasDay = findDayOfWeek(line) != null
            if (hasDay || blocks.isEmpty()) {
                blocks.add(StringBuilder(line))
            } else {
                // 该行没有星期信息：若上一块已有星期则并入，否则并入上一块（补充课程名/教室）
                if (blocks.last().isNotEmpty()) blocks.last().append(' ')
                blocks.last().append(line)
            }
        }
        for (b in blocks) {
            val block = b.toString()
            val day = findDayOfWeek(block) ?: continue
            val periods = findPeriods(block)
            if (periods == null) continue
            val (sp, ep) = periods
            val title = findCourseTitle(block)
            if (title.isBlank()) continue
            out += Course(
                title = title,
                code = findCode(block),
                teacher = findTeacher(block),
                weeksRaw = findWeeks(block),
                room = findRoom(block),
                startPeriod = sp,
                endPeriod = ep,
                dayOfWeek = day,
            )
        }
        return dedupe(out)
    }

    /** 由表格字段构造课程（字段缺失时尽量从同排其他单元格推断） */
    private fun buildCourse(
        title: String,
        day: String,
        period: String,
        weeks: String,
        room: String,
        teacher: String,
        code: String,
    ): Course? {
        val d = findDayOfWeek(day) ?: return null
        val (sp, ep) = findPeriods(period) ?: return null
        val t = title.trim().ifBlank { return null }
        return Course(
            title = t.take(40),
            code = code.trim(),
            teacher = teacher.trim(),
            weeksRaw = findWeeks(weeks).ifBlank { findWeeks(title) },
            room = room.trim(),
            startPeriod = sp,
            endPeriod = ep,
            dayOfWeek = d,
        )
    }

    private fun dedupe(list: List<Course>): List<Course> {
        val map = LinkedHashMap<String, Course>()
        list.forEach { map[it.id] = it }
        return map.values.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.title }))
    }

    /** 按 Tab / 竖线 / 逗号 / 多个空格切分单元格 */
    private fun splitCells(line: String): List<String> {
        val sep = when {
            line.contains('\t') -> "\t"
            line.contains('|') -> "|"
            line.contains(',') -> ","
            line.contains('，') -> "，"
            CELLS_SPLIT_REGEX.containsMatchIn(line) -> null
            else -> return listOf(line)
        }
        val raw = if (sep == null) line.split(CELLS_SPLIT_REGEX) else line.split(sep)
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun findDayOfWeek(s: String): Int? {
        DAY_EN_REGEX.find(s.lowercase())?.let { m ->
            DAY_MAP[m.value.trim('.', ' ')]?.let { return it }
        }
        DAY_CN_REGEX.find(s)?.let { m ->
            DAY_MAP[(m.groupValues[1] + m.groupValues[2])]?.let { return it }
        }
        return null
    }

    /** 节次：返回 (开始, 结束)；支持“第1-2节 / 1-2节 / 1,2节 / 08:00-09:35” */
    private fun findPeriods(s: String): Pair<Int, Int>? {
        PERIOD_RANGE_REGEX.find(s)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            val b = m.groupValues[2].toIntOrNull() ?: return@let
            if (a in 1..PeriodTimes.MAX_COUNT && b in a..PeriodTimes.MAX_COUNT) return a to b
        }
        PERIOD_SINGLE_REGEX.find(s)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            if (a in 1..PeriodTimes.MAX_COUNT) return a to a
        }
        // 用时间反查节次：08:00-09:35 → 1-2
        PERIOD_TIME_REGEX.find(s)?.let { m ->
            val st = runCatching {
                LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            }.getOrNull() ?: return@let
            val et = runCatching {
                LocalTime.of(m.groupValues[3].toInt(), m.groupValues[4].toInt())
            }.getOrNull() ?: return@let
            val sp = nearestPeriod(st) ?: return@let
            val ep = nearestPeriod(et) ?: return@let
            return sp to maxOf(sp, ep)
        }
        // 兼容从教务系统表格里直接复制的“1-2 / 3-4”（这一格没有“节”字）
        BARE_PERIOD_RANGE_REGEX.find(s)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            val b = m.groupValues[2].toIntOrNull() ?: return@let
            if (a in 1..PeriodTimes.MAX_COUNT && b in a..PeriodTimes.MAX_COUNT) return a to b
        }
        return null
    }

    /** 找出与给定时间最接近的节次（相差不超过 15 分钟才算命中） */
    private fun nearestPeriod(t: LocalTime): Int? {
        var best = -1
        var bestDiff = Long.MAX_VALUE
        for (p in 1..PeriodTimes.count) {
            val diff = kotlin.math.abs(
                PeriodTimes.startOf(p).toSecondOfDay().toLong() - t.toSecondOfDay().toLong()
            )
            if (diff < bestDiff) {
                bestDiff = diff
                best = p
            }
        }
        return best.takeIf { bestDiff <= 15 * 60 }
    }

    /** 周次：“1-16周” / “第2周” / “1,3,5周” / “单周” / “双周”（无则回空串=每周） */
    private fun findWeeks(s: String): String {
        WEEKS_RANGE_REGEX.find(s)?.let { range ->
            val a = range.groupValues[1].toIntOrNull() ?: 0
            val b = range.groupValues[2].toIntOrNull() ?: 0
            if (a in 1..40 && b in a..40) return "$a-$b"
        }
        WEEKS_LIST_REGEX.find(s)?.let { list ->
            val nums = list.groupValues[1].split(',', '，', '、').mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..40 }
            if (nums.isNotEmpty()) return nums.joinToString(",")
        }
        WEEKS_SINGLE_REGEX.find(s)?.let { single ->
            val a = single.groupValues[1].toIntOrNull() ?: 0
            if (a in 1..40) return "$a"
        }
        // 单/双周：展开为显式周次列表（教务系统里常见“1-16周(单)”）
        if (s.contains("单周") || s.contains("(单)") || s.contains("（单）")) {
            return (1..19 step 2).joinToString(",")
        }
        if (s.contains("双周") || s.contains("(双)") || s.contains("（双）")) {
            return (2..20 step 2).joinToString(",")
        }
        return ""
    }

    private fun findCode(s: String): String = CODE_REGEX.find(s)?.value.orEmpty()

    private fun findTeacher(s: String): String {
        // “张三老师/李四教授”或“教师：张三”
        TEACHER_LABEL_REGEX.find(s)?.let { return it.groupValues[1] }
        TEACHER_SUFFIX_REGEX.find(s)?.let {
            return it.groupValues[1] + it.value.substringAfter(it.groupValues[1])
        }
        return ""
    }

    private fun findRoom(s: String): String {
        // 1) “教室：xxx” / “地点：xxx”
        ROOM_LABEL_REGEX.find(s)?.let { return it.groupValues[1] }
        // 2) 形如 “教1-101 / A301 / C1-2003 / 主楼B201”；
        //    必须排除周次/节次片段（“1-16周”“第1-2节”），否则会把周次误当教室
        for (m in ROOM_CODE_REGEX.findAll(s)) {
            val v = m.value.trim()
            val after = s.getOrNull(m.range.last + 1)
            val before = if (m.range.first > 0) s[m.range.first - 1] else null
            if (after == '周' || after == '节' || before == '第') continue
            if (v.isEmpty()) continue
            return v.replace(" ", "")
        }
        // 3) 含场馆关键词的片段
        for (k in ROOM_KEYWORDS) {
            val i = s.indexOf(k)
            if (i > 0) {
                val start = maxOf(0, i - 4)
                return s.substring(start, minOf(s.length, i + 12)).trim()
            }
        }
        return ""
    }

    /** 课程名：去掉星期/节次/周次/教室/教师等已识别片段后剩下的最长中文片段 */
    private fun findCourseTitle(block: String): String {
        var s = block
        s = s.replace(DAY_CN_REGEX, " ")
        s = s.replace(TITLE_PERIOD_REGEX, " ")
        s = s.replace(TITLE_TIME_REGEX, " ")
        s = s.replace(WEEKS_RANGE_REGEX, " ")
        s = s.replace(TITLE_UNIT_REGEX, " ")
        s = s.replace(CODE_REGEX, " ")
        val room = findRoom(s)
        if (room.isNotBlank()) s = s.replace(room, " ")
        val teacher = findTeacher(s)
        if (teacher.isNotBlank()) s = s.replace(teacher, " ")
        return s.replace(TITLE_SEP_REGEX, " ")
            .replace(TITLE_WS_REGEX, " ")
            .trim()
            .trim('-', '—', '·', ',', '，', '、', ':')
            .take(40)
    }
}
