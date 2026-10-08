package com.kstudio.agenda

import android.app.Application
import com.kstudio.agenda.data.ScheduleRepository
import com.kstudio.agenda.data.SchoolFlows
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.model.HolidayTable
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.notif.Notifier
import com.kstudio.agenda.notif.SyncWorker
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.runBlocking

class KebiaoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 日志系统（设置页可查看/分享）
        AppLog.init(this)
        // 全局崩溃兜底：把未捕获异常写入日志并同步落盘，保证崩溃现场可被分享排查
        installCrashLogger()
        // 加载用户自定义学校（适配器）
        com.kstudio.agenda.model.Schools.loadCustom(this)
        // 通知渠道
        Notifier.ensureChannel(this)
        // 定期兜底调度提醒
        SyncWorker.enqueuePeriodic(this)
        // ⚠️ 顺序很重要：**先把全局状态（当前学校 / 作息 / 节假日开关）准备好，再读课表缓存**。
        // 缓存文件里记着它属于哪所学校；若此刻 Schools.currentId 还是默认值（buaa），
        // 江大缓存会被当成“别的学校的缓存”而丢弃 → 冷启动课表空白（2026-10-08 修复）。
        // 设置读取本身很小，这里阻塞几毫秒换取启动一致性（后台入口也靠这些全局状态）。
        val bootSettings = runCatching { runBlocking { SettingsStore.read(this@KebiaoApp) } }.getOrNull()
        if (bootSettings != null) {
            Schools.setCurrent(bootSettings.schoolId)
            // 法定节假日是否照常显示课表（默认停课）
            HolidayTable.setShowCoursesOnHoliday(bootSettings.showHolidayCourses)
            // 课程时间：用户自定义过就用自定义；否则套用当前学校的作息预设
            // （如江苏大学 11 节；无预设的学校回落内置默认）。提醒排程会用到，必须在此之前设置好。
            val custom = PeriodTimes.decode(bootSettings.periodTimesRaw)
            if (custom != null) {
                PeriodTimes.applyCustom(custom)
            } else {
                val school = Schools.of(bootSettings.schoolId)
                PeriodTimes.applySchoolPreset(SchoolFlows.of(school).periodPreset(school))
            }
        }
        // 加载本地缓存的课表 + 恢复提醒
        ScheduleRepository.get(this).bootstrap()
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                AppLog.e(
                    "Crash",
                    "未捕获异常（线程 ${thread.name}）：" +
                        "${throwable.javaClass.name}: ${throwable.message}",
                )
                AppLog.e("Crash", throwable.stackTraceToString())
                AppLog.flushNow()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
