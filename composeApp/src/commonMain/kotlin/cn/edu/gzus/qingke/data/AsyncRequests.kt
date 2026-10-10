package cn.edu.gzus.qingke.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend inline fun <T> activeRequest(block: () -> T): Result<T> {
    val result = requestResult(block)
    currentCoroutineContext().ensureActive()
    return result
}

// Cancellation is control flow, not a network failure or a fallback result.
internal inline fun <T> requestResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

/** UI-owned requests: only the newest search for each key may publish a result. */
internal class LatestRequests(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<String, Job>()

    fun launch(key: String, block: suspend () -> Unit) {
        cancel(key)
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { block() }
        jobs[key] = job
        job.invokeOnCompletion { if (jobs[key] === job) jobs.remove(key) }
        job.start()
    }

    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
    }

    fun cancelAll() {
        val pending = jobs.values.toList()
        jobs.clear()
        pending.forEach { it.cancel() }
    }
}

internal fun AppSnapshot.withInterruptedCoursePicks(): AppSnapshot {
    if (settings.coursePickQueue.none { it.status == "running" }) return this
    return copy(settings = settings.copy(coursePickQueue = settings.coursePickQueue.map {
        if (it.status == "running") it.copy(status = "fail", message = "上次提交已中断，请先核对教务已选结果") else it
    }))
}

internal data class SessionStamp(
    val generation: Long,
    val schoolId: String,
    val loginChannel: String,
    val studentId: String,
    val loggedIn: Boolean,
    val custom: CustomJwxt,
)

internal fun AppSnapshot.sessionStamp(generation: Long): SessionStamp = SessionStamp(
    generation, settings.schoolId, settings.gzusLoginChannel,
    session.studentId, session.loggedIn, settings.customJwxt,
)
