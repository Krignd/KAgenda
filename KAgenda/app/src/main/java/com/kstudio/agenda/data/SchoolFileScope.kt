package com.kstudio.agenda.data

import org.json.JSONObject
import java.io.File

/** 学校数据文件的归属与旧格式迁移，不依赖 Android，便于验证跨学校读写。 */
internal object SchoolFileScope {
    fun file(root: File, name: String, school: String): File {
        require(school.matches(Regex("[a-zA-Z0-9_-]{1,100}")))
        require(name in setOf("schedule_cache.json", "extra_courses.json"))
        return File(root, "school_data/$school/$name")
    }
    private fun read(file: File): JSONObject? = runCatching {
        if (!file.exists()) null else JSONObject(file.readText(Charsets.UTF_8))
    }.getOrNull()
    private fun write(file: File, data: JSONObject) {
        AtomicFileStore.write(file, data.toString())
    }
    @Synchronized fun load(root: File, name: String, school: String, allowUnowned: Boolean = true): JSONObject? {
        val own = file(root, name, school)
        val legacy = File(root, name)
        val recent = read(legacy)
        val owner = recent?.optString("school").orEmpty()
        if (recent != null && (owner == school || (owner.isBlank() && allowUnowned))) {
            if (owner.isBlank()) {
                recent.put("school", school)
                write(own, recent)
                write(legacy, recent)
            }
            return recent
        }
        return read(own)?.takeIf { it.optString("school") == school }
    }
    @Synchronized fun save(root: File, name: String, school: String, data: JSONObject) {
        val own = file(root, name, school)
        val legacy = File(root, name)
        val recent = read(legacy)
        val owner = recent?.optString("school").orEmpty()
        if (recent != null && owner.matches(Regex("[a-zA-Z0-9_-]{1,100}")) && owner != school) write(file(root, name, owner), recent)
        data.put("school", school)
        write(own, data)
        write(legacy, data)
    }
    @Synchronized fun clear(root: File, name: String, school: String) {
        file(root, name, school).delete()
        val legacy = File(root, name)
        if (read(legacy)?.optString("school").orEmpty().let { it.isBlank() || it == school }) legacy.delete()
    }
}
