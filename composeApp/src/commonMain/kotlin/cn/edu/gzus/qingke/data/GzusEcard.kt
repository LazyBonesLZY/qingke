package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 广软一卡通的宿舍水电。
 *
 * powerfee/getRoomInfo 和 powerfee/getBalance 这两个是公开的：不带 cookie 也能查，
 * 所以青课不登录也能看水电。只有 waterfee/memberInfo（自动认出本人宿舍）要门户会话，
 * 那部分留在 GzusCasClient 里。
 */
class GzusEcardClient(
    private val client: HttpClient = createBareHttpClient(),
) {
    private var catalog: List<EcardRoomRow> = emptyList()
    private var catalogAt: Long = 0L
    private var catalogLoaded = false

    /**
     * 整个校区的宿舍表有 8000 多条、下载约 3.7MB，所以只在搜索时才要，
     * 而且内存和磁盘都缓存，能不下就不下。
     */
    internal suspend fun rooms(force: Boolean = false): List<EcardRoomRow> {
        if (!force) {
            if (catalog.isNotEmpty() && ecardNowMillis() - catalogAt < ROOM_CACHE_MILLIS) return catalog
            if (!catalogLoaded) {
                catalogLoaded = true
                readRoomCache()?.let { cached ->
                    catalog = cached.rows.map { it.toRow() }
                    catalogAt = cached.fetchedAt
                    if (catalog.isNotEmpty() && ecardNowMillis() - catalogAt < ROOM_CACHE_MILLIS) return catalog
                }
            }
        }
        val rows = fetchRooms()
        if (rows.isNotEmpty()) {
            catalog = rows
            catalogAt = ecardNowMillis()
            writeRoomCache(rows)
        }
        return rows.ifEmpty { catalog }
    }

    internal suspend fun search(query: String, limit: Int = 100): List<EcardRoomRow> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val rows = rooms()
        if (rows.isEmpty()) error("宿舍列表加载失败")
        val needle = foldRoomKey(q)
        return rows.asSequence()
            .filter { it.matches(q) }
            .sortedWith(
                compareBy(
                    { foldRoomKey(it.roomName) != needle },
                    { !foldRoomKey(it.roomName).startsWith(needle) },
                    { it.display.length },
                ),
            )
            .take(limit)
            .toList()
    }

    /** 按绑定的宿舍查余额。返回的 bind 会补上目录里的 id，方便下次直接查。 */
    suspend fun balance(bind: UtilityBind): UtilitySnapshot {
        if (!bind.hasRoom()) return UtilitySnapshot(error = "未绑定宿舍")
        // 绑过一次之后编号都齐了，就不用再拉整份宿舍表。
        val row = if (bind.readyToQuery()) {
            null
        } else {
            runCatching { rooms() }.getOrDefault(emptyList()).firstOrNull { it.matchesBind(bind) }
        }
        val query = bind.mergeWith(row)
        val fields = balanceFields(query)
        if (fields.isEmpty()) {
            return UtilitySnapshot(
                ready = false,
                error = "这个宿舍缺楼栋编号，重新搜索并选一次",
                bind = query,
            )
        }
        var power = ""
        var cold = ""
        var hot = ""
        var refused = ""
        var failure: Throwable? = null
        for (implType in ECARD_IMPL_TYPES) {
            val root = runCatching { post("powerfee/getBalance", fields + ("implType" to implType)) }
                .onFailure { failure = it }
                .getOrNull() ?: continue
            if (ecardDenied(root)) continue
            if (root.ecardStr("ret").equals("false", ignoreCase = true)) {
                // 一卡通自己的话更有用，比如"该房间不存在"。
                refused = refused.ifBlank { root.ecardStr("msg") }
                continue
            }
            val found = pickUtilityTriple(root)
            power = power.ifBlank { found.power }
            cold = cold.ifBlank { found.cold }
            hot = hot.ifBlank { found.hot }
            if (power.isNotBlank() && found.hasWater()) break
        }
        power = power.ifBlank { row?.power.orEmpty() }
        val ready = power.isNotBlank() || cold.isNotBlank() || hot.isNotBlank()
        // 一条都没查成而且是网络问题，就照实说，别糊成"没查到读数"。
        if (!ready) failure?.let { if (isTransientNetwork(it)) throw it }
        return UtilitySnapshot(
            ready = ready,
            building = listOf(query.areaName, query.buildingName).filter { it.isNotBlank() }.joinToString(" "),
            room = listOf(query.floorName, query.roomName).filter { it.isNotBlank() }.joinToString(" "),
            power = power,
            coldWater = cold,
            hotWater = hot,
            error = if (ready) "" else refused.ifBlank { "这个宿舍没有查到水电读数" },
            bind = query,
        )
    }

    private suspend fun fetchRooms(): List<EcardRoomRow> {
        var last: Throwable? = null
        for (implType in ECARD_IMPL_TYPES) {
            val root = runCatching { post("powerfee/getRoomInfo", mapOf("implType" to implType)) }
                .onFailure { last = it }
                .getOrNull() ?: continue
            if (ecardDenied(root)) continue
            val rows = parseRoomRows(root)
            if (rows.isNotEmpty()) return rows
        }
        last?.let { if (isTransientNetwork(it)) throw it }
        return emptyList()
    }

    private fun balanceFields(bind: UtilityBind): Map<String, String> {
        val area = bind.areaId
        val building = bind.buildingId.ifBlank { bind.buildingName }
        val room = bind.roomId.ifBlank { bind.roomName }
        if (area.isBlank() || building.isBlank() || room.isBlank()) return emptyMap()
        return ecardFields(
            "schoolAreaNo" to area,
            "buildingNo" to building,
            "roomNum" to room,
        )
    }

    private suspend fun post(path: String, fields: Map<String, String>): JsonObject {
        val text = client.submitForm(
            url = "$GZUS_ECARD_ORIGIN/$path",
            formParameters = Parameters.build {
                fields.forEach { (key, value) -> if (value.isNotBlank()) append(key, value) }
            },
        ) {
            ecardHeaders(wechat = true)
        }.bodyAsText()
        return parseEcardJson(text)
    }
}

