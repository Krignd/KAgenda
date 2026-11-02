package com.kstudio.agenda.data

import android.content.Context
import com.kstudio.agenda.model.School
import com.kstudio.agenda.model.SchoolNameIndex
import org.json.JSONArray
import org.json.JSONObject

/** 官方名称目录与社区脚本分别存储；名称收录不意味着导入已经通过实校验证。 */
object SchoolCatalog {
    data class Adapter(
        val id: String, val name: String, val category: String, val url: String, val path: String,
        val maintainer: String, val description: String, val combo: Boolean,
        val repository: String = REPOSITORY, val revision: String = COMMIT,
        val license: String = "MIT", val updated: String = "", val sourceUrl: String = "",
        val parser: String = "", val directoryUrl: String = "", val licenseAsset: String = "", val preferred: Boolean = false,
    )
    const val COMMIT = "c4a35feb061a3f6db2d6c122927083c067c61309"
    const val REPOSITORY = "https://github.com/ShiGuangSchedule/shiguang_warehouse"
    private var byWarehouse = emptyMap<String, List<Adapter>>()
    private var general = emptyList<Adapter>()
    private var historical = emptyMap<String, List<Adapter>>()
    private var systemTypes = emptyMap<String, List<String>>()
    private var glyphs = emptyMap<String, String>()
    @Volatile private var spellingIndex: SchoolNameIndex? = null
    fun nameIndex(context: Context): SchoolNameIndex = spellingIndex ?: synchronized(this) {
        spellingIndex ?: SchoolNameIndex.parse(context.assets.open("catalog/pinyin.json").bufferedReader().use { it.readText() })
            .also { spellingIndex = it }
    }
    /** Independent/custom adapters keep their engine; all other schools can try a visible browser. */
    fun usesBrowserImport(school: School): Boolean = !school.custom && (school.warehouseId.isNotBlank() || !school.supported)
    fun adapters(school: School): List<Adapter> {
        // Unsupported combined time schemes must not hide an available port
        // behind the default adapter, even when the old entry is equally new.
        val dedicated = byWarehouse[school.warehouseId].orEmpty()
            .sortedWith(compareBy<Adapter> { it.combo }.thenByDescending { it.preferred }.thenByDescending { it.updated })
        val legacy = historical[normalize(school.name)].orEmpty()
        val startUrl = dedicated.firstOrNull()?.url ?: legacy.firstOrNull()?.url ?: school.homeUrl
        return dedicated + legacy + general.map { it.copy(url = startUrl) } + AcademicPortalParser.adapters(startUrl)
    }
    fun glyph(name: String): String? = glyphs[name]
    fun hasAdditionalSource(school: School): Boolean = byWarehouse[school.warehouseId].orEmpty().any { it.licenseAsset.isNotBlank() }
    /** Only school-bound directory records and explicit adapter names; never global fallback options. */
    fun knownSystemTypes(school: School): List<String> = systemTypes[normalize(school.name)].orEmpty()

