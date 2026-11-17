package cn.edu.gzus.qingke.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TomorrowReminderTest {
    private val day = LocalDate(2026, 11, 16)
    private fun snapshot() = AppSnapshot(
        settings = AppSettings(termStart = day.toString(), tomorrowReminderEnabled = true,
            liveCountdownEnabled = false, liveCountdownMigrated = true, dailyCareEnabled = false),
        slots = listOf(LessonSlot("tomorrow", "课程", "2", "老师", "A101", weekday = 2,
            weekdayName = "周二", period = "1-2", periodLabel = "1-2", weeks = "1-16")),
    )

    @Test fun alarmAlwaysTargetsBeijing2030AndAdvancesAfterTheBoundary() {
        val at = Instant.parse("2026-11-16T12:30:00Z").toEpochMilliseconds()
        assertEquals(at, nextTomorrowReminderAt(at - 1L))
        assertEquals(at + 86_400_000L, nextTomorrowReminderAt(at))
        assertEquals(at + 86_400_000L, nextTomorrowReminderAt(at + 1L))
        assertEquals(combineMillis(day, "20:30"), at)
    }

    @Test fun independentReminderWorksWithLiveAndCareDisabled() {
        assertFalse(AppSettings().tomorrowReminderEnabled)
        assertFalse(Json.decodeFromString<AppSettings>("{}").tomorrowReminderEnabled)
        val at = combineMillis(day, "20:30")
        assertNotNull(tomorrowNotificationHint(snapshot(), at, day.toString(), null))
        assertNull(tomorrowNotificationHint(snapshot(), at - 1, day.toString(), null))
        assertNotNull(tomorrowNotificationHint(snapshot(), combineMillis(day, "23:59"), day.toString(), null))
    }

    @Test fun duplicateDisabledMissingDatesAndEmptySchedulesStaySilent() {
        val at = combineMillis(day, "20:30")
        val snapshot = snapshot()
        assertNull(tomorrowNotificationHint(snapshot, at, day.toString(), day.toString()))
        assertNull(tomorrowNotificationHint(snapshot, at, "2026-11-15", null))
        assertNull(tomorrowNotificationHint(snapshot, at, null, null))
        assertNull(tomorrowNotificationHint(snapshot, combineMillis(LocalDate(2026, 11, 17), "00:01"), day.toString(), null))
        for (invalid in listOf(
            snapshot.copy(settings = snapshot.settings.copy(tomorrowReminderEnabled = false)),
            snapshot.copy(settings = snapshot.settings.copy(termStart = "")),
            snapshot.copy(slots = emptyList()),
            snapshot.copy(slots = snapshot.slots.map { it.copy(weekday = 3) }),
        )) assertNull(tomorrowNotificationHint(invalid, at, day.toString(), null))
    }

    @Test fun holidaysSuppressNotificationsWithoutChangingTheOriginalSchedule() {
        val at = combineMillis(day, "20:30")
        val snapshot = snapshot()
        val off = snapshot.copy(scheduleAdjust = CloudScheduleAdjust(schoolId = School.Gzus.id,
            offs = listOf(CloudOffDay("2026-11-17", "放假"))))
        assertNull(tomorrowNotificationHint(off, at, day.toString(), null))
        assertNotNull(tomorrowNotificationHint(snapshot, at, day.toString(), null))
    }

    @Test fun coldReadsHonorTheIndependentSwitchWithoutChangingLive() {
        val previousFlag = readStore(TOMORROW_ENABLED_FILE)
        val previousLiveFlag = readStore(LIVE_MASTER_FILE)
        val previousSnapshot = readStore(STORE)
        val previousMemory = committedSnapshot
        try {
            writeStore(STORE, Json.encodeToString(AppSnapshot.serializer(), snapshot()))
            writeLiveMasterEnabled(false)
            committedSnapshot = null
            writeTomorrowReminderEnabled(false)
            assertFalse(tomorrowReminderEnabled())
            assertFalse(readSnapshotStore()!!.settings.tomorrowReminderEnabled)
            writeTomorrowReminderEnabled(true)
            assertTrue(tomorrowReminderEnabled())
            assertFalse(liveMasterEnabled())
            assertTrue(readSnapshotStore()!!.settings.tomorrowReminderEnabled)
            committedSnapshot = snapshot().copy(settings = snapshot().settings.copy(tomorrowReminderEnabled = false))
            assertFalse(tomorrowReminderEnabled())
        } finally {
            writeStore(TOMORROW_ENABLED_FILE, previousFlag.orEmpty())
            writeStore(LIVE_MASTER_FILE, previousLiveFlag.orEmpty())
            writeStore(STORE, previousSnapshot.orEmpty())
            committedSnapshot = previousMemory
        }
    }
}