private const val ROOM_CACHE_MILLIS = 7 * 24 * 60 * 60 * 1000L
private const val ROOM_CACHE_FILE = "qingke-ecard-rooms.json"

private val ecardJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 楼栋号和房间号都在手里就能直接查余额。 */
internal fun UtilityBind.readyToQuery(): Boolean =
    areaId.isNotBlank() && buildingId.isNotBlank() && roomId.isNotBlank()

@Serializable
private data class RoomCache(val fetchedAt: Long = 0L, val rows: List<RoomCacheRow> = emptyList())

@Serializable
private data class RoomCacheRow(
    val a: String = "",
    val an: String = "",
    val b: String = "",
    val bn: String = "",
    val r: String = "",
    val rn: String = "",
    val d: String = "",
) {
    fun toRow(): EcardRoomRow = EcardRoomRow(
        areaId = a,
        areaName = an,
        buildingId = b,
        buildingName = bn,
        roomId = r,
        roomName = rn,
        display = d,
    )
}

private fun EcardRoomRow.toCacheRow(): RoomCacheRow = RoomCacheRow(
    a = areaId,
    an = areaName,
    b = buildingId,
    bn = buildingName,
    r = roomId,
    rn = roomName,
    d = display,
)

private fun readRoomCache(): RoomCache? {
    val raw = readStore(ROOM_CACHE_FILE) ?: return null
    return runCatching { ecardJson.decodeFromString(RoomCache.serializer(), raw) }
        .getOrNull()
        ?.takeIf { it.rows.isNotEmpty() }
}

private fun writeRoomCache(rows: List<EcardRoomRow>) {
    runCatching {
        writeStore(
            ROOM_CACHE_FILE,
            ecardJson.encodeToString(
                RoomCache.serializer(),
                RoomCache(fetchedAt = ecardNowMillis(), rows = rows.map { it.toCacheRow() }),
            ),
        )
    }
}

internal val ECARD_IMPL_TYPES = listOf("CGCOMMON1111", "CGCOMMON2222", "CGCOMMON3333")

internal fun HttpRequestBuilder.ecardHeaders(wechat: Boolean = false) {
    header(
        HttpHeaders.UserAgent,
        if (wechat) GZUS_ECARD_WX_UA else QINGKE_UA,
    )
    header(HttpHeaders.Referrer, "$GZUS_ECARD_ORIGIN/")
    header(HttpHeaders.Origin, GZUS_ECARD_ORIGIN)
    header(HttpHeaders.Accept, "application/json, text/plain, */*")
    header("X-Requested-With", "XMLHttpRequest")
}

