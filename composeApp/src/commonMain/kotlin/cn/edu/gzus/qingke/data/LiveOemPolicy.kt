package cn.edu.gzus.qingke.data

import kotlinx.serialization.json.*

internal data class OppoFluidCloudConfig(
    val intentName: String,
    val intentVersion: String,
    val serviceIds: List<String>,
    val upcomingMilestoneCode: Int,
    val ongoingMilestoneCode: Int,
    val endedMilestoneCode: Int,
) {
    val complete: Boolean get() = intentName.isNotBlank() && intentVersion.isNotBlank() &&
        serviceIds.isNotEmpty() && serviceIds.all { it.isNotBlank() } &&
        minOf(upcomingMilestoneCode, ongoingMilestoneCode, endedMilestoneCode) >= 0
}

/** Caller serializes access. A provider response may affect only the frame that requested it. */
internal class LiveOemLifecycle {
    data class Ticket(val generation: Long, val key: String, val deadlineMillis: Long)
    private var generation = 0L
    private var current: Ticket? = null

    fun begin(key: String, deadlineMillis: Long): Ticket =
        Ticket(++generation, key, deadlineMillis).also { current = it }

    fun cancel() { current = null; generation++ }

    fun isCurrent(ticket: Ticket, nowMillis: Long): Boolean =
        ticket == current && nowMillis < ticket.deadlineMillis
}

/** T/TAF 283-2025 queryFeature: an absent or malformed grant never authorizes a share. */
internal fun oppoFeaturesAllowShare(response: String?, config: OppoFluidCloudConfig): Boolean = runCatching {
    if (!config.complete || response == null) return false
    val root = Json.parseToJsonElement(response).jsonObject
    if (root["code"]?.jsonPrimitive?.intOrNull != 0) return false
    val raw = root["data"]
    val data = if (raw is JsonObject) raw else Json.parseToJsonElement(raw!!.jsonPrimitive.content).jsonObject
    fun granted(value: JsonElement?): Boolean = value is JsonPrimitive && !value.isString && value.booleanOrNull == true
    if (!granted(data["enableIntelligentIntent"]) || !granted(data["userAcceptShareIntent"])) return false
    data["querySupportIntent"]!!.jsonArray.any { entry ->
        val item = entry.jsonObject
        item["intentName"]?.jsonPrimitive?.content == config.intentName && granted(item["support"]) &&
            item["version"]!!.jsonArray.any { it.jsonPrimitive.content == config.intentVersion }
    }
}.getOrDefault(false)

/** Build the local protocol; cloud push uses a different serviceId shape. */
internal fun buildOppoLiveIntent(
    config: OppoFluidCloudConfig,
    packageName: String,
    identifier: String,
    entityId: String,
    title: String,
    detail: String,
    startMillis: Long,
    endMillis: Long,
    nowMillis: Long,
    actionStatus: Int,
    countdownEnabled: Boolean,
): String {
    require(config.complete)
    require(actionStatus in 0..2)
    val timing = requireNotNull(liveTiming(nowMillis, startMillis, endMillis))
    val capsule = if (countdownEnabled) timing.countdownText else timing.statusText
    return buildJsonObject {
        put("intentName", config.intentName)
        put("identifier", identifier)
        put("timestamp", nowMillis)
        putJsonArray("serviceId") { config.serviceIds.forEach { add(it) } }
        putJsonObject("intentAction") {
            put("actionType", "fact")
            putJsonObject("actionTime") { put("startTime", startMillis); put("endTime", endMillis) }
            put("actionStatus", actionStatus)
        }
        putJsonObject("intentEntity") {
            put("entityName", "TASK"); put("entityId", entityId); put("isPublic", false)
            putJsonObject("milestone") {
                put("code", if (timing.inClass) config.ongoingMilestoneCode else config.upcomingMilestoneCode)
                put("text", if (timing.inClass) "in_progress" else "upcoming")
            }
            putJsonObject("capsule") {
                val icon = "android://$packageName/res/drawable/ic_stat_live"
                put("leftImg", icon); put("rightText", capsule)
                put("legacyImg", icon); put("legacyText", capsule)
            }
            putJsonObject("primary") {
                putJsonArray("title") {
                    add(buildJsonObject {
                        put("text", title.take(80)); put("color", "#3482FF"); put("darkColor", "#8AB4FF")
                    })
                }
                put("content", detail.take(120))
            }
            putJsonObject("secondaryData") {
                put("type", "PROGRESS"); put("progress", timing.progressPercent); put("style", "inside")
                putJsonArray("nodeLabels") { add("课前"); add("上课中"); add("下课") }
            }
        }
        putJsonObject("extra") { put("source", "qingke"); put("milestoneEndedCode", config.endedMilestoneCode) }
        put("isSensitive", false)
    }.toString()
}
