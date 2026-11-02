@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import com.kstudio.agenda.ui.theme.glassFrost
import com.kstudio.agenda.ui.theme.LocalGlassEnabled

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kstudio.agenda.ui.components.AppOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.kstudio.agenda.ui.components.AppTextButton as TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.i18n.searchHelpQuestions
import com.kstudio.agenda.notif.StatusNotification
import com.kstudio.agenda.ui.components.CollapsibleSectionCard
import com.kstudio.agenda.ui.theme.overlayBackgroundColor

/** A separate full-screen window keeps Settings and its scroll position intact. */
@Composable
fun HelpScreen(initialTopic: String = "", onClose: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val glass = LocalGlassEnabled.current
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    // Separate the reading cards from the page by luminance, including in dark mode.
    val pageColor = if (!glass) overlayBackgroundColor()
        else if (dark) Color(0xD9101A2A) else Color(0xCDE0E9F7)
    val cardColor = if (!glass) com.kstudio.agenda.ui.components.cardBaseColor()
        else if (dark) Color(0xD933455E) else scheme.surface
    var query by rememberSaveable { mutableStateOf("") }
    val questions = t.helpQuestions
    val results = remember(questions, query) { searchHelpQuestions(questions, query) }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(Modifier.fillMaxSize().glassFrost(popup = true), color = pageColor, contentColor = scheme.onSurface) {
            Scaffold(
                containerColor = if (LocalGlassEnabled.current) androidx.compose.ui.graphics.Color.Transparent else overlayBackgroundColor(),
                contentColor = scheme.onSurface,
                topBar = {
                    TopAppBar(
                        title = { Text(t.helpTitle) },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
                            }
                        },
                    )
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(t.helpSearch) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = t.helpClearSearch)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (results.isEmpty()) item {
                            Text(t.helpNoResults, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(results, key = { it.id }) { question ->
                            var expanded by rememberSaveable(question.id, query) {
                                mutableStateOf(question.id == initialTopic || query.isNotBlank())
                            }
                            CollapsibleSectionCard(
                                title = question.title,
                                expanded = expanded,
                                onToggle = { expanded = !expanded },
                                containerColor = cardColor,
                                contentColor = scheme.onSurface,
                            ) {
                                Text(question.answer, style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface)
                                if (question.id == "lock_screen") TextButton(onClick = {
                                    val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, StatusNotification.CHANNEL_ID)
                                    }
                                    runCatching { context.startActivity(channelIntent) }.onFailure {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            })
                                        }
                                    }
                                }) { Text(t.btnOpenNotifSettings) }
                            }
                        }
                    }
                }
            }
        }
    }
}
