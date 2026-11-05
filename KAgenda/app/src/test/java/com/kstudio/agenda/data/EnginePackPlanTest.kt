package com.kstudio.agenda.data

import com.kstudio.agenda.data.engines.EngineId
import com.kstudio.agenda.data.engines.EnginePackFile
import com.kstudio.agenda.data.engines.EnginePackPlan
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EnginePackPlanTest {
    private fun manifest(): JSONObject = JSONObject("""{"schema":1,"packageName":"com.kstudio.agenda","versionCode":20261101,"baseApkSha256":{"arm64-v8a":"${"c".repeat(64)}"},"engines":{"ocr":{"abis":{"arm64-v8a":[{"name":"engine-ocr-20261101-arm64.apk","bytes":1024,"sha256":"${"a".repeat(64)}"}],"x86_64":[{"name":"engine-ocr-20261101-x86.apk","bytes":2048,"sha256":"${"b".repeat(64)}"}]}}}}""")
    private fun parse(root: JSONObject, abis: List<String> = listOf("arm64-v8a", "armeabi-v7a")) =
        EnginePackPlan.parse(root.toString(), EngineId.OCR, 20261101, abis)
    private fun rejects(block: () -> Unit) { try { block(); fail("Accepted an invalid component manifest") } catch (_: Exception) { } }
    @Test fun `chooses device ABI without downloading other architectures`() {
        val plan = parse(manifest()); assertEquals("arm64-v8a", plan.abi); assertEquals(1024L, plan.bytes)
        assertEquals("x86_64", parse(manifest(), listOf("x86_64", "x86")).abi)
    }
    @Test fun `rejects wrong package version missing ABI and unsupported engine`() {
        rejects { parse(manifest().put("packageName", "other.package")) }
        rejects { parse(manifest().put("versionCode", 20261006)) }
        rejects { parse(manifest().put("schema", 2)) }
        rejects { parse(manifest(), listOf("x86")) }
        rejects { EnginePackPlan.parse(manifest().toString(), EngineId.AI, 20261101, listOf("arm64-v8a")) }
    }
    @Test fun `rejects path traversal mixed module unbounded files and bad hash`() {
        for (name in listOf("../evil.apk", "engine-pdf-1.apk", "https://evil/engine-ocr.apk", "engine-ocr.apk?x=1", "engine-ocr.apk/extra")) {
            val root = manifest(); root.getJSONObject("engines").getJSONObject("ocr").getJSONObject("abis").getJSONArray("arm64-v8a").getJSONObject(0).put("name", name)
            rejects { parse(root) }
        }
        for (bytes in listOf(-1L, 0L, 80L*1024*1024+1)) {
            val root = manifest(); root.getJSONObject("engines").getJSONObject("ocr").getJSONObject("abis").getJSONArray("arm64-v8a").getJSONObject(0).put("bytes", bytes)
            rejects { parse(root) }
        }
        val root = manifest(); root.getJSONObject("engines").getJSONObject("ocr").getJSONObject("abis").getJSONArray("arm64-v8a").getJSONObject(0).put("sha256", "bad")
        rejects { parse(root) }
    }
    @Test fun `rejects duplicate packages and unsafe download origins`() {
        val root = manifest(); val files = root.getJSONObject("engines").getJSONObject("ocr").getJSONObject("abis").getJSONArray("arm64-v8a")
        files.put(files.getJSONObject(0)); rejects { parse(root) }
        val file = EnginePackFile("engine-ocr-1.apk", 10, "a".repeat(64))
        assertEquals("https://example.invalid/engines/20261101/engine-ocr-1.apk", EnginePackPlan.downloadUrl("https://example.invalid/engines/20261101/manifest.json", file))
        rejects { EnginePackPlan.downloadUrl("http://example.invalid/manifest.json", file) }
        rejects { EnginePackPlan.downloadUrl("https://user:pass@example.invalid/manifest.json", file) }
    }
}
