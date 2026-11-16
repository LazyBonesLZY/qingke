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

/**
 * deleteIntent 的身份必须同时包含课次和阶段。PendingIntent 的 extras 不参与判等，
 * 只复用 requestCode 会让旧阶段的超时回调带上下一节课刚更新的 extras。
 */
internal fun liveDismissIdentity(courseKey: String, deadlineMillis: Long): String =
    "cn.edu.gzus.qingke.LIVE_DISMISS:$courseKey:$deadlineMillis"

internal fun isLiveStageTimeout(nowMillis: Long, deadlineMillis: Long): Boolean =
    deadlineMillis > 0L && nowMillis >= deadlineMillis - 1_000L

/** 按刚发布内容的绝对边界等候；发送/渲染耗时跨过边界时立即校正，而不是再等完整一分钟。 */
internal fun liveRefreshDelayMillis(nowMillis: Long, refreshAtMillis: Long?): Long =
    refreshAtMillis?.let { (it - nowMillis).coerceIn(1L, LIVE_STEP_MILLIS) } ?: LIVE_STEP_MILLIS

/** 首页只刷新可见的分钟、上下课边界和每 10 秒的上课进度，不做逐秒整页刷新。 */
internal fun todayRefreshDelayMillis(nowMillis: Long, timing: LiveTiming?): Long {
    val clockMinute = LIVE_STEP_MILLIS - nowMillis.mod(LIVE_STEP_MILLIS)
    val countdown = timing?.refreshDelayMillis ?: clockMinute
    val progress = if (timing?.inClass == true) 10_000L - nowMillis.mod(10_000L) else clockMinute
    return minOf(clockMinute, countdown, progress).coerceAtLeast(1L)
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
