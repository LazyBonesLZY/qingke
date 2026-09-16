package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

class GzusCasClient(
    private val client: HttpClient = createHttpClient(),
    private val jwxt: JwxtClient = JwxtClient(origin = JWXT_ORIGIN, client = client),
) : SchoolPortal by jwxt {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cas = GZUS_CAS_ORIGIN.trimEnd('/')
    private val ehall = GZUS_EHALL_ORIGIN.trimEnd('/')

    override suspend fun fetchCaptcha(): LoginCaptcha? {
        client.get("$cas/login") {
            header(HttpHeaders.UserAgent, UA)
            parameter("service", GZUS_JWXT_SSO_SERVICE)
        }
        val text = client.get("$cas/kaptcha") {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$cas/login?service=$GZUS_JWXT_SSO_SERVICE")
        }.bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        val uid = root.lyuapStr("uid")
        val bytes = decodeLyuapCaptcha(root.lyuapStr("content"))
        if (uid.isBlank() || bytes.isEmpty()) return null
        return LoginCaptcha(id = uid, bytes = bytes, hint = "算术题得数")
    }

    override suspend fun login(
        studentId: String,
        password: String,
        captcha: String,
        captchaId: String,
    ): Result<Unit> = runCatching {
        if (captcha.isBlank() || captchaId.isBlank()) error("统一身份认证要先填验证码")
        val encrypted = lyuapEncrypt(password, LYUAP_MOD, LYUAP_EXP)
        val raw = client.submitForm(
            url = "$cas/v1/tickets",
            formParameters = Parameters.build {
                append("username", studentId)
                append("password", encrypted)
                append("service", GZUS_JWXT_SSO_SERVICE)
                append("loginType", "")
                append("id", captchaId)
                append("code", captcha.trim())
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$cas/login?service=$GZUS_JWXT_SSO_SERVICE")
            header(HttpHeaders.Origin, "https://cas.gzus.edu.cn")
        }.bodyAsText()
        if (isCasFirstLogin(raw)) throw JwxtNeedFirstLogin()
        val tickets = parseCasTickets(raw, json)
        consumeJwxtTicket(tickets.serviceTicket)
        val tgt = tickets.ticketGrantingTicket.ifBlank { tgtFromCookies() }
        if (tgt.startsWith("TGT-")) {
            runCatching { openEhall(tgt) }.onFailure { error ->
                if (error is JwxtNeedFirstLogin) throw error
            }
            runCatching { openEcard(tgt) }.onFailure { error ->
                if (error is JwxtNeedFirstLogin) throw error
            }
        }
    }

    override suspend fun fetchHall(): HallSnapshot {
        runCatching { tryLoginEhall() }
        val userName = runCatching { hallUserName() }.getOrDefault("")
        val pending = runCatching { hallTodos("api/bpm/processes/tasks/pending", "待办") }.getOrDefault(emptyList())
        val apply = runCatching { hallTodos("api/bpm/processes/tasks/apply", "申请") }.getOrDefault(emptyList())
        val messages = runCatching { hallMessages() }.getOrDefault(emptyList())
        val news = runCatching { hallNews("api/global/user/news") }.getOrDefault(emptyList())
        val announcement = runCatching { hallNews("api/global/user/announcement") }.getOrDefault(emptyList())
        val often = runCatching { hallAffairs("api/affair/uis/affairs/often") }.getOrDefault(emptyList())
        val all = runCatching { hallAffairs("api/affair/uis/affairs") }.getOrDefault(emptyList())
        val events = runCatching { hallEvents() }.getOrDefault(emptyList())
        val todos = (pending + apply).distinctBy { it.id }
        val mail = (messages + news + announcement).distinctBy { it.id }
        val apps = (often + all).distinctBy { it.id }.sortedByDescending { it.isLeave() }
        val applyLeaves = runCatching { hallLeaves("api/bpm/processes/tasks/apply", "申请中") }.getOrDefault(emptyList())
        val draftLeaves = runCatching { hallLeaves("api/bpm/processes/tasks/draft", "草稿") }.getOrDefault(emptyList())
        val doneLeaves = runCatching { hallLeaves("api/bpm/processes/tasks/done", "已结束") }.getOrDefault(emptyList())
        val pendingLeaves = pending.filter { it.isLeave() }.map { it.toLeaveApplication("待办") }
        val leaves = (applyLeaves + draftLeaves + doneLeaves + pendingLeaves).distinctBy { it.id }
        val ready = userName.isNotBlank() || todos.isNotEmpty() || mail.isNotEmpty() || apps.isNotEmpty() || events.isNotEmpty() || leaves.isNotEmpty()
        return HallSnapshot(
            ready = ready,
            userName = userName,
            todos = todos,
            messages = mail,
            affairs = apps,
            events = events,
            leaves = leaves,
            error = if (ready) "" else "这次门户没给办事大厅票据，重新用统一身份认证登录一次",
        )
    }

    override suspend fun fetchLeaveForm(affairId: String): LeaveForm {
        runCatching { tryLoginEhall() }
        val affairs = runCatching { hallAffairs("api/affair/uis/affairs") }.getOrDefault(emptyList()) +
            runCatching { hallAffairs("api/affair/uis/affairs/often") }.getOrDefault(emptyList())
        val leave = affairs.firstOrNull { it.id == affairId && affairId.isNotBlank() }
            ?: affairs.firstOrNull { it.isLeave() }
        val id = leave?.id?.ifBlank { affairId }.orEmpty().ifBlank { affairId }
        if (id.isBlank()) error("办事大厅里还没有请假事项。用统一身份认证登录后再同步一次。")
        val build = hallGetFirst(
            listOf(
                "api/affair/user/affairs/build" to mapOf("id" to id),
                "api/affair/user/affairs/build" to mapOf("affairId" to id),
                "api/affair/user/affairs/build" to mapOf("affairID" to id),
                "api/affair/user/affairs/build/type" to mapOf("id" to id, "affairId" to id),
            ),
        )
        val bag = build?.hallBag() ?: JsonObject(emptyMap())
        val processId = bag.pick("processId", "processDefId", "procDefId", "procId")
        val taskId = bag.pick("taskId", "orunid", "docUnid")
        val cardId = bag.pick("cardId", "formId", "formKey", "cardInfoId")
        var fields = parseLeaveFields(bag)
        if (fields.isEmpty() && cardId.isNotBlank()) {
            fields = parseLeaveFields(hallGet("api/pas/cardInfos/selectCardById/$cardId"))
            if (fields.isEmpty()) {
                fields = parseLeaveFields(hallGet("api/pas/fields", mapOf("cardId" to cardId, "id" to cardId)))
            }
            val defaults = runCatching {
                hallGet("api/pas/defaultFields/getFields", mapOf("cardId" to cardId, "id" to cardId))
            }.getOrNull()
            if (defaults != null) fields = mergeLeaveDefaults(fields, defaults)
        }
        return LeaveForm(
            affairId = id,
            affairName = leave?.name?.ifBlank { "请假" } ?: "请假",
            processId = processId,
            taskId = taskId,
            cardId = cardId,
            fields = mergeLeaveFields(fields),
        )
    }

    override suspend fun submitLeave(form: LeaveForm, values: Map<String, String>): String {
        runCatching { tryLoginEhall() }
        val filled = values.filterValues { it.isNotBlank() }
        if (filled.isEmpty()) error("先填请假时间和事由")
        val root = hallPost("api/bpm/processes/tasks/run", leaveRunBody(form, filled))
        hallFailMessage(root)?.let { error(it) }
        val message = (root["meta"] as? JsonObject)?.lyuapStr("message")
            .orEmpty()
            .ifBlank { root.lyuapStr("message") }
            .ifBlank { root.lyuapStr("msg") }
        return message.ifBlank { "请假已提交" }
    }

    override suspend fun fetchUtility(bind: UtilityBind): UtilitySnapshot {
        ensureEcard()
        val member = runCatching { ecardJson("waterfee/memberInfo", "{}") }.getOrNull()
        if (member != null && ecardDenied(member)) {
            return UtilitySnapshot(error = "一卡通没登录上。用统一身份认证再登一次")
        }
        val bag = member?.hallBag() ?: JsonObject(emptyMap())
        val buildingName = bind.buildingName.ifBlank {
            bag.pick("buildingNo", "building", "buildingName", "loudong", "areaName")
        }
        val roomName = bind.roomName.ifBlank {
            bag.pick("roomNo", "room", "roomName", "roomId", "ssbh")
        }
        var power = bag.pick("powerBalance", "remainPower", "electricity", "power", "elecBalance", "surplus")
        var cold = bag.pick("coldWaterBalance", "coldWater", "cold", "waterBalance")
        var hot = bag.pick("hotWaterBalance", "hotWater", "hot")
        if (power.isBlank() || (cold.isBlank() && hot.isBlank())) {
            val payload = buildEcardRoomBody(bind, bag)
            if (power.isBlank()) {
                val info = runCatching { ecardJson("powerfee/getRoomInfo", payload) }.getOrNull()?.hallBag()
                val balance = runCatching { ecardJson("powerfee/getBalance", payload) }.getOrNull()?.hallBag()
                power = power.ifBlank {
                    listOf(info, balance).firstNotNullOfOrNull { obj ->
                        obj?.pick("powerBalance", "remainPower", "electricity", "power", "surplus", "mdye", "remain")
                    }.orEmpty()
                }
            }
            if (cold.isBlank() && hot.isBlank()) {
                val water = runCatching { ecardJson("waterfee/getBalance", payload) }.getOrNull()?.hallBag()
                cold = cold.ifBlank { water?.pick("coldWaterBalance", "coldWater", "cold", "waterBalance").orEmpty() }
                hot = hot.ifBlank { water?.pick("hotWaterBalance", "hotWater", "hot").orEmpty() }
            }
        }
        val ready = power.isNotBlank() || cold.isNotBlank() || hot.isNotBlank()
        return UtilitySnapshot(
            ready = ready,
            building = listOf(bind.areaName, buildingName).filter { it.isNotBlank() }.joinToString(" "),
            room = listOf(bind.floorName, roomName).filter { it.isNotBlank() }.joinToString(" "),
            power = power,
            coldWater = cold,
            hotWater = hot,
            error = if (ready) "" else "宿舍水电还没查到。在水电页按校区、楼栋、楼层、房间选一次",
        )
    }

    override suspend fun fetchUtilityOptions(level: String, parentId: String): List<UtilityOption> {
        ensureEcard()
        val path = when (level) {
            "building" -> "powerfee/queryBuilding"
            "floor" -> "powerfee/queryFloor"
            "room" -> "powerfee/queryRoom"
            else -> "powerfee/queryArea"
        }
        val keys = when (level) {
            "building" -> listOf("areaid", "areaId", "aid", "id")
            "floor" -> listOf("buildingid", "buildingId", "id")
            "room" -> listOf("floorid", "floorId", "id")
            else -> emptyList()
        }
        val extra = if (parentId.isBlank() || keys.isEmpty()) emptyMap() else keys.associateWith { parentId }
        val first = ecardQuery(path, extra)
        if (first.isNotEmpty()) return first
        val alt = path.replace("powerfee/", "waterfee/")
        return if (alt != path) ecardQuery(alt, extra) else emptyList()
    }

    private suspend fun ensureEcard() {
        val tgt = tgtFromCookies()
        if (tgt.startsWith("TGT-")) {
            runCatching { openEcard(tgt) }.onFailure { error ->
                if (error is JwxtNeedFirstLogin) throw error
            }
        }
    }

    override suspend fun logout() {
        runCatching { jwxt.logout() }
        runCatching {
            client.get("$ehall/logout") { header(HttpHeaders.UserAgent, UA) }
        }
        runCatching {
            client.get("$cas/logout") { header(HttpHeaders.UserAgent, UA) }
        }
    }

    private suspend fun consumeJwxtTicket(ticket: String) {
        val jump = client.get(GZUS_JWXT_SSO_SERVICE) {
            header(HttpHeaders.UserAgent, UA)
            parameter("ticket", ticket)
        }
        val body = jump.bodyAsText()
        val url = jump.request.url.toString()
        if (isPasswordChangePage(body, url)) throw JwxtNeedFirstLogin()
        if (isLoginForm(body) || url.contains("login_slogin")) {
            error(loginFormTip(body).ifBlank { "门户登录了，教务还没放行。再试一次，或打开官方门户。" })
        }
        if (!url.contains("initMenu") && !body.contains("index_initMenu") && !body.contains("gnmkdm")) {
            val home = client.get("$JWXT_ORIGIN/jwglxt/xtgl/index_initMenu.html") {
                header(HttpHeaders.UserAgent, UA)
                parameter("jsdm", "xs")
            }
            val homeBody = home.bodyAsText()
            val homeUrl = home.request.url.toString()
            if (isPasswordChangePage(homeBody, homeUrl)) throw JwxtNeedFirstLogin()
            if (isLoginForm(homeBody)) error("门户登录了，教务会话没建起来")
        }
    }

    private suspend fun openEhall(tgt: String) {
        val raw = client.submitForm(
            url = "$cas/v1/tickets/$tgt",
            formParameters = Parameters.build {
                append("service", GZUS_EHALL_CAS_SERVICE)
                append("loginToken", "loginToken")
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$cas/login")
            header(HttpHeaders.Origin, "https://cas.gzus.edu.cn")
        }.bodyAsText()
        if (isCasFirstLogin(raw)) throw JwxtNeedFirstLogin()
        val st = parseServiceTicket(raw)
        val jump = client.get(GZUS_EHALL_CAS_SERVICE) {
            header(HttpHeaders.UserAgent, UA)
            parameter("ticket", st)
        }
        if (isCasFirstLogin(jump.bodyAsText(), jump.request.url.toString())) throw JwxtNeedFirstLogin()
        tryLoginEhall()
    }

    private suspend fun openEcard(tgt: String) {
        val service = gzusEcardCasService()
        val raw = client.submitForm(
            url = "$cas/v1/tickets/$tgt",
            formParameters = Parameters.build {
                append("service", service)
                append("loginToken", "loginToken")
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$cas/login")
            header(HttpHeaders.Origin, "https://cas.gzus.edu.cn")
        }.bodyAsText()
        if (isCasFirstLogin(raw)) throw JwxtNeedFirstLogin()
        val st = parseServiceTicket(raw)
        val jump = client.get(service) {
            header(HttpHeaders.UserAgent, UA)
            parameter("ticket", st)
        }
        val url = jump.request.url.toString()
        val body = jump.bodyAsText()
        if (isCasFirstLogin(body, url)) throw JwxtNeedFirstLogin()
    }

    private fun tgtFromCookies(): String =
        currentCookies()
            .map { it.second.trim() }
            .firstOrNull { it.startsWith("TGT-") }
            .orEmpty()

    private fun parseServiceTicket(text: String): String {
        val trimmed = text.trim().trim('"')
        if (trimmed.startsWith("ST-")) return trimmed
        return parseCasTickets(trimmed, json).serviceTicket
    }

    private suspend fun tryLoginEhall() {
        hallPost("tryLoginUserInfo", """{"version":"2","loadType":"6"}""")
    }

    private suspend fun hallUserName(): String {
        val root = hallGet("api/authc/users/name")
        if (hallDenied(root)) return ""
        val data = root["data"]
        return when (data) {
            is JsonPrimitive -> data.contentOrNull?.trim().orEmpty()
            is JsonObject -> data.pick("userName", "name", "realName", "nickName", "username")
            else -> root.pick("userName", "name", "realName")
        }
    }

    private suspend fun hallLeaves(path: String, fallbackStatus: String): List<LeaveApplication> =
        hallList(path, mapOf("pageNum" to "1", "pageSize" to "50")).mapNotNull { item ->
            val title = item.pick("subject", "title", "processName", "affairName", "name")
            val extra = item.pick("processName", "affairName", "typeName", "category")
            if (title.isBlank()) return@mapNotNull null
            if (!isLeaveText(title) && !isLeaveText(extra) && item.pick("KSSJ", "QJLY", "kssj", "qjly").isBlank()) {
                return@mapNotNull null
            }
            item.toLeaveApplication(fallbackStatus)
        }

    private suspend fun hallGetFirst(requests: List<Pair<String, Map<String, String>>>): JsonObject? {
        for ((path, extra) in requests) {
            val root = runCatching { hallGet(path, extra) }.getOrNull() ?: continue
            if (hallDenied(root) || hallFailMessage(root) != null) continue
            val bag = root.hallBag()
            if (bag.isNotEmpty() || root.hallObjects().isNotEmpty()) return root
        }
        return null
    }

    private fun parseLeaveFields(root: JsonObject): List<LeaveField> {
        val bags = listOf(root, root.hallBag())
        val arrays = bags.flatMap { obj ->
            listOf("fields", "formFields", "list", "records", "rows", "items", "controls", "columns", "fieldList", "formList")
                .mapNotNull { key -> obj[key] as? JsonArray }
        }
        val items = arrays.flatMap { arr -> arr.mapNotNull { it as? JsonObject } }
            .ifEmpty { root.hallObjects() }
        return items.mapNotNull { item ->
            val key = item.pick("name", "fieldName", "fieldCode", "code", "id", "key", "columnName", "field")
            val label = item.pick("label", "title", "comment", "fieldLabel", "caption")
            if (key.isBlank() || !key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,31}"))) return@mapNotNull null
            LeaveField(
                key = key,
                label = label.ifBlank { key },
                type = item.pick("type", "fieldType", "controlType", "dataType").ifBlank { "text" },
                required = item.pick("required", "mustFill", "isRequired") in listOf("true", "1", "Y", "yes") ||
                    item.pick("nullable") in listOf("false", "0", "N"),
                value = item.pick("value", "defaultValue", "defaultVal"),
                options = parseFieldOptions(item),
            )
        }.distinctBy { it.key }
    }

    private fun parseFieldOptions(item: JsonObject): List<String> {
        val raw = item["options"] ?: item["dicts"] ?: item["list"]
        return when (raw) {
            is JsonArray -> raw.mapNotNull {
                when (it) {
                    is JsonPrimitive -> it.contentOrNull?.trim()?.takeIf { text -> text.isNotBlank() }
                    is JsonObject -> it.pick("label", "name", "text", "value").takeIf { text -> text.isNotBlank() }
                    else -> null
                }
            }
            is JsonPrimitive -> raw.contentOrNull
                ?.split(',', ';', '|', '/', '、')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            else -> emptyList()
        }
    }

    private fun mergeLeaveDefaults(fields: List<LeaveField>, defaults: JsonObject): List<LeaveField> {
        val bag = defaults.hallBag()
        return fields.map { field ->
            val value = bag.pick(field.key).ifBlank { defaults.pick(field.key) }
            if (value.isBlank()) field else field.copy(value = value)
        }
    }

    private fun mergeLeaveFields(found: List<LeaveField>): List<LeaveField> {
        val defaults = defaultLeaveFields()
        val useful = found.filter { field ->
            val key = field.key.uppercase()
            val label = field.label
            key in setOf("KSSJ", "JSSJ", "QJLY", "QJLX") ||
                label.contains("请假") || label.contains("开始") || label.contains("结束") ||
                label.contains("事由") || label.contains("类型") ||
                label.contains("老师") || label.contains("辅导员") || label.contains("审批")
        }
        val source = useful.ifEmpty { found.take(12) }
        if (source.isEmpty()) return defaults
        val keys = source.map { it.key.uppercase() }.toSet()
        return source + defaults.filter { it.key.uppercase() !in keys }
    }

    private fun leaveRunBody(form: LeaveForm, values: Map<String, String>): String {
        val data = buildJsonObject {
            values.forEach { (key, value) -> put(key, value) }
        }
        return buildJsonObject {
            if (form.affairId.isNotBlank()) put("affairId", form.affairId)
            if (form.processId.isNotBlank()) {
                put("processId", form.processId)
                put("processDefId", form.processId)
            }
            if (form.taskId.isNotBlank()) {
                put("taskId", form.taskId)
                put("id", form.taskId)
                put("orunid", form.taskId)
            }
            if (form.cardId.isNotBlank()) put("cardId", form.cardId)
            put("action", "submit")
            put("formData", data)
            put("variables", data)
            put("data", data)
            values.forEach { (key, value) -> put(key, value) }
        }.toString()
    }

    private fun hallFailMessage(root: JsonObject): String? {
        if (hallDenied(root)) return "大厅会话失效，重新用统一身份认证登录"
        val meta = root["meta"] as? JsonObject
        val success = meta?.get("success") ?: root["success"]
        val failed = when (success) {
            is JsonPrimitive -> {
                val text = success.contentOrNull?.trim().orEmpty()
                text == "false" || text == "0"
            }
            else -> false
        }
        if (failed) {
            return meta?.lyuapStr("message").orEmpty()
                .ifBlank { root.lyuapStr("message") }
                .ifBlank { root.lyuapStr("msg") }
                .ifBlank { "提交失败" }
        }
        val code = root.lyuapStr("code")
        if (code.isNotBlank() && code != "0" && code != "200" && !code.equals("success", true)) {
            return root.lyuapStr("message").ifBlank { root.lyuapStr("msg") }.ifBlank { "提交失败 $code" }
        }
        return null
    }

    private suspend fun hallTodos(path: String, fallbackStatus: String): List<HallTodo> =
        hallList(path).mapNotNull { item ->
            val title = item.pick("subject", "title", "processName", "affairName", "name")
            if (title.isBlank()) return@mapNotNull null
            HallTodo(
                id = item.pick("id", "taskId", "processId", "uid").ifBlank { title },
                title = title,
                time = item.pick("createTime", "startTime", "sendTime", "time", "applyTime"),
                status = item.pick("status", "taskStatus", "state", "processStatus").ifBlank { fallbackStatus },
                url = hallAbs(item.pick("pcUrl", "appUrl", "url", "formUrl", "mobileUrl"))
                    .ifBlank { "$GZUS_EHALL_ORIGIN/#/processcard" },
            )
        }

    private suspend fun hallMessages(): List<HallMessage> =
        hallList("api/message/uis/messages").mapNotNull { item ->
            val title = item.pick("title", "subject", "name")
            if (title.isBlank()) return@mapNotNull null
            HallMessage(
                id = item.pick("id", "messageId", "uid").ifBlank { title },
                title = title,
                time = item.pick("createTime", "sendTime", "time", "publishTime"),
                content = item.pick("content", "msgContent", "text", "summary"),
            )
        }

    private suspend fun hallNews(path: String): List<HallMessage> =
        hallList(path).mapNotNull { item ->
            val title = item.pick("title", "subject", "name")
            if (title.isBlank()) return@mapNotNull null
            HallMessage(
                id = item.pick("id", "newsId", "uid").ifBlank { title },
                title = title,
                time = item.pick("publishTime", "createTime", "time"),
                content = item.pick("content", "summary", "text"),
            )
        }

    private suspend fun hallAffairs(path: String): List<HallAffair> =
        hallList(path, mapOf("pageNum" to "1", "pageSize" to "50", "appId" to "LYGTC")).mapNotNull { item ->
            val name = item.pick("name", "affairName", "appName", "title")
            if (name.isBlank()) return@mapNotNull null
            val id = item.pick("id", "affairId", "appId").ifBlank { name }
            val type = item.pick("typeName", "type", "category", "tagName")
            val leave = name.contains("请假") || name.contains("销假") || type.contains("请假")
            val fallback = if (leave) {
                "$GZUS_EHALL_ORIGIN/#/affairs/allAffairs/guide/$id"
            } else {
                GZUS_EHALL_LEAVE
            }
            HallAffair(
                id = id,
                name = name,
                type = type,
                url = hallAbs(item.pick("pcUrl", "appUrl", "url", "path")).ifBlank { fallback },
                kind = if (leave) "leave" else "",
            )
        }

    private suspend fun hallEvents(): List<HallEvent> =
        hallList("api/schedule/schedule/calendar").mapNotNull { item ->
            val title = item.pick("title", "name", "subject")
            if (title.isBlank()) return@mapNotNull null
            HallEvent(
                id = item.pick("id", "scheduleId", "uid").ifBlank { title },
                title = title,
                time = listOf(
                    item.pick("startTime", "beginTime", "time"),
                    item.pick("endTime"),
                ).filter { it.isNotBlank() }.joinToString(" ~ "),
                place = item.pick("address", "place", "location", "room"),
            )
        }

    private suspend fun hallList(path: String, extra: Map<String, String> = mapOf("pageNum" to "1", "pageSize" to "20")): List<JsonObject> {
        val root = hallGet(path, extra)
        if (hallDenied(root)) return emptyList()
        return root.hallObjects()
    }

    private suspend fun hallGet(path: String, extra: Map<String, String> = emptyMap()): JsonObject {
        val first = hallGetOnce(path, extra, csrf = true)
        return if (hallDenied(first) || first.isEmpty()) hallGetOnce(path, extra, csrf = false) else first
    }

    private suspend fun hallGetOnce(path: String, extra: Map<String, String>, csrf: Boolean): JsonObject {
        val text = client.get("$ehall/$path") {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$ehall/")
            header("X-Requested-With", "XMLHttpRequest")
            if (csrf) {
                val (ts, token) = ehallCsrf()
                parameter("csrfTimestamp", ts)
                parameter("csrfToken", token)
            }
            extra.forEach { (key, value) -> parameter(key, value) }
        }.bodyAsText()
        return parseHallJson(text)
    }

    private suspend fun hallPost(path: String, body: String): JsonObject {
        val (ts, token) = ehallCsrf()
        val text = client.post("$ehall/$path") {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$ehall/")
            header("X-Requested-With", "XMLHttpRequest")
            contentType(ContentType.Application.Json)
            parameter("csrfTimestamp", ts)
            parameter("csrfToken", token)
            setBody(body)
        }.bodyAsText()
        return parseHallJson(text)
    }

    private fun parseHallJson(text: String): JsonObject {
        if (text.contains("未登录") || text.contains("lyuapServer/login")) {
            return JsonObject(mapOf("denied" to JsonPrimitive(true)))
        }
        return runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())
    }

    private fun hallDenied(root: JsonObject): Boolean {
        if (root["denied"] is JsonPrimitive) return true
        val message = (root["meta"] as? JsonObject)?.lyuapStr("message").orEmpty() +
            root.lyuapStr("message") +
            root.lyuapStr("msg")
        return message.contains("未登录") || message.contains("登录") && message.contains("过期")
    }

    private fun ehallCsrf(): Pair<String, String> {
        val ts = Clock.System.now().toEpochMilliseconds().toString()
        return ts to md5Hex("timestamp=$ts,key=lianyi2019")
    }

    private suspend fun ecardJson(path: String, body: String): JsonObject {
        val text = client.post("$GZUS_ECARD_ORIGIN/$path") {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$GZUS_ECARD_ORIGIN/")
            header("X-Requested-With", "XMLHttpRequest")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
        if (text.contains("登录失效") || text.contains("ssoHost") || isCasFirstLogin(text)) {
            return JsonObject(mapOf("denied" to JsonPrimitive(true), "message" to JsonPrimitive(text.take(80))))
        }
        return parseHallJson(text)
    }

    private fun ecardDenied(root: JsonObject): Boolean {
        if (root["denied"] is JsonPrimitive) return true
        val code = root.lyuapStr("code")
        val message = root.lyuapStr("msg") + root.lyuapStr("message")
        return code == "203" || message.contains("登录失效") || message.contains("未登录")
    }

    private fun buildEcardRoomBody(bind: UtilityBind, bag: JsonObject): String {
        val roomId = bind.roomId.ifBlank { bag.pick("roomId", "roomid", "id", "mdid") }.ifBlank { bind.roomName }
        val buildingNo = bind.buildingId.ifBlank { bind.buildingName }.ifBlank { bag.pick("buildingNo", "building") }
        val roomNo = bind.roomName.ifBlank { bag.pick("roomNo", "room") }
        val floorId = bind.floorId
        val areaId = bind.areaId
        return buildString {
            append('{')
            val parts = buildList {
                if (roomId.isNotBlank()) {
                    add("\"roomId\":\"${roomId.jsonEsc()}\"")
                    add("\"id\":\"${roomId.jsonEsc()}\"")
                }
                if (buildingNo.isNotBlank()) add("\"buildingNo\":\"${buildingNo.jsonEsc()}\"")
                if (roomNo.isNotBlank()) add("\"roomNo\":\"${roomNo.jsonEsc()}\"")
                if (floorId.isNotBlank()) add("\"floorId\":\"${floorId.jsonEsc()}\"")
                if (areaId.isNotBlank()) add("\"areaId\":\"${areaId.jsonEsc()}\"")
            }
            append(parts.joinToString(","))
            append('}')
        }
    }

    private suspend fun ecardQuery(path: String, extra: Map<String, String>): List<UtilityOption> {
        val get = runCatching { ecardGet(path, extra) }.getOrNull()
        val fromGet = get?.let(::parseUtilityOptions).orEmpty()
        if (fromGet.isNotEmpty()) return fromGet
        val jsonBody = if (extra.isEmpty()) "{}" else {
            extra.entries.joinToString(",", "{", "}") { (k, v) -> "\"${k.jsonEsc()}\":\"${v.jsonEsc()}\"" }
        }
        val posted = runCatching { ecardJson(path, jsonBody) }.getOrNull()
        return posted?.let(::parseUtilityOptions).orEmpty()
    }

    private suspend fun ecardGet(path: String, extra: Map<String, String>): JsonObject {
        val text = client.get("$GZUS_ECARD_ORIGIN/$path") {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$GZUS_ECARD_ORIGIN/")
            header("X-Requested-With", "XMLHttpRequest")
            extra.forEach { (key, value) -> parameter(key, value) }
        }.bodyAsText()
        if (text.contains("登录失效") || text.contains("ssoHost")) {
            return JsonObject(mapOf("denied" to JsonPrimitive(true)))
        }
        return parseHallJson(text)
    }

    private fun parseUtilityOptions(root: JsonObject): List<UtilityOption> {
        if (ecardDenied(root)) return emptyList()
        return root.hallObjects().mapNotNull { item ->
            val id = item.pick("id", "areaid", "areaId", "buildingid", "buildingId", "floorid", "floorId", "roomid", "roomId", "mdid")
            val name = item.pick("name", "areaName", "buildingName", "floorName", "roomName", "mdname", "title", "text")
            if (id.isBlank() && name.isBlank()) null
            else UtilityOption(id = id.ifBlank { name }, name = name.ifBlank { id })
        }.distinctBy { it.id + "/" + it.name }
    }

    private fun hallAbs(url: String): String {
        val raw = url.trim()
        if (raw.isBlank() || raw == "#" || raw == "/") return ""
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("#/")) return "$ehall/$raw"
        return if (raw.startsWith("/")) ehall + raw else "$ehall/$raw"
    }
}

private fun JsonObject.pick(vararg keys: String): String {
    for (key in keys) {
        val value = this[key]
        val text = when (value) {
            is JsonPrimitive -> value.contentOrNull?.trim().orEmpty()
            else -> ""
        }
        if (text.isNotBlank() && text != "null") return text
    }
    return ""
}

private fun JsonObject.hallObjects(): List<JsonObject> {
    val data = this["data"]
    val arrays = buildList<JsonArray> {
        when (data) {
            is JsonArray -> add(data)
            is JsonObject -> {
                listOf("list", "records", "rows", "data", "items", "content").forEach { key ->
                    (data[key] as? JsonArray)?.let(::add)
                }
            }
            else -> Unit
        }
        listOf("list", "records", "rows").forEach { key ->
            (this@hallObjects[key] as? JsonArray)?.let(::add)
        }
    }
    return arrays.flatMap { arr -> arr.mapNotNull { it as? JsonObject } }.distinct()
}

private fun JsonObject.hallBag(): JsonObject {
    val data = this["data"]
    val obj = this["obj"]
    return when {
        data is JsonObject -> data
        obj is JsonObject -> obj
        else -> this
    }
}

private fun String.jsonEsc(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private fun isLeaveText(text: String): Boolean =
    text.contains("请假") || text.contains("销假")

private fun JsonObject.toLeaveApplication(fallbackStatus: String): LeaveApplication {
    val title = pick("subject", "title", "processName", "affairName", "name")
    return LeaveApplication(
        id = pick("id", "taskId", "processId", "uid", "orunid").ifBlank { title },
        title = title,
        status = pick("status", "taskStatus", "state", "processStatus").ifBlank { fallbackStatus },
        time = pick("createTime", "startTime", "applyTime", "time"),
        start = pick("KSSJ", "kssj", "startTime", "beginTime"),
        end = pick("JSSJ", "jssj", "endTime"),
        reason = pick("QJLY", "qjly", "reason", "comment"),
        node = pick("nodeName", "currentNode", "taskName"),
    )
}

private fun HallTodo.toLeaveApplication(fallbackStatus: String): LeaveApplication =
    LeaveApplication(
        id = id,
        title = title,
        status = status.ifBlank { fallbackStatus },
        time = time,
    )
