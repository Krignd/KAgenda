package com.kstudio.agenda.data

import org.json.JSONObject

/** 每次提取单独作用域；静态绑定验证函数，避免 CSP 禁用 eval 后无法确认输入。 */
object WarehouseScriptRunner {
    private val reserved = ("break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof new return super switch this throw try typeof var void while with yield enum implements interface let package private protected public static await null true false").split(' ').toSet()
    private val identifiers = Regex("\\b(?:function|var|let|const)\\s+([A-Za-z_$][A-Za-z0-9_$]*)|[\"']([A-Za-z_$][A-Za-z0-9_$]*)[\"']")
    fun wrap(bridge: String, adapter: String, template: String): String {
        val names = identifiers.findAll(adapter).map { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
            .filter { it !in reserved && !it.startsWith("__kagenda") }.toSortedSet()
        val dispatch = names.joinToString("\n") { name ->
            "case ${JSONObject.quote(name)}: if (typeof $name === 'function') return $name(__kagendaValidatorInput); break;"
        }
        return template.replace("__BRIDGE_SOURCE__", bridge).replace("__VALIDATOR_DISPATCH__", dispatch).replace("__ADAPTER_SOURCE__", adapter)
    }
}
