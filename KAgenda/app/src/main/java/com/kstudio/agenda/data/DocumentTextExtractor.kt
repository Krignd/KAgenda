package com.kstudio.agenda.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.kstudio.agenda.util.AppLog
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 常见文档的纯文本提取（完全本地、无网络）。
 *
 * 支持读取：
 * - 纯文本类：txt / md / markdown / csv / tsv / json / xml / html / htm / log / ini / yaml
 * - Office（O OXML zip 包）：docx / xlsx / pptx（按 zip 内的 XML 提取文字）
 * - PDF：尽力而为地解出文本（扫描版/纯图片 PDF 无法提取，会给出明确提示）
 *
 * 不支持的格式（如 .doc/.xls 旧二进制格式）返回失败并给出提示。
 */
object DocumentTextExtractor {

    /** 单次读取上限：避免超大文件占用内存（超过则拒绝） */
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val TEXT_EXT = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "log", "ini", "yaml", "yml",
    )

    /** 是否是可读取的文档格式 */
    fun isSupported(displayName: String, mime: String?): Boolean {
        val ext = extensionOf(displayName)
        if (ext in TEXT_EXT) return true
        if (ext in setOf("docx", "xlsx", "pptx", "pdf")) return true
        val m = mime.orEmpty().lowercase()
        return m.startsWith("text/") ||
            m in setOf(
                "application/pdf",
                "application/json",
                "application/xml",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            )
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().trim()

    /** 查询文档显示名（用于判断格式） */
    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    /**
     * 读取 uri 指向的文档并提取纯文本。
     * 失败时返回 Result.failure（message 已本地化为可直接展示的说明）。
     */
    fun extract(context: Context, uri: Uri): Result<String> {
        val name = displayName(context, uri)
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val ext = extensionOf(name)
        val bytes = runCatching { readAll(context, uri) }
            .getOrElse { return Result.failure(it) }
        PDFBoxResourceLoader.init(context.applicationContext)
        return extractBytes(bytes, ext, mime)
    }

    internal fun extractBytes(bytes: ByteArray, ext: String, mime: String? = null): Result<String> {
        return runCatching {
            require(bytes.size <= MAX_BYTES)
            val resolved = when (mime?.substringBefore(';')?.lowercase()) {
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
                "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
                "application/pdf" -> "pdf"
                "text/html" -> "html"
                else -> ext.lowercase()
            }
            when {
                resolved in TEXT_EXT || (ext.isEmpty() && mime.orEmpty().startsWith("text/")) ->
                    decodeText(bytes, resolved)

                resolved == "html" || resolved == "htm" -> stripHtml(decodeText(bytes, resolved))

                mime == "application/json" || mime == "application/xml" -> decodeText(bytes, resolved)

                resolved == "docx" -> extractOoxml(bytes, listOf("word/document.xml")) { xml ->
                    officeText(xml, "w")
                }

                resolved == "pptx" -> extractOoxml(bytes, null) { xml ->
                    officeText(xml, "a")
                }

                resolved == "xlsx" -> extractXlsx(bytes)

                resolved == "pdf" || mime == "application/pdf" -> extractPdf(bytes)

                ext.isEmpty() -> decodeText(bytes, resolved)

                else -> error("UNSUPPORTED:$ext")
            }
        }
    }

    private fun readAll(context: Context, uri: Uri): ByteArray {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: error("无法打开文档")
        input.use { ins ->
            return BoundedInput.read(ins, MAX_BYTES)
        }
    }

    /** 文本文件解码：优先 UTF-8，含替换字符过多时回落 GBK（国内教务导出的 csv 常见） */
    private fun decodeText(bytes: ByteArray, ext: String): String {
        val bomText = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, Charsets.UTF_16BE)
            else -> null
        }
        val utf8 = bomText ?: String(bytes, Charsets.UTF_8)
        val bad = utf8.count { it == '\uFFFD' }
        val text = if (bomText == null && bad > 0 && bad * 50 > utf8.length) {
            runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
        } else {
            utf8
        }
        return if (ext == "html" || ext == "htm") stripHtml(text) else text.trimStart('\uFEFF')
    }

    /** 去掉 HTML 标签与常见实体，保留可读文本 */
    private fun stripHtml(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script,style,noscript,input,textarea,select,button").remove()
        return doc.body().let { body ->
            val tables = body.select("table")
            if (tables.isNotEmpty()) tables.joinToString("\n\n") { table -> table.select("tr").joinToString("\n") { row ->
                row.children().filter { it.tagName() in listOf("td", "th") }.joinToString("\t") { it.text() }
            } } else body.wholeText().trim()
        }
    }

    private fun officeText(xml: String, prefix: String): String {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        fun read(element: org.jsoup.nodes.Element): String = when (element.tagName()) {
            "$prefix:tbl" -> element.getElementsByTag("$prefix:tr").joinToString("\n") { row ->
                row.getElementsByTag("$prefix:tc").joinToString("\t") { cell ->
                    cell.getElementsByTag("$prefix:t").joinToString(" ") { it.text() }
                }
            }
            "$prefix:p" -> element.getElementsByTag("$prefix:t").joinToString("") { it.text() }
            else -> element.children().map(::read).filter { it.isNotBlank() }.joinToString("\n")
        }
        return read(doc)
    }

    private fun stripXmlTags(xml: String): String {
        // 先去标签再解实体，否则正文里的 &lt;文字&gt; 会被误删。
        val s = unescapeXml(xml.replace(Regex("<[^>]+>"), ""))
        return s.replace(Regex("[ \\t]{2,}"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /** 从 zip（OOXML）中提取指定条目（[entrySuffix] 为 null 时取全部匹配 XML 条目） */
    private fun extractOoxml(
        bytes: ByteArray,
        entryNames: List<String>?,
        transform: (String) -> String,
    ): String {
        val sb = StringBuilder()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var remaining = MAX_BYTES
            var entries = 0
            var entry = zip.nextEntry
            while (entry != null) {
                require(++entries <= 2048)
                val expanded = BoundedInput.read(zip, remaining)
                remaining -= expanded.size
                val name = entry.name
                val wanted = when {
                    entryNames != null -> name in entryNames
                    name.startsWith("ppt/slides/slide") && name.endsWith(".xml") -> true
                    else -> false
                }
                if (wanted && !entry.isDirectory) {
                    val text = transform(String(expanded, Charsets.UTF_8))
                    if (text.isNotBlank()) {
                        sb.append(text).append('\n')
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (sb.isBlank()) error("文档中未找到可读取的文字")
        return sb.toString().trimEnd('\n', '\r')
    }

    /** xlsx：sharedStrings + 单元格引用（含 inlineStr） → 制表符分隔的文本 */
    private fun extractXlsx(bytes: ByteArray): String {
        var shared = listOf<String>()
        val sheets = LinkedHashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var remaining = MAX_BYTES
            var entries = 0
            var entry = zip.nextEntry
            while (entry != null) {
                require(++entries <= 2048)
                val expanded = BoundedInput.read(zip, remaining)
                remaining -= expanded.size
                val name = entry.name
                if (!entry.isDirectory && name.endsWith(".xml")) {
                    val content = String(expanded, Charsets.UTF_8)
                    when {
                        name == "xl/sharedStrings.xml" -> {
                            shared = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
                                .findAll(content)
                                .map { m ->
                                    Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                                        .findAll(m.groupValues[1])
                                        .joinToString("") { it.groupValues[1] }
                                }
                                .map { unescapeXml(it) }
                                .toList()
                        }
                        name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                            sheets[name] = content
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (sheets.isEmpty()) error("文档中未找到可读取的表格")
        // ZIP 条目顺序不固定，共享字符串可能在工作表之后。
        return sheets.values.map { parseSheet(it, shared) }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /** 解析一个 worksheet：/row/c 的 r 属性决定列，t="s" 引用共享字符串 */
    private fun parseSheet(xml: String, shared: List<String>): String {
        val sb = StringBuilder()
        for (rowM in Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val cells = mutableListOf<Pair<Int, String>>()
            for (cM in Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(rowM.groupValues[1])) {
                val attrs = cM.groupValues[1]
                val body = cM.groupValues[2]
                val type = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1).orEmpty()
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1).orEmpty()
                val col = columnIndex(ref)
                val raw = when (type) {
                    "s" -> {
                        val i = Regex("<v>(\\d+)</v>").find(body)?.groupValues?.get(1)?.toIntOrNull()
                        i?.let { shared.getOrNull(it) }.orEmpty()
                    }
                    "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                        .findAll(body).joinToString("") { it.groupValues[1] }
                    else -> Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty()
                }
                if (raw.isNotBlank()) cells.add(col to if (type == "s") raw else unescapeXml(raw))
            }
            if (cells.isNotEmpty()) {
                val maxCol = cells.maxOf { it.first }
                val arr = Array(maxCol + 1) { "" }
                cells.forEach { arr[it.first] = it.second }
                sb.append(arr.joinToString("\t")).append('\n')
            }
        }
        return sb.toString().trimEnd('\n', '\r')
    }

    /** "AB" → 27（0 基） */
    private fun columnIndex(ref: String): Int {
        if (ref.isEmpty()) return 0
        var n = 0
        for (ch in ref) {
            n = n * 26 + (ch - 'A' + 1)
            require(n in 1..16384) { "Invalid worksheet column" }
        }
        return (n - 1).coerceAtLeast(0)
    }

    private fun unescapeXml(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")

    /** PDFBox resolves PDF fonts/ToUnicode maps; scanned pages require screenshot OCR. */
    private fun extractPdf(bytes: ByteArray): String {
        return PDDocument.load(bytes).use { doc ->
            require(doc.numberOfPages <= 100) { "PDF has too many pages" }
            val text = PDFTextStripper().apply { sortByPosition = true }.getText(doc).trim()
            require(text.isNotBlank()) { "PDF 中未提取到文字，请将课表页面截图后使用截图导入" }
            require(text.length <= MAX_BYTES) { "Input exceeds size limit" }
            text
        }
    }

    /** 记录一次提取失败（便于用户分享日志排查） */
    fun logFailure(name: String, t: Throwable) {
        AppLog.w("DocExtract", "文档 <$name> 读取失败：${t.message}")
    }
}
