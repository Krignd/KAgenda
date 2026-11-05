package com.kstudio.agenda.engines

import android.content.Context
import com.kstudio.agenda.data.engines.PdfEngineContract
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

class PdfEngine : PdfEngineContract {
    override fun extract(context: Context, bytes: ByteArray): String {
        val MAX_BYTES = 8 * 1024 * 1024
        require(bytes.size <= MAX_BYTES)
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(bytes).use { doc ->
            require(doc.numberOfPages <= 100) { "PDF has too many pages" }
            val text = PDFTextStripper().apply { sortByPosition = true }.getText(doc).trim()
            require(text.isNotBlank()) { "PDF 中未提取到文字，请将课表页面截图后使用截图导入" }
            require(text.length <= MAX_BYTES) { "Input exceeds size limit" }
            text
        }
    }
}
