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
    val reminderEnabled: Boolean = true,
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
    /** 常驻通知：开关 + 内容源（course/plan/agenda 逗号分隔） */
    val statusNotifEnabled: Boolean = false,
    val statusNotifSources: String = "course,plan,agenda",
    /** 系统悬浮球（需「显示在其他应用上层」权限） */
    val floatingBall: Boolean = false,
)

/** 登录凭据 */
data class Credentials(val studentId: String, val password: String)

object SettingsStore {

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
    private val KEY_STATUS_ENABLED = booleanPreferencesKey("status_notif_enabled")
    private val KEY_STATUS_SOURCES = stringPreferencesKey("status_notif_sources")
    private val KEY_FLOATING_BALL = booleanPreferencesKey("floating_ball")

    fun settingsFlow(context: Context): Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            studentId = p[KEY_STUDENT_ID] ?: "",
            hasPassword = !p[KEY_PASSWORD_ENC].isNullOrEmpty(),
            reminderEnabled = p[KEY_REMINDER_ENABLED] ?: true,
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
            statusNotifEnabled = p[KEY_STATUS_ENABLED] ?: false,
            statusNotifSources = p[KEY_STATUS_SOURCES] ?: "course,plan,agenda",
            floatingBall = p[KEY_FLOATING_BALL] ?: false,
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

    /** 保存账户；password 为 null 时保留旧密码 */
    suspend fun setAccount(context: Context, studentId: String, password: String?) {
        context.settingsDataStore.edit { p ->
            p[KEY_STUDENT_ID] = studentId.trim()
            if (password != null) {
                val enc = CryptoManager.encrypt(password)
                if (enc != null) p[KEY_PASSWORD_ENC] = enc
            }
        }
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

    /** 保存 AI Key（null=清除；加密存储，与本机账密同一机制） */
    suspend fun setAiKey(context: Context, apiKey: String?) {
        context.settingsDataStore.edit { p ->
            if (apiKey == null) {
                p.remove(KEY_AI_KEY_ENC)
            } else {
                CryptoManager.encrypt(apiKey)?.let { p[KEY_AI_KEY_ENC] = it }
            }
        }
    }

    /** 读取解密后的 AI Key（未配置返回 null） */
    suspend fun aiKey(context: Context): String? {
        val enc = context.settingsDataStore.data.first()[KEY_AI_KEY_ENC] ?: return null
        return CryptoManager.decrypt(enc)?.takeIf { it.isNotBlank() }
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
