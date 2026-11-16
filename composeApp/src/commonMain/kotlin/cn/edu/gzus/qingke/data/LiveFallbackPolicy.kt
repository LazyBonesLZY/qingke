package cn.edu.gzus.qingke.data

/** One stable notification id for the entire downgrade chain; never post duplicates. */
internal enum class LiveDeliveryAttempt { HYPEROS_PRIVATE, AOSP_NATIVE, STANDARD }

/** No private path is attempted without the prior ROM/permission/approval checks. */
internal fun liveFallbackChain(mode: LivePresentation, sdk: Int): List<LiveDeliveryAttempt> = when (mode) {
    LivePresentation.HYPEROS_ISLAND -> buildList {
        add(LiveDeliveryAttempt.HYPEROS_PRIVATE)
        if (sdk >= 36) add(LiveDeliveryAttempt.AOSP_NATIVE)
        add(LiveDeliveryAttempt.STANDARD)
    }
    LivePresentation.COLOROS_FLUID, LivePresentation.AOSP_PROMOTED ->
        if (sdk >= 36) listOf(LiveDeliveryAttempt.AOSP_NATIVE, LiveDeliveryAttempt.STANDARD)
        else listOf(LiveDeliveryAttempt.STANDARD)
    LivePresentation.STANDARD -> listOf(LiveDeliveryAttempt.STANDARD)
}

/** Two retry wakeups after the initial failure, always capped at the class boundary. */
internal fun livePostRetryAt(nowMillis: Long, endMillis: Long, failures: Int): Long =
    if (failures in 1..2) minOf(nowMillis + 60_000L, endMillis) else endMillis
