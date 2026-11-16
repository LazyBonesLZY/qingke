package cn.edu.gzus.qingke.data

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeijingHostTimeZoneTest {
    @Test fun foreignHostZonesDoNotShiftBeijingDatesWeeksOrCoursePickTimes() {
        val original = java.util.TimeZone.getDefault()
        try {
            for (zone in listOf("UTC", "Asia/Tokyo", "America/Los_Angeles")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                assertEquals(zone, TimeZone.currentSystemDefault().id)
                val midnight = Instant.parse("2026-11-15T16:05:00Z").toEpochMilliseconds()
                val beijing = dateTimeAt(midnight)
                assertEquals(LocalDateTime.parse("2026-11-16T00:05"), beijing)
                assertEquals(1, weekdayIndex(beijing.date))
                assertEquals(2, resolvedCurrentWeek(AppSettings(termStart = "2026-11-09"), beijing.date))
                val pick = Instant.parse("2026-11-16T04:30:00Z").toEpochMilliseconds()
                assertEquals(pick, parseCoursePickWhen("2026-11-16", "12:30"))
                assertEquals("2026-11-16 12:30:00", formatCoursePickWhen(pick))
                assertEquals(pick, combineMillis(LocalDate(2026, 11, 16), "12:30"))
                val before = Clock.System.now().toEpochMilliseconds()
                val now = nowDateTime()
                val after = Clock.System.now().toEpochMilliseconds()
                assertTrue(now >= dateTimeAt(before) && now < dateTimeAt(after + 1L))
            }
        } finally { java.util.TimeZone.setDefault(original) }
    }
}
