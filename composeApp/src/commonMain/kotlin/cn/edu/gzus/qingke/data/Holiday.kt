package cn.edu.gzus.qingke.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

fun HolidayCalendar.lookup(date: LocalDate): HolidayDay? =
    days.firstOrNull { it.date == date.toString() }

fun HolidayDay.chip(): String = if (off) shortHolidayName(name) else "班"

fun shortHolidayName(name: String): String = when {
    name.contains("元旦") -> "元旦"
    name.contains("春节") -> "春节"
    name.contains("清明") -> "清明"
    name.contains("劳动") -> "劳动"
    name.contains("端午") -> "端午"
    name.contains("中秋") -> "中秋"
    name.contains("国庆") -> "国庆"
    else -> name.replace("节", "").take(2).ifBlank { "休" }
}

fun weekDateLabel(date: LocalDate, monday: LocalDate): String =
    if (date.monthNumber == monday.monthNumber) date.dayOfMonth.toString()
    else "${date.monthNumber}/${date.dayOfMonth}"

fun holidayYearsNeeded(snapshot: AppSnapshot, today: LocalDate): List<Int> {
    val start = snapshot.settings.termStartMonday() ?: today
    val weeks = snapshot.settings.resolvedWeekCount(snapshot.slots).coerceIn(1, 30)
    val end = start.plus(DatePeriod(days = weeks * 7 + 7))
    val lo = minOf(start.year, today.year, end.year)
    val hi = maxOf(start.year, today.year, end.year)
    return (lo..hi).toList()
}
