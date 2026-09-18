package com.kstudio.agenda.data

import android.util.Base64

/**
 * 内置的开发者 DeepSeek API Key（设置页「使用开发者的 API key」默认启用）。
 *
 * 保护措施：常量以混淆形式存储（掩码异或 + Base64 + 反转），运行时在内存中还原，
 * 源码与 APK 中均不出现明文；配合 AppLog 的 sk- 遮蔽，确保任何日志/异常信息不携带该 Key。
 */
object DevKey {

    private const val MASK = "kagenda-2026-kstudio-mask"

    /** 混淆串（反转的 Base64；与掩码异或后还原明文） */
    private const val OBFUSCATED_REVERSED = "=EgBIl1VNYwAVxFXHhVVU0wCVZBQDt1TTRACEkUWBwVAKpAG"

    @Volatile
    private var cached: String? = null

    /** 解码后的 Key；解码失败返回空串（调用方按"未配置"处理） */
    fun value(): String {
        cached?.let { return it }
        val v = runCatching {
            val b64 = OBFUSCATED_REVERSED.reversed()
            val data = Base64.decode(b64, Base64.DEFAULT)
            val mask = MASK.toByteArray(Charsets.UTF_8)
            for (i in data.indices) {
                data[i] = (data[i].toInt() xor mask[i % mask.size].toInt()).toByte()
            }
            String(data, Charsets.UTF_8)
        }.getOrDefault("")
        cached = v
        return v
    }
}
