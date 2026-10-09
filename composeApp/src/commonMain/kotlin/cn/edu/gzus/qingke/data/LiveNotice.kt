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
 * 后台两次推送的间隔。Collapse 的胶囊/shortCriticalText 和正文里的
 * 「还剩 N 分」都是静态文本，只能靠重新 notify 刷新；秒级数字由系统
 * Chronometer 自己走字。1 分钟一推，倒计时最多慢 1 分钟；严禁按秒推。
 */
private const val LIVE_STEP_MILLIS = 60_000L

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
        chip = if (next.inClass) "下课 ${next.minutesToEnd}′" else "${next.minutesToStart}′后",
    )
}

/**
 * 下一次该叫醒后台更新通知的时刻，null 表示不用再叫。
 * 有通知时每分钟推一次（刷新胶囊文本和进度），并在上课、下课那一刻切状态；
 * 没挂时等到下一节的提醒点，今天没课了就等明天零点过五分再看。
 */
fun AppSnapshot.nextLiveWake(now: LocalDateTime, nowMs: Long): Long? {
    val school = resolved()
    if (!school.hasPeriodClock) return null
    if (!settings.remindBeforeClass || slots.isEmpty()) return null
    val notice = liveNotice(now, nowMs)
    if (notice != null) {
        val edge = if (notice.inClass) notice.endMillis else notice.startMillis
        return minOf(nowMs + LIVE_STEP_MILLIS, edge + 1_000L)
    }
    val next = nextLiveLesson(slots, now.date, settings, now.date, now.time, scheduleAdjust, school.periodBlocks)
    if (next != null) {
        val start = combineMillis(now.date, school.periodStartOf(next.slot.period))
        val at = start - settings.resolvedRemindLead() * 60_000L
        return if (at > nowMs) at else nowMs + LIVE_STEP_MILLIS
    }
    return combineMillis(now.date.plus(DatePeriod(days = 1)), "00:05").takeIf { it > nowMs }
}
