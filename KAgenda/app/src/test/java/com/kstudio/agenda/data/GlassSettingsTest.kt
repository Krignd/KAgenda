package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test

class GlassSettingsTest {
    @Test fun freshSettingsUseSoftStaticGlass() {
        val settings = AppSettings()
        assertTrue(settings.glassUi)
        assertEquals(0, settings.glassColorDepth)
        assertFalse(settings.glassBackgroundMotion)
        assertEquals(100, settings.motionSpeedPercent)
        assertTrue(settings.holidayOverridesRaw.isEmpty())
    }

    @Test fun backupRejectsMalformedHolidayOverridesAndOutOfRangeSpeeds() {
        assertTrue(BackupSettings.validate("motion_speed_percent", 25))
        assertTrue(BackupSettings.validate("motion_speed_percent", 400))
        assertFalse(BackupSettings.validate("motion_speed_percent", 24))
        assertFalse(BackupSettings.validate("motion_speed_percent", 401))
        assertFalse(BackupSettings.validate("motion_speed_percent", "100"))
        assertTrue(BackupSettings.validate("holiday_overrides", "2026-10-19|HOLIDAY|校庆"))
        assertTrue(BackupSettings.validate("holiday_overrides", ""))
        assertFalse(BackupSettings.validate("holiday_overrides", "2026-02-30|NORMAL|"))
    }

    @Test fun missingStyleUsesGlassAndExplicitMinimalSurvivesUpgrade() {
        assertEquals(UI_STYLE_GLASS, resolvedUiStyle(null))
        assertEquals(UI_STYLE_GLASS, resolvedUiStyle(UI_STYLE_GLASS))
        assertEquals(UI_STYLE_DEFAULT, resolvedUiStyle(UI_STYLE_DEFAULT))
        assertEquals(UI_STYLE_GLASS, resolvedUiStyle("unsupported"))
    }

    @Test fun backupAcceptsAppearancePreferencesWithValidTypesAndBounds() {
        for (depth in listOf(0, 50, 100, 150, 200)) assertTrue(BackupSettings.validate("glass_color_depth", depth))
        assertFalse(BackupSettings.validate("glass_color_depth", -1))
        assertFalse(BackupSettings.validate("glass_color_depth", 201))
        assertFalse(BackupSettings.validate("glass_color_depth", "50"))
        assertTrue(BackupSettings.validate("glass_background_motion", true))
        assertTrue(BackupSettings.validate("glass_background_motion", false))
        assertFalse(BackupSettings.validate("glass_background_motion", 1))
    }
}
