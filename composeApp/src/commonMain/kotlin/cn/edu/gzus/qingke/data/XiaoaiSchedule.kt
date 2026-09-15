package cn.edu.gzus.qingke.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val XIAOAI_SCHEDULE_HOME = "https://i.xiaomixiaoai.com/h5/precache/ai-schedule/#/"

private val xiaoaiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

@Serializable
data class XiaoaiOfficialCourse(
    val name: String,
    val position: String,
    val teacher: String,
    val weeks: List<Int>,
    val day: Int,
    val sections: List<Int>,
)

@Serializable
data class XiaoaiTimerSection(
    val section: Int,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class XiaoaiTimer(
    val totalWeek: Int,
    val startSemester: String,
    val startWithSunday: Boolean,
    val showWeekend: Boolean,
    val forenoon: Int,
    val afternoon: Int,
    val night: Int,
    val sections: List<XiaoaiTimerSection>,
)

@Serializable
data class XiaoaiOfficialBundle(
    val courseInfos: List<XiaoaiOfficialCourse>,
    val timer: XiaoaiTimer,
)

@Serializable
data class XiaoaiApiCourse(
    val name: String,
    val position: String,
    val teacher: String,
    val weeks: String,
    val day: Int,
    val style: String = "",
    val sections: String,
)

data class XiaoaiToken(
    val userId: Long,
    val deviceId: String,
    val expireAt: Long,
    val ctId: Long,
    val fromUserInfo: Boolean = false,
    val tableName: String = "",
) {
    fun expired(now: Long = nowMillis()): Boolean =
        expireAt in 1 until now

    fun daysLeft(now: Long = nowMillis()): Int =
        if (expireAt <= 0L) 0 else ((expireAt - now) / 86_400_000L).toInt()
}

data class XiaoaiConvertResult(
    val official: XiaoaiOfficialBundle,
    val apiCourses: List<XiaoaiApiCourse>,
    val skipped: Int,
)

fun parseXiaoaiToken(raw: String): XiaoaiToken? {
    val text = raw.trim()
    if (text.isBlank()) return null
    return parseXiaoaiUserInfo(text) ?: parseXiaoaiPcToken(text)
}

internal fun parseXiaoaiUserInfo(raw: String): XiaoaiToken? {
    val text = unwrapQuotedJson(raw.trim())
    if (text.isBlank()) return null
    parseUserInfoJson(text)?.let { return it }
    return parseUserInfoLoose(text)
}

private fun parseXiaoaiPcToken(raw: String): XiaoaiToken? {
    val text = raw.trim()
    val extracted = Regex("""(?:[?&#]token=)([^&#]+)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)
        ?: if (text.contains("://") || text.contains("pceditor", ignoreCase = true)) return null else text
    val token = decodeTokenParam(extracted)
    val decoded = decodeBase64Utf8(token) ?: return null
    val parts = when {
        decoded.contains("%26") -> decoded.split("%26")
        decoded.contains("&") -> decoded.split("&")
        else -> return null
    }
    if (parts.size < 4) return null
    val userId = parts[0].toLongOrNull() ?: return null
    val deviceId = parts[1].ifBlank { return null }
    if (isFakeXiaoaiDevice(deviceId)) return null
    val expireAt = parts[2].toLongOrNull() ?: 0L
    val ctId = parts[3].toLongOrNull() ?: return null
    return XiaoaiToken(userId, deviceId, expireAt, ctId)
}

fun convertQingkeToXiaoai(snapshot: AppSnapshot): XiaoaiConvertResult {
    val official = mutableListOf<XiaoaiOfficialCourse>()
    val api = mutableListOf<XiaoaiApiCourse>()
    var skipped = 0
    snapshot.slots.forEach { slot ->
        val weeks = parseWeekSet(slot.weeks).filter { it in 1..30 }.sorted()
        val sections = parseSections(slot.period).filter { it in 1..30 }
        val day = slot.weekday.takeIf { it in 1..7 }
        val name = clipXiaoai(slot.courseName)
        if (weeks.isEmpty() || sections.isEmpty() || day == null || name.isBlank()) {
            skipped += 1
            return@forEach
        }
        val position = clipXiaoai(slot.room.ifBlank { slot.building })
        val teacher = clipXiaoai(slot.teacher)
        official += XiaoaiOfficialCourse(
            name = name,
            position = position,
            teacher = teacher,
            weeks = weeks,
            day = day,
            sections = sections,
        )
        api += XiaoaiApiCourse(
            name = name,
            position = position,
            teacher = teacher,
            weeks = weeks.joinToString(","),
            day = day,
            sections = sections.joinToString(","),
        )
    }
    val weekCount = snapshot.settings.resolvedWeekCount(snapshot.slots).coerceIn(1, 30)
    val timer = XiaoaiTimer(
        totalWeek = weekCount,
        startSemester = termStartMillis(snapshot.settings.termStart),
        startWithSunday = false,
        showWeekend = official.any { it.day >= 6 },
        forenoon = 6,
        afternoon = 6,
        night = 4,
        sections = if (snapshot.resolved().hasPeriodClock) {
            (1..16).map { index ->
                XiaoaiTimerSection(
                    section = index,
                    startTime = periodStart("$index-$index"),
                    endTime = periodEnd("$index-$index"),
                )
            }
        } else {
            emptyList()
        },
    )
    return XiaoaiConvertResult(
        official = XiaoaiOfficialBundle(courseInfos = official, timer = timer),
        apiCourses = api,
        skipped = skipped + snapshot.practices.size,
    )
}

fun encodeXiaoaiOfficial(bundle: XiaoaiOfficialBundle): String =
    xiaoaiJson.encodeToString(XiaoaiOfficialBundle.serializer(), bundle)

fun XiaoaiToken.authSummary(now: Long = nowMillis()): String = when {
    expired(now) -> "授权已过期，重新复制 UserInfo"
    fromUserInfo && tableName.isNotBlank() -> "已用 UserInfo · $tableName"
    fromUserInfo -> "已用 UserInfo 授权"
    expireAt <= 0L -> "已授权"
    daysLeft(now) <= 1 -> "授权快到期，导入前最好换一份 UserInfo"
    else -> "已授权 · 大约还有 ${daysLeft(now)} 天"
}

fun xiaoaiImportSummary(snapshot: AppSnapshot): String {
    val converted = convertQingkeToXiaoai(snapshot)
    val token = parseXiaoaiToken(snapshot.settings.xiaoaiEditUrl)
    return when {
        converted.apiCourses.isEmpty() -> "先同步课表"
        token == null -> "贴 UserInfo，就能一键导入"
        token.expired() -> "授权过期，点进去换 UserInfo"
        else -> "替换导入 · ${converted.apiCourses.size} 门"
    }
}

private fun parseUserInfoJson(text: String): XiaoaiToken? {
    var current = text
    repeat(2) {
        val element = runCatching { xiaoaiJson.parseToJsonElement(current) }.getOrNull() ?: return@repeat
        val obj = (element as? JsonObject)?.userInfoObject() ?: return@repeat
        val token = tokenFromUserInfo(obj)
        if (token != null) return token
        val nested = obj["userInfo"] ?: obj["data"] ?: obj["user"]
        current = nested?.toString() ?: return null
    }
    return null
}

private fun JsonObject.userInfoObject(): JsonObject {
    listOf("userInfo", "data", "user").forEach { key ->
        val nested = this[key] as? JsonObject
        if (nested != null && (nested.hasUserId() || nested.hasDeviceId())) return nested
    }
    return this
}

private fun JsonObject.hasUserId(): Boolean = longOf("userId", "uid", "user_id") != null
private fun JsonObject.hasDeviceId(): Boolean = !stringOf("deviceId", "device_id", "did", "deviceID").isNullOrBlank()

private fun tokenFromUserInfo(obj: JsonObject): XiaoaiToken? {
    val userId = obj.longOf("userId", "uid", "user_id") ?: 0L
    val deviceId = obj.stringOf("deviceId", "device_id", "did", "deviceID").orEmpty()
    if (deviceId.isBlank() || isFakeXiaoaiDevice(deviceId)) return null
    val table = (obj["currentTable"] as? JsonObject) ?: (obj["table"] as? JsonObject)
    val ctId = obj.longOf("ctId", "ct_id", "tableId", "currentTableId")
        ?: table?.longOf("id", "ctId")
        ?: 0L
    val tableName = obj.stringOf("tableName", "name").orEmpty().ifBlank {
        table?.stringOf("name").orEmpty()
    }
    return XiaoaiToken(
        userId = userId,
        deviceId = deviceId,
        expireAt = 0L,
        ctId = ctId,
        fromUserInfo = true,
        tableName = tableName,
    )
}

private fun parseUserInfoLoose(text: String): XiaoaiToken? {
    val userId = Regex("""(?:userId|uid|user_id)\s*[:=]\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val deviceId = Regex("""(?:deviceId|device_id|did|deviceID)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1).orEmpty()
    if (deviceId.isBlank() || isFakeXiaoaiDevice(deviceId)) return null
    val ctId = Regex("""(?:ctId|ct_id|tableId)\s*[:=]\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return XiaoaiToken(userId, deviceId, 0L, ctId, fromUserInfo = true)
}

private fun unwrapQuotedJson(text: String): String {
    var current = text.trim()
    if (current.startsWith("copy(") && current.endsWith(")")) {
        current = current.removePrefix("copy(").removeSuffix(")").trim()
    }
    repeat(2) {
        if (current.length >= 2 && current.first() == '"' && current.last() == '"') {
            current = runCatching {
                xiaoaiJson.parseToJsonElement(current).jsonPrimitive.content
            }.getOrDefault(current.trim('"'))
        } else {
            return current
        }
    }
    return current
}

private fun isFakeXiaoaiDevice(deviceId: String): Boolean {
    val id = deviceId.trim()
    return id.equals("thisiswebkitdeviceid", ignoreCase = true) ||
        id.equals("d14beca24454faab0aa2a8cd2e076", ignoreCase = true)
}

private fun JsonObject.longOf(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key ->
        val value = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        value.longOrNull ?: value.contentOrNull?.toLongOrNull()
    }

private fun JsonObject.stringOf(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }

private fun parseSections(period: String): List<Int> {
    val start = period.substringBefore("-").filter { it.isDigit() }.toIntOrNull() ?: return emptyList()
    val end = period.substringAfter("-", start.toString()).filter { it.isDigit() }.toIntOrNull() ?: start
    val lo = minOf(start, end)
    val hi = maxOf(start, end)
    return (lo..hi).toList()
}

private fun clipXiaoai(text: String, maxBytes: Int = 50): String {
    val sb = StringBuilder()
    var used = 0
    for (ch in text.trim()) {
        val add = if (ch.code > 127) 2 else 1
        if (used + add > maxBytes) break
        sb.append(ch)
        used += add
    }
    return sb.toString()
}

private fun termStartMillis(termStart: String): String {
    val date = parseIsoDate(termStart)?.let { mondayOf(it) } ?: return ""
    return LocalDateTime(date, LocalTime(0, 0))
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
        .toString()
}

private fun decodeTokenParam(raw: String): String =
    raw.replace("%2B", "+", ignoreCase = true)
        .replace("%2F", "/", ignoreCase = true)
        .replace("%3D", "=", ignoreCase = true)

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64Utf8(token: String): String? {
    val normalized = token.replace('-', '+').replace('_', '/')
    val padded = when (normalized.length % 4) {
        0 -> normalized
        2 -> "$normalized=="
        3 -> "$normalized="
        else -> return null
    }
    return runCatching { Base64.Default.decode(padded).decodeToString() }.getOrNull()
}

private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
