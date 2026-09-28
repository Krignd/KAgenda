package com.kstudio.agenda.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kstudio.agenda.util.AppLog
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * 常见文档的纯文本提取（完全本地、无需第三方库、无网络）。
 *
 * 支持读取：
 * - 纯文本类：txt / md / markdown / csv / tsv / json / xml / html / htm / log / ini / yaml
 * - Office（O OXML zip 包）：docx / xlsx / pptx（按 zip 内的 XML 提取文字）
 * - PDF：尽力而为地解出文本（扫描版/纯图片 PDF 无法提取，会给出明确提示）
 *
 * 不支持的格式（如 .doc/.xls 旧二进制格式）返回失败并给出提示。
 */
object DocumentTextExtractor {

    /** 单次读取上限：避免超大文件占用内存（超过则截断） */
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
        return runCatching {
            when {
                ext in TEXT_EXT || (ext.isEmpty() && mime.orEmpty().startsWith("text/")) ->
                    decodeText(bytes, ext)

                ext == "html" || ext == "htm" -> stripHtml(decodeText(bytes, ext))

                mime == "application/json" || mime == "application/xml" -> decodeText(bytes, ext)

                ext == "docx" -> extractOoxml(bytes, listOf("word/document.xml")) { xml ->
                    xml.replace("</w:p>", "\n").replace("<w:br/>", "\n").let { stripXmlTags(it) }
                }

                ext == "pptx" -> extractOoxml(bytes, null) { xml ->
                    xml.replace("</a:p>", "\n").let { stripXmlTags(it) }
                }

                ext == "xlsx" -> extractXlsx(bytes)

                ext == "pdf" || mime == "application/pdf" -> extractPdf(bytes)

                ext.isEmpty() -> decodeText(bytes, ext)

                else -> error("UNSUPPORTED:$ext")
            }
        }
    }

    private fun readAll(context: Context, uri: Uri): ByteArray {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: error("无法打开文档")
        input.use { ins ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_BYTES) {
                    out.write(buf, 0, n - (total - MAX_BYTES))
                    break
                }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    /** 文本文件解码：优先 UTF-8，含替换字符过多时回落 GBK（国内教务导出的 csv 常见） */
    private fun decodeText(bytes: ByteArray, ext: String): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        val bad = utf8.count { it == '\uFFFD' }
        val text = if (bad > 0 && bad * 50 > utf8.length) {
            runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
        } else {
            utf8
        }
        return if (ext == "html" || ext == "htm") stripHtml(text) else text
    }

    /** 去掉 HTML 标签与常见实体，保留可读文本 */
    private fun stripHtml(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), "\n")
        s = s.replace(Regex("(?i)</t[dh]>"), "\t")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        return s.replace(Regex("[ \\t\\u00A0]{2,}"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun stripXmlTags(xml: String): String {
        var s = xml.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&apos;", "'")
        s = s.replace(Regex("<[^>]+>"), "")
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
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val wanted = when {
                    entryNames != null -> name in entryNames
                    name.startsWith("ppt/slides/slide") && name.endsWith(".xml") -> true
                    else -> false
                }
                if (wanted && !entry.isDirectory) {
                    val text = transform(String(readEntry(zip), Charsets.UTF_8))
                    if (text.isNotBlank()) {
                        sb.append(text).append('\n')
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (sb.isBlank()) error("文档中未找到可读取的文字")
        return sb.toString().trim()
    }

    /** xlsx：sharedStrings + 单元格引用（含 inlineStr） → 制表符分隔的文本 */
    private fun extractXlsx(bytes: ByteArray): String {
        var shared = listOf<String>()
        val sheets = LinkedHashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && name.endsWith(".xml")) {
                    val content = String(readEntry(zip), Charsets.UTF_8)
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
                            sheets[name] = parseSheet(content, shared)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (sheets.isEmpty()) error("文档中未找到可读取的表格")
        return sheets.values.filter { it.isNotBlank() }.joinToString("\n\n")
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
                if (raw.isNotBlank()) cells.add(col to unescapeXml(raw))
            }
            if (cells.isNotEmpty()) {
                val maxCol = cells.maxOf { it.first }
                val arr = Array(maxCol + 1) { "" }
                cells.forEach { arr[it.first] = it.second }
                sb.append(arr.joinToString("\t")).append('\n')
            }
        }
        return sb.toString().trim()
    }

    /** "AB" → 27（0 基） */
    private fun columnIndex(ref: String): Int {
        if (ref.isEmpty()) return 0
        var n = 0
        for (ch in ref) n = n * 26 + (ch - 'A' + 1)
        return (n - 1).coerceAtLeast(0)
    }

    private fun unescapeXml(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")

    private fun readEntry(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = zip.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------ PDF（尽力而为）

    /**
     * PDF 文本提取：遍历所有 stream，尝试 zlib 解压后从内容流里取
     * `(文字) Tj` / `[(文字) ...] TJ` / `<hex> Tj` 的文本。
     * 对扫描版 PDF（图片）无效——此时返回空并给出提示。
     */
    private fun extractPdf(bytes: ByteArray): String {
        val sb = StringBuilder()
        var cursor = 0
        while (true) {
            val s = indexOf(bytes, "stream", cursor)
            if (s < 0) break
            var start = s + 6
            if (start < bytes.size && bytes[start] == 13.toByte()) start++
            if (start < bytes.size && bytes[start] == 10.toByte()) start++
            val e = indexOf(bytes, "endstream", start)
            if (e < 0) break
            var end = e
            while (end > start && (bytes[end - 1] == 10.toByte() || bytes[end - 1] == 13.toByte())) end--
            val chunk = bytes.copyOfRange(start, end)
            val content = runCatching { inflate(chunk) }.getOrNull()
                ?: chunk.takeIf { looksLikeContentStream(it) }
            if (content != null) {
                val text = pdfTextFromContent(String(content, Charsets.ISO_8859_1))
                if (text.isNotBlank()) sb.append(text).append('\n')
            }
            cursor = e + 9
        }
        val out = sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
        if (out.isBlank()) error("PDF 中未提取到文字（可能是扫描版/图片型 PDF）")
        return out
    }

    private fun looksLikeContentStream(b: ByteArray): Boolean {
        val s = String(b, Charsets.ISO_8859_1)
        return s.contains(" Tj") || s.contains(" TJ") || s.contains("BT")
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val n = runCatching { inflater.inflate(buf) }.getOrDefault(-1)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        inflater.end()
        val result = out.toByteArray()
        if (result.isEmpty()) error("empty")
        return result
    }

    /** 从内容流中提取文本操作符的可见文字 */
    private fun pdfTextFromContent(content: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < content.length) {
            when (content[i]) {
                '(' -> {
                    val (text, next) = readPdfString(content, i)
                    sb.append(text)
                    i = next
                }
                '[' -> {
                    // TJ 数组：(文字) -100 (文字) …
                    var j = i + 1
                    while (j < content.length && content[j] != ']') {
                        if (content[j] == '(') {
                            val (text, next) = readPdfString(content, j)
                            sb.append(text)
                            j = next
                        } else {
                            j++
                        }
                    }
                    i = j
                }
                'T' -> {
                    // Td / TD / T* / Tj / TJ → 换行（粗略：新的一行文字）
                    if (i + 1 < content.length && content[i + 1] in "dD*") {
                        if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                    } else if (i + 1 < content.length && (content[i + 1] == 'j' || content[i + 1] == 'J')) {
                        if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                    }
                    i += 2
                }
                else -> i++
            }
        }
        return sb.toString().lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
    }

    /** 读取 PDF 字符串 "(...)"，处理转义与嵌套括号 */
    private fun readPdfString(s: String, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        var depth = 1
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> {
                    val n = s[i + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b', 'f' -> Unit
                        '(', ')', '\\' -> sb.append(n)
                        in '0'..'7' -> {
                            var oct = ""
                            var k = i + 1
                            while (k < s.length && oct.length < 3 && s[k] in '0'..'7') {
                                oct += s[k]
                                k++
                            }
                            oct.toIntOrNull(8)?.let { sb.append(it.toChar()) }
                            i = k - 1
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                }
                c == '(' -> {
                    depth++
                    sb.append(c)
                    i++
                }
                c == ')' -> {
                    depth--
                    if (depth == 0) return sb.toString() to (i + 1)
                    sb.append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString() to i
    }

    private fun indexOf(haystack: ByteArray, needle: String, from: Int): Int {
        val n = needle.toByteArray(Charsets.US_ASCII)
        outer@ for (i in from..haystack.size - n.size) {
            for (j in n.indices) {
                if (haystack[i + j] != n[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /** 记录一次提取失败（便于用户分享日志排查） */
    fun logFailure(name: String, t: Throwable) {
        AppLog.w("DocExtract", "文档 <$name> 读取失败：${t.message}")
    }
}
