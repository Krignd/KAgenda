@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.SchoolLocationsStore
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaTypes
import com.kstudio.agenda.model.Schools

@Composable
fun CourseColorPicker(value: Int, onChange: (Int) -> Unit) {
    val t = LocalStrings.current
    Column {
        Text(t.labelColor, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = value == 0, onClick = { onChange(0) }, label = { Text(t.colorAuto) })
            AgendaTypes.PALETTE.forEach { c ->
                Box(Modifier.padding(vertical = 8.dp).size(30.dp)
                    .background(Color(c), CircleShape)
                    .border(if (c == value) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onChange(c) })
            }
        }
    }
}

@Composable
fun LocationSuggestions(onPick: (String) -> Unit) {
    val all by SchoolLocationsStore.locations.collectAsState()
    val names = all[Schools.currentId] ?: SchoolLocationsStore.defaultsFor(Schools.currentId)
    if (names.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { name -> SuggestionChip(onClick = { onPick(name) }, label = { Text(name) }) }
        }
    }
}

@Composable
fun StatusTagPicker(value: String, onChange: (String) -> Unit) {
    val t = LocalStrings.current
    Text(t.eventTags, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("delayed" to t.statusDelayed, "advanced" to t.statusAdvanced, "waiting" to t.statusWaiting).forEach { (key, label) ->
            FilterChip(selected = value == key, onClick = { onChange(if (value == key) "" else key) }, label = { Text(label) })
        }
    }
}

@Composable
fun ReminderOptions(follow: Boolean, notify: Boolean, alarm: Boolean, lead: Int,
    onFollow: (Boolean) -> Unit, onNotify: (Boolean) -> Unit, onAlarm: (Boolean) -> Unit, onLead: (Int) -> Unit) {
    val t = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t.followGlobal, Modifier.weight(1f))
        Switch(checked = follow, onCheckedChange = onFollow)
    }
    if (!follow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.notifyEvent, Modifier.weight(1f)); Switch(checked = notify, onCheckedChange = onNotify)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.alarmEvent, Modifier.weight(1f)); Switch(checked = alarm, onCheckedChange = onAlarm)
        }
        if (alarm) Text(t.alarmHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notify || alarm) {
            Text(t.leadLabel, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 5, 10, 15, 20, 30, 45, 60).forEach { minutes ->
                    FilterChip(selected = lead == minutes, onClick = { onLead(minutes) },
                        label = { Text(if (minutes == 0) t.leadZero else t.minutes(minutes)) })
                }
            }
            val context = LocalContext.current
            if (android.os.Build.VERSION.SDK_INT >= 31 && !com.kstudio.agenda.notif.ReminderScheduler.canScheduleExact(context)) {
                TextButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + context.packageName))) }
                }) { Text(t.gotoGrant) }
            }
        }
    }
}

@Composable
fun statusTagLabel(key: String): String {
    val t = LocalStrings.current
    return when (key) { "delayed" -> t.statusDelayed; "advanced" -> t.statusAdvanced; "waiting" -> t.statusWaiting; else -> "" }
}
