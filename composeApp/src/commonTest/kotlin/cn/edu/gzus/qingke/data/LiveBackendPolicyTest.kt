package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveBackendPolicyTest {
    private fun s(rom: LiveRom, sdk: Int, promote: Boolean = false,
                  protocol: Int = 0, focus: Boolean = false, approved: Boolean = false) =
        LiveSignals(rom, sdk, promote, protocol, focus, approved)

    @Test fun android36PromotesOnlyWhenGranted() {
        assertEquals(LivePresentation.AOSP_PROMOTED, chooseLivePresentation(s(LiveRom.AOSP, 36, true)))
        assertEquals(LivePresentation.STANDARD, chooseLivePresentation(s(LiveRom.AOSP, 36, false)))
        assertEquals(LivePresentation.STANDARD, chooseLivePresentation(s(LiveRom.AOSP, 35, true)))
    }

    @Test fun colorOsUsesStandardAndroidApiNotPrivateExtras() {
        assertEquals(LivePresentation.COLOROS_FLUID, chooseLivePresentation(s(LiveRom.COLOROS, 36, true)))
        assertEquals(LivePresentation.STANDARD, chooseLivePresentation(s(LiveRom.COLOROS, 35)))
    }

    @Test fun xiaomiNeverClaimsIslandFromRomNameAlone() {
        assertEquals(LivePresentation.STANDARD, chooseLivePresentation(s(LiveRom.HYPEROS, 35, protocol = 3)))
        assertEquals(LivePresentation.STANDARD, chooseLivePresentation(s(LiveRom.HYPEROS, 35, protocol = 3, focus = true)))
        assertEquals(LivePresentation.STANDARD, chooseLivePresentation(s(LiveRom.HYPEROS, 35, protocol = 2, focus = true, approved = true)))
        assertEquals(LivePresentation.STANDARD, chooseLivePresentation(s(LiveRom.HYPEROS, 35, protocol = 3, approved = true)))
        assertEquals(LivePresentation.HYPEROS_ISLAND, chooseLivePresentation(s(LiveRom.HYPEROS, 35, protocol = 3, focus = true, approved = true)))
    }

    @Test fun hyperOsCanStillUseAospPromotedWhenAvailable() {
        assertEquals(LivePresentation.AOSP_PROMOTED, chooseLivePresentation(s(LiveRom.HYPEROS, 36, true)))
    }
}
