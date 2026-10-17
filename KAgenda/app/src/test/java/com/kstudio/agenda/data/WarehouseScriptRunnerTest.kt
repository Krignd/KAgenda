package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONObject

class WarehouseScriptRunnerTest {
    private val assets = File(requireNotNull(System.getProperty("kagenda.assets")))
    private val bridge = File(assets, "shiguang/bridge.js").readText().replace("__TOKEN__", "\"fixture-session\"")
    private val template = File(assets, "shiguang/runner.js").readText()
    @Test fun `produce the same runner used by the native host for original adapter simulations`() {
        val output = File(requireNotNull(System.getProperty("kagenda.runners"))).apply { mkdirs() }
        val sourceIndex = JSONObject(File(assets, "shiguang/provenance.json").readText()).getJSONObject("scripts")
        sourceIndex.keys().forEach { path ->
            val adapter = File(assets, "shiguang/scripts/$path").readText()
            File(output, "all/$path").apply { parentFile?.mkdirs() }.writeText(WarehouseScriptRunner.wrap(bridge, adapter, template))
        }
        for ((id, path) in listOf("cqu-api" to "CQU/cqu.js", "tongji-selection" to "TONGJI/tongji_01.js", "uzz-form" to "UZZ/uzz_01.js", "niit-merge" to "NIIT/niit.js")) {
            val adapter = File(assets, "shiguang/scripts/$path").readText()
            val result = WarehouseScriptRunner.wrap(bridge, adapter, template)
            assertTrue(result.contains(adapter)) // Source content is retained inside the wrapper.
            assertFalse(result.contains("__VALIDATOR_DISPATCH__"))
            assertFalse(result.contains("__ADAPTER_SOURCE__"))
            File(output, "$id.js").writeText(result)
        }
    }
    @Test fun `script local validators are bound without dynamic code evaluation`() {
        val result = WarehouseScriptRunner.wrap(bridge, "const validator = value => false; function checkDate(value) { return false; }", template)
        assertTrue(result.contains("typeof validator === 'function'"))
        assertTrue(result.contains("typeof checkDate === 'function'"))
        assertFalse(result.contains("eval("))
        assertFalse(result.contains("new Function"))
    }
}
