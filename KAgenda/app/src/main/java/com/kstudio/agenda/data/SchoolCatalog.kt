package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.School
import com.kstudio.agenda.model.SchoolNameIndex
import org.json.JSONArray
import org.json.JSONObject

/** 官方名称目录与社区脚本分别存储；名称收录不意味着导入已经通过实校验证。 */
object SchoolCatalog {
    data class Adapter(val id: String, val name: String, val category: String, val url: String, val path: String, val maintainer: String, val description: String, val combo: Boolean)
    const val COMMIT = "1dc0772566ba610f495906410734db4e7b8ef0c1"
    const val REPOSITORY = "https://github.com/ShiGuangSchedule/shiguang_warehouse"
    private var byWarehouse = emptyMap<String, List<Adapter>>()
    private var glyphs = emptyMap<String, String>()
    @Volatile private var spellingIndex: SchoolNameIndex? = null
    fun nameIndex(context: Context): SchoolNameIndex = spellingIndex ?: synchronized(this) {
        spellingIndex ?: SchoolNameIndex.parse(context.assets.open("catalog/pinyin.json").bufferedReader().use { it.readText() })
            .also { spellingIndex = it }
    }
    fun adapters(school: School): List<Adapter> = byWarehouse[school.warehouseId].orEmpty()
    fun glyph(name: String): String? = glyphs[name]
    private fun normalize(name: String): String = name.replace('(', '（').replace(')', '）').replace(" ", "")
    fun load(context: Context, independent: List<School>): List<School> {
        val logos = JSONObject(context.assets.open("catalog/logos.json").bufferedReader().use { it.readText() })
        glyphs = logos.keys().asSequence().associateWith { logos.getString(it) }
        val index = JSONArray(context.assets.open("shiguang/index.json").bufferedReader().use { it.readText() })
        val named = linkedMapOf<String, String>()
        val adapterMap = linkedMapOf<String, List<Adapter>>()
        for (i in 0 until index.length()) {
            val row = index.getJSONObject(i); val id = row.getString("id")
            named[normalize(row.getString("name"))] = id
            val list = row.getJSONArray("adapters")
            adapterMap[id] = (0 until list.length()).map { n -> list.getJSONObject(n).let { a ->
                Adapter(a.getString("id"), a.getString("name"), a.getString("category"), a.getString("url"), a.getString("path"), a.optString("maintainer"), a.optString("description"), a.optBoolean("combo"))
            } }
        }
        byWarehouse = adapterMap
        val rows = JSONArray(context.assets.open("catalog/schools.json").bufferedReader().use { it.readText() })
        val schools = (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
            val name = row.getString("name")
            val own = independent.firstOrNull { normalize(it.name) == normalize(name) }
            if (own?.supported == true) own.copy(province = row.optString("province"), level = row.optString("level")) else {
                val warehouseId = named[normalize(name)].orEmpty()
                val url = adapterMap[warehouseId]?.firstOrNull()?.url.orEmpty()
                School(own?.id ?: row.getString("id"), name, url, url, url, warehouseId.isNotBlank(),
                    manualLogin = warehouseId.isNotBlank(), warehouseId = warehouseId,
                    province = row.optString("province"), level = row.optString("level"))
            }
        }.toMutableList()
        // 分校/校区可能没有单独的教育部标识码，仍保留仓库原名，避免错配到主校区。
        val seen = schools.map { normalize(it.name) }.toHashSet()
        for (i in 0 until index.length()) {
            val row = index.getJSONObject(i); val name = row.getString("name")
            if (normalize(name) in seen) continue
            val id = row.getString("id"); val url = adapterMap[id]?.firstOrNull()?.url ?: continue
            schools += School("sg_" + id.lowercase(), name, url, url, url, true, manualLogin = true, warehouseId = id)
        }
        return independent.filter { it.supported }.map { own -> schools.firstOrNull { it.id == own.id } ?: own } + schools.filterNot { it.id in independent.filter { s -> s.supported }.map { s -> s.id } }
    }
}
