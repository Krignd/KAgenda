package com.kstudio.agenda.data.engines

import android.content.Context
import android.net.Uri
import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText

fun engineText(zh: String, en: String, fr: String): String = when (AppText.state.value) {
    AppLang.ZH -> zh
    AppLang.FR -> fr
    else -> en
}

enum class EngineId(val key: String, val split: String, val entry: String) {
    OCR("ocr", "engine_ocr", "com.kstudio.agenda.engines.OcrEngine"),
    PDF("pdf", "engine_pdf", "com.kstudio.agenda.engines.PdfEngine"),
    AI("ai", "engine_ai", "");
    val title: String get() = when (this) {
        OCR -> engineText("截图文字识别组件", "Image text recognition", "Reconnaissance de texte")
        PDF -> engineText("PDF 读取组件", "PDF reader", "Lecture PDF")
        AI -> engineText("本地 AI 运行组件", "Local AI runtime", "Moteur IA local")
    }
    companion object { fun find(key: String?) = entries.firstOrNull { it.key == key } }
}

class EngineMissingException(val engine: EngineId) : IllegalStateException(
    engineText("请先下载并安装", "Download and install first: ", "Téléchargez et installez : ") + engine.title)

interface OcrEngineContract { suspend fun extract(context: Context, uri: Uri): String }
interface PdfEngineContract { fun extract(context: Context, bytes: ByteArray): String }

object EngineFeatures {
    fun installed(context: Context, engine: EngineId): Boolean =
        context.packageManager.getApplicationInfo(context.packageName, 0).splitNames.orEmpty().contains(engine.split)

    fun requireInstalled(context: Context, engine: EngineId) {
        if (!installed(context, engine)) throw EngineMissingException(engine)
    }

    fun <T> instance(context: Context, engine: EngineId, contract: Class<T>): T {
        requireInstalled(context, engine)
        try {
            val splitContext = context.createContextForSplit(engine.split)
            return contract.cast(splitContext.classLoader.loadClass(engine.entry).getDeclaredConstructor().newInstance())!!
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException(engineText("组件已安装，请重新打开应用后重试", "Component installed. Reopen the app and retry.", "Composant installé. Rouvrez l’application."), e)
        }
    }
}