internal fun parseEcardJson(text: String): JsonObject {
    if (isCasFirstLogin(text) || text.contains("登录失效") || isEcardLoginPage(text)) {
        return JsonObject(mapOf("denied" to JsonPrimitive(true), "message" to JsonPrimitive(text.take(80))))
    }
    val body = text.trimStart()
    if (body.startsWith("<")) return JsonObject(emptyMap())
    if (text.contains("ssoHost")) {
        return JsonObject(mapOf("denied" to JsonPrimitive(true), "message" to JsonPrimitive(text.take(80))))
    }
    return runCatching { ecardJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: JsonObject(emptyMap())
}

internal fun isEcardLoginPage(body: String): Boolean =
    body.contains("登录失效") ||
        (body.contains("lyuapServer/login") && (body.contains("name=\"username\"") || body.contains("id=\"username\"")))

internal fun ecardDenied(root: JsonObject): Boolean {
    if (root["denied"] is JsonPrimitive) return true
    val code = root.ecardStr("code")
    val message = root.ecardStr("msg") + root.ecardStr("message")
    return code == "203" || message.contains("登录失效") || message.contains("未登录")
}

internal fun ecardFields(vararg pairs: Pair<String, String>): Map<String, String> =
    pairs.mapNotNull { (key, value) -> value.trim().takeIf { it.isNotBlank() }?.let { key to it } }.toMap()

internal fun parseRoomRows(root: JsonObject): List<EcardRoomRow> {
    if (ecardDenied(root)) return emptyList()
    return root.ecardObjects().mapNotNull { item ->
        val areaName = item.ecardPick("schoolArea", "areaName", "campus", "xqmc")
        val areaId = item.ecardPick("schoolAreaNo", "areaId", "areaid", "campusId", "xqid").ifBlank { areaName }
        val buildingName = item.ecardPick("building", "buildingName", "loudong")
        val buildingId = item.ecardPick("buildingNo", "buildingId", "buildingid").ifBlank { buildingName }
        val roomName = item.ecardPick("room", "roomDisplay", "roomName", "roomNo")
        val roomId = item.ecardPick("roomNum", "roomId", "roomid", "mdid", "id").ifBlank { roomName }
        val display = item.ecardPick("displayName").ifBlank {
            listOf(areaName, buildingName, roomName.ifBlank { roomId }).filter { it.isNotBlank() }.joinToString(" ")
        }
        if (listOf(areaName, buildingName, roomName, display).all { it.isBlank() }) {
            null
        } else {
            EcardRoomRow(
                areaId = areaId,
                areaName = areaName,
                buildingId = buildingId,
                buildingName = buildingName,
                roomId = roomId,
                roomName = roomName.ifBlank { roomId },
                display = display,
                power = pickUtilityTree(item),
                coldWater = pickColdWaterTree(item),
                hotWater = pickHotWaterTree(item),
            )
        }
    }
}

internal data class EcardRoomRow(
    val areaId: String,
    val areaName: String,
    val buildingId: String,
    val buildingName: String,
    val roomId: String,
    val roomName: String,
    val display: String = "",
    val power: String = "",
    val coldWater: String = "",
    val hotWater: String = "",
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return false
        val fields = listOf(display, areaName, areaId, buildingName, buildingId, roomName, roomId)
        if (fields.any { it.contains(q, ignoreCase = true) }) return true
        val needle = foldRoomKey(q)
        if (needle.isBlank()) return false
        return fields.any { foldRoomKey(it).contains(needle) } ||
            foldRoomKey(buildingName + roomName).contains(needle) ||
            foldRoomKey(display).contains(needle)
    }

    fun matchesBind(bind: UtilityBind): Boolean = matchesRoomBind(
        bind,
        areaId = areaId,
        areaName = areaName,
        buildingId = buildingId,
        buildingName = buildingName,
        roomId = roomId,
        roomName = roomName,
    )

    fun toBind(): UtilityBind = UtilityBind(
        areaId = areaId,
        areaName = areaName,
        buildingId = buildingId,
        buildingName = buildingName.ifBlank { buildingId },
        roomId = roomId.ifBlank { roomName },
        roomName = roomName.ifBlank { roomId },
    )

    fun toOption(): UtilityOption {
        val title = display.ifBlank {
            listOf(areaName, buildingName, roomName.ifBlank { roomId }).filter { it.isNotBlank() }.joinToString(" ")
        }
        return UtilityOption(
            id = roomId.ifBlank { title },
            name = title,
            areaId = areaId,
            areaName = areaName,
            buildingId = buildingId,
            buildingName = buildingName,
            roomId = roomId,
            roomName = roomName.ifBlank { roomId },
        )
    }
}

