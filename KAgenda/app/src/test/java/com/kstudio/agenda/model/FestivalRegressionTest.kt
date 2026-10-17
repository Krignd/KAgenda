package com.kstudio.agenda.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FestivalRegressionTest {
    @Test fun `2027 Spring Festival displays a name without inventing an official vacation`() {
        val springFestival = LocalDate.of(2027, 2, 6)
        assertEquals("春节", HolidayTable.nameOf(springFestival))
        assertFalse(HolidayTable.isHoliday(springFestival))
        assertFalse(HolidayTable.hasOfficialYear(2027))
        assertNull(HolidayTable.nameOf(LocalDate.of(2027, 2, 5)))
    }
}
