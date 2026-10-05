package com.kstudio.agenda.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.ui.theme.LocalGlassEnabled

/**
 * 卡片底色（白天 / 夜间 / 液态玻璃统一口径）：
 * - **白天：纯白** —— 比页面底色明显更白，卡片看起来更干净；
 * - 夜间：用 surface（比深色背景略亮）；
 * - 液态玻璃：用带 alpha 的 surface（叠加在渐变背景上形成毛玻璃）。
 *
 * 注意：不要再靠 `Surface(tonalElevation = 2.dp)` 来做白底——M3 会把主色按比例混进表面，
 * 白天看起来是「灰蓝白」，反而不如页面底色干净（见 [cardTonalElevation]）。
 */
@Composable
fun cardBaseColor(): Color =
    if (isSystemInDarkTheme() || LocalGlassEnabled.current) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.White
    }

/** 配合 [cardBaseColor] 的色调叠加：白天与液态玻璃为 0（避免把白染灰），夜间 2dp */
@Composable
fun cardTonalElevation(): Dp =
    if (isSystemInDarkTheme() && !LocalGlassEnabled.current) 2.dp else 0.dp

/** 配合 [cardBaseColor] 的阴影：白天给极淡投影（不带主色染色），夜间/玻璃不需要 */
@Composable
fun cardShadowElevation(): Dp =
    if (isSystemInDarkTheme() || LocalGlassEnabled.current) 0.dp else 1.dp

/**
 * 卡片描边色（**只在液态玻璃下使用**）：玻璃卡片靠这圈微光边才有“厚度”。
 * 默认（简约）主题不画描边 —— 那里靠白底 + 极淡投影分层（与课程卡片一致）。
 */
@Composable
fun cardBorderColor(): Color =
    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

/**
 * 卡片统一修饰符：
 * - 液态玻璃：画 1dp 玻璃微光描边（这就是玻璃的“厚重感”来源）；
 * - 默认（简约）：什么都不画，靠白底与页面浅灰底对比 + Surface 极淡投影。
 */
@Composable
fun Modifier.cardGlassBorder(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
): Modifier =
    if (LocalGlassEnabled.current) this.border(1.dp, cardBorderColor(), shape) else this

/** 小标签（本 / 研 / 周次等） */
@Composable
fun TagChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 可选择的小圆角标签（用于提前分钟数等选项） */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlassEnabled.current
    // 未选中态在玻璃卡片上很容易“融”进背景：玻璃模式下改用更高一层的表面色 + 细描边
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        glass -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (!selected && glass) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** 分组卡片 */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().cardGlassBorder(),
        // 默认主题：白底、无描边、18dp 圆角，靠白底 vs 页面浅灰底 + 极淡投影分层（与课程卡片一致）
        shape = RoundedCornerShape(18.dp),
        color = cardBaseColor(),
        tonalElevation = cardTonalElevation(),
        shadowElevation = cardShadowElevation(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/**
 * 分组卡片（可折叠）：标题行本身可点击展开/收起，右侧箭头提示「可打开」；
 * [trailing] 用于折叠状态下在右侧显示摘要（如学号、版本号）。
 */
@Composable
fun CollapsibleSectionCard(
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().cardGlassBorder(),
        // 默认主题：白底、无描边、18dp 圆角（与课程卡片一致）；玻璃：多一圈微光边
        shape = RoundedCornerShape(18.dp),
        color = cardBaseColor(),
        tonalElevation = cardTonalElevation(),
        shadowElevation = cardShadowElevation(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // 折叠时收起说明文字，行更紧凑（摘要显示在右侧）
                    if (expanded && !subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!expanded && !trailing.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                ExpandArrow(expanded)
            }
            // 展开/折叠动画：高度伸缩 + 淡入淡出，避免内容突然出现或消失
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(140)),
            ) {
                Column(Modifier.padding(top = 14.dp)) {
                    content()
                }
            }
        }
    }
}

/** 右侧「可打开」箭头：展开时向下、折叠时向右（>），带过渡动画 */
@Composable
fun ExpandArrow(expanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "expandArrow",
    )
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.rotate(rotation),
    )
}

/** 行尾「进入/打开」箭头（>），用于跳转页面或弹出选择器的行 */
@Composable
fun TrailingChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** 提示横幅 */
@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 空状态占位 */
@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 键值信息行 */
@Composable
fun InfoLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
