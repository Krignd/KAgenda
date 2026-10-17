package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.Schools
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** School-specific location extension point. Add curated names to defaultsFor when available. */
object SchoolLocationsStore {
    const val FILE_NAME = "school_locations.json"
    val locations = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private var loaded = false
    fun defaultsFor(schoolId: String): List<String> = when (schoolId) {
        "buaa" -> emptyList() // TODO campus buildings and aliases
        "ujs" -> emptyList() // TODO campus buildings and aliases
        else -> emptyList()
    }
    @Synchronized fun load(context: Context, force: Boolean = false) {
        if (loaded && !force) return
        val parsed = runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return@runCatching emptyMap<String, List<String>>()
            val json = JSONObject(file.readText())
            json.keys().asSequence().associateWith { key ->
                val arr = json.optJSONArray(key) ?: JSONArray()
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyMap())
        locations.value = parsed
        loaded = true
    }
    fun forSchool(schoolId: String = Schools.currentId): List<String> = locations.value[schoolId] ?: defaultsFor(schoolId)
    @Synchronized fun save(context: Context, schoolId: String, names: List<String>) {
        load(context)
        val updated = locations.value + (schoolId to names.map { it.trim() }.filter { it.isNotBlank() }.distinct())
        val json = JSONObject()
        updated.forEach { (id, values) -> json.put(id, JSONArray(values)) }
        File(context.filesDir, FILE_NAME).writeText(json.toString())
        locations.value = updated
    }
}
