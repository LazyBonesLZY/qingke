package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class AsyncRequestsTest {
    @Test
    fun cancellationNeverBecomesAnEmptyResult() {
        assertFailsWith<CancellationException> { requestResult<Unit> { throw CancellationException("Cancelled") } }
        assertTrue(requestResult<Unit> { error("Server failed") }.isFailure)
    }

    @Test
    fun onlyTheNewestRequestPublishes() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val requests = LatestRequests(scope)
        val oldResponse = CompletableDeferred<String>()
        val newResponse = CompletableDeferred<String>()
        var shown = ""
        requests.launch("search") { activeRequest { oldResponse.await() }.onSuccess { shown = it } }
        yield()
        requests.launch("search") { activeRequest { newResponse.await() }.onSuccess { shown = it } }
        yield()
        newResponse.complete("new")
        yield()
        oldResponse.complete("old")
        yield()
        assertEquals("new", shown)
        requests.cancelAll()
        scope.cancel()
    }

    @Test
    fun clearCancelsPendingResultsAcrossAllKeys() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val requests = LatestRequests(scope)
        val response = CompletableDeferred<Unit>()
        var published = false
        requests.launch("room") { activeRequest { response.await() }.onSuccess { published = true } }
        requests.launch("class") { activeRequest { response.await() }.onSuccess { published = true } }
        yield()
        requests.cancelAll()
        response.complete(Unit)
        yield()
        assertFalse(published)
        scope.cancel()
    }

    @Test
    fun cancelledScopeCannotPublishEvenWhenClientSwallowsCancellation() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var published = false
        val requests = LatestRequests(scope)
        val ready = CompletableDeferred<Unit>()
        requests.launch("search") {
            activeRequest {
                ready.complete(Unit)
                try { CompletableDeferred<Unit>().await() } catch (_: CancellationException) { }
                "stale"
            }.onSuccess { published = true }
        }
        ready.await()
        requests.cancelAll()
        yield()
        assertFalse(published)
        scope.cancel()
    }

    @Test
    fun sessionStampSeparatesAccountsChannelsAndRelogins() {
        val original = AppSnapshot(session = SessionState(loggedIn = true, studentId = "A"))
        assertNotEquals(original.sessionStamp(1), original.sessionStamp(2))
        assertNotEquals(original.sessionStamp(1), original.copy(session = original.session.copy(studentId = "B")).sessionStamp(1))
        assertNotEquals(original.sessionStamp(1), original.copy(settings = original.settings.copy(gzusLoginChannel = GZUS_LOGIN_JWXT)).sessionStamp(1))
        assertEquals(original.sessionStamp(1), original.copy(settings = original.settings.copy(blurEnabled = false)).sessionStamp(1))
    }

    @Test
    fun invalidPriceNeverReachesBalanceFormatting() {
        val settings = AppSettings(utilityUseCustomPrice = true, utilityWaterPrice = Double.POSITIVE_INFINITY, utilityElectricPrice = Double.NaN)
        assertEquals(JIANGMEN_WATER_PRICE, settings.resolvedWaterPrice(UtilityBind()))
        assertEquals(JIANGMEN_ELECTRIC_PRICE, settings.resolvedElectricPrice(UtilityBind()))
    }

    @Test
    fun interruptedQueueRestoresWithoutAutomaticResubmission() {
        val snapshot = AppSnapshot(settings = AppSettings(coursePickQueue = listOf(
            CoursePickTask(id = "running", status = "running"),
            CoursePickTask(id = "waiting", status = "waiting"),
            CoursePickTask(id = "done", status = "ok"),
        )))
        val restored = snapshot.withInterruptedCoursePicks()
        assertEquals(listOf("fail", "waiting", "ok"), restored.settings.coursePickQueue.map { it.status })
        assertEquals(restored, restored.withInterruptedCoursePicks())
    }

    @Test
    fun zeroLengthAndReversedPeriodOverridesKeepOriginalClock() {
        val school = AppSettings(schoolId = School.Gzus.id).resolved()
        assertEquals(school.periodBlocks, school.withPeriodTimeOverrides(mapOf("1-2" to "10:00-10:00")).periodBlocks)
        assertEquals(school.periodBlocks, school.withPeriodTimeOverrides(mapOf("1-2" to "10:00-09:00")).periodBlocks)
    }
}
