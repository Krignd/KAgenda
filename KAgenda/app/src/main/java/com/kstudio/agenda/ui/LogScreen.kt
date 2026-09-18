@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 运行日志查看页：可分享 / 清空 */
@Composable
fun LogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val t = LocalStrings.current
    var content by remember { mutableStateOf(t.readingLog) }
    var refreshKey by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.logTitle) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        runCatching { shareLog(context, content) }
                    }) {
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
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
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
}

private fun shareLog(context: Context, text: String) {
    val t = AppText.current
    // 日志过长时只发送最后 30 万字符
    val payload = if (text.length > 300_000) text.takeLast(300_000) else text
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, t.logShareSubject)
        putExtra(Intent.EXTRA_TEXT, payload.ifBlank { t.emptyLog })
    }
    context.startActivity(Intent.createChooser(intent, t.share))
}
