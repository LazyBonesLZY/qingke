package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class LiveTimingTest {
    @Test
    fun roundsUpAndNeverShowsZeroBeforeTheDeadline() {
        val start = 120_000L
        val end = 240_000L
        assertEquals(2L, liveTiming(start - 60_001L, start, end)!!.remainingMinutes)
        assertEquals(1L, liveTiming(start - 60_000L, start, end)!!.remainingMinutes)
        assertEquals(1L, liveTiming(start - 1L, start, end)!!.remainingMinutes)
        assertEquals(1L, liveTiming(end - 1L, start, end)!!.remainingMinutes)
        assertNull(liveTiming(end, start, end))
        assertNull(liveTiming(end + 1L, start, end))
    }

    @Test
    fun switchesToEndCountdownAtTheExactStart() {
        val before = liveTiming(99_999L, 100_000L, 220_000L)!!
        val started = liveTiming(100_000L, 100_000L, 220_000L)!!
        assertFalse(before.inClass)
        assertEquals("距上课1分", before.countdownText)
        assertEquals(100_000L, before.deadlineMillis)
        assertTrue(started.inClass)
        assertEquals("距下课2分", started.countdownText)
        assertEquals(220_000L, started.deadlineMillis)
        assertEquals(0, started.progressPercent)
    }

    @Test
    fun refreshesAtRemainingMinuteBoundaryInsteadOfClockMinute() {
        val timing = liveTiming(17_555L, 100_003L, 300_003L)!!
        assertEquals(22_448L, timing.refreshDelayMillis)
        val refreshed = liveTiming(17_555L + timing.refreshDelayMillis, 100_003L, 300_003L)!!
        assertEquals(1L, refreshed.remainingMinutes)
        assertEquals(60_000L, refreshed.refreshDelayMillis)
        assertEquals(1L, liveTiming(100_002L, 100_003L, 300_003L)!!.refreshDelayMillis)
    }

    @Test
    fun rejectsInvalidCourseWindows() {
        assertNull(liveTiming(0L, 10L, 10L))
        assertNull(liveTiming(0L, 20L, 10L))
    }

    @Test
    fun keepsProgressWithinBounds() {
        assertEquals(0, liveTiming(10L, 100L, 200L)!!.progressPercent)
        assertEquals(50, liveTiming(150L, 100L, 200L)!!.progressPercent)
        assertEquals(99, liveTiming(199L, 100L, 200L)!!.progressPercent)
    }

    @Test
    fun schedulesUpcomingReminderWhenNoNotificationIsActive() {
        val snapshot = snapshot()
        val now = LocalDateTime(day, LocalTime(8, 0))
        val nowMs = combineMillis(day, "08:00")
        assertNull(snapshot.liveNotice(now, nowMs))
        assertEquals(combineMillis(day, "09:55"), snapshot.nextLiveWake(now, nowMs))
    }

    @Test
    fun reminderWindowUsesExactMillisecondsAndRoundedUpMinutes() {
        val snapshot = snapshot()
        val reminderAt = combineMillis(day, "09:55")
        assertNull(snapshot.liveNotice(LocalDateTime(day, LocalTime(9, 54, 59)), reminderAt - 1L))
        val notice = snapshot.liveNotice(LocalDateTime(day, LocalTime(9, 55)), reminderAt)!!
        assertEquals(5, notice.etaMinutes)
        assertFalse(notice.inClass)
        assertEquals(reminderAt + 60_000L, snapshot.nextLiveWake(LocalDateTime(day, LocalTime(9, 55)), reminderAt))
    }

    @Test
    fun transitionsToNextCourseAndCancelsWhenDisabled() {
        val snapshot = snapshot()
        val start = combineMillis(day, "10:00")
        val end = combineMillis(day, "10:40")
        val started = snapshot.liveNotice(LocalDateTime(day, LocalTime(10, 0)), start)!!
        assertTrue(started.inClass)
        assertEquals(40, started.etaMinutes)
        assertNull(snapshot.liveNotice(LocalDateTime(day, LocalTime(10, 40)), end))
        assertEquals(combineMillis(day, "10:55"), snapshot.nextLiveWake(LocalDateTime(day, LocalTime(10, 40)), end))
        val disabled = snapshot.copy(settings = snapshot.settings.copy(remindBeforeClass = false))
        assertNull(disabled.liveNotice(LocalDateTime(day, LocalTime(10, 0)), start))
        assertNull(disabled.nextLiveWake(LocalDateTime(day, LocalTime(10, 0)), start))
    }

    @Test
    fun preservesExplicitCountdownPreferenceAfterMigration() {
        val saved = snapshot().copy(settings = snapshot().settings.copy(liveCountdownEnabled = false, liveCountdownMigrated = true))
        assertFalse(saved.withLiveCountdownMigration().settings.liveCountdownEnabled)
        assertTrue(saved.copy(settings = saved.settings.copy(liveCountdownMigrated = false))
            .withLiveCountdownMigration().settings.liveCountdownEnabled)
    }

    @Test
    fun backToBackCoursesHandOverWithoutNegativeCountdown() {
        // 连堂：课间 10 分钟比提醒提前量还短，下课那一刻要无缝换成下一节的「距上课」。
        val snapshot = snapshot().copy(
            settings = snapshot().settings.copy(
                remindLeadMinutes = 15,
                periodTimeOverrides = mapOf("1-2" to "10:00-10:40", "3-4" to "10:50-11:30"),
            ),
        )
        val firstEnd = combineMillis(day, "10:40")
        val secondStart = combineMillis(day, "10:50")
        val secondEnd = combineMillis(day, "11:30")

        val last = snapshot.liveNotice(dateTimeAt(firstEnd - 1L), firstEnd - 1L)!!
        assertEquals("Course 1-2", last.title)
        assertTrue(last.inClass)
        assertEquals(firstEnd, snapshot.nextLiveWake(dateTimeAt(firstEnd - 30_000L), firstEnd - 30_000L))

        val handover = snapshot.liveNotice(dateTimeAt(firstEnd), firstEnd)!!
        assertEquals("Course 3-4", handover.title)
        assertFalse(handover.inClass)
        assertEquals(10, handover.etaMinutes)
        assertEquals(secondStart, handover.startMillis)

        // 逐秒走完两节课：任何时刻挂出的通知，倒数的截止点都在未来，分钟数不会是 0 或负数。
        var nowMs = combineMillis(day, "10:00")
        while (nowMs < secondEnd) {
            val notice = snapshot.liveNotice(dateTimeAt(nowMs), nowMs)!!
            val timing = liveTiming(nowMs, notice.startMillis, notice.endMillis)!!
            assertTrue(timing.deadlineMillis > nowMs, "deadline passed at $nowMs")
            assertTrue(notice.etaMinutes >= 1, "eta ${notice.etaMinutes} at $nowMs")
            val wake = snapshot.nextLiveWake(dateTimeAt(nowMs), nowMs)!!
            assertTrue(wake in (nowMs + 1)..timing.deadlineMillis, "wake $wake at $nowMs")
            nowMs += 1_000L
        }
        assertNull(snapshot.liveNotice(dateTimeAt(secondEnd), secondEnd))
    }

    @Test
    fun picksTheEarliestUnfinishedLessonEvenWithUnnumberedBlocks() {
        val blocks = listOf(
            PeriodBlock(index = 1, label = "1-2", start = "08:00", end = "09:40", band = ""),
            PeriodBlock(index = 2, label = "中午", start = "12:00", end = "13:00", band = ""),
            PeriodBlock(index = 3, label = "5-6", start = "14:00", end = "15:40", band = ""),
        )
        val day = listOf(slot("5-6"), slot("中午"))
        val atEleven = nextLiveLesson(day, 1, 1, LocalTime(11, 0), blocks)!!
        assertEquals("中午", atEleven.slot.period)
        val atNoon = nextLiveLesson(day, 1, 1, LocalTime(12, 30), blocks)!!
        assertEquals("中午", atNoon.slot.period)
        assertTrue(atNoon.inClass)
    }

    private val day = LocalDate(2026, 10, 5)

    private fun snapshot(): AppSnapshot = AppSnapshot(
        settings = AppSettings(
            schoolId = School.Gzus.id,
            currentWeek = 1,
            remindLeadMinutes = 5,
            periodTimeOverrides = mapOf("1-2" to "10:00-10:40", "3-4" to "11:00-11:40"),
        ),
        slots = listOf(slot("1-2"), slot("3-4")),
    )

    private fun slot(period: String): LessonSlot = LessonSlot(
        courseId = period,
        courseName = "Course $period",
        credit = "2",
        teacher = "Teacher",
        room = "Room 101",
        weekday = 1,
        weekdayName = "Monday",
        period = period,
        periodLabel = period,
        weeks = "1-16",
    )
}
