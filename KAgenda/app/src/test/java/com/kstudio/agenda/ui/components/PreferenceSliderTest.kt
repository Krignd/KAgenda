package com.kstudio.agenda.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceSliderTest {
    @Test fun standardSpeedIsAtOneFifthOfTheExpandedTrack() {
        assertEquals(0.2f, preferenceFraction(100f, 25f..400f), 0.0001f)
        assertEquals(0f, preferenceFraction(25f, 25f..400f), 0f)
        assertEquals(1f, preferenceFraction(400f, 25f..400f), 0f)
    }
    @Test fun colorAndSpeedMarkersClampToTheVisibleTrack() {
        assertEquals(0.5f, preferenceFraction(100f, 0f..200f), 0f)
        assertEquals(0f, preferenceFraction(-1f, 0f..200f), 0f)
        assertEquals(1f, preferenceFraction(999f, 25f..400f), 0f)
    }
}
