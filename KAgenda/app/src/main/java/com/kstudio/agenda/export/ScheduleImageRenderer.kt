package com.kstudio.agenda.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.kstudio.agenda.i18n.AppStrings
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.CoursePalette
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.model.WeekSchedule
import java.time.LocalDate

/**
 * 将课表绘制成相册图片（不依赖屏幕截图，因此可完整导出全部内容）。
 * 日课表 / 周课表 / 月课表三个版本，尺寸 1080px 宽；均包含“我的日程”。
 */
object ScheduleImageRenderer {

    private const val W = 1080
    private const val PAD = 36f

    private val BG = Color.parseColor("#F6F8FB")
    private val CARD = Color.WHITE
    private val TEXT_MAIN = Color.parseColor("#0F172A")
    private val TEXT_SUB = Color.parseColor("#64748B")
    private val TEXT_LIGHT = Color.parseColor("#94A3B8")
    private val LINE = Color.parseColor("#E2E8F0")
    private val ACCENT = Color.parseColor("#1D4ED8")
    private val AGENDA_DEFAULT = Color.parseColor("#FB8C00")

    /** 当前语言下的星期短名数组（周一/Mon/Lun…） */
    private fun weekdayNames(t: AppStrings): Array<String> = Array(7) { t.weekdayShort(it + 1) }

    // ---------------------------------------------------------------- 日课表

    fun renderDay(
        week: WeekSchedule,
        date: LocalDate,
        events: List<AgendaEvent> = emptyList(),
    ): Bitmap {
        val t = AppText.current
        val courses = week.coursesOnDate(date)
        val headerH = 250f
        val blockH = 210f
        val gap = 20f
        val evBlockH = 170f
        val footerH = 90f
        val listH = if (courses.isEmpty()) 260f else courses.size * (blockH + gap)
        val agendaH = if (events.isEmpty()) 0f else 80f + events.size * (evBlockH + gap)
        val height = (headerH + listH + agendaH + footerH).toInt()

        val bitmap = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(BG) }

        val title = t.dayImageTitle(date.monthValue, date.dayOfMonth, date.dayOfWeek.value)
        val sub = buildString {
            if (week.semesterLabel.isNotBlank()) append(week.semesterLabel).append(" · ")
            append(t.weekNo(week.teachingWeekOf(date)))
            append(" · ").append(t.courseCount(courses.size))
            if (events.isNotEmpty()) append(" · ").append(t.eventCount(events.size))
        }
        canvas.drawText(title, PAD, 110f, paintText(64f, TEXT_MAIN, bold = true))
        canvas.drawText(sub, PAD, 168f, paintText(32f, TEXT_SUB))
        drawBrand(canvas, 200f, t)

        if (courses.isEmpty()) {
            val rect = RectF(PAD, headerH, W - PAD, headerH + 200f)
            canvas.drawRoundRect(rect, 28f, 28f, paintFill(CARD))
            canvas.drawText(t.dayNoCoursesImage, PAD + 48f, headerH + 118f, paintText(40f, TEXT_SUB))
        } else {
            var y = headerH
            courses.forEach { course ->
                val color = CoursePalette.colorFor(course)
                val rect = RectF(PAD, y, W - PAD, y + blockH)
                canvas.drawRoundRect(rect, 28f, 28f, paintFill(CARD))
                // 左侧色条
                val bar = RectF(PAD, y + 26f, PAD + 16f, y + blockH - 26f)
                canvas.drawRoundRect(bar, 8f, 8f, paintFill(color))

                canvas.drawText(
                    course.timeRange + "  ·  " + course.periodLabel,
                    PAD + 56f, y + 62f, paintText(34f, color, bold = true)
                )
                canvas.drawText(
                    trim(course.title, 34f, W - PAD * 2 - 56f),
                    PAD + 56f, y + 122f, paintText(46f, TEXT_MAIN, bold = true)
                )
                val info = listOfNotNull(
                    course.teacher.ifBlank { null },
                    course.room.ifBlank { null },
                ).joinToString("  ·  ")
                if (info.isNotBlank()) {
                    canvas.drawText(trim(info, 30f, W - PAD * 2 - 56f), PAD + 56f, y + 172f, paintText(30f, TEXT_SUB))
                }
                val meta = buildString {
                    if (course.code.isNotBlank()) append(course.code)
                    if (course.weeksRaw.isNotBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(t.weeksValue(course.weeksRaw))
                    }
                }
                if (meta.isNotBlank()) {
                    canvas.drawText(meta, PAD + 56f, y + 200f, paintText(24f, TEXT_LIGHT))
                }
                y += blockH + gap
            }
        }

