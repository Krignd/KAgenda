package com.kstudio.agenda.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 界面风格：默认 */
const val UI_STYLE_DEFAULT = "default"

/** 界面风格：液态玻璃 */
const val UI_STYLE_GLASS = "glass"

/** 应用设置（对外暴露的只读快照） */
data class AppSettings(
    val studentId: String = "",
    val hasPassword: Boolean = false,
    /** 课前提醒：默认关闭（开关由用户主动开启；开启时才申请通知权限） */
    val reminderEnabled: Boolean = false,
    val leadMinutes: Int = 30,
    val autoRefresh: Boolean = true,
    val lastSyncAtMillis: Long = 0L,
    val semesterLabel: String = "",
    val anchorEpochDay: Long? = null,
    /** 课程表模式（日程表页顶部勾选框；true=按节次等行显示） */
    val timetableMode: Boolean = true,
    /** 应用语言：""=跟随系统；"zh"/"fr"/"en" */
    val appLanguage: String = "",
    /** 所选学校 id（见 Schools.ALL） */
    val schoolId: String = "buaa",
    /** AI（DeepSeek）：是否已保存 Key；模型名 */
    val aiKeySet: Boolean = false,
    val aiModel: String = "deepseek-flash",
    /** 默认勾选：使用开发者内置的 API Key；取消后使用自行填写的 Key */
    val useDevAiKey: Boolean = true,
    /** 常驻通知：开关 + 内容源（course/plan/agenda 逗号分隔） */
    val statusNotifEnabled: Boolean = false,
    val statusNotifSources: String = "course,plan,agenda",
    /** 常驻通知里的「AI 快速添加」文本框入口（可单独关闭，关闭后该条目不再常驻） */
    val statusAiEntry: Boolean = true,
    /** 系统悬浮球（需「显示在其他应用上层」权限） */
    val floatingBall: Boolean = false,
    /** 时间线模式（非课程表）显示范围：起止分钟数（0~1439；结束 ≤ 开始表示跨到次日，如 06:00–次日 02:00） */
    val timelineStartMinutes: Int = 6 * 60,
    val timelineEndMinutes: Int = 2 * 60,
    /** 小组件刷新频率：自定义开关 + 三档间隔（分钟，见 [widgetRefreshTiers]） */
    val widgetRefreshCustom: Boolean = false,
    val widgetRefreshNearMinutes: Int = SettingsStore.WIDGET_REFRESH_NEAR_DEFAULT,
    val widgetRefreshSoonMinutes: Int = SettingsStore.WIDGET_REFRESH_SOON_DEFAULT,
    val widgetRefreshFarMinutes: Int = SettingsStore.WIDGET_REFRESH_FAR_DEFAULT,
    /** 用户自定义的课程时间（持久化原文；空串=使用默认作息，见 [com.kstudio.agenda.model.PeriodTimes]） */
    val periodTimesRaw: String = "",
    /** 常驻通知是否允许在锁屏上显示（关闭时通知不出现在锁屏） */
    val statusOnLockScreen: Boolean = true,
    /** 界面风格："default"=默认；"glass"=液态玻璃 */
    val uiStyle: String = UI_STYLE_DEFAULT,
    /**
     * 江苏大学专用：是否「通过 WebVPN」访问教务系统（默认勾选）。
     *
     * 勾选与不勾选对应 data/UjsFlow.kt 里两份完全隔离的链路实现
     * （[UjsWebVpnFlow] / [UjsDefaultFlow]），见 WebScheduleEngine 与 WebLoginActivity 的分派。
     */
    val ujsWebVpn: Boolean = true,
    /**
     * 法定节假日是否照常显示课表（默认 false = 节假日停课，与既有行为一致）。
     * 只影响「展示 / 提醒 / 小组件 / 导出」是否跳过节假日当天的课程。
     */
    val showHolidayCourses: Boolean = false,
    /**
     * AI 身份预设：学院 / 专业 / 年级 / 班级（都可留空）。
     *
     * 用途：很多通知/公告会同时列出多个班级或多个时间分支（如「1、2班周三；3、4班周四」），
     * 填了身份后 AI 助手只挑与本人相符的那一条，而不是把所有分支都加成日程。
     */
    val profileCollege: String = "",
    val profileMajor: String = "",
    val profileGrade: String = "",
    val profileClazz: String = "",
) {

    /** 身份预设的展示文本（形如「计算机学院 软件工程 2024级 4班」；未填写时为空串） */
    val profileText: String
        get() = listOf(profileCollege, profileMajor, profileGrade, profileClazz)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** 是否使用液态玻璃界面风格 */
    val glassUi: Boolean get() = uiStyle == UI_STYLE_GLASS

    /** 时间线实际显示窗口（分钟）：结束时间 ≤ 开始时间时视为跨到次日（end 可 > 1440） */
    val timelineWindow: Pair<Int, Int>
        get() {
            val s = timelineStartMinutes
            val e = if (timelineEndMinutes <= s) timelineEndMinutes + 24 * 60 else timelineEndMinutes
            return s to e
        }

    /**
     * 小组件刷新间隔（分钟）：第一/二/三个值依次对应
     * 「距下个日程 ≤ 1 小时」/「≤ 3 小时」/「更远或无日程」。
     * 未自定义时用内置默认值（比旧版更快：1 / 5 / 60 分钟）。
     */
    val widgetRefreshTiers: Triple<Int, Int, Int>
        get() = if (widgetRefreshCustom) {
            Triple(widgetRefreshNearMinutes, widgetRefreshSoonMinutes, widgetRefreshFarMinutes)
        } else {
            Triple(
                SettingsStore.WIDGET_REFRESH_NEAR_DEFAULT,
                SettingsStore.WIDGET_REFRESH_SOON_DEFAULT,
                SettingsStore.WIDGET_REFRESH_FAR_DEFAULT,
            )
        }
}

