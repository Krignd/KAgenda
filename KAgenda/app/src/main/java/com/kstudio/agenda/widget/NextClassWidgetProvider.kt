package com.kstudio.agenda.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** 「下一节课」小组件基类：四种尺寸共用同一套更新 / 调度逻辑 */
abstract class BaseNextClassWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        NextClassWidgetUpdater.updateAndSchedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // 尺寸变化（拉伸/收缩）后立即刷新内容
        NextClassWidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        // 第一个小组件被添加：启动定时刷新链
        NextClassWidgetUpdater.updateAndSchedule(context)
    }

    override fun onDisabled(context: Context) {
        // 尺寸范围内最后一个组件被移除：若无其他尺寸的小组件则停止刷新链
        NextClassWidgetUpdater.scheduleNext(context)
    }
}

/** 下一节课 · 2×1 */
class NextClassWidget2x1 : BaseNextClassWidget()

/** 下一节课 · 2×2 */
class NextClassWidget2x2 : BaseNextClassWidget()

/** 下一节课 · 4×1 */
class NextClassWidget4x1 : BaseNextClassWidget()

/** 下一节课 · 4×2 */
class NextClassWidget4x2 : BaseNextClassWidget()
