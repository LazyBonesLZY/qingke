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
)

/**
 * 后台两次推送的间隔。胶囊上显示的是「下课 23′」这种静态文案，只能靠重推刷新，
 * 所以压到 1 分钟（最多慢 1 分钟；严禁按秒推）。展开卡片的秒级数字由
 * when+Chronometer 自己走，不吃这个间隔。进度条不会自己插值，也靠它跟上。
 */
internal const val LIVE_STEP_MILLIS = 60_000L

/** 测试用的假课次窗口落盘用的键。 */
private const val LIVE_TEST_FILE = "qingke-live-test"

/**
 * 读测试窗口。落盘是为了 App 被划掉之后，后台的 tick 还能接着刷新那条测试通知
 *（否则它只在内存里，后台不认识它，还会把它当真实课表取消掉）。
 */
internal fun readLiveTestWindow(): Pair<Long, Long>? {
    val parts = readStore(LIVE_TEST_FILE)?.split(",") ?: return null
    if (parts.size != 2) return null
    val start = parts[0].toLongOrNull() ?: return null
    val end = parts[1].toLongOrNull() ?: return null
    return if (end > start) start to end else null
}

internal fun writeLiveTestWindow(start: Long, end: Long) {
    writeStore(LIVE_TEST_FILE, if (start <= 0L || end <= start) "" else "$start,$end")
}

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
    )
}

/**
 * 下一次该叫醒后台更新通知的时刻，null 表示不用再叫。
 * 挂着通知时定期重推（让正文和进度条跟上）并在上下课那一刻切状态；
 * 没挂时等到下一节的提醒点，都过了也别断链，兜到明天 00:05。
 */
fun AppSnapshot.nextLiveWake(now: LocalDateTime, nowMs: Long): Long? {
    val school = resolved()
    if (!school.hasPeriodClock) return null
    if (!settings.remindBeforeClass || slots.isEmpty()) return null
    val notice = liveNotice(now, nowMs)
    if (notice != null) {
        val edge = if (notice.inClass) notice.endMillis else notice.startMillis
        return minOf(nowMs + LIVE_STEP_MILLIS, edge + 1_000L).coerceAtLeast(nowMs + 1_000L)
    }
    return fallbackWake(now, nowMs, school)
}

/**
 * 没挂通知时等下一节的提醒点；提醒点已过（作息未知等原因没挂上）就等上课点；
 * 都过了也别断链，兜到明天 00:05。
 */
private fun AppSnapshot.fallbackWake(now: LocalDateTime, nowMs: Long, school: ResolvedSchool): Long? {
    val next = nextLiveLesson(slots, now.date, settings, now.date, now.time, scheduleAdjust, school.periodBlocks)
    if (next != null) {
        val start = combineMillis(now.date, school.periodStartOf(next.slot.period))
        val at = start - settings.resolvedRemindLead() * 60_000L
        val wake = if (at > nowMs) at else start + 1_000L
        if (wake > nowMs) return wake
    }
    return combineMillis(now.date.plus(DatePeriod(days = 1)), "00:05").takeIf { it > nowMs }
}
