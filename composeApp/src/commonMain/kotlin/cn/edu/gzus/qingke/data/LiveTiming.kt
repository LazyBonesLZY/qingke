package cn.edu.gzus.qingke.data

internal data class LiveTiming(
    val inClass: Boolean,
    val deadlineMillis: Long,
    val remainingMillis: Long,
    val remainingMinutes: Long,
    val progressPercent: Int,
) {
    val countdownText: String
        get() = "距${if (inClass) "下课" else "上课"}${remainingMinutes}分"
    val statusText: String
        get() = if (inClass) "上课中" else "即将上课"

    // Refresh when the displayed rounded-up minute changes, not at wall-clock minutes.
    val refreshDelayMillis: Long
        get() = minOf(remainingMillis, (remainingMillis - 1L) % LIVE_STEP_MILLIS + 1L)
}

internal fun liveTiming(nowMillis: Long, startMillis: Long, endMillis: Long): LiveTiming? {
    if (endMillis <= startMillis || nowMillis >= endMillis) return null
    val inClass = nowMillis >= startMillis
    val deadline = if (inClass) endMillis else startMillis
    val remaining = deadline - nowMillis
    return LiveTiming(
        inClass = inClass,
        deadlineMillis = deadline,
        remainingMillis = remaining,
        remainingMinutes = (remaining - 1L) / LIVE_STEP_MILLIS + 1L,
        progressPercent = if (inClass) {
            ((nowMillis - startMillis) * 100L / (endMillis - startMillis)).toInt().coerceIn(0, 100)
        } else {
            0
        },
    )
}
