package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.SchoolLocationsStore
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.ui.components.CollapsibleSectionCard
import java.time.LocalDate

@Composable
fun DataManagementCards(vm: AppViewModel) {
    val t = LocalStrings.current
    val settings by vm.settings.collectAsState()
    val agenda by vm.agenda.collectAsState()
    val locations by SchoolLocationsStore.locations.collectAsState()
    var locationText by remember(settings.schoolId, locations) { mutableStateOf(SchoolLocationsStore.forSchool(settings.schoolId).joinToString("\n")) }
    var expLocations by remember { mutableStateOf(false) }
    var expEvents by remember { mutableStateOf(false) }
    var expHolidays by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<AgendaEvent?>(null) }
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var source by remember { mutableStateOf("") }
    val fetching by vm.holidayFetching.collectAsState()
    val holidayStatus by vm.holidayStatus.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CollapsibleSectionCard(t.holidayFetch, t.holidayHint, expHolidays, { expHolidays = !expHolidays }) {
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text(t.holidayYear) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (year.toIntOrNull()?.let { !com.kstudio.agenda.model.HolidayTable.hasOfficialYear(it) } == true) Text(t.officialCalendarPending, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(source, { source = it }, label = { Text(t.holidayUrl) }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = !fetching && year.toIntOrNull() in 2000..2100,
                onClick = { vm.fetchOfficialHolidays(year.toInt(), source) }) { Text(if (fetching) t.holidayBusy else t.holidayFetch) }
            if (holidayStatus.isNotBlank()) Text(holidayStatus, style = MaterialTheme.typography.bodySmall)
        }
        CollapsibleSectionCard(t.myEvents, null, expEvents, { expEvents = !expEvents }, trailing = agenda.count { !it.isPlan }.toString()) {
            val events = agenda.filter { !it.isPlan }.sortedBy { it.dateEpochDay }
            if (events.isEmpty()) Text(t.widgetEmpty)
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                events.forEach { event -> AgendaCard(event, dateLabel = event.date.toString(), onClick = { editing = event }) }
            }
        }
        CollapsibleSectionCard(t.locationsTitle, t.locationsHint, expLocations, { expLocations = !expLocations }) {
            OutlinedTextField(locationText, { locationText = it }, minLines = 3, maxLines = 12, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { vm.saveSchoolLocations(locationText.lines()) }) { Text(t.save) }
        }
    }
    editing?.let { event ->
        AgendaEditorDialog(event, event.date, { editing = null }, { vm.saveAgendaEvent(it); editing = null }, { vm.deleteAgendaEvent(it); editing = null })
    }
}