        // ---------------- 我的日程 -------------
        if (events.isNotEmpty()) {
            var y = headerH + listH + 10f
            canvas.drawText(t.myAgenda, PAD, y + 40f, paintText(40f, TEXT_MAIN, bold = true))
            y += 80f
            events.forEach { ev ->
                val color = if (ev.displayColor != 0) ev.displayColor else AGENDA_DEFAULT
                val rect = RectF(PAD, y, W - PAD, y + evBlockH)
                canvas.drawRoundRect(rect, 28f, 28f, paintFill(CARD))
                val bar = RectF(PAD, y + 26f, PAD + 16f, y + evBlockH - 26f)
                canvas.drawRoundRect(bar, 8f, 8f, paintFill(color))
                val label = listOfNotNull(
                    t.typeLabel(ev.type).ifBlank { null },
                    if (ev.isLong) t.tagLong else null,
                    ev.rangeLabel.ifBlank { null },
                ).joinToString("  ·  ").ifBlank { t.tagAgenda }
                canvas.drawText(label, PAD + 56f, y + 56f, paintText(30f, color, bold = true))
                canvas.drawText(
                    trim(ev.title, 46f, W - PAD * 2 - 56f),
                    PAD + 56f, y + 116f, paintText(46f, TEXT_MAIN, bold = true)
                )
                val info = listOfNotNull(
                    ev.location.ifBlank { null },
                    ev.note.ifBlank { null },
                ).joinToString("  ·  ")
                if (info.isNotBlank()) {
                    canvas.drawText(
                        trim(info, 28f, W - PAD * 2 - 56f),
                        PAD + 56f, y + 158f, paintText(28f, TEXT_SUB)
                    )
                }
                y += evBlockH + gap
            }
        }

