package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class WeekendVisibilityTest {
    @Test fun hiddenByDefaultIncludingLegacySettings() {
        assertFalse(AppSettings().showWeekend)
        assertEquals((1..5).toList(), visibleTimetableDays(AppSettings().showWeekend).toList())
        // Old snapshots don't have showWeekend; deserializing must default to false.
        assertFalse(Json.decodeFromString<AppSettings>("{}").showWeekend)
    }

    @Test fun manuallyShowingWeekendsIsPersistent() {
        val changed = AppSettings().copy(showWeekend = true)
        assertTrue(changed.showWeekend)
        assertEquals((1..7).toList(), visibleTimetableDays(changed.showWeekend).toList())
        val serialized = Json.encodeToString(AppSettings.serializer(), changed)
        assertTrue(Json.decodeFromString<AppSettings>(serialized).showWeekend)
    }
}
