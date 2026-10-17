package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 学校页只承载各校特色功能；尚未接入功能时留空，学校选择仍在设置页。 */
@Composable
internal fun SchoolHomeScreen() {
    Box(Modifier.fillMaxSize()) {
        // TODO：后续接入学校专属功能，差异留在各校插件中，不在此处按学校硬编码。
    }
}
