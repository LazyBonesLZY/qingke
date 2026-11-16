package cn.edu.gzus.qingke.data

/** Injectable sender makes build failure, eligibility failure and post failure testable. */
internal fun <T : Any> deliverLiveWithFallback(
    attempts: List<LiveDeliveryAttempt>,
    build: (LiveDeliveryAttempt) -> T?,
    eligible: (LiveDeliveryAttempt, T) -> Boolean,
    post: (LiveDeliveryAttempt, T) -> Boolean,
): Boolean {
    for (attempt in attempts) {
        val candidate = runCatching { build(attempt) }.getOrNull() ?: continue
        if (!runCatching { eligible(attempt, candidate) }.getOrDefault(false)) continue
        if (runCatching { post(attempt, candidate) }.getOrDefault(false)) return true
    }
    return false
}
