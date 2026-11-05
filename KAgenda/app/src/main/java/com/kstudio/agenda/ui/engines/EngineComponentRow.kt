package com.kstudio.agenda.ui.engines

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.kstudio.agenda.data.engines.EngineId
import com.kstudio.agenda.ui.components.AppOutlinedButton

@Composable
fun EngineComponentRow(engine: EngineId) {
    val context = LocalContext.current
    AppOutlinedButton(onClick = { EngineDownloadActivity.open(context, engine) }) { Text(engine.title) }
}
