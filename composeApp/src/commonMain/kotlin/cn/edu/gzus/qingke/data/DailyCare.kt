package cn.edu.gzus.qingke.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class DailyCareHint(val title: String, val detail: String)

/** Returns null when a timetable date cannot be established. */
fun tomorrowCareHint(snapshot: AppSnapshot, today: LocalDate): DailyCareHint? {
    // 实践课缺少可推算到具体日期的信息，不能据此断言明天没课。
    if (snapshot.slots.isEmpty() || !snapshot.settings.hasTermStart()) return null
    val tomorrow = today.plus(DatePeriod(days = 1))
    fun lessons(date: LocalDate) = snapshot.slots.forDate(date, snapshot.settings, today, snapshot.scheduleAdjust)
    val next = lessons(tomorrow)
    if (next.isEmpty()) {
        if (snapshot.practices.any { it.activeIn(teachingWeekOn(tomorrow, snapshot.settings, today)) }) return null
        val freeWeekend = weekdayIndex(today) == 5 &&
            lessons(today.plus(DatePeriod(days = 2))).isEmpty()
        return if (freeWeekend) DailyCareHint("明天没课，享受周末吧～", "周六和周日都没有课程安排。")
        else DailyCareHint("明天没有课", "好好休息，也可以提前安排自己的时间。")
    }
    val school = snapshot.resolved()
    val starts = next.map { normalizeClock(school.periodStartOf(it.period)) }
    val first = if (school.hasPeriodClock && next.all { school.clockKnown(it.period) } && starts.all { it != null }) {
        starts.filterNotNull().minOrNull()
    } else null
    return DailyCareHint(
        "明日课程 · ${next.size} 个上课时段",
        if (first != null) "第一节 $first 开始，记得提前准备。" else "课程时间未完整设置，请查看课表。",
    )
}

private fun careClockMinutes(text: String): Int? {
    val parts = text.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun dailyCareHint(
    today: List<LessonSlot>,
    upcoming: LessonSlot?,
    school: ResolvedSchool,
    nowMinutes: Int,
    upcomingExamName: String? = null,
    daysUntilExam: Int? = null,
    tomorrow: DailyCareHint? = null,
): DailyCareHint? {
    if (daysUntilExam != null && daysUntilExam in 0..1 && !upcomingExamName.isNullOrBlank()) {
        return DailyCareHint(
            if (daysUntilExam == 0) "今天有考试" else "明天有考试",
            "${upcomingExamName.take(24)} · 出发前确认考试时间、地点和证件。",
        )
    }
    if (nowMinutes >= 19 * 60 && tomorrow != null) return tomorrow
    if (upcoming != null && upcoming.room.isNotBlank()) {
        val nextStart = careClockMinutes(school.periodStartOf(upcoming.period))
        if (nextStart != null && nextStart - nowMinutes in 0..35) {
            val lastRoom = today.asSequence()
                .filter { it != upcoming && it.room.isNotBlank() && it.room.trim() != upcoming.room.trim() }
                .mapNotNull { lesson ->
                    val end = careClockMinutes(school.periodEndOf(lesson.period)) ?: return@mapNotNull null
                    if (end <= nextStart && nextStart - end in 0..25) end to lesson.room else null
                }
                .maxByOrNull { it.first }
            if (lastRoom != null) {
                return DailyCareHint("下一节要换教室", "下一节在 ${upcoming.room.take(30)}，课间记得预留路上的时间。")
            }
        }
    }
    if (today.size >= 5 && nowMinutes in 7 * 60..18 * 60) {
        return DailyCareHint("今天安排较满", "今天有 ${today.size} 节课，有空档时也给自己留一点休息时间。")
    }
    return null
}
