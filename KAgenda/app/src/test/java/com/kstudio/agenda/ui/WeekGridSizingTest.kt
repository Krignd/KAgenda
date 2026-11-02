package com.kstudio.agenda.ui

import com.kstudio.agenda.ui.components.fittedMinuteScale
import com.kstudio.agenda.ui.components.fittedPeriodHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekGridSizingTest {
    @Test fun fourteenPeriodsFitAnOrdinaryViewport() {
        val row = fittedPeriodHeight(476f, 14, 1f)
        assertEquals(476f, row * 14, 0.01f)
        assertTrue(row >= 28f)
    }

    @Test fun smallScreensAndLargeFontsRemainScrollable() {
        val row = fittedPeriodHeight(260f, 14, 1.5f)
        assertTrue(row >= 42f)
        assertTrue(row * 14 > 260f)
    }

    @Test fun twentyHourTimelineFitsWithoutChangingTimeProportions() {
        val scale = fittedMinuteScale(480f, 1200, 1f)
        assertEquals(480f, scale * 1200, 0.01f)
        assertEquals(2f, (scale * 90) / (scale * 45), 0.001f)
    }

    @Test fun emptyOrUninitializedGridDoesNotProduceInvalidDimensions() {
        assertTrue(fittedPeriodHeight(0f, 0, 1f).isFinite())
        assertTrue(fittedMinuteScale(0f, 0, 1f).isFinite())
    }
}
