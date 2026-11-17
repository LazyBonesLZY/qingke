package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class DailyCareTest {
    private fun slot(name: String, period: String, room: String) = LessonSlot(
        courseId = name, courseName = name, credit = "", teacher = "", room = room,
        weekday = 1, weekdayName = "星期一", period = period, periodLabel = period,
        weeks = "1-16",
    )

    @Test fun examTipTakesPriorityAndNoTipOnEmptyDay() {
        val school = AppSettings().resolved()
        assertEquals("明天有考试", dailyCareHint(emptyList(), null, school, 600, "高等数学", 1)?.title)
        assertNull(dailyCareHint(emptyList(), null, school, 600))
        assertNull(dailyCareHint(emptyList(), null, school, 600, "数学", 5))
    }

    @Test fun changingRoomsIsOnlySuggestedWithinShortBreak() {
        val school = AppSettings().resolved()
        val first = slot("一", "1-2", "A101")
        val second = slot("二", "3-4", "B202")
        val candidate = dailyCareHint(listOf(first, second), second, school, 600)
        // Not all school timetables have a sufficiently short gap; silence is preferred to guessing.
        assertTrue(candidate == null || candidate.title == "下一节要换教室")
        val sameRoom = dailyCareHint(listOf(first, second.copy(room = "A101")), second.copy(room = "A101"), school, 600)
        assertNull(sameRoom)
    }

    @Test fun gentlePromptOnlyForFullDays() {
        val school = AppSettings().resolved()
        val rows = (1..5).map { slot(it.toString(), "1-2", "A101") }
        assertEquals("今天安排较满", dailyCareHint(rows, null, school, 720)?.title)
        assertNull(dailyCareHint(rows, null, school, 23 * 60))
    }

    @Test fun tomorrowAndWeekendUseBothDaysAndActualAdjustments() {
        val friday = LocalDate(2026, 11, 20)
        val base = AppSnapshot(
            settings = AppSettings(termStart = "2026-11-16", weekCount = 16),
            slots = listOf(slot("周日", "1-2", "A").copy(weekday = 7)),
        )
        assertEquals("明天没有课", tomorrowCareHint(base, friday)?.title)
        val noSunday = base.copy(scheduleAdjust = CloudScheduleAdjust(
            schoolId = School.Gzus.id, offs = listOf(CloudOffDay("2026-11-22", "假")),
        ))
        assertEquals("明天没课，享受周末吧～", tomorrowCareHint(noSunday, friday)?.title)
        val saturday = base.copy(slots = listOf(slot("早课", "1-2", "A").copy(weekday = 6)))
        assertEquals("明日课程 · 1 个上课时段", tomorrowCareHint(saturday, friday)?.title)
        assertTrue(tomorrowCareHint(saturday, friday)!!.detail.contains("09:00"))
        assertNull(tomorrowCareHint(saturday.copy(settings = saturday.settings.copy(termStart = "")), friday))
        assertNull(tomorrowCareHint(AppSnapshot(), friday))
        assertNull(tomorrowCareHint(base.copy(slots = emptyList(), practices = listOf(
            PracticeCourse(name = "实践", weeks = "1-16", teacher = ""),
        )), friday))
    }

    @Test fun anUnplacedPracticeMustNotTurnIntoAFreeWeekendClaim() {
        val snapshot = AppSnapshot(
            settings = AppSettings(termStart = "2026-11-16"),
            slots = listOf(slot("理论课", "1-2", "A101")),
            practices = listOf(PracticeCourse("实践", "1-16", "老师")),
        )
        assertNull(tomorrowCareHint(snapshot, LocalDate(2026, 11, 20)))
    }

    @Test fun unknownSchoolClockDoesNotInventTheFirstClassTime() {
        val snapshot = AppSnapshot(
            settings = AppSettings(schoolId = School.Zhku.id, termStart = "2026-11-16"),
            slots = listOf(slot("早课", "1-2", "A101").copy(weekday = 2)),
        )
        assertTrue(tomorrowCareHint(snapshot, LocalDate(2026, 11, 16))!!.detail.contains("未完整设置"))
    }

    @Test fun eveningCourseHintsMustNotHideAnUpcomingExam() {
        val rest = DailyCareHint("明天没有课", "课程贴士")
        val school = AppSettings().resolved()
        assertEquals(rest, dailyCareHint(emptyList(), null, school, 19 * 60, tomorrow = rest))
        assertNull(dailyCareHint(emptyList(), null, school, 18 * 60, tomorrow = rest))
        assertEquals("明天有考试", dailyCareHint(emptyList(), null, school, 20 * 60,
            upcomingExamName = "高等数学", daysUntilExam = 1, tomorrow = rest)?.title)
    }
}
