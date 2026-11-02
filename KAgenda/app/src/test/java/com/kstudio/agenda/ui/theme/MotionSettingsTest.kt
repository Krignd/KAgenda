package com.kstudio.agenda.ui.theme

import org.junit.Assert.*
import org.junit.Test

class MotionSettingsTest {
    @Test fun speedScalesTimeAndSpringPeriodTogether() {
        assertEquals(300, MotionSettings().duration(300))
        assertEquals(1200, MotionSettings(25).duration(300))
        assertEquals(75, MotionSettings(400).duration(300))
        assertEquals(150, MotionSettings(200).duration(300))
        assertEquals(600, MotionSettings(50).duration(300))
        assertEquals(1240f, MotionSettings(200).stiffness(310f), 0.01f)
        assertEquals(77.5f, MotionSettings(50).stiffness(310f), 0.01f)
    }
    @Test fun invalidImportedSpeedsCannotProduceZeroDurationOrUnboundedSprings() {
        assertEquals(1200, MotionSettings(Int.MIN_VALUE).duration(300))
        assertEquals(75, MotionSettings(Int.MAX_VALUE).duration(300))
        assertEquals(1, MotionSettings(200).duration(0))
    }
}
