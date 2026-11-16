package cn.edu.gzus.qingke.ui.timetable

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimetablePagingTest {
    @Test
    fun keepsNearbyNavigationContinuous() {
        for (page in 0..39) {
            for (target in (page - 3).coerceAtLeast(0)..(page + 3).coerceAtMost(39)) {
                assertNull(timetablePreJumpPage(page, target), "$page -> $target")
            }
        }
    }

    @Test
    fun farNavigationOnlyAnimatesTheFinalPageInEitherDirection() {
        assertEquals(14, timetablePreJumpPage(1, 15))
        assertEquals(2, timetablePreJumpPage(15, 1))
        assertEquals(38, timetablePreJumpPage(0, 39))
        assertEquals(1, timetablePreJumpPage(39, 0))
    }

    @Test
    fun neverPreJumpsOutsideTheRangeOrPastTheDestination() {
        for (current in 0..39) {
            for (target in 0..39) {
                val preJump = timetablePreJumpPage(current, target) ?: continue
                assertTrue(preJump in minOf(current, target)..maxOf(current, target))
                assertEquals(1, abs(target - preJump))
                assertTrue(abs(target - current) > 3)
            }
        }
    }
}