    private fun namedSystems(name: String): List<String> = buildList {
        if (name.contains("正方")) add(when {
            Regex("新(?:版)?正方|正方.*新版").containsMatchIn(name) -> "zf_new"
            Regex("旧(?:版)?正方|正方.*旧版").containsMatchIn(name) -> "zf"
            else -> "zhengfang"
        })
        if (name.contains("强智")) add("qz")
        if (name.contains("金智")) add("jinzhi")
        if (name.contains("青果")) add("cf")
        if (name.contains("超星")) add("chaoxing")
        if (Regex("(?<![A-Za-z])URP(?![A-Za-z])", RegexOption.IGNORE_CASE).containsMatchIn(name)) add("urp")
        if (name.contains("树维") || Regex("(?<![A-Za-z])EAMS(?![A-Za-z])", RegexOption.IGNORE_CASE).containsMatchIn(name)) add("eams")
    }
    private fun normalize(name: String): String = name.replace('(', '（').replace(')', '）').replace(" ", "")
    fun load(context: Context, independent: List<School>): List<School> {
        fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
        return parseSources(read("shiguang/index.json"), read("shiguang/general.json"), read("academic/wakeup-schools.json"),
            read("catalog/schools.json"), read("catalog/logos.json"), independent, read("academic/school-adapters.json"),
            read("academic/school-systems.json"))
    }
    internal fun parseSources(indexRaw: String, toolsRaw: String, hintsRaw: String, schoolsRaw: String, logosRaw: String, independent: List<School>, extraRaw: String = "[]", systemEvidenceRaw: String = "[]"): List<School> {
        val logos = JSONObject(logosRaw)
        glyphs = logos.keys().asSequence().associateWith { logos.getString(it) }
        val index = JSONArray(indexRaw)
        fun readAdapters(list: JSONArray): List<Adapter> = (0 until list.length()).map { n -> list.getJSONObject(n).let { a ->
            Adapter(a.getString("id"), a.getString("name"), a.getString("category"), a.getString("url"),
                a.getString("path"), a.optString("maintainer"), a.optString("description"), a.optBoolean("combo"),
                repository = a.optString("repository", REPOSITORY), revision = a.optString("revision", COMMIT),
                license = a.optString("license", "MIT"), updated = a.optString("updated"), sourceUrl = a.optString("sourceUrl"),
                licenseAsset = a.optString("licenseAsset"), preferred = a.optBoolean("preferred", false))
        } }
        val tools = JSONArray(toolsRaw)
        general = (0 until tools.length()).flatMap { readAdapters(tools.getJSONObject(it).getJSONArray("adapters")) }
        val hints = JSONArray(hintsRaw)
        val recordedSystems = linkedMapOf<String, MutableSet<String>>()
        val knownTypes = setOf("zf_new", "zf", "zf_1", "qz", "qz_with_node", "qz_br", "qz_crazy", "qz_old", "urp", "urp_new", "cf")
        for (i in 0 until hints.length()) {
            val hint = hints.getJSONObject(i)
            val type = hint.getString("type")
            if (type in knownTypes) recordedSystems.getOrPut(normalize(hint.getString("name"))) { linkedSetOf() }.add(type)
        }
        // A source can identify the vendor without supplying a safe importer.
        // These exact school records add labels only: no adapter, URL or support
        // flag is inferred, and historical directory evidence remains intact.
        val vendorEvidence = JSONArray(systemEvidenceRaw)
        val evidenceTypes = knownTypes + setOf("zhengfang", "jinzhi", "eams")
        for (i in 0 until vendorEvidence.length()) {
            val row = vendorEvidence.getJSONObject(i)
            val type = row.getString("type")
            require(type in evidenceTypes)
            recordedSystems.getOrPut(normalize(row.getString("name"))) { linkedSetOf() }.add(type)
        }
        historical = (0 until hints.length()).mapNotNull { i ->
            val hint = hints.getJSONObject(i)
            AcademicPortalParser.adapters(hint.getString("url")).firstOrNull { it.parser == hint.getString("type") }
                ?.copy(id = "legacy_${i}", category = "HISTORICAL", directoryUrl = AcademicPortalParser.DIRECTORY_SOURCE)
                ?.let { normalize(hint.getString("name")) to it }
        }.groupBy({ it.first }, { it.second })
        val named = linkedMapOf<String, String>()
        val adapterMap = linkedMapOf<String, List<Adapter>>()
        for (i in 0 until index.length()) {
            val row = index.getJSONObject(i); val id = row.getString("id")
            named[normalize(row.getString("name"))] = id
            val list = row.getJSONArray("adapters")
            adapterMap[id] = readAdapters(list)
        }
        // 单校来源按完整名称合并；原有社区入口与独立引擎仍保留。
        val extra = JSONArray(extraRaw)
        for (i in 0 until extra.length()) {
            val row = extra.getJSONObject(i)
            val name = normalize(row.getString("name"))
            val id = named.getOrPut(name) { row.getString("id") }
            adapterMap[id] = adapterMap[id].orEmpty() + readAdapters(row.getJSONArray("adapters"))
        }
        byWarehouse = adapterMap
        for ((name, id) in named) {
            val types = recordedSystems.getOrPut(name) { linkedSetOf() }
            adapterMap[id].orEmpty().forEach { types.addAll(namedSystems(it.name)) }
            // A generic vendor name must not erase a recorded version (e.g. WMU's new Zhengfang).
            if (types.any { it in setOf("zf_new", "zf", "zf_1") }) types.remove("zhengfang")
        }
        systemTypes = recordedSystems.mapValues { it.value.toList() }
        val rows = JSONArray(schoolsRaw)
        val schools = (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
            val name = row.getString("name")
            val own = independent.firstOrNull { normalize(it.name) == normalize(name) }
            if (own?.supported == true) own.copy(province = row.optString("province"), level = row.optString("level")) else {
                val warehouseId = named[normalize(name)].orEmpty()
                val url = adapterMap[warehouseId]?.firstOrNull()?.url ?: historical[normalize(name)]?.firstOrNull()?.url ?: own?.homeUrl.orEmpty()
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
