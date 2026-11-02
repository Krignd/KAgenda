package com.kstudio.agenda.ui.components

/** Fit first, but keep small screens / large fonts scrollable instead of crushing text. */
internal fun fittedPeriodHeight(availableDp: Float, periods: Int, fontScale: Float): Float =
    (availableDp / periods.coerceAtLeast(1)).coerceIn(28f * fontScale.coerceAtLeast(1f), 58f * fontScale.coerceAtLeast(1f))

internal fun fittedMinuteScale(availableDp: Float, minutes: Int, fontScale: Float): Float =
    (availableDp / minutes.coerceAtLeast(1)).coerceIn(0.30f * fontScale.coerceAtLeast(1f), 0.85f * fontScale.coerceAtLeast(1f))
