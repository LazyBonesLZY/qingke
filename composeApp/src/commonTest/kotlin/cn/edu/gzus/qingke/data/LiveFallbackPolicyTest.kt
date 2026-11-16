package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveFallbackPolicyTest {
    @Test fun hyperOsPrivateTriesNativeThenStandard() {
        assertEquals(listOf(LiveDeliveryAttempt.HYPEROS_PRIVATE, LiveDeliveryAttempt.AOSP_NATIVE,
            LiveDeliveryAttempt.STANDARD), liveFallbackChain(LivePresentation.HYPEROS_ISLAND,36))
        assertEquals(listOf(LiveDeliveryAttempt.HYPEROS_PRIVATE, LiveDeliveryAttempt.STANDARD),
            liveFallbackChain(LivePresentation.HYPEROS_ISLAND,35))
        assertEquals(liveFallbackChain(LivePresentation.HYPEROS_ISLAND, 36),
            liveFallbackChain(LivePresentation.HYPEROS_FOCUS, 36))
    }
    @Test fun nativeAndColorosHaveReliableStandardFallback() {
        for (rom in listOf(LivePresentation.COLOROS_FLUID, LivePresentation.AOSP_PROMOTED)) {
            assertEquals(listOf(LiveDeliveryAttempt.AOSP_NATIVE, LiveDeliveryAttempt.STANDARD),
                liveFallbackChain(rom,36))
            assertEquals(listOf(LiveDeliveryAttempt.STANDARD),liveFallbackChain(rom,35))
        }
    }
    @Test fun ordinaryNoticeNeverEscalatesToVendor() {
        assertEquals(listOf(LiveDeliveryAttempt.STANDARD),liveFallbackChain(LivePresentation.STANDARD,36))
    }

    @Test fun failuresAllowOnlyTwoRetriesAndNeverPassClassEnd() {
        assertEquals(61_000L, livePostRetryAt(1_000L, 300_000L, 1))
        assertEquals(121_000L, livePostRetryAt(61_000L, 300_000L, 2))
        assertEquals(300_000L, livePostRetryAt(121_000L, 300_000L, 3))
        assertEquals(300_000L, livePostRetryAt(121_000L, 300_000L, 4))
        assertEquals(30_000L, livePostRetryAt(1_000L, 30_000L, 1))
    }
}