/** 只补空着的字段，已经填好的（尤其是用户自己选的房间名）不动。 */
internal fun UtilityBind.fillIdsFrom(found: UtilityBind): UtilityBind = UtilityBind(
    areaId = areaId.ifBlank { found.areaId },
    areaName = areaName.ifBlank { found.areaName },
    buildingId = buildingId.ifBlank { found.buildingId },
    buildingName = buildingName.ifBlank { found.buildingName },
    floorId = floorId.ifBlank { found.floorId },
    floorName = floorName.ifBlank { found.floorName },
    roomId = roomId.ifBlank { found.roomId },
    roomName = roomName.ifBlank { found.roomName },
)

/** 目录里有更准的 id 就补上，名字保留用户选的那份。 */
internal fun UtilityBind.mergeWith(row: EcardRoomRow?): UtilityBind {
    if (row == null) return this
    return UtilityBind(
        areaId = areaId.ifBlank { row.areaId },
        areaName = areaName.ifBlank { row.areaName },
        buildingId = buildingId.ifBlank { row.buildingId },
        buildingName = buildingName.ifBlank { row.buildingName },
        floorId = floorId,
        floorName = floorName,
        roomId = roomId.ifBlank { row.roomId },
        roomName = roomName.ifBlank { row.roomName },
    )
}

internal data class UtilityTriple(val power: String, val cold: String, val hot: String) {
    fun hasWater(): Boolean = cold.isNotBlank() || hot.isNotBlank()
}

internal fun pickUtilityTriple(root: JsonObject): UtilityTriple {
    val bags = root.ecardBags() + root.ecardObjects()
    var power = ""
    var cold = ""
    var hot = ""
    for (bag in bags) {
        power = power.ifBlank { pickUtilityTree(bag) }
        cold = cold.ifBlank { pickColdWaterTree(bag) }
        hot = hot.ifBlank { pickHotWaterTree(bag) }
        if (power.isNotBlank() && cold.isNotBlank() && hot.isNotBlank()) break
    }
    return UtilityTriple(power, cold, hot)
}

internal fun pickUtilityTree(obj: JsonObject): String =
    obj.ecardBalance(
        "powerBalance", "formatPowerBalance", "formatPowerBalanceStr", "powerText",
        "remainPower", "utilityElectricity",
    )

internal fun pickColdWaterTree(obj: JsonObject): String =
    obj.ecardBalance(
        "waterBalance", "formatWaterBalanceStr", "coldWaterBalance", "coldWaterText",
        "utilityColdWater", "cold_water",
    )

internal fun pickHotWaterTree(obj: JsonObject): String =
    obj.ecardBalance(
        "hotWaterBalance", "formatHotWaterBalanceStr", "hotWaterText",
        "utilityHotWater", "hot_water",
    )

private fun JsonObject.ecardBalance(vararg keys: String): String =
    ecardPickDeep(*keys).trim().removeSuffix("吨").removeSuffix("度").trim()

internal fun JsonObject.ecardBags(): List<JsonObject> {
    val bags = ArrayList<JsonObject>(6)
    bags.add(this)
    listOf("data", "obj", "result", "info", "member", "user").forEach { key ->
        when (val value = this[key]) {
            is JsonObject -> bags.add(value)
            is JsonArray -> value.mapNotNull { it as? JsonObject }.forEach(bags::add)
            else -> Unit
        }
    }
    return bags
}

internal fun JsonObject.ecardObjects(): List<JsonObject> {
    val bagKeys = listOf(
        "list", "records", "rows", "data", "items", "content", "obj", "result",
        "areas", "buildings", "floors", "rooms", "areaList", "buildingList", "floorList", "roomList",
    )
    val data = this["data"]
    val obj = this["obj"]
    val arrays = buildList {
        when (data) {
            is JsonArray -> add(data)
            is JsonObject -> bagKeys.forEach { key -> (data[key] as? JsonArray)?.let(::add) }
            else -> Unit
        }
        when (obj) {
            is JsonArray -> add(obj)
            is JsonObject -> bagKeys.forEach { key -> (obj[key] as? JsonArray)?.let(::add) }
            else -> Unit
        }
        bagKeys.forEach { key -> (this@ecardObjects[key] as? JsonArray)?.let(::add) }
    }
    val fromArrays = arrays.flatMap { arr -> arr.mapNotNull { it as? JsonObject } }
    if (fromArrays.isNotEmpty()) return fromArrays.distinct()
    val roomMaps = buildList {
        (data as? JsonObject)?.let { addAll(it.values.mapNotNull { v -> v as? JsonObject }.filter { v -> v.looksLikeEcardRoom() }) }
        (obj as? JsonObject)?.let { addAll(it.values.mapNotNull { v -> v as? JsonObject }.filter { v -> v.looksLikeEcardRoom() }) }
    }
    if (roomMaps.isNotEmpty()) return roomMaps.distinct()
    return if (looksLikeEcardRoom()) listOf(this) else emptyList()
}

