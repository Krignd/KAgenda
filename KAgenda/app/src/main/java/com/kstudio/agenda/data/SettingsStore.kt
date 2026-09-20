package com.kstudio.agenda.data

import android.content.Context
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
    /** 系统悬浮球（需「显示在其他应用上层」权限） */
    val floatingBall: Boolean = false,
    /** 时间线模式（非课程表）显示范围：起止分钟数（0~1439；结束 ≤ 开始表示跨到次日，如 06:00–次日 02:00） */
    val timelineStartMinutes: Int = 6 * 60,
    val timelineEndMinutes: Int = 2 * 60,
) {
    /** 时间线实际显示窗口（分钟）：结束时间 ≤ 开始时间时视为跨到次日（end 可 > 1440） */
    val timelineWindow: Pair<Int, Int>
        get() {
            val s = timelineStartMinutes
            val e = if (timelineEndMinutes <= s) timelineEndMinutes + 24 * 60 else timelineEndMinutes
            return s to e
        }
}

/** 登录凭据 */
data class Credentials(val studentId: String, val password: String)

object SettingsStore {

    /** 内置（开发者）Key 固定使用的模型：不可修改 */
    const val DEV_AI_MODEL = "deepseek-flash"

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
    private val KEY_FLOATING_BALL = booleanPreferencesKey("floating_ball")
    private val KEY_TIMELINE_START = intPreferencesKey("timeline_start_minutes")
    private val KEY_TIMELINE_END = intPreferencesKey("timeline_end_minutes")

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
            floatingBall = p[KEY_FLOATING_BALL] ?: false,
            timelineStartMinutes = (p[KEY_TIMELINE_START] ?: 6 * 60).coerceIn(0, 1439),
            timelineEndMinutes = (p[KEY_TIMELINE_END] ?: 2 * 60).coerceIn(0, 1439),
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

    suspend fun setFloatingBall(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_FLOATING_BALL] = enabled }
    }
}