/** 登录凭据 */
data class Credentials(val studentId: String, val password: String)

object SettingsStore {

    /** 内置（开发者）Key 固定使用的模型：不可修改 */
    const val DEV_AI_MODEL = "deepseek-flash"

    /** 小组件刷新间隔默认值（分钟）：临近(≤1h) / 较近(≤3h) / 较远(>3h 或无日程) */
    const val WIDGET_REFRESH_NEAR_DEFAULT = 1
    const val WIDGET_REFRESH_SOON_DEFAULT = 5
    const val WIDGET_REFRESH_FAR_DEFAULT = 60

    private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
    private val KEY_PASSWORD_ENC = stringPreferencesKey("password_enc")
    private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    private val KEY_LEAD_MINUTES = intPreferencesKey("lead_minutes")
    private val KEY_AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
    private val KEY_LAST_SYNC = longPreferencesKey("last_sync_at")
    private val KEY_SEMESTER = stringPreferencesKey("semester_label")
    private val KEY_ANCHOR = longPreferencesKey("anchor_epoch_day")
    private val KEY_TIMETABLE_MODE = booleanPreferencesKey("timetable_mode")
    private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    private val KEY_SCHOOL = stringPreferencesKey("school_id")
    private val KEY_AI_KEY_ENC = stringPreferencesKey("ai_key_enc")
    private val KEY_AI_MODEL = stringPreferencesKey("ai_model")
    private val KEY_USE_DEV_AI_KEY = booleanPreferencesKey("use_dev_ai_key")
    private val KEY_STATUS_ENABLED = booleanPreferencesKey("status_notif_enabled")
    private val KEY_STATUS_SOURCES = stringPreferencesKey("status_notif_sources")
    private val KEY_STATUS_AI_ENTRY = booleanPreferencesKey("status_ai_entry")
    private val KEY_FLOATING_BALL = booleanPreferencesKey("floating_ball")
    private val KEY_TIMELINE_START = intPreferencesKey("timeline_start_minutes")
    private val KEY_TIMELINE_END = intPreferencesKey("timeline_end_minutes")
    private val KEY_WIDGET_REFRESH_CUSTOM = booleanPreferencesKey("widget_refresh_custom")
    private val KEY_WIDGET_REFRESH_NEAR = intPreferencesKey("widget_refresh_near_minutes")
    private val KEY_WIDGET_REFRESH_SOON = intPreferencesKey("widget_refresh_soon_minutes")
    private val KEY_WIDGET_REFRESH_FAR = intPreferencesKey("widget_refresh_far_minutes")
    private val KEY_PERIOD_TIMES = stringPreferencesKey("period_times")
    private val KEY_STATUS_LOCK_SCREEN = booleanPreferencesKey("status_on_lock_screen")
    private val KEY_UI_STYLE = stringPreferencesKey("ui_style")
    private val KEY_UJS_WEB_VPN = booleanPreferencesKey("ujs_webvpn")
    private val KEY_SHOW_HOLIDAY_COURSES = booleanPreferencesKey("show_holiday_courses")
    private val KEY_PROFILE_COLLEGE = stringPreferencesKey("profile_college")
    private val KEY_PROFILE_MAJOR = stringPreferencesKey("profile_major")
    private val KEY_PROFILE_GRADE = stringPreferencesKey("profile_grade")
    private val KEY_PROFILE_CLAZZ = stringPreferencesKey("profile_clazz")

