package com.kstudio.agenda.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.RepeatRules

/**
 * 重复规则选择器（计划编辑器使用）：
 * - 模式：不重复 / 按天（每 1~7 天）/ 每周（一~日多选）/ 隔周（一~日多选）/ 每月同日；
 * - [rule] 为 [RepeatRules] 的 token；[anchorDayOfWeek] 用于默认选中星期（1=周一）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatRulePicker(
    rule: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    anchorDayOfWeek: Int = 1,
) {
    val t = LocalStrings.current
    val kind = when {                                   // 0 不重复 / 1 按天 / 2 每周 / 3 隔周 / 4 每月
        rule.isBlank() -> 0
        rule == RepeatRules.MONTHLY -> 4
        rule.startsWith("daily:") -> 1
        rule.startsWith("biweekly:") -> 3
        rule.startsWith("weekly:") -> 2
        else -> 0
    }
    val dailyN = if (kind == 1) (rule.removePrefix("daily:").toIntOrNull() ?: 1).coerceIn(1, 30) else 1
    val days: Set<Int> = if (kind == 2 || kind == 3) {
        rule.substringAfter(":").split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet()
    } else emptySet()
    val anchor = anchorDayOfWeek.coerceIn(1, 7)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = t.repeatSection,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = kind == 0,
                onClick = { onChange("") },
                label = { Text(t.repeatNone) },
            )
            FilterChip(
                selected = kind == 1,
                onClick = { onChange(RepeatRules.daily(dailyN)) },
                label = { Text(t.repeatDailyMode) },
            )
            FilterChip(
                selected = kind == 2,
                onClick = { onChange(RepeatRules.weekly(days.ifEmpty { setOf(anchor) })) },
                label = { Text(t.repeatWeeklyMode) },
            )
            FilterChip(
                selected = kind == 3,
                onClick = { onChange(RepeatRules.biweekly(days.ifEmpty { setOf(anchor) })) },
                label = { Text(t.repeatBiweeklyMode) },
            )
            FilterChip(
                selected = kind == 4,
                onClick = { onChange(RepeatRules.MONTHLY) },
                label = { Text(t.repeatMonthlyMode) },
            )
        }
        if (kind == 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (n in 1..7) {
                    FilterChip(
                        selected = dailyN == n,
                        onClick = { onChange(RepeatRules.daily(n)) },
                        label = { Text(t.repeatEveryNDays(n)) },
                    )
                }
            }
        }
        if (kind == 2 || kind == 3) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (d in 1..7) {
                    FilterChip(
                        selected = d in days,
                        onClick = {
                            val next = if (d in days) days - d else days + d
                            // 允许一个都不选：由编辑器在保存时提醒（选了“每周/隔周”却无星期无法保存）
                            onChange(if (kind == 3) RepeatRules.biweekly(next) else RepeatRules.weekly(next))
                        },
                        label = { Text(t.weekdayShort(d)) },
                    )
                }
            }
            if (days.isEmpty()) {
                Text(
                    text = t.repeatNeedDayHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
