package cn.edu.gzus.qingke.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

fun parseWeekSet(raw: String): Set<Int> {
    var text = raw
        .replace("周", "")
        .replace("第", "")
        .replace(" ", "")
        .replace("－", "-")
        .replace("—", "-")
        .replace("~", "-")
        .replace("到", "-")
    if (text.isBlank()) return emptySet()
    val oddOnly = text.contains("单") && !text.contains("双")
    val evenOnly = text.contains("双") && !text.contains("单")
    text = text.replace("单", "").replace("双", "")
        .replace("(", "").replace(")", "")
        .replace("（", "").replace("）", "")
    val out = mutableSetOf<Int>()
    text.split(",", "、", ";", "；").forEach { part ->
        val piece = part.filter { it.isDigit() || it == '-' }
        if (piece.isBlank()) return@forEach
        val nums = piece.split("-").mapNotNull { it.toIntOrNull() }.filter { it in 1..40 }
        when (nums.size) {
            0 -> Unit
            1 -> out += nums[0]
            else -> {
                val lo = minOf(nums[0], nums[1])
                val hi = maxOf(nums[0], nums[1])
                for (w in lo..hi) out += w
            }
        }
    }
    return when {
        oddOnly -> out.filter { it % 2 == 1 }.toSet()
        evenOnly -> out.filter { it % 2 == 0 }.toSet()
        else -> out
    }
}

fun parseWeekday(xqj: String, xqjmc: String): Int {
    xqj.toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
    val label = xqjmc
    return when {
        label.contains("一") -> 1
        label.contains("二") -> 2
        label.contains("三") -> 3
        label.contains("四") -> 4
        label.contains("五") -> 5
        label.contains("六") -> 6
        label.contains("日") || label.contains("天") -> 7
        else -> 1
    }
}

fun LessonSlot.activeIn(week: Int): Boolean {
    if (week < 1) return false
    return week in parseWeekSet(weeks)
}

fun PracticeCourse.activeIn(week: Int): Boolean {
    if (week < 1) return false
    return week in parseWeekSet(weeks)
}

fun List<LessonSlot>.forDay(week: Int, weekday: Int): List<LessonSlot> =
    filter { it.weekday == weekday && it.activeIn(week) }
        .sortedBy { periodSortKey(it.period) }

fun List<LessonSlot>.forWeekdayAnyWeek(weekday: Int): List<LessonSlot> =
    filter { it.weekday == weekday }.sortedBy { periodSortKey(it.period) }

fun periodSortKey(period: String): Int =
    period.substringBefore("-").toIntOrNull() ?: 99

fun periodToBlock(period: String): String {
    val start = period.substringBefore("-").toIntOrNull() ?: return period
    val end = period.substringAfter("-", start.toString()).toIntOrNull() ?: start
    val a = if (start % 2 == 0) start - 1 else start
    val b = if (end % 2 == 0) end else end + 1
    return "$a-$b"
}

