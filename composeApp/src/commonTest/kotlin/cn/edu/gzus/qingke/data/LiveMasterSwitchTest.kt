package cn.edu.gzus.qingke.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveMasterSwitchTest {
    @Test fun disablingStopsNoticesAndFutureWakesAndEnablingRestoresThem() {
        val day = LocalDate(2026, 11, 16)
        val now = combineMillis(day, "09:10")
        val snapshot = AppSnapshot(
            settings = AppSettings(termStart = day.toString(), remindLeadMinutes = 20,
                periodTimeOverrides = mapOf("1-2" to "09:20-10:00")),
            slots = listOf(LessonSlot("course", "课程", "2", "老师", "A101", weekday = 1,
                weekdayName = "周一", period = "1-2", periodLabel = "1-2", weeks = "1-16")),
        )
        assertNotNull(snapshot.liveNotice(dateTimeAt(now), now))
        assertNotNull(snapshot.nextLiveWake(dateTimeAt(now), now))
        val disabled = snapshot.copy(settings = snapshot.settings.copy(liveCountdownEnabled = false))
        assertNull(disabled.liveNotice(dateTimeAt(now), now))
        assertNull(disabled.nextLiveWake(dateTimeAt(now), now))
        val earlier = combineMillis(day, "08:00")
        assertNull(disabled.nextLiveWake(dateTimeAt(earlier), earlier))
        val enabled = disabled.copy(settings = disabled.settings.copy(liveCountdownEnabled = true))
        assertNotNull(enabled.liveNotice(dateTimeAt(now), now))
    }

    @Test fun durableOffStopsAStaleEnabledFrameAndSurvivesSnapshotReload() = withSavedState {
        writeLiveMasterEnabled(false)
        committedSnapshot = AppSnapshot()
        assertFalse(liveMasterEnabled())
        assertFalse(AppSnapshot().withLiveMasterPreference().settings.liveCountdownEnabled)
        // Even the legacy migration that enables countdown must not undo an explicit durable off.
        assertFalse(AppSnapshot().withLiveCountdownMigration().withLiveMasterPreference().settings.liveCountdownEnabled)
    }

    @Test fun eitherDisabledSourcePreventsDeliveryUntilBothHaveEnabled() = withSavedState {
        writeLiveMasterEnabled(true)
        committedSnapshot = AppSnapshot(settings = AppSettings(liveCountdownEnabled = false))
        assertFalse(liveMasterEnabled())
        committedSnapshot = AppSnapshot()
        assertTrue(liveMasterEnabled())
        writeLiveMasterEnabled(false)
        assertFalse(liveMasterEnabled())
    }

    @Test fun coldReaderAppliesDurableChoiceEvenWithAnUnchangedCachedSnapshot() = withSavedState {
        val previous = readStore(STORE)
        try {
            val stale = AppSnapshot(settings = AppSettings(liveCountdownEnabled = false, liveCountdownMigrated = true))
            writeStore(STORE, Json.encodeToString(AppSnapshot.serializer(), stale))
            committedSnapshot = null
            writeLiveMasterEnabled(false)
            assertFalse(readSnapshotStore()!!.settings.liveCountdownEnabled)
            writeLiveMasterEnabled(true)
            assertTrue(readSnapshotStore()!!.settings.liveCountdownEnabled)
            assertTrue(liveMasterEnabled())
            writeLiveMasterEnabled(false)
            assertFalse(readSnapshotStore()!!.settings.liveCountdownEnabled)
            assertFalse(liveMasterEnabled())
        } finally { writeStore(STORE, previous.orEmpty()) }
    }

    private fun withSavedState(block: () -> Unit) {
        val previous = readStore(LIVE_MASTER_FILE)
        val snapshot = committedSnapshot
        try { block() } finally {
            writeStore(LIVE_MASTER_FILE, previous.orEmpty())
            committedSnapshot = snapshot
        }
    }
}
