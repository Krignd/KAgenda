package com.kstudio.agenda.ui.components

import com.kstudio.agenda.ui.theme.LocalMotionSettings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalDate

/** Each outgoing page retains its own date and data, including during rapid navigation. */
data class CalendarFrame<T>(val date: LocalDate, val data: T)

@Composable
fun <T> CalendarMotion(
    frame: CalendarFrame<T>,
    modifier: Modifier = Modifier,
    content: @Composable (CalendarFrame<T>) -> Unit,
) {
    val motion = LocalMotionSettings.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AnimatedContent(
        targetState = frame,
        modifier = modifier.clipToBounds(),
        contentKey = { it.date },
        transitionSpec = {
            val direction = (if (targetState.date > initialState.date) 1 else -1) * (if (rtl) -1 else 1)
            ((slideInHorizontally(tween(motion.duration(360), easing = FastOutSlowInEasing)) { direction * it / 4 } +
                fadeIn(tween(motion.duration(240), delayMillis = motion.duration(70)))) togetherWith
                (slideOutHorizontally(tween(motion.duration(260))) { -direction * it / 5 } + fadeOut(tween(motion.duration(150)))))
                .using(null)
        },
        label = "calendarDate",
    ) { content(it) }
}

/** Navigation labels change with a short vertical dissolve while the grid travels horizontally. */
@Composable
fun CalendarLabel(
    text: String,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
) {
    val motion = LocalMotionSettings.current
    AnimatedContent(text, modifier = Modifier.clipToBounds(), transitionSpec = {
        (fadeIn(tween(motion.duration(220), delayMillis = motion.duration(50))) + slideInVertically(tween(motion.duration(280))) { it / 3 }) togetherWith
            (fadeOut(tween(motion.duration(100))) + slideOutVertically(tween(motion.duration(180))) { -it / 3 })
    }, label = "calendarLabel") { label ->
        Text(label, style = style, color = color, fontWeight = fontWeight)
    }
}
