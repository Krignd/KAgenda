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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        // 加载本地缓存的课表 + 恢复提醒
        ScheduleRepository.get(this).bootstrap()
        // 课程时间：用户自定义过就用自定义；否则套用当前学校的作息预设
        // （如江苏大学 11 节；无预设的学校回落内置默认，行为与以前一致）。
        // 小组件、常驻通知等后台入口也会用到，所以放在 Application 层加载。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val settings = SettingsStore.read(this@KebiaoApp)
                val custom = PeriodTimes.decode(settings.periodTimesRaw)
                if (custom != null) {
                    PeriodTimes.applyCustom(custom)
                } else {
                    val school = Schools.of(settings.schoolId)
                    PeriodTimes.applySchoolPreset(SchoolFlows.of(school).periodPreset(school))
                }
                // 法定节假日是否照常显示课表（默认停课）——小组件/常驻通知/提醒也在用，所以放 Application 层
                HolidayTable.setShowCoursesOnHoliday(settings.showHolidayCourses)
                // 当前学校：课表缓存与课程修正记录都按学校归属，后台入口也靠它判定
                Schools.setCurrent(settings.schoolId)
            }
        }
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
