package com.kstudio.agenda

import android.app.Application
import com.kstudio.agenda.data.ScheduleRepository
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.model.PeriodTimes
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
        // 用户自定义的课程时间（异步读设置；未自定义 / 读取失败时保持默认作息）。
        // 小组件、常驻通知等后台入口也会用到，所以放在 Application 层加载
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val raw = SettingsStore.read(this@KebiaoApp).periodTimesRaw
                PeriodTimes.applyCustom(PeriodTimes.decode(raw))
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