internal fun JsonObject.looksLikeEcardRoom(): Boolean =
    keys.any {
        it in setOf(
            "areaName", "areaId", "buildingNo", "roomId", "roomName", "roomDisplay",
            "schoolArea", "schoolAreaNo", "roomNum", "displayName",
        )
    }

internal fun JsonObject.toUtilityBind(): UtilityBind {
    val roomId = ecardPickDeep("roomNum", "roomId", "roomNo")
    val roomName = ecardPickDeep("room", "roomDisplay", "roomName").ifBlank { roomId }
    val buildingId = ecardPickDeep("buildingNo", "buildingId")
    val buildingName = ecardPickDeep("building", "buildingName").ifBlank { buildingId }
    val areaId = ecardPickDeep("schoolAreaNo", "areaId", "areaid")
    val areaName = ecardPickDeep("schoolArea", "areaName").ifBlank { areaId }
    return UtilityBind(
        areaId = areaId,
        areaName = areaName,
        buildingId = buildingId,
        buildingName = buildingName,
        roomId = roomId,
        roomName = roomName,
    )
}

internal fun mergeUtilityBind(local: UtilityBind, ecard: UtilityBind): UtilityBind {
    if (!ecard.hasRoom()) return local
    return UtilityBind(
        areaId = ecard.areaId.ifBlank { local.areaId },
        areaName = ecard.areaName.ifBlank { local.areaName },
        buildingId = ecard.buildingId.ifBlank { local.buildingId },
        buildingName = ecard.buildingName.ifBlank { local.buildingName },
        roomId = ecard.roomId.ifBlank { local.roomId },
        roomName = ecard.roomName.ifBlank { local.roomName }.ifBlank { ecard.roomId },
    )
}

internal fun foldRoomKey(text: String): String = buildString(text.length) {
    for (ch in text) {
        val mapped = when (ch) {
            in 'Ａ'..'Ｚ' -> 'A' + (ch - 'Ａ')
            in 'ａ'..'ｚ' -> 'a' + (ch - 'ａ')
            in '０'..'９' -> '0' + (ch - '０')
            else -> ch
        }
        if (mapped.isLetterOrDigit()) append(mapped.lowercaseChar())
    }
}

private fun sameRoomToken(token: String, roomId: String, roomName: String): Boolean {
    if (token == roomId || token == roomName) return true
    val folded = foldRoomKey(token)
    if (folded.isNotBlank() && listOf(roomId, roomName).any { foldRoomKey(it) == folded }) return true
    return token.length >= 3 && (roomName.contains(token, ignoreCase = true) || token.contains(roomName, ignoreCase = true))
}

private fun sameBindId(bindVal: String, a: String, b: String): Boolean =
    bindVal.isBlank() || bindVal == a || bindVal == b ||
        foldRoomKey(bindVal).let { it.isNotBlank() && (it == foldRoomKey(a) || it == foldRoomKey(b)) }

internal fun matchesRoomBind(
    bind: UtilityBind,
    areaId: String,
    areaName: String,
    buildingId: String,
    buildingName: String,
    roomId: String,
    roomName: String,
): Boolean {
    val roomOk = listOf(bind.roomId, bind.roomName).any { token ->
        token.isNotBlank() && sameRoomToken(token, roomId, roomName)
    }
    if (!roomOk) return false
    return sameBindId(bind.buildingId, buildingId, buildingName) &&
        sameBindId(bind.buildingName, buildingId, buildingName) &&
        sameBindId(bind.areaId, areaId, areaName) &&
        sameBindId(bind.areaName, areaId, areaName)
}

internal fun JsonObject.ecardPick(vararg keys: String): String {
    for (key in keys) {
        val text = (this[key] as? JsonPrimitive)?.contentOrNullCompat()?.trim().orEmpty()
        if (text.isNotBlank() && text != "null") return text
    }
    return ""
}

internal fun JsonObject.ecardPickDeep(vararg keys: String): String {
    ecardPick(*keys).takeIf { it.isNotBlank() }?.let { return it }
    for ((_, value) in this) {
        when (value) {
            is JsonObject -> value.ecardPickDeep(*keys).takeIf { it.isNotBlank() }?.let { return it }
            is JsonArray -> for (el in value) {
                (el as? JsonObject)?.ecardPickDeep(*keys)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            else -> Unit
        }
    }
    return ""
}

internal fun JsonObject.ecardStr(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNullCompat()?.trim().orEmpty()

private fun JsonPrimitive.contentOrNullCompat(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private fun ecardNowMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
