package com.kstudio.agenda.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * 耗时与容错策略：
 * 1. 默认请求 JSON 输出模式（response_format=json_object）；仅当接口明确不支持（HTTP 400/422）时降级重试一次；
 * 2. 输出被 max_tokens 截断（finish_reason=length）时自动提高上限重试一次（上限本身不额外计费）；
 * 3. 网络类错误与 5xx/限流/余额等错误直接抛出（重试不会成功），并对状态码给出精确提示；
 * 4. 整次识别有 90 秒总预算，超时给明确文案，避免界面长时间“看似卡住”。
 */
object AiClient {

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 30_000

    /** 单次识别的总耗时预算（跨重试）：超时给出明确提示 */
    private const val TOTAL_BUDGET_MS = 90_000L
    private const val MAX_TOKENS = 2_000

    /** 返回模型回复文本；失败抛异常（调用方捕获并展示；错误消息已做“可行动”的精确映射） */
    suspend fun chat(apiKey: String, model: String, systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            val reply = withTimeoutOrNull(TOTAL_BUDGET_MS) {
                try {
                    chatOnce(apiKey, model, systemPrompt, userPrompt)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    throw friendly(e)
                }
            }
            reply ?: throw Exception("AI 响应超时（超过 90 秒），请检查网络后重试")
        }

    /** 单轮识别：JSON 模式失败时，仅对“接口不接受 JSON 模式”的 400/422 降级重试一次 */
    private fun chatOnce(apiKey: String, model: String, systemPrompt: String, userPrompt: String): String {
        val first = runCatching {
            request(apiKey, model, systemPrompt, userPrompt, jsonMode = true, maxTokens = MAX_TOKENS)
        }
        first.getOrNull()?.let { return it }
        val err = first.exceptionOrNull() ?: error("未知错误")
        if (err is SocketTimeoutException || err is ConnectException || err is UnknownHostException) throw err
        // 5xx / 限流 / 余额等错误重试不会成功，直接抛出（避免让用户白等一整轮）
        val msg = err.message.orEmpty()
        if (msg.startsWith("HTTP 400") || msg.startsWith("HTTP 422")) {
            return request(apiKey, model, systemPrompt, userPrompt, jsonMode = false, maxTokens = MAX_TOKENS)
        }
        throw err
    }

    /** 把网络层异常映射为对用户更精确的提示（告诉用户下一步该做什么） */
    private fun friendly(e: Throwable): Throwable = when (e) {
        is SocketTimeoutException -> Exception("连接 AI 服务超时，请检查网络后重试")
        is ConnectException, is UnknownHostException -> Exception("网络不可用，请检查网络连接后重试")
        else -> e
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
                val detail = runCatching {
                    JSONObject(text).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty()
                throw Exception(httpErrorMessage(code, detail))
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

    /** HTTP 状态码 → 精确、可行动的用户提示 */
    private fun httpErrorMessage(code: Int, detail: String): String = when (code) {
        401 -> "API Key 无效或已过期，请检查「设置 → AI 识别」"
        402 -> "余额不足：请充值，或改用自备 API Key"
        403 -> "请求被拒绝（HTTP 403），请检查 API Key 权限"
        404 -> "接口不存在（HTTP 404），请检查模型设置"
        429 -> "请求过于频繁，请稍等片刻再试"
        in 500..599 -> "AI 服务暂时不可用（HTTP $code），请稍后重试"
        else -> "HTTP $code ${detail.take(120)}".trim()
    }
}
