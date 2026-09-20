@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kstudio.agenda.BuildConfig
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 运行日志查看页：可分享 / 清空 */
@Composable
fun LogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val t = LocalStrings.current
    var content by remember { mutableStateOf(t.readingLog) }
    var refreshKey by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }
    var showShareOptions by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        content = t.readingLog
        val empty = t.emptyLog
        content = withContext(Dispatchers.IO) { AppLog.readAll().ifBlank { empty } }
    }

    // 级别筛选：0=全部，1=警告及以上，2=仅错误
    val shownContent = when (filter) {
        1 -> content.lineSequence()
            .filter { it.contains(" W/") || it.contains(" E/") }
            .joinToString("\n").ifBlank { t.emptyLog }
        2 -> content.lineSequence()
            .filter { it.contains(" E/") }
            .joinToString("\n").ifBlank { t.emptyLog }
        else -> content
    }

    Column(Modifier.fillMaxSize()) {
        // 标题行紧贴「K日程」标题下方（不再用独立 Scaffold/AppBar，避免标题间距过大）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
            }
            Text(
                text = t.logTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showShareOptions = true }) {
                Icon(Icons.Filled.Share, contentDescription = t.share)
            }
            IconButton(onClick = {
                AppLog.clear()
                refreshKey++
            }) {
                Icon(Icons.Filled.Delete, contentDescription = t.clearLogBtn)
            }
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = t.refresh)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(t.logFilterAll, t.logFilterWarn, t.logFilterError)
                    .forEachIndexed { index, label ->
                        FilterChip(
                            selected = filter == index,
                            onClick = { filter = index },
                            label = { Text(label) },
                        )
                    }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                SelectionContainer {
                    Text(
                        text = shownContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 分享格式选择：生成 .txt 或 .md 文件（英文文件名 + 时间点）后分享
    if (showShareOptions) {
        AlertDialog(
            onDismissRequest = { showShareOptions = false },
            title = { Text(t.shareChooseFormat) },
            text = { Text(t.shareFormatHint) },
            confirmButton = {
                TextButton(onClick = {
                    showShareOptions = false
                    runCatching { shareLogFile(context, content, markdown = false) }
                }) { Text(t.shareAsTxt) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareOptions = false
                    runCatching { shareLogFile(context, content, markdown = true) }
                }) { Text(t.shareAsMd) }
            },
        )
    }
}

/**
 * 分享日志：先在缓存目录生成真实文件（文件名英文 + 时间点，如 kagenda_log_20260920_211806.md），
 * 再以附件形式分享。Markdown 便于直接粘贴到 issue，“.txt” 为纯文本。
 */
private fun shareLogFile(context: Context, text: String, markdown: Boolean) {
    val t = AppText.current
    val now = Date()
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
    val name = "kagenda_log_$stamp." + if (markdown) "md" else "txt"
    // 日志过长时只保留最后 30 万字符
    val payload = if (text.length > 300_000) text.takeLast(300_000) else text
    val body = if (markdown) {
        buildMarkdownLog(payload, now)
    } else {
        payload.ifBlank { t.emptyLog }
    }
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, name)
    runCatching { file.writeText(body, Charsets.UTF_8) }.onFailure { return }
    val uri = runCatching {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (markdown) "text/markdown" else "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, t.logShareSubject)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, name)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, t.share)) }
}

/** Markdown 版日志：基本信息头 + 代码块（便于直接贴到 issue / 聊天窗口） */
private fun buildMarkdownLog(payload: String, at: Date): String {
    val t = AppText.current
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(at)
    return buildString {
        append("# ").append(t.logShareSubject).append("\n\n")
        append("- ").append(t.logMdTime).append(": ").append(time).append('\n')
        append("- ").append(t.logMdVersion).append(": ")
        append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        append("- ").append(t.logMdDevice).append(": ")
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append(" · Android ").append(Build.VERSION.RELEASE)
        append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
        append("```text\n")
        append(payload.ifBlank { t.emptyLog })
        if (!payload.endsWith("\n")) append('\n')
        append("```\n")
    }
}
