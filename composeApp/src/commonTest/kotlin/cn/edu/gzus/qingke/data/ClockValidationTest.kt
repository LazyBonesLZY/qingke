package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ClockValidationTest {
    @Test fun rejectsBrokenSchoolTimesWithoutThrowing() {
        val day = LocalDate(2026, 11, 16)
        assertEquals(0L, combineMillis(day, "25:61"))
        assertEquals(0L, combineMillis(day, "not-a-time"))
        assertEquals(combineMillis(day, "09:20"), combineMillis(day, "09:20:00"))
        assertNull(minutesUntil(LocalTime(9, 0), "-1:90"))
        assertEquals("", periodClockRange("4-3"))
    }

    @Test fun partialClockCannotPretendFullRangeIsKnown() {
        val blocks = listOf(
            PeriodBlock(1, "1-2", "09:00", "10:20", ""),
            PeriodBlock(2, "3-4", "", "", ""),
        )
        val school = AppSettings().resolved().copy(periodBlocks = blocks, hasPeriodClock = true)
        assertFalse(school.clockKnown("1-4"))
        assertEquals("", school.clockRangeOf("1-4"))
        assertEquals("", clockRangeIn("1-4", blocks))
    }

    @Test fun lastSectionEndsExactlyAtBlockEnd() {
        val school = AppSettings().resolved().copy(
            periodBlocks = listOf(PeriodBlock(1, "1-2", "09:00", "10:21", "")),
        )
        assertEquals("10:21", school.sectionTime(2)?.second)
    }
}
