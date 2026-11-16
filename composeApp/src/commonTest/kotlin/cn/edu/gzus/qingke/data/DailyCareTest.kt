package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