    fun settingsFlow(context: Context): Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            studentId = p[KEY_STUDENT_ID] ?: "",
            hasPassword = !p[KEY_PASSWORD_ENC].isNullOrEmpty(),
            reminderEnabled = p[KEY_REMINDER_ENABLED] ?: false,
            leadMinutes = p[KEY_LEAD_MINUTES] ?: 30,
            autoRefresh = p[KEY_AUTO_REFRESH] ?: true,
            lastSyncAtMillis = p[KEY_LAST_SYNC] ?: 0L,
            semesterLabel = p[KEY_SEMESTER] ?: "",
            anchorEpochDay = p[KEY_ANCHOR],
            timetableMode = p[KEY_TIMETABLE_MODE] ?: true,
            appLanguage = p[KEY_APP_LANGUAGE] ?: "",
            schoolId = p[KEY_SCHOOL] ?: "buaa",
            aiKeySet = !p[KEY_AI_KEY_ENC].isNullOrEmpty(),
            aiModel = p[KEY_AI_MODEL] ?: "deepseek-flash",
            useDevAiKey = p[KEY_USE_DEV_AI_KEY] ?: true,
            statusNotifEnabled = p[KEY_STATUS_ENABLED] ?: false,
            statusNotifSources = p[KEY_STATUS_SOURCES] ?: "course,plan,agenda",
            statusAiEntry = p[KEY_STATUS_AI_ENTRY] ?: true,
            floatingBall = p[KEY_FLOATING_BALL] ?: false,
            timelineStartMinutes = (p[KEY_TIMELINE_START] ?: 6 * 60).coerceIn(0, 1439),
            timelineEndMinutes = (p[KEY_TIMELINE_END] ?: 2 * 60).coerceIn(0, 1439),
            widgetRefreshCustom = p[KEY_WIDGET_REFRESH_CUSTOM] ?: false,
            widgetRefreshNearMinutes = (p[KEY_WIDGET_REFRESH_NEAR] ?: WIDGET_REFRESH_NEAR_DEFAULT)
                .coerceIn(1, 60),
            widgetRefreshSoonMinutes = (p[KEY_WIDGET_REFRESH_SOON] ?: WIDGET_REFRESH_SOON_DEFAULT)
                .coerceIn(1, 120),
            widgetRefreshFarMinutes = (p[KEY_WIDGET_REFRESH_FAR] ?: WIDGET_REFRESH_FAR_DEFAULT)
                .coerceIn(1, 720),
            periodTimesRaw = p[KEY_PERIOD_TIMES] ?: "",
            statusOnLockScreen = p[KEY_STATUS_LOCK_SCREEN] ?: true,
            uiStyle = p[KEY_UI_STYLE] ?: UI_STYLE_DEFAULT,
            ujsWebVpn = p[KEY_UJS_WEB_VPN] ?: true,
            showHolidayCourses = p[KEY_SHOW_HOLIDAY_COURSES] ?: false,
            profileCollege = p[KEY_PROFILE_COLLEGE] ?: "",
            profileMajor = p[KEY_PROFILE_MAJOR] ?: "",
            profileGrade = p[KEY_PROFILE_GRADE] ?: "",
            profileClazz = p[KEY_PROFILE_CLAZZ] ?: "",
        )
    }

    suspend fun read(context: Context): AppSettings = settingsFlow(context).first()

    /** 读取可用的登录凭据（学号 + 解密后的密码） */
    suspend fun credentials(context: Context): Credentials? {
        val p = context.settingsDataStore.data.first()
        val id = p[KEY_STUDENT_ID] ?: return null
        val enc = p[KEY_PASSWORD_ENC] ?: return null
        val pwd = CryptoManager.decrypt(enc) ?: return null
        if (id.isBlank() || pwd.isBlank()) return null
        return Credentials(id, pwd)
    }

    /** 保存账户；password 为 null 时保留旧密码。返回 false 表示密码加密失败（未保存） */
    suspend fun setAccount(context: Context, studentId: String, password: String?): Boolean {
        var ok = true
        context.settingsDataStore.edit { p ->
            p[KEY_STUDENT_ID] = studentId.trim()
            if (password != null) {
                val enc = CryptoManager.encrypt(password)
                if (enc != null) {
                    p[KEY_PASSWORD_ENC] = enc
                } else {
                    ok = false
                }
            }
        }
        return ok
    }

    suspend fun clearAccount(context: Context) {
        context.settingsDataStore.edit { p ->
            p.remove(KEY_STUDENT_ID)
            p.remove(KEY_PASSWORD_ENC)
        }
    }

    /** 清空全部设置（恢复初始状态） */
    suspend fun clearAll(context: Context) {
        context.settingsDataStore.edit { it.clear() }
    }

    suspend fun setReminderEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setLeadMinutes(context: Context, minutes: Int) {
        context.settingsDataStore.edit { it[KEY_LEAD_MINUTES] = minutes.coerceIn(0, 120) }
    }

    suspend fun setAutoRefresh(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_REFRESH] = enabled }
    }

    suspend fun setTimetableMode(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_TIMETABLE_MODE] = enabled }
    }

    /** 时间线显示范围：开始/结束时间（分钟，0~1439；结束 ≤ 开始表示跨到次日） */
    suspend fun setTimelineStart(context: Context, minutes: Int) {
        context.settingsDataStore.edit { it[KEY_TIMELINE_START] = minutes.coerceIn(0, 1439) }
    }

    suspend fun setTimelineEnd(context: Context, minutes: Int) {
        context.settingsDataStore.edit { it[KEY_TIMELINE_END] = minutes.coerceIn(0, 1439) }
    }

    /** 用户自定义课程时间（持久化原文；空串=恢复默认作息） */
    suspend fun setPeriodTimes(context: Context, raw: String) {
        context.settingsDataStore.edit { p ->
            if (raw.isBlank()) p.remove(KEY_PERIOD_TIMES) else p[KEY_PERIOD_TIMES] = raw
        }
    }

    /** 常驻通知是否在锁屏显示 */
    suspend fun setStatusOnLockScreen(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_STATUS_LOCK_SCREEN] = enabled }
    }

    /** 界面风格（default / glass） */
    suspend fun setUiStyle(context: Context, style: String) {
        context.settingsDataStore.edit { it[KEY_UI_STYLE] = style }
    }

    suspend fun setAppLanguage(context: Context, tag: String) {
        context.settingsDataStore.edit { it[KEY_APP_LANGUAGE] = tag }
    }

    suspend fun setLastSync(context: Context, millis: Long) {
        context.settingsDataStore.edit { it[KEY_LAST_SYNC] = millis }
    }

    suspend fun setSemesterAnchor(context: Context, semesterLabel: String, anchorEpochDay: Long) {
        context.settingsDataStore.edit { p ->
            p[KEY_SEMESTER] = semesterLabel
            p[KEY_ANCHOR] = anchorEpochDay
        }
    }

    /** 清空已保存的学期锚点（切换学校后旧学校的锚点不再适用，避免显示错误的教学周） */
    suspend fun clearSemesterAnchor(context: Context) {
        context.settingsDataStore.edit { p ->
            p.remove(KEY_SEMESTER)
            p.remove(KEY_ANCHOR)
        }
    }

    suspend fun setSchool(context: Context, id: String) {
        context.settingsDataStore.edit { it[KEY_SCHOOL] = id }
    }

    /** 保存 AI Key（null=清除；加密存储，与本机账密同一机制）。返回 false 表示加密失败（未保存） */
    suspend fun setAiKey(context: Context, apiKey: String?): Boolean {
        var ok = true
        context.settingsDataStore.edit { p ->
            if (apiKey == null) {
                p.remove(KEY_AI_KEY_ENC)
            } else {
                val enc = CryptoManager.encrypt(apiKey)
                if (enc != null) {
                    p[KEY_AI_KEY_ENC] = enc
                } else {
                    ok = false
                }
            }
        }
        return ok
    }

    /** 读取解密后的 AI Key（未配置返回 null） */
    suspend fun aiKey(context: Context): String? {
        val enc = context.settingsDataStore.data.first()[KEY_AI_KEY_ENC] ?: return null
        return CryptoManager.decrypt(enc)?.takeIf { it.isNotBlank() }
    }

    /** 切换「使用开发者的 API key」（默认勾选） */
    suspend fun setUseDevAiKey(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_USE_DEV_AI_KEY] = enabled }
    }

    /** 实际用于 AI 请求的 Key：默认使用开发者内置 Key；关闭后使用用户自己保存的 Key */
    suspend fun effectiveAiKey(context: Context): String? {
        val p = context.settingsDataStore.data.first()
        if (p[KEY_USE_DEV_AI_KEY] ?: true) {
            return DevKey.value().takeIf { it.isNotBlank() }
        }
        val enc = p[KEY_AI_KEY_ENC] ?: return null
        return CryptoManager.decrypt(enc)?.takeIf { it.isNotBlank() }
    }

    /** 实际用于 AI 请求的模型：使用开发者内置 Key 时固定 deepseek-flash（不可修改）；否则用用户设置 */
    suspend fun effectiveAiModel(context: Context): String {
        val p = context.settingsDataStore.data.first()
        return if (p[KEY_USE_DEV_AI_KEY] ?: true) DEV_AI_MODEL else p[KEY_AI_MODEL] ?: DEV_AI_MODEL
    }

    suspend fun setAiModel(context: Context, model: String) {
        context.settingsDataStore.edit { it[KEY_AI_MODEL] = model.trim().ifBlank { "deepseek-flash" } }
    }

    suspend fun setStatusNotif(context: Context, enabled: Boolean, sourcesCsv: String) {
        context.settingsDataStore.edit { p ->
            p[KEY_STATUS_ENABLED] = enabled
            p[KEY_STATUS_SOURCES] = sourcesCsv
        }
    }

    /** 常驻通知里的「AI 快速添加」入口开关 */
    suspend fun setStatusAiEntry(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_STATUS_AI_ENTRY] = enabled }
    }

    /** 小组件刷新频率：自定义开关 */
    suspend fun setWidgetRefreshCustom(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_WIDGET_REFRESH_CUSTOM] = enabled }
    }

    /** 小组件刷新间隔（分钟）：[tier] 0=临近(≤1h) 1=较近(≤3h) 2=较远(>3h) */
    suspend fun setWidgetRefreshMinutes(context: Context, tier: Int, minutes: Int) {
        context.settingsDataStore.edit { p ->
            when (tier) {
                0 -> p[KEY_WIDGET_REFRESH_NEAR] = minutes.coerceIn(1, 60)
                1 -> p[KEY_WIDGET_REFRESH_SOON] = minutes.coerceIn(1, 120)
                else -> p[KEY_WIDGET_REFRESH_FAR] = minutes.coerceIn(1, 720)
            }
        }
    }

    suspend fun setFloatingBall(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_FLOATING_BALL] = enabled }
    }

    /**
     * 江苏大学：是否「通过 WebVPN」访问教务系统。
     * 写入后由引擎/登录窗口在下一次同步或下一次打开网页登录时按新值分派链路。
     */
    suspend fun setUjsWebVpn(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_UJS_WEB_VPN] = enabled }
    }

    /** 法定节假日是否照常显示课表（默认 false=停课） */
    suspend fun setShowHolidayCourses(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_HOLIDAY_COURSES] = enabled }
    }

    // ------------------------------------------------------------ 备份 / 恢复

    /**
     * 导出全部设置为可序列化的键值（值只有 Boolean / Int / Long / String）。
     *
     * 按**已知键名**逐个取值（不用 `Preferences.asMap()`——那是 datastore 内部扩展，
     * 本项目的 datastore 版本没有）：这样导出内容可控，不会把以后的临时键带出去。
     * 密码与 AI Key 的密文包含在内，由备份层（[BackupManager]）决定是否剔除。
     */
    /** 身份预设（学院/专业/年级/班级）：空串=清除该项 */
    suspend fun setProfile(context: Context, college: String, major: String, grade: String, clazz: String) {
        context.settingsDataStore.edit { p ->
            fun put(key: Preferences.Key<String>, value: String) {
                if (value.isBlank()) p.remove(key) else p[key] = value.trim()
            }
            put(KEY_PROFILE_COLLEGE, college)
            put(KEY_PROFILE_MAJOR, major)
            put(KEY_PROFILE_GRADE, grade)
            put(KEY_PROFILE_CLAZZ, clazz)
        }
    }

    suspend fun exportPreferences(context: Context): Map<String, Any> {
        val p = context.settingsDataStore.data.first()
        val out = LinkedHashMap<String, Any>()
        fun put(key: Preferences.Key<*>, name: String) {
            when (val v = p[key]) {
                is Boolean, is Int, is Long, is String -> out[name] = v
                else -> Unit
            }
        }
        put(KEY_STUDENT_ID, "student_id")
        put(KEY_PASSWORD_ENC, "password_enc")
        put(KEY_REMINDER_ENABLED, "reminder_enabled")
        put(KEY_LEAD_MINUTES, "lead_minutes")
        put(KEY_AUTO_REFRESH, "auto_refresh")
        put(KEY_LAST_SYNC, "last_sync_at")
        put(KEY_SEMESTER, "semester_label")
        put(KEY_ANCHOR, "anchor_epoch_day")
        put(KEY_TIMETABLE_MODE, "timetable_mode")
        put(KEY_APP_LANGUAGE, "app_language")
        put(KEY_SCHOOL, "school_id")
        put(KEY_AI_KEY_ENC, "ai_key_enc")
        put(KEY_AI_MODEL, "ai_model")
        put(KEY_USE_DEV_AI_KEY, "use_dev_ai_key")
        put(KEY_STATUS_ENABLED, "status_notif_enabled")
        put(KEY_STATUS_SOURCES, "status_notif_sources")
        put(KEY_STATUS_AI_ENTRY, "status_ai_entry")
        put(KEY_FLOATING_BALL, "floating_ball")
        put(KEY_TIMELINE_START, "timeline_start_minutes")
        put(KEY_TIMELINE_END, "timeline_end_minutes")
        put(KEY_WIDGET_REFRESH_CUSTOM, "widget_refresh_custom")
        put(KEY_WIDGET_REFRESH_NEAR, "widget_refresh_near_minutes")
        put(KEY_WIDGET_REFRESH_SOON, "widget_refresh_soon_minutes")
        put(KEY_WIDGET_REFRESH_FAR, "widget_refresh_far_minutes")
        put(KEY_PERIOD_TIMES, "period_times")
        put(KEY_STATUS_LOCK_SCREEN, "status_on_lock_screen")
        put(KEY_UI_STYLE, "ui_style")
        put(KEY_UJS_WEB_VPN, "ujs_webvpn")
        put(KEY_SHOW_HOLIDAY_COURSES, "show_holiday_courses")
        put(KEY_PROFILE_COLLEGE, "profile_college")
        put(KEY_PROFILE_MAJOR, "profile_major")
        put(KEY_PROFILE_GRADE, "profile_grade")
        put(KEY_PROFILE_CLAZZ, "profile_clazz")
        return out
    }

    /**
     * 导入设置：按键覆盖写入。
     * **不清空**现有设置——备份里不含密码/AI Key 的密文（换机后本就解不开），
     * 直接 clear 会把本机还能用的登录状态抹掉。
     */
    suspend fun importPreferences(context: Context, prefs: Map<String, Any>) {
        if (prefs.isEmpty()) return
        context.settingsDataStore.edit { p ->
            prefs.forEach { (key, value) ->
                when (value) {
                    is Boolean -> p[booleanPreferencesKey(key)] = value
                    is Int -> p[intPreferencesKey(key)] = value
                    is Long -> p[longPreferencesKey(key)] = value
                    is String -> p[stringPreferencesKey(key)] = value
                }
            }
        }
    }
}
