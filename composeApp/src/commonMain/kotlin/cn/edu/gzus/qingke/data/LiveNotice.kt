package cn.edu.gzus.qingke.data

import kotlinx.datetime.DatePeriod
import cn.edu.gzus.qingke.data.periodText
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

/**
 * 省电策略：后台只在状态边沿叫醒（提醒点、上课点、下课点），中间不叫醒。
 * 秒级数字由系统 Chronometer 按 when 自己走字；胶囊和正文只放静态文案
 * （"上课中/下一节"），不放分钟数，所以不存在 stale。
 */

private val snapshotJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 应用没开时后台闹钟用：直接读存档，读不到就当没有课表。 */
internal fun readSnapshotStore(): AppSnapshot? {
    val text = readStore(STORE) ?: return null
    return runCatching { snapshotJson.decodeFromString(AppSnapshot.serializer(), text) }.getOrNull()
}

/** 现在该挂哪条上课通知；不该挂就返回 null。应用里的循环和后台闹钟共用这一份判断。 */
fun AppSnapshot.liveNotice(now: LocalDateTime, nowMs: Long): LiveNotice? {
    // 时间一律走学校自己的作息块：广生态的作息和内置表差很多，
    // 用错了会让提醒在错的时间弹。
    val school = resolved()
    if (!school.hasPeriodClock) return null
    if (!settings.remindBeforeClass || slots.isEmpty()) return null
    val next = nextLiveLesson(slots, now.date, settings, now.date, now.time, scheduleAdjust, school.periodBlocks)
        ?: return null
    if (!next.inClass && next.minutesToStart > settings.resolvedRemindLead()) return null
    val slot = next.slot
    // 这一节的作息未知时直接不弹：宁可没有提醒，也不能拿别的学校的作息在错的时间弹。
    if (!school.clockKnown(slot.period)) return null
    val startMillis = combineMillis(now.date, school.periodStartOf(slot.period))
    val endMillis = combineMillis(now.date, school.periodEndOf(slot.period))
    if (startMillis <= 0L || endMillis <= startMillis) return null
    val progress = when {
        nowMs <= startMillis -> 0f
        nowMs >= endMillis -> 1f
        else -> ((nowMs - startMillis).toFloat() / (endMillis - startMillis).toFloat()).coerceIn(0f, 1f)
    }
    val period = periodText(slot.period, slot.periodLabel)
    val clock = school.clockRangeOf(slot.period).replace("-", "–")
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
        chip = if (next.inClass) "上课中" else "下一节",
    )
}

/**
 * 下一次该叫醒后台更新通知的时刻，null 表示不用再叫。
 * 有通知时只在边沿推一次（上课中→等下课点，课前→等上课点），中间不叫醒；
 * 没挂时等到下一节的提醒点，今天没课了就等明天零点过五分再看。
 */
fun AppSnapshot.nextLiveWake(now: LocalDateTime, nowMs: Long): Long? {
    val school = resolved()
    if (!school.hasPeriodClock) return null
    if (!settings.remindBeforeClass || slots.isEmpty()) return null
    val notice = liveNotice(now, nowMs)
    if (notice != null) {
        val edge = if (notice.inClass) notice.endMillis else notice.startMillis
        return (edge + 1_000L).takeIf { it > nowMs }
    }
    val next = nextLiveLesson(slots, now.date, settings, now.date, now.time, scheduleAdjust, school.periodBlocks)
    if (next != null) {
        val start = combineMillis(now.date, school.periodStartOf(next.slot.period))
        val at = start - settings.resolvedRemindLead() * 60_000L
        // 提醒点在未来就等它；已过（作息未知等原因没挂上）就等上课点再看一次。
        return (if (at > nowMs) at else start + 1_000L).takeIf { it > nowMs }
    }
    return combineMillis(now.date.plus(DatePeriod(days = 1)), "00:05").takeIf { it > nowMs }
}
