package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
            PeriodBlock(3, "5-6", "14:00", "15:20", ""),
        )
        val school = AppSettings().resolved().copy(periodBlocks = blocks, hasPeriodClock = true)
        assertFalse(school.clockKnown("1-4"))
        assertEquals("", school.periodEndOf("1-4"))
        assertEquals("", school.clockRangeOf("1-4"))
        assertEquals("", clockRangeIn("1-4", blocks))

        val day = LocalDate(2026, 11, 16)
        val slots = listOf(
            LessonSlot("c1", "连堂课", "", "", "A101", weekday = 1, weekdayName = "星期一", period = "1-4", periodLabel = "1-4节", weeks = "1-16周"),
            LessonSlot("c2", "下午课", "", "", "B202", weekday = 1, weekdayName = "星期一", period = "5-6", periodLabel = "5-6节", weeks = "1-16周"),
        )
        val next = nextLiveLesson(slots, week = 1, weekday = 1, time = LocalTime(9, 10), blocks = blocks)
        assertEquals("c2", next?.slot?.courseId)
    }

    @Test fun widgetBoundaryOnlyWakesForActualTodayClasses() {
        val monday = LocalDate(2026, 11, 16)
        val tuesday = LocalDate(2026, 11, 17)
        val slot = LessonSlot("c1", "下午课", "", "", "B202", weekday = 1, weekdayName = "星期一", period = "7-8", periodLabel = "7-8节", weeks = "1-16周")
        val snap = AppSnapshot(
            slots = listOf(slot),
            settings = AppSettings(termStart = monday.toString(), currentWeek = 1),
        )
        // 周一上午 08:00：1-6 节无课，应直接排到 7-8 节上课点（14:00 + 5s），不唤醒 09:00/10:20 等空节次。
        val mondayMorning = combineMillis(monday, "08:00")
        assertEquals(combineMillis(monday, "14:00") + 5_000L, nextWidgetBoundaryMillis(snap, mondayMorning))

        // 周二全天无课：直接排到周三 00:05 跨日刷新。
        val tuesdayMorning = combineMillis(tuesday, "08:00")
        assertEquals(combineMillis(LocalDate(2026, 11, 18), "00:05"), nextWidgetBoundaryMillis(snap, tuesdayMorning))
    }

    @Test fun lastSectionEndsExactlyAtBlockEnd() {
        val school = AppSettings().resolved().copy(
            periodBlocks = listOf(PeriodBlock(1, "1-2", "09:00", "10:21", "")),
        )
        assertEquals("10:21", school.sectionTime(2)?.second)
    }

    @Test fun widgetCellContrastAdaptsToCustomTints() {
        // 默认浅色粉彩用深字，默认深色块用浅字
        assertFalse(cellNeedsLightInk(0xFFDCE9FF.toInt(), darkBg = false))
        assertTrue(cellNeedsLightInk(0xFF2B4570.toInt(), darkBg = true))
        // 浅色主题下自定义深蓝强调色需要浅字；深色主题下自定义亮黄粉彩需要深字
        assertTrue(cellNeedsLightInk(0xFF3482FF.toInt(), darkBg = false))
        assertFalse(cellNeedsLightInk(0xFFFDE68A.toInt(), darkBg = true))
        // 半透明色块会与画布底色混合计算
        assertFalse(cellNeedsLightInk(0x443482FF, darkBg = false))
        assertTrue(cellNeedsLightInk(0x44FFF1C7, darkBg = true))
    }
}