        drawFooter(canvas, height - footerH, week, t)
        return bitmap
    }

    // ---------------------------------------------------------------- 周课表

    fun renderWeek(
        week: WeekSchedule,
        events: List<AgendaEvent> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): Bitmap {
        val t = AppText.current
        val wd = weekdayNames(t)
        val headerH = 250f
        val dayHeadH = 90f
        val rowH = 140f
        val timeColW = 118f
        val footerH = 90f
        val evRowH = 72f
        val gridTop = headerH + dayHeadH
        val agendaH = if (events.isEmpty()) 0f else 90f + events.size * evRowH
        val height = (gridTop + PeriodTimes.count * rowH + agendaH + footerH).toInt()

        val bitmap = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(BG) }

        val title = t.weekImageTitle + (if (week.semesterLabel.isNotBlank()) " · ${week.semesterLabel}" else "")
        val sub = buildString {
            append(t.weekNo(week.weekNo))
            if (week.weekRangeLabel.isNotBlank()) append("（${week.weekRangeLabel}）")
            append(" · ").append(t.courseCount(week.courses.size))
            if (events.isNotEmpty()) append(" · ").append(t.eventCount(events.size))
        }
        canvas.drawText(title, PAD, 110f, paintText(64f, TEXT_MAIN, bold = true))
        canvas.drawText(sub, PAD, 168f, paintText(32f, TEXT_SUB))
        drawBrand(canvas, 200f, t)

        val dayW = (W - PAD * 2 - timeColW) / 7f

        // 星期表头
        for (d in 0 until 7) {
            val x = PAD + timeColW + d * dayW
            val isToday = week.containsDate(today) && today.dayOfWeek.value == d + 1
            if (isToday) {
                canvas.drawRoundRect(
                    RectF(x + 6, headerH + 12, x + dayW - 6, headerH + dayHeadH - 12),
                    18f, 18f, paintFill(Color.parseColor("#DBEAFE"))
                )
            }
            val namePaint = paintText(34f, if (isToday) ACCENT else TEXT_MAIN, bold = isToday).apply {
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(wd[d], x + dayW / 2, headerH + 58f, namePaint)
        }

        // 网格
        val gridPaint = paintStroke(LINE, 2f)
        for (p in 0 until PeriodTimes.count) {
            val top = gridTop + p * rowH
            canvas.drawRect(PAD, top, W - PAD, top + rowH, gridPaint)
            // 时间列
            canvas.drawText(
                t.periodNoShort(p + 1), PAD + timeColW / 2, top + 44f,
                paintText(24f, TEXT_MAIN, bold = true).apply { textAlign = Paint.Align.CENTER }
            )
            canvas.drawText(
                PeriodTimes.format(PeriodTimes.startOf(p + 1)), PAD + timeColW / 2, top + 82f,
                paintText(20f, TEXT_LIGHT).apply { textAlign = Paint.Align.CENTER }
            )
        }
        for (d in 0..7) {
            val x = PAD + timeColW + d * dayW
            canvas.drawLine(x, gridTop, x, gridTop + PeriodTimes.count * rowH, gridPaint)
        }
        canvas.drawLine(PAD, gridTop + PeriodTimes.count * rowH, W - PAD, gridTop + PeriodTimes.count * rowH, gridPaint)

        // 课程块
        for (course in week.courses) {
            val d = course.dayOfWeek - 1
            if (d !in 0..6) continue
            val color = CoursePalette.colorFor(course)
            val x = PAD + timeColW + d * dayW
            val top = gridTop + (course.startPeriod - 1) * rowH + 6f
            val bottom = gridTop + course.endPeriod * rowH - 6f
            val rect = RectF(x + 6f, top, x + dayW - 6f, bottom)
            canvas.drawRoundRect(rect, 16f, 16f, paintFill(CoursePalette.lightOf(color, 60)))
            val bar = RectF(rect.left, rect.top + 10f, rect.left + 10f, rect.bottom - 10f)
            canvas.drawRoundRect(bar, 5f, 5f, paintFill(color))

            val textPaint = paintText(26f, TEXT_MAIN, bold = true)
            val maxW = rect.width() - 40f
            var ty = rect.top + 46f
            trimmedLines(course.title, textPaint, maxW, 3).forEach { line ->
                canvas.drawText(line, rect.left + 26f, ty, textPaint)
                ty += 34f
            }
            val subPaint = paintText(22f, TEXT_SUB)
            trimmedLines(course.room, subPaint, maxW, 1).forEach { line ->
                canvas.drawText(line, rect.left + 26f, ty + 6f, subPaint)
            }
        }

        // ---------------- 我的日程（本周） ----------------
        if (events.isNotEmpty()) {
            var y = gridTop + PeriodTimes.count * rowH + 14f
            canvas.drawText(t.myAgendaWeek, PAD, y + 44f, paintText(40f, TEXT_MAIN, bold = true))
            y += 86f
            for (ev in events) {
                val color = if (ev.displayColor != 0) ev.displayColor else AGENDA_DEFAULT
                val d = ev.date
                val dotRect = RectF(PAD + 6f, y + 16f, PAD + 34f, y + 44f)
                canvas.drawRoundRect(dotRect, 14f, 14f, paintFill(color))
                val text = buildString {
                    append("${d.monthValue}/${d.dayOfMonth} ${wd[d.dayOfWeek.value - 1]}")
                    if (ev.rangeLabel.isNotBlank()) append("  ").append(ev.rangeLabel)
                    append("  ")
                    val typeLabel = t.typeLabel(ev.type)
                    if (typeLabel.isNotBlank()) append("[").append(typeLabel).append("] ")
                    append(ev.title)
                    if (ev.location.isNotBlank()) append("  @").append(ev.location)
                }
                canvas.drawText(
                    trim(text, 30f, W - PAD * 2 - 60f),
                    PAD + 48f, y + 42f, paintText(30f, TEXT_MAIN)
                )
                y += evRowH
            }
        }

        drawFooter(canvas, height - footerH, week, t)
        return bitmap
    }

    // ---------------------------------------------------------------- 月课表

    /**
     * 月课表：月历网格 + 每日课程/日程迷你条。
     * 课程按教学周+星期匹配；日程按覆盖日期匹配（含跨天长日程）。
     * 导出图会“完整显示”所有时间日程：格子高度随条目数自适应，不再截断，且日程带 HH:mm 时间标记。
     */
    fun renderMonth(
        monthStart: LocalDate,
        semester: SemesterSchedule?,
        events: List<AgendaEvent>,
    ): Bitmap {
        val t = AppText.current
        val headerH = 240f
        val dayHeadH = 76f
        val footerH = 90f
        val leading = monthStart.dayOfWeek.value - 1
        val daysInMonth = monthStart.lengthOfMonth()
        val rows = (leading + daysInMonth + 6) / 7
        val gridTop = headerH + dayHeadH

        // 先统计每天条目（课程 + 日程），用于自适应格子高度
        val dayBars = HashMap<Int, MutableList<Pair<String, Int>>>()
        var maxBars = 0
        for (dayNum in 1..daysInMonth) {
            val date = monthStart.withDayOfMonth(dayNum)
            val bars = mutableListOf<Pair<String, Int>>()
            if (semester != null) {
                val weekNo = semester.teachingWeekOf(date)
                semester.weeks[weekNo]
                    ?.filter { it.dayOfWeek == date.dayOfWeek.value }
                    ?.sortedBy { it.startPeriod }
                    ?.forEach { bars.add(it.title to CoursePalette.colorFor(it)) }
            }
            events.filter { it.coversDate(date) }
                .sortedWith(compareBy({ com.kstudio.agenda.model.FuzzyTime.sortKey(it.startTime) }))
                .forEach {
                    // 时间标记：有开始时间时以 "HH:mm 标题" 完整展示
                    val label = if (it.startTime.isNotBlank()) "${it.startTime} ${it.title}" else it.title
                    bars.add(label to (if (it.displayColor != 0) it.displayColor else AGENDA_DEFAULT))
                }
            dayBars[dayNum] = bars
            if (bars.size > maxBars) maxBars = bars.size
        }
        // 格子高度随最多条目数自适应（不再出现 +N 截断）
        val cellH = maxOf(150f, 74f + maxBars * 30f + 12f)

        val height = (gridTop + rows * cellH + footerH).toInt()
        val bitmap = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(BG) }

        val title = t.monthImageTitle(monthStart.year, monthStart.monthValue)
        val sub = if (semester != null && semester.semesterLabel.isNotBlank()) {
            semester.semesterLabel
        } else {
            t.viewMonth
        }
        canvas.drawText(title, PAD, 110f, paintText(56f, TEXT_MAIN, bold = true))
        canvas.drawText(sub, PAD, 166f, paintText(30f, TEXT_SUB))
        drawBrand(canvas, 200f, t)

        // 星期表头
        val cellW = (W - PAD * 2) / 7f
        for (i in 0 until 7) {
            val x = PAD + i * cellW
            canvas.drawText(
                t.weekdayShort(i + 1), x + cellW / 2, gridTop - 26f,
                paintText(30f, TEXT_SUB, bold = true).apply { textAlign = Paint.Align.CENTER }
            )
        }

        // 逐格绘制（展示全部条目）
        for (r in 0 until rows) {
            for (c in 0 until 7) {
                val dayNum = r * 7 + c - leading + 1
                val cellX = PAD + c * cellW
                val cellTop = gridTop + r * cellH
                val cellRect = RectF(cellX + 4f, cellTop + 4f, cellX + cellW - 4f, cellTop + cellH - 4f)
                if (dayNum !in 1..daysInMonth) {
                    canvas.drawRoundRect(cellRect, 16f, 16f, paintFill(Color.parseColor("#EDF1F7")))
                    continue
                }
                canvas.drawRoundRect(cellRect, 16f, 16f, paintFill(CARD))
                canvas.drawText(
                    "$dayNum", cellX + 16f, cellTop + 40f,
                    paintText(30f, TEXT_MAIN, bold = true)
                )

                var ty = cellTop + 74f
                for ((text, color) in dayBars[dayNum].orEmpty()) {
                    val barRect = RectF(cellX + 10f, ty - 24f, cellX + cellW - 10f, ty + 6f)
                    canvas.drawRoundRect(barRect, 8f, 8f, paintFill(CoursePalette.lightOf(color, 60)))
                    canvas.drawText(
                        trim(text, 20f, cellW - 44f),
                        cellX + 16f, ty, paintText(20f, color)
                    )
                    ty += 30f
                }
            }
        }

        drawFooterTime(canvas, height - footerH, t)
        return bitmap
    }

    // ---------------------------------------------------------------- 工具

    private fun drawBrand(canvas: Canvas, baseline: Float, t: AppStrings) {
        val p = paintText(28f, TEXT_LIGHT).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(t.brand, W - PAD, baseline, p)
    }

    private fun drawFooter(canvas: Canvas, top: Float, week: WeekSchedule, t: AppStrings) {
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(week.fetchedAtMillis))
        canvas.drawText(t.syncTimeLabel + time, PAD, top + 56f, paintText(24f, TEXT_LIGHT))
    }

    private fun drawFooterTime(canvas: Canvas, top: Float, t: AppStrings) {
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date())
        canvas.drawText(t.exportTimeLabel + time, PAD, top + 56f, paintText(24f, TEXT_LIGHT))
    }

    private fun paintText(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun paintFill(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

    private fun paintStroke(color: Int, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color
            strokeWidth = width
        }

    private fun trim(text: String, size: Float, maxWidth: Float): String {
        val paint = paintText(size, TEXT_MAIN)
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText(t + "…") > maxWidth) {
            t = t.dropLast(1)
        }
        return t + "…"
    }

    /** 将文本折成最多 maxLines 行（中文按字符折行） */
    private fun trimmedLines(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        for (ch in text) {
            if (paint.measureText(current + ch) > maxWidth) {
                lines.add(current)
                current = ""
                if (lines.size == maxLines) break
            }
            current += ch
        }
        if (lines.size < maxLines && current.isNotEmpty()) lines.add(current)
        if (lines.size == maxLines) {
            val last = lines.last()
            if (paint.measureText(last) > maxWidth - 20f) {
                lines[lines.size - 1] = last.dropLast(1) + "…"
            } else if (text.length > lines.joinToString("").length) {
                lines[lines.size - 1] = last + "…"
            }
        }
        return lines
    }
}
