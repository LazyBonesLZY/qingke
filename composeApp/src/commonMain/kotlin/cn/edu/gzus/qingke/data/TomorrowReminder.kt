package cn.edu.gzus.qingke.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

internal const val TOMORROW_ENABLED_FILE = "qingke-tomorrow-enabled"

internal fun writeTomorrowReminderEnabled(enabled: Boolean) = writeStore(TOMORROW_ENABLED_FILE, enabled.toString())
internal fun readTomorrowReminderPreference(): Boolean? =
    readStore(TOMORROW_ENABLED_FILE)?.trim()?.toBooleanStrictOrNull()

internal fun AppSnapshot.withTomorrowReminderPreference(): AppSnapshot = readTomorrowReminderPreference()?.let {
    if (settings.tomorrowReminderEnabled == it) this else copy(settings = settings.copy(tomorrowReminderEnabled = it))
} ?: this

/** This reminder has its own opt-in and never depends on the Live or daily-care switches. */
internal fun tomorrowReminderEnabled(): Boolean =
    readSnapshotStore()?.settings?.tomorrowReminderEnabled == true && readTomorrowReminderPreference() != false

internal fun nextTomorrowReminderAt(nowMillis: Long): Long {
    val today = dateTimeAt(nowMillis).date
    val evening = combineMillis(today, "20:30")
    return if (evening > nowMillis) evening else combineMillis(today.plus(DatePeriod(days = 1)), "20:30")
}

/** A delayed alarm may run this evening, but must never notify for an expired date or an empty day. */
internal fun tomorrowNotificationHint(
    snapshot: AppSnapshot,
    nowMillis: Long,
    requestedDate: String?,
    lastNotifiedDate: String?,
): DailyCareHint? {
    val now = dateTimeAt(nowMillis)
    if (!snapshot.settings.tomorrowReminderEnabled || !snapshot.hasTimetable || !snapshot.settings.hasTermStart() ||
        requestedDate != now.date.toString() || lastNotifiedDate == requestedDate ||
        nowMillis < combineMillis(now.date, "20:30")) return null
    val tomorrow = now.date.plus(DatePeriod(days = 1))
    if (snapshot.slots.forDate(tomorrow, snapshot.settings, now.date, snapshot.scheduleAdjust).isEmpty()) return null
    return tomorrowCareHint(snapshot, now.date)
}
