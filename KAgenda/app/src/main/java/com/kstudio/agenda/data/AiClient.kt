package com.kstudio.agenda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * DeepSeek Chat Completions 极简客户端（无第三方依赖，HttpURLConnection 实现）。
 * 需在「设置 → AI 识别」中配置 API Key；Key 经 Android Keystore 加密后仅存本机。
 *
 * 容错策略（提升识别成功率）：
 * 1. 默认请求 JSON 输出模式（response_format=json_object）；部分模型/接口不支持时自动降级重试一次；
 * 2. 输出被 max_tokens 截断（finish_reason=length）时自动提高上限重试一次（上限本身不额外计费）；
 * 3. 网络超时/无法连接类错误不重试，避免让用户长时间等待。
 */
object AiClient {

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val MAX_TOKENS = 2_000

    /** 返回模型回复文本；失败抛异常（调用方捕获并展示） */
    suspend fun chat(apiKey: String, model: String, systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            val first = runCatching {
                request(apiKey, model, systemPrompt, userPrompt, jsonMode = true, maxTokens = MAX_TOKENS)
            }
            first.getOrNull()?.let { return@withContext it }
            val err = first.exceptionOrNull() ?: error("未知错误")
            val networkError = err is SocketTimeoutException || err is ConnectException || err is UnknownHostException
            if (networkError) throw err
            // 接口/参数类错误（如不支持 response_format、HTTP 4xx）→ 去掉 JSON 模式重试一次
            request(apiKey, model, systemPrompt, userPrompt, jsonMode = false, maxTokens = MAX_TOKENS)
        }

    private fun request(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean,
        maxTokens: Int,
    ): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            val body = JSONObject().apply {
                put("model", model.ifBlank { "deepseek-flash" })
                put("temperature", 0.1)
                put("max_tokens", maxTokens)
                if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt)),
                )
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching {
                    JSONObject(text).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty()
                error("HTTP $code ${msg.take(160)}".trim())
            }
            val choice = JSONObject(text).getJSONArray("choices").getJSONObject(0)
            val content = choice.getJSONObject("message").optString("content", "")
            // 截断保护：输出被 max_tokens 截断 → 提高上限重试一次
            if (choice.optString("finish_reason") == "length" && maxTokens < 4 * MAX_TOKENS) {
                return request(apiKey, model, systemPrompt, userPrompt, jsonMode, maxTokens * 2)
            }
            return content
        } finally {
            conn.disconnect()
        }
    }
}
