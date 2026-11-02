@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.i18n.LocalAiText.text as tr
import com.kstudio.agenda.model.*
import com.kstudio.agenda.ui.components.AppOutlinedTextField
import com.kstudio.agenda.ui.theme.FrostedAlertDialog
import com.kstudio.agenda.ui.theme.glassFrost
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun HolidayMarksEditor(vm: AppViewModel, initialDate: LocalDate, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val overrides = remember(settings.holidayOverridesRaw) { HolidayOverrides.decode(settings.holidayOverridesRaw) }
    var day by rememberSaveable { mutableLongStateOf(initialDate.toEpochDay()) }
    val date = LocalDate.ofEpochDay(day)
    var kind by remember(day, settings.holidayOverridesRaw) { mutableStateOf(overrides[day]?.kind) }
    var name by remember(day, settings.holidayOverridesRaw) { mutableStateOf(overrides[day]?.name.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    val valid = kind != HolidayKind.HOLIDAY || name.isNotBlank()
    fun label(value: HolidayKind?): String = when (value) {
        HolidayKind.HOLIDAY -> tr("假日", "Holiday", "Férié")
        HolidayKind.WORKDAY -> tr("班", "Workday", "Travaillé")
        HolidayKind.NORMAL -> tr("普通日期", "Unmarked date", "Sans marque")
        null -> tr("官方", "Official", "Officiel")
    }
    FrostedAlertDialog(onDismissRequest = onDismiss,
        title = { Text(tr("修改节假日标记", "Edit holiday marks", "Modifier les jours fériés")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text(date.toString()) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, HolidayKind.HOLIDAY, HolidayKind.WORKDAY, HolidayKind.NORMAL).forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), label = { Text(label(option)) })
                    }
                }
                if (kind == HolidayKind.HOLIDAY) AppOutlinedTextField(value = name,
                    onValueChange = { name = it.filter { c -> c != '|' && !c.isISOControl() }.take(20) },
                    label = { Text(tr("假日名称", "Holiday name", "Nom du jour férié")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text(tr("自定义标记优先于官方数据。假日遵循“节假日显示课表”设置；“班”只标记上班日，不自动调换课程。普通日期清除节假日标记，周末属性保留。选择官方可恢复该日。",
                    "Custom marks override official data. Holidays follow the holiday-course setting. Workday marks do not move classes. Unmarked dates keep their weekday. Choose Official to restore a date.",
                    "Les marques personnalisées priment. Les jours fériés suivent le réglage des cours. Un jour travaillé ne déplace pas les cours. Le jour de semaine est conservé. Officiel rétablit la date."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (overrides.isNotEmpty()) {
                    Text(tr("已修改 · 点按编辑", "Edited · tap to edit", "Modifiés · toucher pour éditer"), style = MaterialTheme.typography.labelLarge)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(overrides.values.sortedBy { it.date }, key = { it.date.toEpochDay() }) { item ->
                            TextButton(onClick = { day = item.date.toEpochDay() }, modifier = Modifier.fillMaxWidth()) {
                                Text("${item.date}  ·  ${if (item.kind == HolidayKind.HOLIDAY) item.name else label(item.kind)}")
                            }
                        }
                    }
                }
            }
        }, confirmButton = {
            TextButton(enabled = valid, onClick = {
                vm.setHolidayOverride(date, kind?.let { HolidayOverride(date, it, if (it == HolidayKind.HOLIDAY) name.trim() else "") })
                onDismiss()
            }) { Text(if (kind == null) tr("恢复官方", "Restore official", "Rétablir") else tr("保存", "Save", "Enregistrer")) }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel", "Annuler")) } })
    if (pickDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), yearRange = 2000..2100)
        DatePickerDialog(onDismissRequest = { pickDate = false }, modifier = Modifier.glassFrost(popup = true),
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                pickDate = false
            }) { Text(tr("确定", "OK", "OK")) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(tr("取消", "Cancel", "Annuler")) } }) {
            DatePicker(state = picker)
        }
    }
}
