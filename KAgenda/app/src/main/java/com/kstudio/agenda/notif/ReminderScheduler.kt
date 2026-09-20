package com.kstudio.agenda.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kstudio.agenda.data.ScheduleCache
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import org.json.JSONArray
import java.io.File
import java.time.LocalDate

/**
 * 上课提醒调度：
 * - 依据缓存课表 + 设置中的“提前分钟数”，为未来 7 天内每一节课安排闹钟；
 * - 优先使用精确闹钟（需系统授权），未授权时自动降级为窗口闹钟（约 10 分钟误差）；
 * - 同一个（课程 + 日期）重复调度会覆盖旧闹钟，不会重复提醒。
 */
object ReminderScheduler {

    private const val LOOKAHEAD_DAYS = 7
    private const val CODES_FILE = "scheduled_alarms.json"

    suspend fun reschedule(context: Context) {
        val settings = SettingsStore.read(context)
        if (!settings.reminderEnabled) {
            // 关闭提醒时，清掉已排闹钟，避免残留通知
            cancelAll(context)
            return
        }

        val semester = ScheduleCache.load(context)
        if (semester == null) {
            // 课表缓存不存在（尚未同步 / 已被“清缓存”清掉）：取消残留闹钟，
            // 避免按旧课表继续弹出“即将上课”提醒
            cancelAll(context)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // 先取消上一轮调度的闹钟，再统一重排（保证不重复、不错配）
        cancelAll(context)
        val scheduledCodes = mutableListOf<Int>()
        val today = LocalDate.now()
        val nowMillis = System.currentTimeMillis()

        for (offset in 0..LOOKAHEAD_DAYS) {
            val date = today.plusDays(offset.toLong())
            val weekNo = semester.teachingWeekOf(date)
            if (weekNo < 1 || weekNo > 40) continue
            val courses = semester.weeks[weekNo].orEmpty()
                .filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(weekNo) }
            for (course in courses) {
                val triggerAt = PeriodTimes.startMillisEpoch(date, course.startPeriod) -
                    settings.leadMinutes * 60_000L
                if (triggerAt <= nowMillis + 30_000L) continue   // 已过时或即将发生
                scheduledCodes.add(scheduleOne(context, am, triggerAt, course, date))
            }
        }
        writeCodes(context, scheduledCodes)
    }

    private fun scheduleOne(
        context: Context,
        am: AlarmManager,
        triggerAtMillis: Long,
        course: Course,
        date: LocalDate,
    ): Int {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, course.title)
            putExtra(ReminderReceiver.EXTRA_ROOM, course.room)
            putExtra(ReminderReceiver.EXTRA_TIME_RANGE, course.timeRange)
            putExtra(ReminderReceiver.EXTRA_PERIOD_LABEL, course.periodLabel)
            putExtra(ReminderReceiver.EXTRA_DATE, date.toString())
        }
        val requestCode = (course.id + "@" + date).hashCode()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // 未授予“闹钟和提醒”权限时降级：低功耗下仍可唤醒（比 setWindow 更可靠，无 10 分钟窗口延迟）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
        return requestCode
    }

    /** 取消所有已排闹钟（用于关闭提醒 / 重置应用） */
    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val codes = readCodes(context)
        if (am != null) {
            for (code in codes) {
                val pi = PendingIntent.getBroadcast(
                    context,
                    code,
                    Intent(context, ReminderReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pi != null) {
                    am.cancel(pi)
                    pi.cancel()
                }
            }
        }
        writeCodes(context, emptyList())
    }

    private fun readCodes(context: Context): List<Int> = try {
        val file = File(context.filesDir, CODES_FILE)
        if (!file.exists()) {
            emptyList()
        } else {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val result = mutableListOf<Int>()
            for (i in 0 until arr.length()) {
                val value = arr.optInt(i, Int.MIN_VALUE)
                if (value != Int.MIN_VALUE) result.add(value)
            }
            result
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun writeCodes(context: Context, codes: List<Int>) {
        try {
            val arr = JSONArray()
            codes.forEach { arr.put(it) }
            File(context.filesDir, CODES_FILE).writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    /** 供设置页/开发者工具展示：当前是否可调度精确闹钟 */
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return canExact(am)
    }

    /** Android 12 起才有 canScheduleExactAlarms 门槛；12 以下无需授权即可精确闹钟 */
    private fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    /** 供开发者工具展示：当前已排提醒数量 */
    fun scheduledCount(context: Context): Int = readCodes(context).size

    /** 是否已忽略电池优化（后台提醒可靠性相关） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 系统“电池优化”白名单授权页的 Intent（供设置页用启动器打开并在返回时刷新状态） */
    @android.annotation.SuppressLint("BatteryLife")
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(android.net.Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 跳转系统“电池优化”白名单授权页（请求忽略电池优化） */
    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching { context.startActivity(batteryExemptionIntent(context)) }
    }
}
