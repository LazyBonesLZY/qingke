package cn.edu.gzus.qingke.data

/** A switch changed during a synchronous system post must also retract that in-flight notice. */
internal fun postLiveWhileEnabled(enabled: () -> Boolean, post: () -> Unit, cancel: () -> Unit): Boolean {
    if (!enabled()) return false
    post()
    if (!enabled()) {
        cancel()
        return false
    }
    return true
}

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
