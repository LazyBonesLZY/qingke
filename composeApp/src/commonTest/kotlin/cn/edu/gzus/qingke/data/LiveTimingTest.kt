package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

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
        // 后台只在阶段边界唤醒；分钟变化仍由 liveTiming 的前台刷新延时驱动。
        assertEquals(notice.startMillis, snapshot.nextLiveWake(LocalDateTime(day, LocalTime(9, 55)), reminderAt))
        assertEquals(60_000L, liveTiming(reminderAt, notice.startMillis, notice.endMillis)!!.refreshDelayMillis)
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
            assertEquals(timing.deadlineMillis, wake, "wake $wake at $nowMs")
            nowMs += 1_000L
        }
        assertNull(snapshot.liveNotice(dateTimeAt(secondEnd), secondEnd))
    }

    @Test
    fun consecutiveCoursesSwitchAtExactBoundaryAndAfterSuspension() {
        val base = snapshot()
        // 同名连堂也要靠时间窗口区分，不能只靠课名识别。
        val snapshot = base.copy(
            settings = base.settings.copy(periodTimeOverrides = mapOf(
                "1-2" to "10:00-10:40", "3-4" to "10:40-11:20", "5-6" to "11:20-12:00",
            )),
            slots = listOf(slot("1-2"), slot("3-4"), slot("5-6")).map { it.copy(courseName = "连续课程") },
        )
        val boundary = combineMillis(day, "10:40")
        val before = snapshot.liveNotice(dateTimeAt(boundary - 1), boundary - 1)!!
        val after = snapshot.liveNotice(dateTimeAt(boundary), boundary)!!
        assertEquals("1-2", before.slot.period)
        assertEquals("3-4", after.slot.period)
        assertTrue(after.inClass)
        assertEquals(40, after.etaMinutes)
        assertEquals(boundary, snapshot.nextLiveWake(dateTimeAt(boundary - 1), boundary - 1))
        assertEquals(after.endMillis, snapshot.nextLiveWake(dateTimeAt(boundary), boundary))
        val resumed = combineMillis(day, "11:39")
        assertEquals("5-6", snapshot.liveNotice(dateTimeAt(resumed), resumed)!!.slot.period)
        assertEquals(21, snapshot.liveNotice(dateTimeAt(resumed), resumed)!!.etaMinutes)
        val nextDay = combineMillis(day.plus(DatePeriod(days = 1)), "00:00")
        assertNull(snapshot.liveNotice(dateTimeAt(nextDay), nextDay))
    }

    @Test
    fun dismissCallbacksKeepOldStageIdentityDuringHandover() {
        val start = combineMillis(day, "10:00")
        val end = combineMillis(day, "10:40")
        val nextEnd = combineMillis(day, "11:20")
        val first = "同名课:$start"
        val next = "同名课:$end"
        // 预告→上课和前一节→下一节都要创建不同 PendingIntent（extras 本身不参与判等）。
        assertNotEquals(liveDismissIdentity(first, start), liveDismissIdentity(first, end))
        assertNotEquals(liveDismissIdentity(first, end), liveDismissIdentity(next, nextEnd))
        assertEquals(liveDismissIdentity(first, end), liveDismissIdentity(first, end))
        assertFalse(isLiveStageTimeout(end - 2_000L, end))
        assertTrue(isLiveStageTimeout(end, end))
        assertTrue(isLiveStageTimeout(end + 30_000L, end))
        assertFalse(isLiveStageTimeout(end, nextEnd))
        assertFalse(isLiveStageTimeout(end, 0L))
    }

    @Test
    fun publishingAcrossBoundaryRefreshesImmediatelyInsteadOfWaitingForNextCourseMinute() {
        val boundary = 100_003L
        val frame = liveTiming(boundary - 1L, boundary - 80_000L, boundary)!!
        val refreshAt = boundary - 1L + frame.refreshDelayMillis
        assertEquals(boundary, refreshAt)
        assertEquals(1L, liveRefreshDelayMillis(boundary, refreshAt))
        assertEquals(1L, liveRefreshDelayMillis(boundary + 10L, refreshAt))
        assertEquals(1L, liveRefreshDelayMillis(boundary + 120_000L, refreshAt))
        assertEquals(10_000L, liveRefreshDelayMillis(boundary - 10_000L, refreshAt))
        assertEquals(60_000L, liveRefreshDelayMillis(boundary - 120_000L, refreshAt))
        assertEquals(60_000L, liveRefreshDelayMillis(boundary, null))
    }

    @Test
    fun todayRefreshIsBoundedByCountdownProgressAndClockChanges() {
        // 非整分钟的窗口同样在分钟文案变化和上下课的准确毫秒刷新。
        val start = 100_003L
        val end = 300_003L
        val beforeStart = liveTiming(start - 1L, start, end)!!
        assertEquals(1L, todayRefreshDelayMillis(start - 1L, beforeStart))
        assertEquals(1L, todayRefreshDelayMillis(end - 1L, liveTiming(end - 1L, start, end)))
        assertEquals(9_997L, todayRefreshDelayMillis(start, liveTiming(start, start, end)))
        assertEquals(42_445L, todayRefreshDelayMillis(17_555L, null))
        assertEquals(60_000L, todayRefreshDelayMillis(60_000L, null))
        assertEquals(1L, todayRefreshDelayMillis(59_999L, null))
        var now = start - 1_500L
        while (now < end) {
            val timing = liveTiming(now, start, end)!!
            val delay = todayRefreshDelayMillis(now, timing)
            assertTrue(delay in 1..60_000L)
            assertTrue(now + delay <= timing.deadlineMillis)
            now += delay
        }
        assertEquals(end, now)
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