fun nowDateTime(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun greeting(hour: Int): String = when (hour) {
    in 0..4 -> "夜深了"
    in 5..10 -> "早上好"
    in 11..13 -> "中午好"
    in 14..17 -> "下午好"
    else -> "晚上好"
}

fun formatMonthDay(date: LocalDate): String = "${date.monthNumber}月${date.dayOfMonth}日"

fun weekdayIndex(date: LocalDate): Int = date.dayOfWeek.ordinal + 1 // 1=Mon

fun minutesUntil(now: LocalTime, start: String): Int? {
    val parts = start.split(":")
    if (parts.size < 2) return null
    val target = parts[0].toInt() * 60 + parts[1].toInt()
    val cur = now.hour * 60 + now.minute
    return target - cur
}

fun periodStart(period: String): String {
    val first = period.substringBefore("-").toIntOrNull() ?: return "09:00"
    return when (first) {
        1 -> "09:00"
        2 -> "09:41"
        3 -> "10:40"
        4 -> "11:21"
        5 -> "12:30"
        6 -> "13:11"
        7 -> "14:00"
        8 -> "14:41"
        9 -> "15:30"
        10 -> "16:11"
        11 -> "17:00"
        12 -> "17:41"
        13 -> "19:00"
        14 -> "19:41"
        15 -> "20:30"
        16 -> "21:11"
        else -> "09:00"
    }
}

fun periodClockRange(period: String): String {
    val first = period.substringBefore("-").toIntOrNull() ?: return ""
    val last = period.substringAfter("-", period).toIntOrNull() ?: first
    if (first !in 1..16 || last !in 1..16) return ""
    return "${periodStart(period)}-${periodEnd(period)}"
}

fun formatPeriodWithClock(period: String, periodLabel: String = "", hasClock: Boolean): String {
    val label = periodLabel.ifBlank { period }
    val clock = if (hasClock) periodClockRange(period) else ""
    return if (clock.isBlank()) label else "$label · $clock"
}

fun periodEnd(period: String): String {
    val last = period.substringAfter("-", period).toIntOrNull() ?: return "10:20"
    return when (last) {
        1 -> "09:40"
        2 -> "10:20"
        3 -> "11:20"
        4 -> "12:00"
        5 -> "13:10"
        6 -> "13:50"
        7 -> "14:40"
        8 -> "15:20"
        9 -> "16:10"
        10 -> "16:50"
        11 -> "17:40"
        12 -> "18:20"
        13 -> "19:40"
        14 -> "20:20"
        15 -> "21:10"
        16 -> "21:50"
        else -> "10:20"
    }
}

data class LiveLesson(
    val slot: LessonSlot,
    val minutesToStart: Int,
    val minutesToEnd: Int,
) {
    val inClass: Boolean get() = minutesToStart <= 0 && minutesToEnd > 0
}

fun nextLiveLesson(slots: List<LessonSlot>, week: Int, weekday: Int, time: LocalTime): LiveLesson? =
    liveLessonFrom(slots.forDay(week, weekday), time)

fun nextLiveLesson(
    slots: List<LessonSlot>,
    date: LocalDate,
    settings: AppSettings,
    today: LocalDate,
    time: LocalTime,
): LiveLesson? = liveLessonFrom(slots.forDate(date, settings, today), time)

private fun liveLessonFrom(day: List<LessonSlot>, time: LocalTime): LiveLesson? {
    day.forEach { slot ->
        val toStart = minutesUntil(time, periodStart(slot.period)) ?: return@forEach
        val toEnd = minutesUntil(time, periodEnd(slot.period)) ?: return@forEach
        if (toEnd > 0) return LiveLesson(slot, toStart, toEnd)
    }
    return null
}

fun nextLesson(slots: List<LessonSlot>, week: Int, weekday: Int, time: LocalTime): Pair<LessonSlot, Int>? {
    val next = nextLiveLesson(slots, week, weekday, time) ?: return null
    return next.slot to if (next.inClass) 0 else next.minutesToStart
}

fun nextLesson(
    slots: List<LessonSlot>,
    date: LocalDate,
    settings: AppSettings,
    today: LocalDate,
    time: LocalTime,
): Pair<LessonSlot, Int>? {
    val next = nextLiveLesson(slots, date, settings, today, time) ?: return null
    return next.slot to if (next.inClass) 0 else next.minutesToStart
}

fun uniqueCourses(slots: List<LessonSlot>): List<CourseDetail> =
    slots.groupBy { it.courseId }.map { (id, items) -> items.toCourseDetail(id) }

fun resolveCourseDetail(snapshot: AppSnapshot, courseId: String): CourseDetail? {
    val fromSlots = snapshot.slots.toCourseDetail(courseId)
    if (fromSlots.slots.isNotEmpty()) return fromSlots
    return snapshot.grades.firstOrNull { it.courseId == courseId }?.toCourseDetail()
}

fun GradeItem.toCourseDetail(): CourseDetail {
    val point = resolvedGpa()
    return CourseDetail(
        courseId = courseId,
        courseName = courseName.ifBlank { courseId },
        credit = credit,
        teacher = "",
        category = "",
        required = "",
        assess = assess,
        className = "",
        rooms = "",
        times = "",
        weeks = "",
        campus = "",
        hours = "",
        slots = emptyList(),
        highlight = buildList {
            if (score.isNotBlank()) add("成绩 $score")
            if (point != null) add("绩点 ${formatGpa(point)}")
            if (term.isNotBlank()) add(term)
        }.joinToString(" · "),
    )
}

fun List<LessonSlot>.toCourseDetail(courseId: String): CourseDetail {
    val items = filter { it.courseId == courseId }
    if (items.isEmpty()) {
        return CourseDetail(
            courseId = courseId,
            courseName = "未知课程",
            credit = "",
            teacher = "",
            category = "",
            required = "",
            assess = "",
            className = "",
            rooms = "",
            times = "",
            weeks = "",
            campus = "",
            hours = "",
            slots = emptyList(),
            highlight = "课表里没有这门课",
        )
    }
    val first = items.first()
    val rooms = items.map { "${WeekdayNames.getOrElse(it.weekday - 1) { "?" }} ${it.room}" }.distinct().joinToString(" · ")
    val times = items.map { "${WeekdayNames.getOrElse(it.weekday - 1) { "?" }} ${it.periodLabel}" }.distinct().joinToString(" · ")
    val weeks = items.map { it.weeks }.distinct().joinToString("，")
    val highlight = buildString {
        append(items.size)
        append(" 个时段")
        if (first.credit.isNotBlank()) append(" · ").append(first.credit).append(" 学分")
        if (first.assess.isNotBlank()) append(" · ").append(first.assess)
        if (first.category.isNotBlank()) append(" · ").append(first.category)
    }
    return CourseDetail(
        courseId = courseId,
        courseName = first.courseName,
        credit = first.credit,
        teacher = first.teacher,
        category = first.category,
        required = first.required,
        assess = first.assess,
        className = first.className,
        rooms = rooms,
        times = times,
        weeks = weeks,
        campus = first.campus,
        hours = first.hours,
        slots = items,
        highlight = highlight,
    )
}

fun upcomingWeekPreview(slots: List<LessonSlot>, currentWeek: Int): Pair<String, String>? {
    if (slots.isEmpty()) return null
    val later = (currentWeek + 1..20).firstOrNull { week ->
        slots.any { it.activeIn(week) && !it.activeIn(currentWeek) }
    } ?: return null
    val names = slots.filter { it.activeIn(later) }.map { it.courseName }.distinct().take(3)
    if (names.isEmpty()) return null
    val nextAfter = (later + 1..20).firstOrNull { week ->
        slots.any { it.activeIn(week) && !it.activeIn(later) && !it.activeIn(currentWeek) }
    }
    val more = nextAfter?.let { week ->
        slots.filter { it.activeIn(week) && !it.activeIn(later) }.map { it.courseName }.distinct().take(4)
    }.orEmpty()
    val title = "第${later}周 · ${names.joinToString(" / ")}"
    val sub = if (more.isEmpty() || nextAfter == null) "之后课程会陆续开始" else "第${nextAfter}周再加${more.joinToString("、")}"
    return title to sub
}

fun teachingWeeks(slots: List<LessonSlot>): Int =
    slots.flatMap { parseWeekSet(it.weeks) }.maxOrNull() ?: 0

fun weeksInUse(slots: List<LessonSlot>): List<Int> =
    slots.flatMap { parseWeekSet(it.weeks) }.toSortedSet().toList()

fun combineMillis(date: LocalDate, hm: String): Long {
    val parts = hm.split(":")
    if (parts.size < 2) return 0L
    val hour = parts[0].toIntOrNull() ?: return 0L
    val minute = parts[1].toIntOrNull() ?: return 0L
    return LocalDateTime(date, LocalTime(hour, minute, 0))
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}

fun plannedCredits(slots: List<LessonSlot>): Double =
    uniqueCourses(slots).sumOf { it.credit.toDoubleOrNull() ?: 0.0 }

fun mondayOf(date: LocalDate): LocalDate =
    date.minus(DatePeriod(days = weekdayIndex(date) - 1))

fun parseIsoDate(raw: String): LocalDate? =
    raw.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun AppSettings.termStartMonday(): LocalDate? = parseIsoDate(termStart)?.let { mondayOf(it) }

fun AppSettings.hasTermStart(): Boolean = termStartMonday() != null

fun daysInMonth(year: Int, month: Int): Int {
    val first = LocalDate(year, month, 1)
    return first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
}

fun formatLongDate(date: LocalDate): String =
    "${date.year}年${date.monthNumber}月${date.dayOfMonth}日 周${WeekdayNames.getOrElse(weekdayIndex(date) - 1) { "" }}"

fun teachingWeekFromStart(date: LocalDate, termStart: LocalDate): Int {
    val days = mondayOf(date).toEpochDays() - mondayOf(termStart).toEpochDays()
    return 1 + (days / 7).toInt()
}

fun resolvedCurrentWeek(settings: AppSettings, today: LocalDate): Int {
    val start = settings.termStartMonday()
    if (start != null) return teachingWeekFromStart(today, start)
    return settings.currentWeek
}

fun AppSettings.resolvedWeekCount(slots: List<LessonSlot>): Int {
    val used = weeksInUse(slots).maxOrNull() ?: 0
    return listOf(weekCount, used, currentWeek, 16).maxOrNull() ?: 16
}

fun termStartFromCurrentWeek(today: LocalDate, week: Int): LocalDate =
    mondayOf(today).minus(DatePeriod(days = (week.coerceAtLeast(1) - 1) * 7))

fun mondayOfTeachingWeek(week: Int, settings: AppSettings, today: LocalDate): LocalDate {
    val start = settings.termStartMonday()
    if (start != null) return start.plus(DatePeriod(days = (week - 1) * 7))
    return mondayOf(today).plus(DatePeriod(days = (week - resolvedCurrentWeek(settings, today)) * 7))
}

fun teachingWeekOn(date: LocalDate, settings: AppSettings, today: LocalDate): Int {
    val start = settings.termStartMonday()
    if (start != null) return teachingWeekFromStart(date, start)
    val days = mondayOf(date).toEpochDays() - mondayOf(today).toEpochDays()
    return resolvedCurrentWeek(settings, today) + (days / 7).toInt()
}

fun formatMonthDayRange(start: LocalDate, end: LocalDate): String =
    if (start.monthNumber == end.monthNumber) {
        "${start.monthNumber}月${start.dayOfMonth}日–${end.dayOfMonth}日"
    } else {
        "${start.monthNumber}月${start.dayOfMonth}日–${end.monthNumber}月${end.dayOfMonth}日"
    }

fun formatYearMonth(date: LocalDate): String = "${date.year}年${date.monthNumber}月"

fun LessonSlot.occupiesBlock(block: PeriodBlock): Boolean {
    val start = period.substringBefore("-").toIntOrNull()
    val end = period.substringAfter("-", start?.toString().orEmpty()).toIntOrNull()
    if (start == null || end == null) return periodToBlock(period) == block.label
    val blockStart = block.label.substringBefore("-").toIntOrNull() ?: return false
    val blockEnd = block.label.substringAfter("-").toIntOrNull() ?: blockStart
    return start <= blockEnd && end >= blockStart
}

fun List<LessonSlot>.forBlock(week: Int, weekday: Int, block: PeriodBlock): List<LessonSlot> =
    filter { it.weekday == weekday && it.activeIn(week) && it.occupiesBlock(block) }
        .sortedBy { periodSortKey(it.period) }

fun List<LessonSlot>.forBlockOnDate(
    date: LocalDate,
    block: PeriodBlock,
    settings: AppSettings,
    today: LocalDate,
): List<LessonSlot> =
    forDate(date, settings, today).filter { it.occupiesBlock(block) }.sortedBy { periodSortKey(it.period) }

fun AppSettings.shiftFrom(date: LocalDate): ScheduleShift? =
    scheduleShifts.firstOrNull { parseIsoDate(it.fromDate) == date }

fun AppSettings.shiftsOnto(date: LocalDate): List<ScheduleShift> =
    scheduleShifts.filter { parseIsoDate(it.toDate) == date }

fun AppSettings.hasShift(date: LocalDate): Boolean =
    shiftFrom(date) != null || shiftsOnto(date).isNotEmpty()

fun AppSettings.shiftConflict(from: LocalDate, to: LocalDate): String? {
    if (from == to) return "原上课日和调到的那天不能是同一天"
    val fromIso = from.toString()
    val toIso = to.toString()
    if (scheduleShifts.any { it.fromDate == fromIso || it.toDate == fromIso }) return "原上课日已经有调课"
    if (scheduleShifts.any { it.fromDate == toIso || it.toDate == toIso }) return "调到的那天已经有调课"
    return null
}

fun formatShift(shift: ScheduleShift): String {
    val from = parseIsoDate(shift.fromDate)
    val to = parseIsoDate(shift.toDate)
    if (from == null || to == null) return "${shift.fromDate} → ${shift.toDate}"
    return "${formatMonthDay(from)}周${WeekdayNames.getOrElse(weekdayIndex(from) - 1) { "?" }} → ${formatMonthDay(to)}周${WeekdayNames.getOrElse(weekdayIndex(to) - 1) { "?" }}"
}

fun AppSettings.shiftNoteFor(slot: LessonSlot, today: LocalDate): String =
    scheduleShifts.mapNotNull { shift ->
        val from = parseIsoDate(shift.fromDate) ?: return@mapNotNull null
        val to = parseIsoDate(shift.toDate) ?: return@mapNotNull null
        if (weekdayIndex(from) != slot.weekday) return@mapNotNull null
        if (!slot.activeIn(teachingWeekOn(from, this, today))) return@mapNotNull null
        "${formatMonthDay(from)} 调到 ${formatMonthDay(to)}"
    }.joinToString("；")

fun holidayShiftHints(
    holidays: HolidayCalendar,
    settings: AppSettings,
    slots: List<LessonSlot>,
    today: LocalDate,
): Pair<List<HolidayDay>, List<HolidayDay>> {
    val start = settings.termStartMonday()
    val end = start?.plus(DatePeriod(days = settings.resolvedWeekCount(slots).coerceIn(1, 30) * 7))
    fun inTerm(date: LocalDate): Boolean = start == null || (date >= start && (end == null || date < end))
    val offDays = holidays.days.filter { day ->
        day.off && parseIsoDate(day.date)?.let { date ->
            inTerm(date) && weekdayIndex(date) in 1..5
        } == true
    }
    val makeupDays = holidays.days.filter { day ->
        !day.off && parseIsoDate(day.date)?.let { date ->
            inTerm(date) && weekdayIndex(date) >= 6
        } == true
    }
    return offDays to makeupDays
}

fun List<LessonSlot>.forDate(date: LocalDate, settings: AppSettings, today: LocalDate): List<LessonSlot> {
    if (settings.shiftFrom(date) != null) return emptyList()
    val incoming = settings.shiftsOnto(date)
    if (incoming.isNotEmpty()) {
        return incoming.flatMap { shift ->
            val src = parseIsoDate(shift.fromDate) ?: return@flatMap emptyList()
            forDay(teachingWeekOn(src, settings, today), weekdayIndex(src))
        }.distinctBy { "${it.courseId}/${it.period}/${it.weekday}/${it.weeks}" }
            .sortedBy { periodSortKey(it.period) }
    }
    val week = teachingWeekOn(date, settings, today)
    if (week < 1) return emptyList()
    return forDay(week, weekdayIndex(date))
}

private val CourseShortNames = listOf(
    "计算机与人工智能导论" to "计导",
    "中国近现代史纲要" to "史纲",
    "习近平新时代中国特色社会主义思想概论" to "习概",
    "毛泽东思想和中国特色社会主义理论体系概论" to "毛概",
    "思想道德与法治" to "德法",
    "马克思主义基本原理" to "马原",
    "形势与政策" to "形策",
    "军事理论" to "军理",
    "军事技能" to "军训",
    "职业生涯规划" to "生涯",
    "电子技术基础" to "电基",
    "C语言程序设计" to "C程",
    "Web程序设计" to "Web",
    "程序设计基础" to "程设",
    "体能训练" to "体能",
    "大学英语" to "英语",
    "大学体育" to "体育",
    "大学语文" to "语文",
    "大学物理" to "物理",
    "高等数学" to "高数",
    "线性代数" to "线代",
    "概率论与数理统计" to "概率",
    "概率论" to "概率",
    "计算机网络" to "计网",
    "数据结构" to "数构",
    "软件工程" to "软工",
    "离散数学" to "离散",
    "数字逻辑" to "数逻",
)

fun compactCourseName(
    name: String,
    aliases: Map<String, String> = emptyMap(),
    courseId: String = "",
): String {
    val custom = aliases[courseId]?.trim().orEmpty().ifBlank { aliases[name]?.trim().orEmpty() }
    if (custom.isNotBlank()) return custom.take(4)
    val raw = name.trim()
    if (raw.isBlank()) return raw
    CourseShortNames.firstOrNull { raw.contains(it.first) }?.let { return it.second }
    var core = raw
        .replace(Regex("（[^）]*）"), "")
        .replace(Regex("\\([^)]*\\)"), "")
        .replace(Regex("[\\sIVXⅠ-Ⅻ一二三四五1-9]+$"), "")
        .trim()
    if (core.isBlank()) core = raw.replace(Regex("[（(][^）)]*[）)]"), "").trim()
    if (core.length <= 4) return core
    val parts = core.split("与", "及").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size >= 2) {
        return (parts[0].take(2) + parts[1].take(2)).take(4)
    }
    return core.take(4)
}

