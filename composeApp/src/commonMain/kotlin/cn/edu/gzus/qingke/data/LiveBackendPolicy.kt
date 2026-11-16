package cn.edu.gzus.qingke.data

/** Presentation is only a request: SystemUI decides whether a chip/island is shown. */
internal enum class LiveRom { AOSP, COLOROS, HYPEROS }
internal enum class LivePresentation { STANDARD, AOSP_PROMOTED, COLOROS_FLUID, HYPEROS_ISLAND }

internal data class LiveSignals(
    val rom: LiveRom,
    val sdk: Int,
    val canPromote: Boolean,
    val islandProtocol: Int,
    val focusGranted: Boolean,
    /** True only after a Xiaomi-approved business ID is configured in the manifest. */
    val islandApproved: Boolean,
)

internal fun chooseLivePresentation(v: LiveSignals): LivePresentation = when {
    v.rom == LiveRom.HYPEROS && v.islandProtocol >= 3 &&
        v.focusGranted && v.islandApproved -> LivePresentation.HYPEROS_ISLAND
    v.sdk >= 36 && v.canPromote && v.rom == LiveRom.COLOROS -> LivePresentation.COLOROS_FLUID
    v.sdk >= 36 && v.canPromote -> LivePresentation.AOSP_PROMOTED
    else -> LivePresentation.STANDARD
}
