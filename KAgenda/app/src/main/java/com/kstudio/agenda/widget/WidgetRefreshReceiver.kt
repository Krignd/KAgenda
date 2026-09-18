package com.kstudio.agenda.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 小组件定时刷新链：每次触发后更新所有小组件，并安排下一次刷新 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NextClassWidgetUpdater.updateAndSchedule(context.applicationContext)
    }
}
