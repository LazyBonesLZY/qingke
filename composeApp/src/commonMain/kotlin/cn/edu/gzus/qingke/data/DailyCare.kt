package cn.edu.gzus.qingke.data

/** Quiet, local-only suggestions. Never creates notifications or pressures students. */
data class DailyCareHint(val title: String, val detail: String)

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
): DailyCareHint? {
    if (daysUntilExam != null && daysUntilExam in 0..1 && !upcomingExamName.isNullOrBlank()) {
        return DailyCareHint(
            if (daysUntilExam == 0) "今天有考试" else "明天有考试",
            "${upcomingExamName.take(24)} · 出发前确认考试时间、地点和证件。",
        )
    }
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
