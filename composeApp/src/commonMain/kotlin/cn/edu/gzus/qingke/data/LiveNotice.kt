package cn.edu.gzus.qingke.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json

data class LiveNotice(
    val slot: LessonSlot,
    val inClass: Boolean,
    val title: String,
    val detail: String,
    val progress: Float,
    val etaMinutes: Int,
    val startMillis: Long,
    val endMillis: Long,
    val chip: String,
)

private const val LIVE_STEP_MILLIS = 5 * 60_000L

private val snapshotJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 应用没开时后台闹钟用：直接读存档，读不到就当没有课表。 */
internal fun readSnapshotStore(): AppSnapshot? {
    val text = readStore(STORE) ?: return null
    return runCatching { snapshotJson.decodeFromString(AppSnapshot.serializer(), text) }.getOrNull()
}

/** 现在该挂哪条上课通知；不该挂就返回 null。应用里的循环和后台闹钟共用这一份判断。 */
fun AppSnapshot.liveNotice(now: LocalDateTime, nowMs: Long): LiveNotice? {
    if (!resolved().hasPeriodClock) return null
    if (!settings.remindBeforeClass || slots.isEmpty()) return null
    val next = nextLiveLesson(slots, now.date, settings, now.date, now.time, scheduleAdjust) ?: return null
    if (!next.inClass && next.minutesToStart > settings.resolvedRemindLead()) return null
    val slot = next.slot
    val startMillis = combineMillis(now.date, periodStart(slot.period))
    val endMillis = combineMillis(now.date, periodEnd(slot.period))
    if (startMillis <= 0L || endMillis <= startMillis) return null
    val progress = when {
        nowMs <= startMillis -> 0f
        nowMs >= endMillis -> 1f
        else -> ((nowMs - startMillis).toFloat() / (endMillis - startMillis).toFloat()).coerceIn(0f, 1f)
    }
    val period = slot.periodLabel.ifBlank { slot.period }.let { if (it.endsWith("节")) it else "${it}节" }
    val clock = periodClockRange(slot.period).replace("-", "–")
    val room = slot.room.ifBlank { "教室待定" }
    val eta = if (next.inClass) next.minutesToEnd else next.minutesToStart
    return LiveNotice(
        slot = slot,
        inClass = next.inClass,
        title = slot.courseName,
        detail = listOf(period, clock, room).filter { it.isNotBlank() }.joinToString(" · "),
        progress = progress,
        etaMinutes = eta,
        startMillis = startMillis,
        endMillis = endMillis,
        chip = if (next.inClass) "下课 ${next.minutesToEnd}′" else "${next.minutesToStart}′后",
    )
}

/**
 * 下一次该叫醒后台更新通知的时刻，null 表示不用再叫。
 * 挂着通知时每五分钟推一次进度，并在上课、下课那一刻切状态；
 * 没挂时等到下一节的提醒点，今天没课了就等明天零点过五分再看。
 */
fun AppSnapshot.nextLiveWake(now: LocalDateTime, nowMs: Long): Long? {
    if (!resolved().hasPeriodClock) return null
    if (!settings.remindBeforeClass || slots.isEmpty()) return null
    val notice = liveNotice(now, nowMs)
    if (notice != null) {
        val edge = if (notice.inClass) notice.endMillis else notice.startMillis
        return minOf(nowMs + LIVE_STEP_MILLIS, edge + 1_000L)
    }
    val next = nextLiveLesson(slots, now.date, settings, now.date, now.time, scheduleAdjust)
    if (next != null) {
        val start = combineMillis(now.date, periodStart(next.slot.period))
        val at = start - settings.resolvedRemindLead() * 60_000L
        return if (at > nowMs) at else nowMs + LIVE_STEP_MILLIS
    }
    return combineMillis(now.date.plus(DatePeriod(days = 1)), "00:05").takeIf { it > nowMs }
}