fun compactCourseLines(
    name: String,
    aliases: Map<String, String> = emptyMap(),
    courseId: String = "",
): List<String> {
    val short = compactCourseName(name, aliases, courseId)
    if (short.length <= 2) return listOf(short)
    if (short.all { it.code < 128 } && short.length <= 4) return listOf(short)
    return listOf(short.take(2), short.drop(2))
}

fun compactRoomName(room: String): String {
    val core = room.replace(Regex("[（(][^）)]*[）)]"), "").trim()
    if (core.length <= 8) return core
    return core.takeLast(6)
}

fun shortCourseName(
    name: String,
    max: Int = 5,
    aliases: Map<String, String> = emptyMap(),
    courseId: String = "",
): String {
    val compact = compactCourseName(name, aliases, courseId)
    return if (compact.length <= max) compact else compact.take(max)
}

fun examSortKey(time: String): Long {
    val match = Regex("""(\d{4})[-年./](\d{1,2})[-月./](\d{1,2})""").find(time) ?: return Long.MAX_VALUE
    val year = match.groupValues[1].toIntOrNull() ?: return Long.MAX_VALUE
    val month = match.groupValues[2].toIntOrNull() ?: return Long.MAX_VALUE
    val day = match.groupValues[3].toIntOrNull() ?: return Long.MAX_VALUE
    return runCatching { LocalDate(year, month, day).toEpochDays().toLong() }.getOrDefault(Long.MAX_VALUE)
}

fun shortRoom(room: String): String = compactRoomName(room)
