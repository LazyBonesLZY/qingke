package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveDeliveryEngineTest {
    @Test fun nullBuildAndEligibilityExceptionStillReachStandard() {
        val tried = mutableListOf<LiveDeliveryAttempt>()
        assertTrue(deliverLiveWithFallback(liveFallbackChain(LivePresentation.HYPEROS_ISLAND, 36),
            build = { if (it == LiveDeliveryAttempt.HYPEROS_PRIVATE) null else it.name },
            eligible = { mode, _ ->
                if (mode == LiveDeliveryAttempt.AOSP_NATIVE) error("ROM eligibility check failed")
                true
            },
            post = { mode, _ -> tried += mode; true },
        ))
        assertEquals(listOf(LiveDeliveryAttempt.STANDARD), tried)
    }

    @Test fun postExceptionFallsBackWithoutPostingTwiceAfterSuccess() {
        val tried = mutableListOf<LiveDeliveryAttempt>()
        assertTrue(deliverLiveWithFallback(liveFallbackChain(LivePresentation.HYPEROS_ISLAND, 36),
            build = { it.name }, eligible = { _, _ -> true },
            post = { mode, _ ->
                tried += mode
                if (mode == LiveDeliveryAttempt.HYPEROS_PRIVATE) error("Rejected vendor extras")
                true
            },
        ))
        assertEquals(listOf(LiveDeliveryAttempt.HYPEROS_PRIVATE, LiveDeliveryAttempt.AOSP_NATIVE), tried)
    }

    @Test fun privateBuildAndNativePostFailuresReachStandard() {
        val tried = mutableListOf<LiveDeliveryAttempt>()
        val attempts = liveFallbackChain(LivePresentation.HYPEROS_ISLAND, 36)
        val result = deliverLiveWithFallback(attempts,
            build = { if (it == LiveDeliveryAttempt.HYPEROS_PRIVATE) error("bad OEM payload") else it.name },
            eligible = { _, _ -> true },
            post = { mode, _ -> tried += mode; mode == LiveDeliveryAttempt.STANDARD },
        )
        assertTrue(result)
        assertEquals(listOf(LiveDeliveryAttempt.AOSP_NATIVE, LiveDeliveryAttempt.STANDARD), tried)
    }

    @Test fun deniedNativeSkipsDirectlyToStandard() {
        val tried = mutableListOf<LiveDeliveryAttempt>()
        assertTrue(deliverLiveWithFallback(liveFallbackChain(LivePresentation.COLOROS_FLUID, 36),
            build = { it.name },
            eligible = { mode, _ -> mode == LiveDeliveryAttempt.STANDARD },
            post = { mode, _ -> tried += mode; true },
        ))
        assertEquals(listOf(LiveDeliveryAttempt.STANDARD), tried)
    }

    @Test fun successShortCircuitsAndAllFailuresReportFalse() {
        val chain = liveFallbackChain(LivePresentation.HYPEROS_ISLAND, 36)
        val tried = mutableListOf<LiveDeliveryAttempt>()
        assertTrue(deliverLiveWithFallback(chain, { it.name }, { _, _ -> true }, { mode, _ ->
            tried += mode; true
        }))
        assertEquals(listOf(LiveDeliveryAttempt.HYPEROS_PRIVATE),tried)
        assertFalse(deliverLiveWithFallback(chain, { it.name }, { _, _ -> true }, { _, _ -> false }))
    }
}
