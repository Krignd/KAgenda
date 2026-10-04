package com.kstudio.agenda.data

import com.kstudio.agenda.util.AppLog
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 用户反馈提交（POST 到开发者站点）。
 *
 * 服务端接口与部署方式见 `相关文件\更新站点部署指南.md` 的「反馈接口」一节：
 * 站点是静态的，所以反馈需要一个小小的后端（文档里给了可直接用的 Nginx + 服务脚本配置）。
 *
 * 隐私：只发送用户自己写的内容 + 主题 + 联系方式（可留空）+ 应用版本 / 系统版本 / 当前学校，
 * **不发送学号、密码、课表内容**（与设置页的提示文案一致）。
 */
object FeedbackClient {

    /** 提交地址（与 AppUpdater.MANIFEST_URL 同域；改路径要同步改服务端 Nginx） */
    private const val ENDPOINT = "https://20071009.xyz/KAgenda/feedback"

    private const val TIMEOUT_MS = 15_000

    private const val TAG = "Feedback"

    data class Payload(
        val topic: String,
        val text: String,
        val contact: String,
        val appVersion: String,
        val school: String,
        val android: String,
    )

    /** 提交；**返回 null 表示成功**，否则返回错误描述（给界面提示用） */
    fun send(payload: Payload): String? {
        val result = runCatching {
            val body = JSONObject().apply {
                put("topic", payload.topic)
                put("text", payload.text.take(4000))
                if (payload.contact.isNotBlank()) put("contact", payload.contact.take(120))
                put("app", payload.appVersion)
                put("school", payload.school)
                put("android", payload.android)
                put("ts", System.currentTimeMillis())
            }.toString()
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "KAgenda/${payload.appVersion}")
            }
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use(BufferedReader::readText)
                .orEmpty()
            conn.disconnect()
            if (code in 200..299) null else "HTTP $code ${text.take(120)}"
        }
        result.exceptionOrNull()?.let { AppLog.e(TAG, "反馈提交失败", it) }
        return result.getOrElse { it.message ?: "unknown" }
    }
}
