package com.kstudio.agenda.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 写入任何设置/文件前检查数据块形状，拒绝损坏备份覆盖已有数据。 */
internal object BackupPayload {
    fun validate(root: JSONObject) {
        root.opt("settings")?.takeUnless { it == JSONObject.NULL }?.let { value ->
            require(value is JSONArray)
            for (i in 0 until value.length()) {
                val entry = value.getJSONObject(i)
                val key = entry.getString("k")
                if (key in setOf("password_enc", "ai_key_enc")) continue
                val raw = entry.get("v")
                val typed: Any = when (entry.getString("t")) {
                    "b" -> { require(raw is Boolean); raw }
                    "s" -> { require(raw is String); raw }
                    "i" -> { require(raw is Number && raw.toLong() in Int.MIN_VALUE..Int.MAX_VALUE && raw.toDouble() == raw.toLong().toDouble()); raw.toInt() }
                    "l" -> { require(raw is Int || raw is Long); (raw as Number).toLong() }
                    else -> error("Unknown setting type")
                }
                require(BackupSettings.validate(key, typed)) { "Invalid setting: $key" }
                if (key == "school_id") require((typed as String).matches(Regex("[a-zA-Z0-9_-]{1,100}")))
                if (key == "anchor_epoch_day") LocalDate.ofEpochDay(typed as Long)
                if (key == "school_profiles") JSONObject(typed as String)
            }
        }
        root.opt("agenda")?.takeUnless { it == JSONObject.NULL }?.let { value ->
            require(value is JSONArray)
            for (i in 0 until value.length()) {
                val event = value.getJSONObject(i)
                require(event.getString("id").isNotBlank() && event.getString("title").isNotBlank())
                LocalDate.ofEpochDay(event.getLong("dateEpochDay"))
                if (!event.isNull("endDateEpochDay")) LocalDate.ofEpochDay(event.getLong("endDateEpochDay"))
            }
        }
        for (key in listOf("courseEdits", "extraCourses", "schedule", "locations", "holidays", "schoolSchedules", "schoolExtraCourses")) {
            if (!root.isNull(key)) require(root.get(key) is JSONObject) { "Invalid data block: $key" }
        }
        if (!root.isNull("courseEdits")) require(root.getJSONObject("courseEdits").get("edits") is JSONArray)
        if (!root.isNull("extraCourses")) require(root.getJSONObject("extraCourses").get("courses") is JSONArray)
        if (!root.isNull("schedule")) validateSchedule(root.getJSONObject("schedule"))
        for (key in listOf("schoolSchedules", "schoolExtraCourses")) {
            root.optJSONObject(key)?.let { entries ->
                entries.keys().forEach { id ->
                    require(id.matches(Regex("[a-zA-Z0-9_-]{1,100}")))
                    val data = entries.getJSONObject(id)
                    require(data.getString("school") == id)
                    if (key == "schoolSchedules") validateSchedule(data) else require(data.get("courses") is JSONArray)
                }
            }
        }
    }

    private fun validateSchedule(data: JSONObject) {
        LocalDate.ofEpochDay(data.getLong("anchorEpochDay"))
        if (data.has("weeks")) {
            val weeks = data.getJSONObject("weeks")
            weeks.keys().forEach { key ->
                require(key.toInt() in 1..40 && weeks.get(key) is JSONArray)
            }
        } else require(data.get("courses") is JSONArray)
    }
}
