package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekSetTest {
    @Test
    fun parityAppliesPerSegment() {
        assertEquals(setOf(2, 4, 6, 8) + (9..16), parseWeekSet("2-8周(双),9-16周"))
        assertEquals(setOf(1, 3, 5, 7, 8, 10, 12, 14, 16), parseWeekSet("1-7周(单),8-16周(双)"))
    }

    @Test
    fun keepsSimpleForms() {
        assertEquals((1..16).toSet(), parseWeekSet("1-16周"))
        assertEquals(setOf(1, 3, 5, 7, 9), parseWeekSet("1-9周(单)"))
        assertEquals(setOf(3, 5, 7), parseWeekSet("3,5,7周(单)"))
        assertEquals((1..16).toSet(), parseWeekSet("1–16周"))
        assertEquals(setOf(1, 2, 5), parseWeekSet("1，2，5周"))
        assertEquals(emptySet(), parseWeekSet(""))
    }
}
