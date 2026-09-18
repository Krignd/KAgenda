@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.i18n.LocalStrings
import kotlinx.coroutines.delay
import java.time.LocalDate

/** 日程表内的三种视图模式 */
private enum class ScheduleMode { Day, Week, Month }

/**
 * 「日程表」主页：顶部切换 日视图 / 周视图 / 月视图，共用同一套课表数据。
 * 顶部另有「课程表模式」勾选框（默认勾选，持久化）：
 * - 勾选：按节次等行展示；周视图课程块与节次列含起止时间；
 * - 未勾选：周视图按真实时间比例显示（含休息时段），日视图课程与日程按时段混排。
 */
@Composable
fun ScheduleScreen(vm: AppViewModel) {
    var modeName by rememberSaveable { mutableStateOf(ScheduleMode.Day.name) }
    val mode = runCatching { ScheduleMode.valueOf(modeName) }.getOrDefault(ScheduleMode.Day)
    val settings by vm.settings.collectAsState()
    val t = LocalStrings.current

    // 周视图滚动位置保持在页面级：切到日/月再切回时不回到顶部
    val weekGridScroll = rememberScrollState()
    val weekContentScroll = rememberScrollState()

    // 外部定位请求（通知/小组件点击）：定位后短暂闪烁反馈，然后自动清除
    val focus by vm.focusRequest.collectAsState()
    LaunchedEffect(focus?.seq) {
        if (focus != null) {
            delay(2800)
            vm.clearFocus()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部：视图切换（日 / 周 / 月）
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ScheduleMode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { modeName = m.name },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ScheduleMode.entries.size,
                    ),
                    label = {
                        Text(
                            when (m) {
                                ScheduleMode.Day -> t.viewDay
                                ScheduleMode.Week -> t.viewWeek
                                ScheduleMode.Month -> t.viewMonth
                            }
                        )
                    },
                )
            }
        }
        // 课程表模式（默认勾选）：整行可点（含文字），点按即切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = settings.timetableMode,
                    role = Role.Checkbox,
                    onValueChange = { vm.setTimetableMode(it) },
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = settings.timetableMode,
                onCheckedChange = null,
            )
            Text(
                text = t.timetableMode,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (mode) {
            ScheduleMode.Day -> DayScreen(vm, timetableMode = settings.timetableMode, flash = focus)
            ScheduleMode.Week -> WeekScreen(
                vm = vm,
                timetableMode = settings.timetableMode,
                gridScroll = weekGridScroll,
                contentScroll = weekContentScroll,
                flash = focus,
            )
            ScheduleMode.Month -> MonthScreen(
                vm = vm,
                onOpenDay = { date: LocalDate ->
                    vm.selectDate(date)
                    modeName = ScheduleMode.Day.name
                },
                flash = focus,
            )
        }
    }
}
