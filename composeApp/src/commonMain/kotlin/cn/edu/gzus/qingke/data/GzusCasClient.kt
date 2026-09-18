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

class GzusCasClient(
    private val client: HttpClient = createHttpClient(),
    private val ecard: GzusEcardClient = GzusEcardClient(),
    private val jwxt: JwxtClient = JwxtClient(origin = JWXT_ORIGIN, client = client),
) : SchoolPortal by jwxt {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cas = GZUS_CAS_ORIGIN.trimEnd('/')
    private val ehall = GZUS_EHALL_ORIGIN.trimEnd('/')
    private val keepMutex = Mutex()
    private var cachedTgt = ""
    private var lastKeepAliveAt = 0L

    override suspend fun fetchCaptcha(): LoginCaptcha? {
        client.get("$cas/login") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            parameter("service", GZUS_JWXT_SSO_SERVICE)
        }
        val text = client.get("$cas/kaptcha") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
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
            header(HttpHeaders.UserAgent, QINGKE_UA)
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

    suspend fun loginSilent(studentId: String, password: String): Result<Unit> {
        repeat(3) {
            val image = runCatching { fetchCaptcha() }.getOrNull() ?: return@repeat
            val code = solveLyuapCaptcha(image.bytes) ?: return@repeat
            val result = login(studentId, password, code, image.id)
            if (result.isSuccess) return result
            val message = result.exceptionOrNull()?.message.orEmpty()
            if (isCaptchaTip(message)) return@repeat
            return result
        }
        return Result.failure(IllegalStateException("验证码没解开"))
    }

    override suspend fun keepAlive(): Boolean = refreshTickets(force = false)

    override suspend fun fetchHall(): HallSnapshot {
        runCatching { ensureEhall() }.onFailure { failed ->
            if (failed is JwxtNeedFirstLogin) throw failed
            if (isTransientNetwork(failed)) throw failed
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

    /** 审批进度轴：看这条申请卡在谁那里。大厅前端用的是 api/bpm/processes/tasks/trace/axis。 */
    override suspend fun fetchLeaveTrace(instanceId: String): List<LeaveStep> {
        val id = instanceId.trim()
        if (id.isBlank()) return emptyList()
        ensureEhall()
        runCatching { tryLoginEhall() }
        val root = hallGet("api/bpm/processes/tasks/trace/axis", mapOf("docUnid" to id, "id" to id))
        if (hallDenied(root)) error(HALL_SESSION_HINT)
        return firstObjectArray(root).mapNotNull { item ->
            val name = item.pick("nodeName", "NODENAME", "name", "taskName", "stepName", "activityName")
            val handler = item.pick(
                "handlerName", "currentHandlerName", "userName", "addNameCn", "assignee",
                "HANDLER", "personName", "operatorName",
            )
            val status = item.pick("statusName", "status", "state", "result", "STATUS", "auditResult")
            val time = item.pick("handleTime", "endTime", "createTime", "time", "LASTMODIFIED", "startTime")
            val comment = item.pick("opinion", "comment", "remark", "content", "OPINION", "auditOpinion")
            if (listOf(name, handler, status, time, comment).all { it.isBlank() }) {
                null
            } else {
                LeaveStep(
                    name = name,
                    handler = handler,
                    status = status,
                    time = time,
                    comment = comment,
                )
            }
        }
    }

    override suspend fun fetchUtility(bind: UtilityBind, sno: String): UtilitySnapshot {
        // 余额接口是公开的，先按绑定直接查；门户会话只用来认出本人宿舍。
        val member = runCatching {
            ensureEcard()
            ecardMemberRoot()
        }.getOrNull()?.ecardMemberBag()
        val used = mergeUtilityBind(bind, member?.toUtilityBind() ?: UtilityBind())
        if (!used.hasRoom()) {
            return UtilitySnapshot(error = "未绑定宿舍")
        }
        return ecard.balance(used)
    }

    override suspend fun fetchUtilityOptions(level: String, parentId: String): List<UtilityOption> {
        val query = parentId.trim()
        if (query.isBlank()) error("请输入楼栋或房间号。")
        val hit = ecard.search(query)
        if (hit.isEmpty()) error("未找到宿舍")
        return hit.map { it.toOption() }
    }

    fun hasTgt(): Boolean = currentTgt().startsWith("TGT-")

    suspend fun refreshTickets(force: Boolean = false): Boolean = keepMutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastKeepAliveAt < 60_000L && hasTgt()) {
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
        runCatching { refreshEcard() }
        lastKeepAliveAt = now
        rememberTgt(currentTgt())
        true
    }

    private suspend fun ensureEhall() {
        if (probeEhall()) return
        val tgt = currentTgt()
        if (!tgt.startsWith("TGT-")) error(HALL_SESSION_HINT)
        openEhall(tgt)
        if (!probeEhall()) error(HALL_SESSION_HINT)
    }

    private suspend fun refreshEhall() {
        if (probeEhall()) return
        val tgt = currentTgt()
        if (!tgt.startsWith("TGT-")) error(HALL_SESSION_HINT)
        openEhall(tgt)
        if (!probeEhall()) error(HALL_SESSION_HINT)
    }

    private suspend fun probeEhall(): Boolean {
        runCatching { tryLoginEhall() }
        val name = runCatching { hallGet("api/authc/users/name") }.getOrNull() ?: return false
        return !hallDenied(name)
    }

    suspend fun probeEcard(): Boolean = runCatching { ecardMemberRoot() != null }.getOrDefault(false)

    suspend fun probeJwxt(): Boolean = runCatching {
        val home = client.get("$JWXT_ORIGIN/jwglxt/xtgl/index_initMenu.html") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            parameter("jsdm", "xs")
        }
        val body = home.bodyAsText()
        val url = home.request.url.toString()
        !isLoginForm(body) && !url.contains("login_slogin") && !isPasswordChangePage(body, url)
    }.getOrDefault(false)

    private suspend fun ensureEcard() {
        if (probeEcard()) return
        val tgt = currentTgt()
        if (tgt.startsWith("TGT-")) {
            runCatching { openEcard(tgt) }
            if (probeEcard()) return
        }
        error(ECARD_SESSION_HINT)
    }

    private suspend fun refreshEcard() {
        if (probeEcard()) return
        val tgt = currentTgt()
        if (!tgt.startsWith("TGT-")) error(ECARD_SESSION_HINT)
        openEcard(tgt)
        if (!probeEcard()) error(ECARD_SESSION_HINT)
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
            client.get("$ehall/logout") { header(HttpHeaders.UserAgent, QINGKE_UA) }
        }
        runCatching {
            client.get("$cas/logout") { header(HttpHeaders.UserAgent, QINGKE_UA) }
        }
    }

    private suspend fun consumeJwxtTicket(ticket: String) {
        val jump = client.get(GZUS_JWXT_SSO_SERVICE) {
            header(HttpHeaders.UserAgent, QINGKE_UA)
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
                header(HttpHeaders.UserAgent, QINGKE_UA)
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
        if (ecardMemberRoot() == null) error(ECARD_SESSION_HINT)
    }

    private suspend fun getFollowing(start: String, configure: HttpRequestBuilder.() -> Unit = {}): Pair<String, String> {
        var url = start
        var first = true
        var hops = 0
        while (hops++ < 8) {
            val resp = client.get(url) {
                header(HttpHeaders.UserAgent, QINGKE_UA)
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
                header(HttpHeaders.UserAgent, QINGKE_UA)
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
            header(HttpHeaders.UserAgent, QINGKE_UA)
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
            header(HttpHeaders.UserAgent, QINGKE_UA)
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

    /** 只给 waterfee/memberInfo 用：这个接口要门户会话，所以走带 cookie 的 client。 */
    private suspend fun ecardPost(path: String, fields: Map<String, String> = emptyMap()): JsonObject {
        val text = client.submitForm(
            url = "$GZUS_ECARD_ORIGIN/$path",
            formParameters = Parameters.build {
                fields.forEach { (key, value) -> if (value.isNotBlank()) append(key, value) }
            },
        ) {
            ecardHeaders()
        }.bodyAsText()
        return parseEcardJson(text)
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

private fun JsonObject.ecardMemberBag(): JsonObject =
    ecardBags().firstOrNull {
        it.toUtilityBind().hasRoom() || it.ecardPickDeep("sno", "xh", "studentId", "userNo", "username").isNotBlank()
    } ?: hallBag()

/**
 * 在 JSON 树里找第一个"看着像列表"的对象数组。
 * 大厅有些接口没公开文档，包了几层不一定叫什么名字，先照常规键找，找不到再全树搜。
 */
internal fun firstObjectArray(root: JsonObject): List<JsonObject> {
    root.hallObjects().takeIf { it.isNotEmpty() }?.let { return it }
    val queue = ArrayDeque<JsonObject>()
    queue.add(root)
    var guard = 0
    while (queue.isNotEmpty() && guard++ < 200) {
        val node = queue.removeFirst()
        for ((_, value) in node) {
            when (value) {
                is JsonArray -> {
                    val rows = value.mapNotNull { it as? JsonObject }.filter { it.size >= 2 }
                    if (rows.isNotEmpty()) return rows
                    value.mapNotNull { it as? JsonObject }.forEach(queue::addLast)
                }
                is JsonObject -> queue.addLast(value)
                else -> Unit
            }
        }
    }
    return emptyList()
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

private fun resolveRedirect(from: String, location: String): String {
    val loc = location.trim()
    if (loc.startsWith("http://") || loc.startsWith("https://")) return loc
    val base = Url(from)
    if (loc.startsWith("//")) return "${base.protocol.name}:$loc"
    if (loc.startsWith("/")) return "${base.protocol.name}://${base.host}$loc"
    return from.substringBeforeLast('/') + "/" + loc
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
        instanceId = pick("orunid", "docUnid", "instanceNumber", "id"),
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
