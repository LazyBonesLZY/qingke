package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class BeijingTimeZoneTest {
    @Test
    fun scheduleClockIsAlwaysBeijingTime() {
        val day = LocalDate(2026, 11, 16)
        val beijingMorning = LocalDateTime(day, LocalTime(9, 20))
        val expectedUtcMillis = LocalDateTime(day, LocalTime(1, 20))
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()

        assertEquals("Asia/Shanghai", APP_TIME_ZONE.id)
        assertEquals(expectedUtcMillis, combineMillis(day, "09:20"))
        assertEquals(beijingMorning, dateTimeAt(expectedUtcMillis))
    }

    @Test
    fun coursePickInputAndOutputUseBeijingTime() {
        val day = LocalDate(2026, 11, 16)
        val expectedUtcMillis = LocalDateTime(day, LocalTime(4, 30))
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()

        val parsed = parseCoursePickWhen("2026-11-16", "12:30")
        assertEquals(expectedUtcMillis, parsed)
        assertEquals("2026-11-16 12:30:00", formatCoursePickWhen(parsed))
    }
}
