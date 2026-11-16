package cn.edu.gzus.qingke.data

/** The key is intentionally specific: a task must not outlive its account or term. */
internal fun AppSnapshot.coursePickOwnerKey(): String? {
    val student = session.studentId.trim().takeIf { session.loggedIn && it.isNotBlank() } ?: return null
    return listOf(
        settings.schoolId, settings.gzusLoginChannel, student,
        settings.yearCode, settings.termCode,
    ).joinToString("|")
}

internal fun AppSnapshot.canRunQueuedPicks(): Boolean =
    settings.coursePickQueue.isEmpty() ||
        (settings.coursePickOwner.isNotBlank() && settings.coursePickOwner == coursePickOwnerKey())
