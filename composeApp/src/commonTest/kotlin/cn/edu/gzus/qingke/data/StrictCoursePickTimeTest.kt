package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrictCoursePickTimeTest {
    @Test fun badTimeIsRejectedNotClamped() {
        val date = "2026-12-10"
        listOf("25:80", "23:60", "12:30:60", "12:30:x", "12::30", "12:30:20:15", "-1:30", "12:30 extra", "").forEach {
            assertEquals(0L, parseCoursePickWhen(date, it), "unexpectedly accepted: $it")
        }
        assertEquals(0L, parseCoursePickWhen("bad-date", "12:30"))
    }

    @Test fun correctTimesAreAccepted() {
        assertTrue(parseCoursePickWhen("2026-12-10", "12:30") > 0L)
        assertTrue(parseCoursePickWhen("2026-12-10", "12:30:00") > 0L)
        assertEquals(parseCoursePickWhen("2026-12-10", "12:30"), parseCoursePickWhen("2026-12-10", "12：30：00"))
    }
}
