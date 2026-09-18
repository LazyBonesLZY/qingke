package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val publicClient: HttpClient = createBareHttpClient(),
    private val jwxt: JwxtClient = JwxtClient(origin = JWXT_ORIGIN, client = client),
) : SchoolPortal by jwxt {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cas = GZUS_CAS_ORIGIN.trimEnd('/')
    private val ehall = GZUS_EHALL_ORIGIN.trimEnd('/')
    private val keepMutex = Mutex()
    private var cachedTgt = ""
    private var lastKeepAliveAt = 0L
    private var roomCatalog: List<EcardRoomRow> = emptyList()

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
        cachedTgt = ""
        roomCatalog = emptyList()
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
        rememberTgt(tickets.ticketGrantingTicket.ifBlank { tgtFromCookies() })
        val st = tickets.serviceTicket.ifBlank {
            val tgt = currentTgt()
            if (tgt.startsWith("TGT-")) requestServiceTicket(tgt, GZUS_JWXT_SSO_SERVICE)
            else error("门户没有返回票据")
        }
        if (!st.startsWith("ST-")) error("门户没有返回票据")
        consumeJwxtTicket(st)
    }

    override suspend fun keepAlive(): Boolean = refreshTickets(force = false)

    override suspend fun fetchHall(): HallSnapshot {
        runCatching { ensureEhall() }.onFailure { failed ->
            if (failed is JwxtNeedFirstLogin) throw failed
            if (isTransientNetwork(failed)) throw failed
            if (isSessionLost(failed.message.orEmpty()) && !hasTgt()) throw failed
            if (isSessionLost(failed.message.orEmpty())) throw failed
            return HallSnapshot(error = failed.message?.ifBlank { HALL_SESSION_HINT } ?: HALL_SESSION_HINT)
        }
        runCatching { tryLoginEhall() }
        var nameRoot = runCatching { hallGet("api/authc/users/name") }.getOrNull()
        if (nameRoot != null && hallDenied(nameRoot) && hasTgt()) {
            runCatching { openEhall(currentTgt()) }
            nameRoot = runCatching { hallGet("api/authc/users/name") }.getOrNull()
        }
        if (nameRoot != null && hallDenied(nameRoot)) {
            return HallSnapshot(error = HALL_SESSION_HINT)
        }
        val userName = runCatching { hallUserName(nameRoot) }.getOrDefault("")
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
            error = if (ready) "" else "办事大厅这次是空的",
        )
    }

    override suspend fun fetchLeaveForm(affairId: String): LeaveForm {
        ensureEhall()
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
        ensureEhall()
        runCatching { tryLoginEhall() }
        val filled = values.filterValues { it.isNotBlank() }
        if (filled.isEmpty()) error("先填请假时间和事由")
        val root = hallPost("api/bpm/processes/tasks/run", leaveRunBody(form, filled))
        hallFailMessage(root)?.let { error(it) }
        val message = (root["meta"] as? JsonObject)?.lyuapStr("message")
            .orEmpty()
            .ifBlank { root.lyuapStr("message") }
            .ifBlank { root.lyuapStr("msg") }
        return message.takeUnless { isCasEnvelopeMessage(it) }.orEmpty().ifBlank { "请假已提交" }
    }

    override suspend fun fetchUtility(bind: UtilityBind, sno: String): UtilitySnapshot {
        runCatching { ensureEcard() }
        val member = runCatching { ecardMemberRoot() }.getOrNull()?.ecardMemberBag()
        val ecardBind = member?.toUtilityBind() ?: UtilityBind()
        val used = mergeUtilityBind(bind, ecardBind)
        if (!used.hasRoom()) {
            return UtilitySnapshot(error = "未绑定宿舍")
        }
        val catalog = runCatching { loadRoomCatalog() }.getOrDefault(emptyList())
        val row = catalog.firstOrNull { it.matchesBind(used) }
        val query = UtilityBind(
            areaId = row?.areaId.orEmpty().ifBlank { used.areaId },
            areaName = used.areaName.ifBlank { row?.areaName.orEmpty() },
            buildingId = row?.buildingId.orEmpty().ifBlank { used.buildingId },
            buildingName = used.buildingName.ifBlank { row?.buildingName.orEmpty() },
            roomId = row?.roomId.orEmpty().ifBlank { used.roomId },
            roomName = used.roomName.ifBlank { row?.roomName.orEmpty() },
        )
        var power = ""
        var cold = ""
        var hot = ""
        val fields = ecardBalanceFields(query)
        if (fields.isNotEmpty()) {
            for (root in fetchEcardBalance(fields)) {
                val found = pickUtilityTriple(root)
                power = power.ifBlank { found.power }
                cold = cold.ifBlank { found.cold }
                hot = hot.ifBlank { found.hot }
                if (power.isNotBlank() && cold.isNotBlank()) break
            }
        }
        power = power.ifBlank { row?.power.orEmpty() }
        val ready = power.isNotBlank() || cold.isNotBlank() || hot.isNotBlank()
        return UtilitySnapshot(
            ready = ready,
            building = listOf(used.areaName, used.buildingName.ifBlank { row?.buildingName.orEmpty() }).filter { it.isNotBlank() }.joinToString(" "),
            room = listOf(used.floorName, used.roomName.ifBlank { row?.roomName.orEmpty() }).filter { it.isNotBlank() }.joinToString(" "),
            power = power,
            coldWater = cold,
            hotWater = hot,
            error = if (ready) "" else "查询失败",
            bind = used,
        )
    }

    override suspend fun fetchUtilityOptions(level: String, parentId: String): List<UtilityOption> {
        val query = parentId.trim()
        if (query.isBlank()) error("请输入楼栋或房间号。")
        val catalog = loadRoomCatalog()
        if (catalog.isEmpty()) error("宿舍列表加载失败")
        val hit = catalog.filter { it.matches(query) }
        if (hit.isEmpty()) error("未找到宿舍")
        val needle = foldRoomKey(query)
        return hit.sortedWith(
            compareBy<EcardRoomRow>(
                { foldRoomKey(it.roomName) != needle },
                { !foldRoomKey(it.roomName).startsWith(needle) },
                { it.display.length },
            ),
        ).take(100).map { it.toOption() }
    }

    suspend fun requireLiveSession() {
        if (!refreshTickets(force = false)) error(SESSION_LOST_HINT)
    }

    fun hasTgt(): Boolean = currentTgt().startsWith("TGT-")

    suspend fun refreshTickets(force: Boolean = false): Boolean = keepMutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastKeepAliveAt < 60_000L && hasTgt() && probeEhall()) {
            return true
        }
        val tgt = currentTgt()
        if (!tgt.startsWith("TGT-")) return false
        val jwxt = runCatching {
            consumeJwxtTicket(requestServiceTicket(tgt, GZUS_JWXT_SSO_SERVICE))
        }
        jwxt.exceptionOrNull()?.let { failed ->
            if (failed is JwxtNeedFirstLogin || isTransientNetwork(failed)) throw failed
            if (isSessionLost(failed.message.orEmpty())) return false
        }
        runCatching { refreshEhall() }
        lastKeepAliveAt = now
        rememberTgt(currentTgt())
        true
    }

    private suspend fun ensureEhall() {
        if (probeEhall()) return
        val tgt = currentTgt()
        if (!tgt.startsWith("TGT-")) error(SESSION_LOST_HINT)
        openEhall(tgt)
        if (!probeEhall()) error(HALL_SESSION_HINT)
    }

    private suspend fun refreshEhall() {
        if (probeEhall()) return
        val tgt = currentTgt()
        if (!tgt.startsWith("TGT-")) error(SESSION_LOST_HINT)
        openEhall(tgt)
        if (!probeEhall()) error(HALL_SESSION_HINT)
    }

    private suspend fun probeEhall(): Boolean {
        runCatching { tryLoginEhall() }
        val name = runCatching { hallGet("api/authc/users/name") }.getOrNull() ?: return false
        return !hallDenied(name)
    }

    private suspend fun ensureEcard() {
        val tgt = currentTgt()
        if (!tgt.startsWith("TGT-")) error(SESSION_LOST_HINT)
        if (ecardMemberRoot() != null) return
        openEcard(tgt)
        if (ecardMemberRoot() == null) error(SESSION_LOST_HINT)
    }

    private suspend fun ecardMemberRoot(): JsonObject? {
        val root = ecardPost("waterfee/memberInfo")
        if (ecardDenied(root)) return null
        return root
    }

    fun forgetTickets() {
        cachedTgt = ""
        lastKeepAliveAt = 0L
    }

    override suspend fun logout() {
        forgetTickets()
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
        rememberTgt(tgt)
        val st = requestServiceTicket(tgt, GZUS_EHALL_CAS_SERVICE)
        val (url, body) = getFollowing(GZUS_EHALL_CAS_SERVICE) { parameter("ticket", st) }
        if (isCasFirstLogin(body, url)) throw JwxtNeedFirstLogin()
        if (url.contains("lyuapServer/login") || body.contains("lyuapServer/login")) {
            error(HALL_SESSION_HINT)
        }
        tryLoginEhall()
    }

    private suspend fun openEcard(tgt: String) {
        rememberTgt(tgt)
        if (ecardMemberRoot() != null) return
        val service = gzusEcardCasService()
        val st = requestServiceTicket(tgt, service)
        val (url, body) = getFollowing(service) { parameter("ticket", st) }
        if (isCasFirstLogin(body, url)) throw JwxtNeedFirstLogin()
        if (ecardMemberRoot() != null) return
        getFollowing("$GZUS_ECARD_ORIGIN/")
        if (ecardMemberRoot() == null) error(SESSION_LOST_HINT)
    }

    private suspend fun getFollowing(start: String, configure: HttpRequestBuilder.() -> Unit = {}): Pair<String, String> {
        var url = start
        var first = true
        var hops = 0
        while (hops++ < 8) {
            val resp = client.get(url) {
                header(HttpHeaders.UserAgent, UA)
                if (first) configure()
            }
            first = false
            val loc = resp.headers[HttpHeaders.Location].orEmpty()
            if (resp.status.value in 300..399 && loc.isNotBlank()) {
                url = resolveRedirect(resp.request.url.toString(), loc)
            } else {
                return resp.request.url.toString() to resp.bodyAsText()
            }
        }
        error("一卡通跳转失败")
    }

    private suspend fun requestServiceTicket(tgt: String, service: String): String {
        val raw = try {
            client.submitForm(
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
        } catch (failed: ResponseException) {
            if (failed.response.status.value in 400..499) error(SESSION_LOST_HINT)
            throw failed
        }
        if (isCasFirstLogin(raw)) throw JwxtNeedFirstLogin()
        return parseServiceTicket(raw)
    }

    private fun extractCasService(url: String, body: String): String? {
        val fromUrl = runCatching { Url(url).parameters["service"].orEmpty() }.getOrDefault("")
        if (fromUrl.isNotBlank()) return fromUrl
        val raw = Regex("""(?:name=["']service["'][^>]*value=["']|[\?&]service=)([^"'&\s]+)""")
            .find(body)?.groupValues?.get(1).orEmpty()
            .replace("&amp;", "&")
        if (raw.isBlank()) return null
        return runCatching { raw.decodeURLQueryComponent() }.getOrDefault(raw)
    }

    private fun isEcardLoginPage(body: String): Boolean =
        body.contains("登录失效") ||
            (body.contains("lyuapServer/login") && (body.contains("name=\"username\"") || body.contains("id=\"username\"")))

    private fun currentTgt(): String {
        val fromCookie = tgtFromCookies()
        if (fromCookie.startsWith("TGT-")) {
            cachedTgt = fromCookie
            return fromCookie
        }
        return cachedTgt.takeIf { it.startsWith("TGT-") }.orEmpty()
    }

    private fun tgtFromCookies(): String =
        currentCookies()
            .map { it.second.trim() }
            .firstOrNull { it.startsWith("TGT-") }
            .orEmpty()

    private suspend fun rememberTgt(tgt: String) {
        if (!tgt.startsWith("TGT-")) return
        cachedTgt = tgt
        val url = Url("$cas/login")
        PersistCookieStorage.shared.addCookie(
            url,
            Cookie(name = "CASTGC", value = tgt, domain = "cas.gzus.edu.cn", path = "/lyuapServer"),
        )
        PersistCookieStorage.shared.addCookie(
            url,
            Cookie(name = "CASTGC", value = tgt, domain = "cas.gzus.edu.cn", path = "/"),
        )
    }

    private fun parseServiceTicket(text: String): String {
        val tickets = runCatching { parseCasTickets(text, json) }.getOrElse { failed ->
            if (failed is JwxtNeedFirstLogin) throw failed
            if (isTransientNetwork(failed)) throw failed
            val msg = failed.message.orEmpty()
            if (
                msg.contains("验证码") ||
                msg.contains("学号或密码") ||
                msg.contains("账号不存在") ||
                msg.contains("已锁定") ||
                msg.contains("已停用") ||
                msg.contains("二次验证") ||
                msg.contains("没有教务权限") ||
                msg.contains("绑定微信") ||
                msg.contains("网络承诺") ||
                msg.contains("多个账号")
            ) {
                throw failed
            }
            error(SESSION_LOST_HINT)
        }
        if (tickets.serviceTicket.startsWith("ST-")) return tickets.serviceTicket
        error(SESSION_LOST_HINT)
    }

    private suspend fun tryLoginEhall() {
        hallPost("tryLoginUserInfo", """{"version":"2","loadType":"6"}""")
    }

    private suspend fun hallUserName(root: JsonObject? = null): String {
        val payload = root ?: hallGet("api/authc/users/name")
        if (hallDenied(payload)) return ""
        val data = payload["data"]
        return when (data) {
            is JsonPrimitive -> data.contentOrNull?.trim().orEmpty()
            is JsonObject -> data.pick("userName", "name", "realName", "nickName", "username")
            else -> payload.pick("userName", "name", "realName")
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
        if (hallDenied(root)) return HALL_SESSION_HINT
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
            val message = meta?.lyuapStr("message").orEmpty()
                .ifBlank { root.lyuapStr("message") }
                .ifBlank { root.lyuapStr("msg") }
            return message.takeUnless { isCasEnvelopeMessage(it) }.orEmpty().ifBlank { "提交失败" }
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

    private suspend fun fetchEcardBalance(fields: Map<String, String>): List<JsonObject> {
        val out = ArrayList<JsonObject>(3)
        for (implType in listOf("CGCOMMON1111", "CGCOMMON2222", "CGCOMMON3333")) {
            val body = fields + ("implType" to implType)
            val root = runCatching {
                ecardPost("powerfee/getBalance", body, http = publicClient, wechat = true)
            }.getOrNull() ?: runCatching {
                ecardPost("powerfee/getBalance", body)
            }.getOrNull() ?: continue
            if (ecardDenied(root)) continue
            if (root.lyuapStr("ret").equals("false", ignoreCase = true)) continue
            out.add(root)
            if (pickUtilityTriple(root).hasWater()) break
        }
        return out
    }

    private suspend fun ecardPost(
        path: String,
        fields: Map<String, String> = emptyMap(),
        http: HttpClient = client,
        wechat: Boolean = false,
    ): JsonObject {
        val text = http.submitForm(
            url = "$GZUS_ECARD_ORIGIN/$path",
            formParameters = Parameters.build {
                fields.forEach { (key, value) ->
                    if (value.isNotBlank()) append(key, value)
                }
            },
        ) {
            ecardHeaders(wechat)
        }.bodyAsText()
        return parseEcardJson(text)
    }

    private suspend fun ecardPostJson(
        path: String,
        fields: Map<String, String>,
        http: HttpClient = client,
        wechat: Boolean = false,
    ): JsonObject {
        val body = buildJsonObject {
            fields.forEach { (key, value) ->
                if (value.isNotBlank()) put(key, value)
            }
        }
        val text = http.post("$GZUS_ECARD_ORIGIN/$path") {
            ecardHeaders(wechat)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()
        return parseEcardJson(text)
    }

    private fun ecardDenied(root: JsonObject): Boolean {
        if (root["denied"] is JsonPrimitive) return true
        val code = root.lyuapStr("code")
        val message = root.lyuapStr("msg") + root.lyuapStr("message")
        return code == "203" || message.contains("登录失效") || message.contains("未登录")
    }

    private fun ecardRoomQueryFields(bind: UtilityBind, bag: JsonObject): Map<String, String> = ecardFields(
        "schoolAreaNo" to bind.areaId.ifBlank { bag.pick("schoolAreaNo", "areaId", "areaid") },
        "buildingNo" to bind.buildingId.ifBlank { bind.buildingName }.ifBlank { bag.pick("buildingNo", "building") },
        "roomNum" to bind.roomId.ifBlank { bind.roomName }.ifBlank { bag.pick("roomNum", "roomId", "roomNo", "room") },
    )

    private fun ecardBalanceFields(bind: UtilityBind): Map<String, String> {
        val schoolAreaNo = bind.areaId
        val buildingNo = bind.buildingId.ifBlank { bind.buildingName }
        val roomNum = bind.roomId.ifBlank { bind.roomName }
        if (schoolAreaNo.isBlank() || buildingNo.isBlank() || roomNum.isBlank()) return emptyMap()
        return ecardFields(
            "implType" to "CGCOMMON1111",
            "schoolAreaNo" to schoolAreaNo,
            "buildingNo" to buildingNo,
            "roomNum" to roomNum,
        )
    }

    private fun parseEcardJson(text: String): JsonObject {
        if (isCasFirstLogin(text) || text.contains("登录失效") || isEcardLoginPage(text)) {
            return JsonObject(mapOf("denied" to JsonPrimitive(true), "message" to JsonPrimitive(text.take(80))))
        }
        if (text.trimStart().startsWith("<")) {
            return JsonObject(emptyMap())
        }
        if (text.contains("ssoHost")) {
            return JsonObject(mapOf("denied" to JsonPrimitive(true), "message" to JsonPrimitive(text.take(80))))
        }
        return parseHallJson(text)
    }

    private fun HttpRequestBuilder.ecardHeaders(wechat: Boolean = false) {
        header(HttpHeaders.UserAgent, if (wechat) GZUS_ECARD_WX_UA else UA)
        header(HttpHeaders.Referrer, "$GZUS_ECARD_ORIGIN/")
        header(HttpHeaders.Origin, GZUS_ECARD_ORIGIN)
        header(HttpHeaders.Accept, "application/json, text/plain, */*")
        header("X-Requested-With", "XMLHttpRequest")
    }

    private suspend fun loadRoomCatalog(force: Boolean = false): List<EcardRoomRow> {
        if (!force && roomCatalog.isNotEmpty()) return roomCatalog
        val rows = fetchRoomCatalog(publicClient, wechat = true).ifEmpty { fetchRoomCatalog(client) }
        if (rows.isNotEmpty()) roomCatalog = rows
        return rows
    }

    private suspend fun fetchRoomCatalog(http: HttpClient, wechat: Boolean = false): List<EcardRoomRow> {
        for (implType in listOf("CGCOMMON1111", "CGCOMMON2222", "CGCOMMON3333")) {
            val root = runCatching {
                ecardPost("powerfee/getRoomInfo", mapOf("implType" to implType), http = http, wechat = wechat)
            }.getOrNull() ?: continue
            if (ecardDenied(root)) continue
            val rows = parseRoomRows(root)
            if (rows.isNotEmpty()) return rows
        }
        return emptyList()
    }

    private fun parseRoomRows(root: JsonObject): List<EcardRoomRow> {
        if (ecardDenied(root)) return emptyList()
        return root.ecardObjects().mapNotNull { item ->
            val areaName = item.pick("schoolArea", "areaName", "campus", "xqmc")
            val areaId = item.pick("schoolAreaNo", "areaId", "areaid", "campusId", "xqid").ifBlank { areaName }
            val buildingName = item.pick("building", "buildingName", "loudong")
            val buildingId = item.pick("buildingNo", "buildingId", "buildingid").ifBlank { buildingName }
            val roomName = item.pick("room", "roomDisplay", "roomName", "roomNo")
            val roomId = item.pick("roomNum", "roomId", "roomid", "mdid", "id").ifBlank { roomName }
            val display = item.pick("displayName").ifBlank {
                listOf(areaName, buildingName, roomName.ifBlank { roomId }).filter { it.isNotBlank() }.joinToString(" ")
            }
            if (listOf(areaName, buildingName, roomName, display).all { it.isBlank() }) null
            else EcardRoomRow(
                areaId = areaId,
                areaName = areaName,
                buildingId = buildingId,
                buildingName = buildingName,
                roomId = roomId,
                roomName = roomName.ifBlank { roomId },
                display = display,
                power = item.pickUtilityBalance(),
                coldWater = item.pickColdWater(),
                hotWater = item.pickHotWater(),
            )
        }
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

private fun JsonObject.pickDeep(vararg keys: String): String {
    pick(*keys).takeIf { it.isNotBlank() }?.let { return it }
    for ((_, value) in this) {
        if (value is JsonObject) {
            value.pickDeep(*keys).takeIf { it.isNotBlank() }?.let { return it }
        } else if (value is JsonArray) {
            for (el in value) {
                (el as? JsonObject)?.pickDeep(*keys)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
    }
    return ""
}

private fun JsonObject.pickBalance(vararg keys: String): String =
    pickDeep(*keys).trim().removeSuffix("吨").removeSuffix("度").trim()

private fun JsonObject.pickUtilityBalance(): String = pickUtilityTree(this)

private fun JsonObject.pickColdWater(): String = pickColdWaterTree(this)

private fun JsonObject.pickHotWater(): String = pickHotWaterTree(this)

private fun pickUtilityTree(obj: JsonObject): String =
    obj.pickBalance(
        "powerBalance", "formatPowerBalance", "formatPowerBalanceStr", "powerText",
        "remainPower", "utilityElectricity",
    )

private fun pickColdWaterTree(obj: JsonObject): String =
    obj.pickBalance(
        "formatWaterBalanceStr", "coldWaterBalance", "coldWaterText", "waterBalance",
        "utilityColdWater", "cold_water",
    )

private fun pickHotWaterTree(obj: JsonObject): String =
    obj.pickBalance(
        "formatHotWaterBalanceStr", "hotWaterBalance", "hotWaterText",
        "utilityHotWater", "hot_water",
    )

private data class UtilityTriple(val power: String, val cold: String, val hot: String) {
    fun hasWater(): Boolean = cold.isNotBlank() || hot.isNotBlank()
}

private fun pickUtilityTriple(root: JsonObject): UtilityTriple {
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

private fun JsonObject.ecardBags(): List<JsonObject> {
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

private fun JsonObject.ecardMemberBag(): JsonObject =
    ecardBags().firstOrNull {
        it.toUtilityBind().hasRoom() || it.pickDeep("sno", "xh", "studentId", "userNo", "username").isNotBlank()
    } ?: hallBag()

private fun JsonObject.toUtilityBind(): UtilityBind {
    val roomId = pickDeep("roomNum", "roomId", "roomNo")
    val roomName = pickDeep("room", "roomDisplay", "roomName").ifBlank { roomId }
    val buildingId = pickDeep("buildingNo", "buildingId")
    val buildingName = pickDeep("building", "buildingName").ifBlank { buildingId }
    val areaId = pickDeep("schoolAreaNo", "areaId", "areaid")
    val areaName = pickDeep("schoolArea", "areaName").ifBlank { areaId }
    return UtilityBind(
        areaId = areaId,
        areaName = areaName,
        buildingId = buildingId,
        buildingName = buildingName,
        roomId = roomId,
        roomName = roomName,
    )
}

private fun mergeUtilityBind(local: UtilityBind, ecard: UtilityBind): UtilityBind {
    if (ecard.hasRoom()) {
        return UtilityBind(
            areaId = ecard.areaId.ifBlank { local.areaId },
            areaName = ecard.areaName.ifBlank { local.areaName },
            buildingId = ecard.buildingId.ifBlank { local.buildingId },
            buildingName = ecard.buildingName.ifBlank { local.buildingName },
            roomId = ecard.roomId.ifBlank { local.roomId },
            roomName = ecard.roomName.ifBlank { local.roomName }.ifBlank { ecard.roomId },
        )
    }
    return local
}

private fun ecardFields(vararg pairs: Pair<String, String>): Map<String, String> =
    pairs.mapNotNull { (key, value) -> value.trim().takeIf { it.isNotBlank() }?.let { key to it } }.toMap()

private data class EcardRoomRow(
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

private fun JsonObject.ecardObjects(): List<JsonObject> {
    val bagKeys = listOf(
        "list", "records", "rows", "data", "items", "content", "obj", "result",
        "areas", "buildings", "floors", "rooms", "areaList", "buildingList", "floorList", "roomList",
    )
    val data = this["data"]
    val obj = this["obj"]
    val arrays = buildList<JsonArray> {
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
    val roomMaps = buildList<JsonObject> {
        when (data) {
            is JsonObject -> addAll(data.values.mapNotNull { it as? JsonObject }.filter { it.looksLikeEcardRoom() })
            else -> Unit
        }
        when (obj) {
            is JsonObject -> addAll(obj.values.mapNotNull { it as? JsonObject }.filter { it.looksLikeEcardRoom() })
            else -> Unit
        }
    }
    if (roomMaps.isNotEmpty()) return (fromArrays + roomMaps).distinct()
    return if (this.looksLikeEcardRoom()) listOf(this) else emptyList()
}

private fun JsonObject.matchesBind(bind: UtilityBind): Boolean = matchesRoomBind(
    bind,
    areaId = pick("schoolAreaNo", "areaId"),
    areaName = pick("schoolArea", "areaName"),
    buildingId = pick("buildingNo", "buildingId"),
    buildingName = pick("building", "buildingName"),
    roomId = pick("roomNum", "roomId"),
    roomName = pick("room", "roomDisplay", "roomName"),
)

private fun resolveRedirect(from: String, location: String): String {
    val loc = location.trim()
    if (loc.startsWith("http://") || loc.startsWith("https://")) return loc
    val base = Url(from)
    if (loc.startsWith("//")) return "${base.protocol.name}:$loc"
    if (loc.startsWith("/")) return "${base.protocol.name}://${base.host}$loc"
    return from.substringBeforeLast('/') + "/" + loc
}

private fun foldRoomKey(text: String): String = buildString(text.length) {
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

private fun matchesRoomBind(
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

private fun JsonObject.looksLikeEcardRoom(): Boolean =
    keys.any {
        it in setOf(
            "areaName", "areaId", "buildingNo", "roomId", "roomName", "roomDisplay",
            "schoolArea", "schoolAreaNo", "roomNum", "displayName",
        )
    }

private fun JsonObject.hallBag(): JsonObject {
    val data = this["data"]
    val obj = this["obj"]
    return when {
        data is JsonObject -> data
        obj is JsonObject -> obj
        obj is JsonArray -> obj.mapNotNull { it as? JsonObject }.firstOrNull() ?: this
        else -> this
    }
}

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
