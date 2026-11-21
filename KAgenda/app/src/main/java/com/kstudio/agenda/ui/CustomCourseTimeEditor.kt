package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.i18n.CourseTimeText
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.ui.components.ChoiceChip
import com.kstudio.agenda.ui.components.AppOutlinedButton

@Composable
internal fun CustomCourseTimeEditor(
    custom: Boolean, start: String, end: String,
    onMode: (Boolean) -> Unit, onStart: (String) -> Unit, onEnd: (String) -> Unit,
) {
    var picker by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(CourseTimeText.periods, !custom, { onMode(false) })
            ChoiceChip(CourseTimeText.custom, custom, { onMode(true) })
        }
        if (custom) {
            Text(CourseTimeText.hint, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppOutlinedButton(onClick = { picker = "start" }, modifier = Modifier.weight(1f)) {
                    Text("${CourseTimeText.start} $start")
                }
                AppOutlinedButton(onClick = { picker = "end" }, modifier = Modifier.weight(1f)) {
                    Text("${CourseTimeText.end} $end")
                }
            }
            if (Course.validCustomTimes(start, end) == null)
                Text(CourseTimeText.invalid, color = MaterialTheme.colorScheme.error)
        }
    }
    if (picker.isNotEmpty()) TimeWheelDialog(
        initial = if (picker == "start") start else end,
        onDismiss = { picker = "" },
        onConfirm = { if (picker == "start") onStart(it) else onEnd(it); picker = "" },
    )
}
