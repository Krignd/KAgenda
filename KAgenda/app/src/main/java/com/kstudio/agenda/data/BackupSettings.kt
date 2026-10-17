package com.kstudio.agenda.data

/** 备份仅接受已知设置及原始类型，避免错误类型使 DataStore 读取持续崩溃。 */
internal object BackupSettings {
    private val types: Map<String, String> = buildMap {
        listOf("student_id", "semester_label", "app_language", "school_id", "ai_model", "status_notif_sources", "period_times", "ui_style", "profile_college", "profile_major", "profile_grade", "profile_clazz", "school_profiles").forEach { put(it, "s") }
        listOf("reminder_enabled", "alarm_enabled", "remind_agenda", "auto_refresh", "auto_check_update", "timetable_mode", "use_dev_ai_key", "status_notif_enabled", "status_ai_entry", "floating_ball", "widget_refresh_custom", "status_on_lock_screen", "ujs_webvpn", "show_holiday_courses").forEach { put(it, "b") }
        listOf("lead_minutes", "week_days", "timeline_start_minutes", "timeline_end_minutes", "widget_refresh_near_minutes", "widget_refresh_soon_minutes", "widget_refresh_far_minutes").forEach { put(it, "i") }
        listOf("last_sync_at", "anchor_epoch_day").forEach { put(it, "l") }
    }

    fun validate(key: String, value: Any): Boolean = when (types[key]) {
        "s" -> value is String
        "b" -> value is Boolean
        "i" -> value is Int
        "l" -> value is Long
        else -> false
    }
}
