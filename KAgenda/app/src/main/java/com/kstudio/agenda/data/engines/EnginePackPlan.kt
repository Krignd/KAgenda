package com.kstudio.agenda.data.engines

import org.json.JSONObject
import java.net.URI

/** Data-only manifest. Android's package installer verifies the APK signature/version at installation. */
data class EnginePackFile(val name: String, val bytes: Long, val sha256: String)
data class EnginePackPlan(val engine: EngineId, val abi: String, val files: List<EnginePackFile>, val baseHashes: Set<String>) {
    val bytes get() = files.sumOf { it.bytes }
    companion object {
        fun parse(raw: String, engine: EngineId, version: Int, supportedAbis: List<String>): EnginePackPlan {
            require(raw.length in 1..65536)
            val root = JSONObject(raw)
            require(root.getInt("schema") == 1 && root.getInt("versionCode") == version)
            require(root.getString("packageName") == "com.kstudio.agenda")
            val bases = root.getJSONObject("baseApkSha256")
            require(bases.length() in 1..8)
            val baseHashes = bases.keys().asSequence().map { bases.getString(it) }.toSet()
            require(baseHashes.all { it.matches(Regex("[a-f0-9]{64}")) })
            val abis = root.getJSONObject("engines").getJSONObject(engine.key).getJSONObject("abis")
            val abi = supportedAbis.firstOrNull { abis.has(it) } ?: "any".takeIf { abis.has(it) }
                ?: error("abi")
            val array = abis.getJSONArray(abi)
            require(array.length() in 1..8)
            val files = (0 until array.length()).map {
                val item = array.getJSONObject(it)
                val name = item.getString("name")
                require(name.matches(Regex("engine-${engine.key}[-A-Za-z0-9_.]*\\.apk")))
                val bytes = item.getLong("bytes"); require(bytes in 1..80L * 1024 * 1024)
                val hash = item.getString("sha256"); require(hash.matches(Regex("[a-f0-9]{64}")))
                EnginePackFile(name, bytes, hash)
            }
            require(files.map { it.name }.distinct().size == files.size)
            require(files.sumOf { it.bytes } <= 160L * 1024 * 1024)
            return EnginePackPlan(engine, abi, files, baseHashes)
        }

        fun downloadUrl(manifest: String, file: EnginePackFile): String {
            val base = URI(manifest)
            require(base.scheme == "https" && base.userInfo == null && base.query == null && base.fragment == null)
            val result = base.resolve(file.name)
            require(result.host == base.host && result.path.substringBeforeLast('/') == base.path.substringBeforeLast('/'))
            return result.toASCIIString()
        }
    }
}
