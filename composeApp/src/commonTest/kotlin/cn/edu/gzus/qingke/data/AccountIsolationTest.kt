package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountIsolationTest {
    private fun loggedIn(id: String): AppSnapshot = AppSnapshot(
        settings = AppSettings(yearCode = "2026", termCode = "3", schoolId = School.Gzus.id),
        session = SessionState(loggedIn = true, studentId = id),
    )

    @Test fun queueOnlyBelongsToTheSameAccountAndTerm() {
        val base = loggedIn("studentA")
        val key = base.coursePickOwnerKey()!!
        val task = CoursePickTask(id = "task1", status = "waiting", fireAt = 1L)
        val queued = base.copy(settings = base.settings.copy(coursePickOwner = key, coursePickQueue = listOf(task)))
        assertTrue(queued.canRunQueuedPicks())
        assertFalse(queued.copy(session = queued.session.copy(studentId = "studentB")).canRunQueuedPicks())
        assertFalse(queued.copy(settings = queued.settings.copy(termCode = "12")).canRunQueuedPicks())
        assertFalse(queued.copy(settings = queued.settings.copy(gzusLoginChannel = "jwxt")).canRunQueuedPicks())
        assertFalse(queued.copy(session = queued.session.copy(loggedIn = false)).canRunQueuedPicks())
        assertEquals(null, loggedIn("").coursePickOwnerKey())
    }

    @Test fun legacyUnownedQueueIsNeverAllowedToRun() {
        val base = loggedIn("studentA")
        val old = base.copy(settings = base.settings.copy(coursePickQueue = listOf(CoursePickTask(id = "legacy"))))
        assertFalse(old.canRunQueuedPicks())
        assertTrue(base.canRunQueuedPicks())
    }
}
