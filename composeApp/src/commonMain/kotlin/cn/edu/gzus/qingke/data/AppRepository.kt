package cn.edu.gzus.qingke.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val STORE = "qingke-snapshot.json"

class AppRepository(
    private val gzus: JwxtClient = JwxtClient(),
    // 宿舍表有几千条，整个应用只留一份缓存，所以 ecard 要先声明再传给门户客户端。
    private val ecard: GzusEcardClient = GzusEcardClient(),
    private val gzusCas: GzusCasClient = GzusCasClient(ecard = ecard),
    private val zhku: ZhkuClient = ZhkuClient(),
    private val gzist: GzistClient = GzistClient(),
    private val xiaoai: XiaoaiClient = XiaoaiClient(),
    private val holidays: HolidayClient = HolidayClient(),
    private val updates: UpdateClient = UpdateClient(),
    private val scheduleAdjustClient: ScheduleAdjustClient = ScheduleAdjustClient(),
) {
    private fun portal(): SchoolPortal {
        val settings = _state.value.settings
        return when (settings.school()) {
            School.Gzus -> if (settings.gzusUsesCas()) gzusCas else gzus
            School.Zhku -> zhku
            School.Gzist -> gzist
            School.Custom -> customPortal(settings.customJwxt)
        }
    }

    private fun customPortal(cfg: CustomJwxt): SchoolPortal {
        val origin = cfg.originClean()
        require(origin.isNotBlank()) { "先在开发者选项里填教务地址" }
        return when (cfg.normalizedKind()) {
            "kingosoft" -> ZhkuClient(origin = origin)
            "lyuap" -> GzistClient(
                casOrigin = cfg.casOrigin.trim().ifBlank { origin },
                casService = cfg.casService.trim(),
                jwxtOrigin = origin,
                supportsFreeRooms = cfg.supportsFreeRooms,
            )
            else -> JwxtClient(
                origin = origin,
                supportsFreeRooms = cfg.supportsFreeRooms,
                paths = cfg.pathMap(),
            )
        }
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSnapshot> = _state
    private val _liveTick = MutableStateFlow(0)
    val liveTick: StateFlow<Int> = _liveTick.asStateFlow()
    private val _captcha = MutableStateFlow<LoginCaptcha?>(null)
    val captcha: StateFlow<LoginCaptcha?> = _captcha
    private val _captchaError = MutableStateFlow<String?>(null)
    val captchaError: StateFlow<String?> = _captchaError.asStateFlow()
    private var testStartMillis = 0L
    private var testEndMillis = 0L
    private var liveWidgetKey = ""
    private val pickMutex = Mutex()

    fun hasLiveTest(): Boolean = testEndMillis > nowMillis()

    private fun load(): AppSnapshot {
        val raw = readStore(STORE) ?: return AppSnapshot()
        val parsed = runCatching { json.decodeFromString<AppSnapshot>(raw) }.getOrNull() ?: return AppSnapshot()
        if (parsed.seeded) {
            val empty = AppSnapshot()
            persist(empty)
            return empty
        }
        return parsed
    }

    private var widgetSignature = 0

    private fun persist(snapshot: AppSnapshot) {
        writeStore(STORE, json.encodeToString(AppSnapshot.serializer(), snapshot))
        // 一次同步会 commit 四五回，但小组件只看课表那几样。
        // 没变就别重画，不然每次都是几张整屏位图。
        val signature = snapshot.widgetSignature()
        if (signature != widgetSignature) {
            widgetSignature = signature
            refreshHomeWidgets()
        }
    }

    private fun commit(transform: (AppSnapshot) -> AppSnapshot) {
        _state.update { current ->
            transform(current).also { persist(it) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        commit { it.copy(settings = transform(it.settings)) }
    }

    fun setSchool(school: School) {
        if (school.id == _state.value.settings.schoolId) return
        clearReloginSecret()
        clearCookieStore()
        _captcha.value = null
        _captchaError.value = null
        cancelLiveClass()
        commit {
            AppSnapshot(
                settings = it.settings.forSchool(school),
                holidays = it.holidays,
                scheduleAdjust = it.scheduleAdjust,
            )
        }
    }

    suspend fun refreshCaptcha() {
        _captchaError.value = null
        if (!_state.value.settings.resolved().showsCaptcha) {
            _captcha.value = null
            return
        }
        runCatching { portal().fetchCaptcha() }
            .onSuccess { image ->
                _captcha.value = image
                if (image == null && _state.value.settings.resolved().requiresCaptcha) {
                    _captchaError.value = "验证码没加载出来，点右边再试"
                }
            }
            .onFailure {
                _captcha.value = null
                _captchaError.value = it.friendlyNetworkMessage()
            }
    }

    fun saveCustomJwxt(next: CustomJwxt, apply: Boolean) {
        val cleaned = next.copy(
            name = next.name.trim().ifBlank { "自定义教务" },
            kind = next.normalizedKind(),
            origin = next.originClean(),
            loginUrl = next.loginUrl.trim(),
            changePasswordUrl = next.changePasswordUrl.trim(),
            casOrigin = next.casOrigin.trim().trimEnd('/'),
            casService = next.casService.trim(),
            captchaHint = next.captchaHint.trim(),
            pathsText = next.pathsText.trim(),
        )
        updateSettings { it.copy(customJwxt = cleaned) }
        if (apply && _state.value.settings.school() != School.Custom) {
            setSchool(School.Custom)
        }
    }

    fun setGzusLoginChannel(channel: String) {
        val next = if (channel == GZUS_LOGIN_CAS) GZUS_LOGIN_CAS else GZUS_LOGIN_JWXT
        val snap = _state.value
        if (snap.settings.school() == School.Gzus && snap.settings.gzusLoginChannel == next) return
        clearReloginSecret()
        clearCookieStore()
        _captcha.value = null
        _captchaError.value = null
        cancelLiveClass()
        commit {
            it.copy(
                settings = it.settings.copy(
                    schoolId = School.Gzus.id,
                    gzusLoginChannel = next,
                ),
                session = SessionState(studentId = it.session.studentId),
                hall = HallSnapshot(),
                grades = emptyList(),
                exams = emptyList(),
                rooms = emptyList(),
            )
        }
    }

    suspend fun checkGithubUpdate(): AppUpdate = updates.checkGithub()

    suspend fun pullScheduleAdjust() {
        val snap = _state.value
        if (!snap.settings.autoPullScheduleAdjust || snap.settings.school() != School.Gzus) return
        val next = scheduleAdjustClient.fetch()
        commit { it.copy(scheduleAdjust = next.copy(fetchedAt = nowMillis())) }
    }

    suspend fun loginAndSync(studentId: String, password: String, captcha: String = "", captchaId: String = "") {
        try {
            portal().login(studentId, password, captcha, captchaId).getOrThrow()
        } catch (failed: Throwable) {
            if (failed !is JwxtNeedFirstLogin) {
                runCatching { refreshCaptcha() }
            }
            throw failed
        }
        _captcha.value = null
        markLoggedIn(studentId)
        if (_state.value.settings.school() == School.Gzus && _state.value.settings.gzusUsesCas()) {
            saveReloginSecret(studentId, password)
        } else {
            clearReloginSecret()
        }
        runCatching { pullRemote(studentId = studentId, keepGradesIfFail = false) }
            .onFailure { error ->
                if (error is JwxtNeedFirstLogin) throw error
                val message = error.message.orEmpty()
                if (isFirstLoginSignal(message)) throw JwxtNeedFirstLogin()
                if (isCaptchaTip(message)) {
                    runCatching { refreshCaptcha() }
                }
                throw error
            }
        runCatching { syncHolidays(force = true) }
        resetLiveDismiss()
        refreshLive()
    }

    private fun markLoggedIn(studentId: String) {
        commit {
            it.copy(
                session = SessionState(
                    loggedIn = true,
                    cookies = currentCookies().toMap(),
                    lastSyncAt = it.session.lastSyncAt,
                    studentId = studentId.ifBlank { it.session.studentId },
                ),
            )
        }
    }

    suspend fun checkSession() {
        if (!_state.value.session.loggedIn) return
        val snap = _state.value
        if (snap.settings.school() == School.Gzus && snap.settings.gzusUsesCas()) {
            if (!ensureCasTickets(force = true) && shouldExpireWholeSession()) {
                markSessionExpired()
                error(SESSION_LOST_HINT)
            }
            return
        }
        withLiveSession { }
    }

    suspend fun keepAlive() {
        if (!_state.value.session.loggedIn) return
        val snap = _state.value
        if (snap.settings.school() != School.Gzus || !snap.settings.gzusUsesCas()) return
        if (!ensureCasTickets(force = false) && shouldExpireWholeSession()) {
            markSessionExpired()
            error(SESSION_LOST_HINT)
        }
    }

    suspend fun sync() {
        if (!_state.value.session.loggedIn) error("还没有登录")
        withLiveSession {
            pullRemote(studentId = _state.value.session.studentId, keepGradesIfFail = true)
            runCatching { syncHolidays() }
            refreshLive()
        }
    }

    suspend fun syncHolidays(force: Boolean = false) {
        val today = nowDateTime().date
        val years = holidayYearsNeeded(_state.value, today)
        val cache = _state.value.holidays
        val stale = cache.fetchedAt <= 0L || nowMillis() - cache.fetchedAt > 7 * 86_400_000L
        val missing = years.any { it !in cache.years } && cache.days.isEmpty()
        if (!force && cache.days.isNotEmpty() && !stale) return
        if (!force && !stale && !missing) return
        val days = mutableListOf<HolidayDay>()
        val got = mutableListOf<Int>()
        for (year in years) {
            val file = runCatching { holidays.fetchYear(year) }.getOrNull() ?: continue
            got += file.years
            days += file.days
        }
        if (got.isEmpty()) return
        commit {
            it.copy(
                holidays = HolidayCalendar(
                    days = days.distinctBy { day -> day.date },
                    years = got.distinct().sorted(),
                    fetchedAt = nowMillis(),
                ),
            )
        }
    }

    suspend fun syncCalendar() {
        if (!_state.value.session.loggedIn) return
        withLiveSession(probe = false) {
            val calendar = portal().fetchTermCalendar()
            commit { it.copy(settings = it.settings.mergeCalendar(calendar)) }
            refreshLive()
        }
    }

    private suspend fun pullRemote(studentId: String, keepGradesIfFail: Boolean) {
        val snap = _state.value
        val calendar = runCatching { portal().fetchTermCalendar() }
            .onFailure { error -> if (isSessionLost(error.message.orEmpty())) throw error }
            .getOrNull()
        val year = calendar?.yearCode?.ifBlank { null } ?: snap.settings.yearCode
        val term = calendar?.termCode?.ifBlank { null } ?: snap.settings.termCode
        val (profile, slots, practices) = portal().fetchTimetable(year, term)
        val grades = runCatching { portal().fetchGrades() }.getOrElse {
            if (keepGradesIfFail) snap.grades else emptyList()
        }
        val exams = runCatching { portal().fetchExams(year, term) }.getOrElse {
            if (keepGradesIfFail) snap.exams else emptyList()
        }
        commit {
            it.copy(
                profile = profile.copy(studentId = studentId.ifBlank { profile.studentId }),
                slots = slots,
                practices = practices,
                grades = grades,
                exams = exams,
                settings = it.settings.mergeCalendar(calendar).copy(
                    yearCode = profile.yearCode.ifBlank { year },
                    termCode = profile.termCode.ifBlank { term },
                ),
                session = SessionState(
                    loggedIn = true,
                    cookies = currentCookies().toMap(),
                    lastSyncAt = nowMillis(),
                    studentId = studentId.ifBlank { it.session.studentId },
                ),
                seeded = false,
            )
        }
        val notices = runCatching { mergeNoticeCache(portal().fetchNotices(), snap.notices) }.getOrElse {
            if (keepGradesIfFail) snap.notices else emptyList()
        }.mapIndexed { index, item ->
            if (index >= 5 || item.content.isNotBlank() || item.id.startsWith("msg:")) item
            else {
                val detail = runCatching { portal().fetchNoticeDetail(item.id) }.getOrNull()
                if (detail == null) item else item.mergeDetail(detail)
            }
        }
        commit { it.copy(notices = notices) }
        val hall = pullHall(keepGradesIfFail)
        commit { it.copy(hall = hall) }
        val utility = pullUtility(keepGradesIfFail)
        commitUtility(utility)
    }

    private suspend fun pullHall(keepGradesIfFail: Boolean): HallSnapshot {
        val snap = _state.value
        return when {
            snap.settings.school() != School.Gzus -> HallSnapshot()
            !snap.settings.gzusUsesCas() -> HallSnapshot(error = "要用统一身份认证登录才能看办事大厅")
            else -> runCatching { portal().fetchHall() ?: HallSnapshot(error = HALL_SESSION_HINT) }
                .getOrElse { error ->
                    if (error is JwxtNeedFirstLogin || isTransientNetwork(error)) throw error
                    if (keepGradesIfFail && snap.hall.ready) {
                        snap.hall.copy(error = error.message ?: "办事大厅同步失败")
                    } else {
                        HallSnapshot(error = error.message ?: "办事大厅同步失败")
                    }
                }
        }
    }

    private suspend fun pullUtility(keepGradesIfFail: Boolean): UtilitySnapshot {
        val snap = _state.value
        if (snap.settings.school() != School.Gzus) return UtilitySnapshot()
        return runCatching { queryUtility(snap) }.getOrElse { error ->
            if (error is JwxtNeedFirstLogin || isTransientNetwork(error)) throw error
            if (keepGradesIfFail && snap.utility.ready) {
                snap.utility.copy(error = error.message ?: "查询失败")
            } else {
                UtilitySnapshot(error = error.message ?: "查询失败")
            }
        }
    }

    /**
     * 一卡通的余额接口是公开的，不登录也能查。
     * 用统一身份认证登录时先走门户，好顺带认出本人宿舍；其余情况直接公开查询。
     */
    private suspend fun queryUtility(snap: AppSnapshot): UtilitySnapshot {
        val bind = snap.settings.resolvedUtilityBind()
        if (snap.session.loggedIn && snap.settings.gzusUsesCas()) {
            // 门户那条路会顺带认出本人宿舍，拿得到结果就用它的。
            runCatching {
                portal().fetchUtility(bind, snap.session.studentId.ifBlank { snap.profile.studentId })
            }.getOrNull()?.let { return it }
        }
        if (!bind.hasRoom()) return UtilitySnapshot(error = "未绑定宿舍")
        return ecard.balance(bind)
    }

    fun saveXiaoaiEditUrl(url: String) {
        val token = parseXiaoaiToken(url) ?: error("这不是小爱 UserInfo，也不是旧的 PC 编辑链接")
        if (token.expired()) error("这份授权看起来过期了。去小爱课程表重新复制一份 UserInfo。")
        updateSettings { it.copy(xiaoaiEditUrl = url.trim()) }
    }

    fun clearXiaoaiEditUrl() {
        updateSettings { it.copy(xiaoaiEditUrl = "") }
    }

    fun xiaoaiOfficialJson(): String =
        encodeXiaoaiOfficial(convertQingkeToXiaoai(_state.value).official)

    suspend fun importToXiaoai(): String {
        val snap = _state.value
        val converted = convertQingkeToXiaoai(snap)
        if (converted.apiCourses.isEmpty()) error("还没有能导入的课。先同步课表。实践课没有节次，不会进去。")
        val token = parseXiaoaiToken(snap.settings.xiaoaiEditUrl)
            ?: error("先粘贴小爱课程表的 UserInfo")
        return xiaoai.replaceCourses(token, converted.apiCourses)
    }

    suspend fun queryRooms(weekday: String, start: String, end: String): List<FreeRoom> {
        val snap = _state.value
        if (!snap.session.loggedIn) error("登录后才能查空教室")
        if (!portal().supportsFreeRooms) error("${snap.resolved().jwxtName}没有空教室查询")
        return withLiveSession {
            val rooms = portal().fetchFreeRooms(snap.settings.yearCode, snap.settings.termCode, weekday, start, end)
            commit { it.copy(rooms = rooms) }
            rooms
        }
    }

    suspend fun loadNoticeBody(id: String) {
        val existing = _state.value.notices.firstOrNull { it.id == id }
        if (existing != null && existing.content.isNotBlank()) return
        if (id.startsWith("msg:")) return
        if (!_state.value.session.loggedIn) error("登录后才能看通知正文")
        val detail = withLiveSession { portal().fetchNoticeDetail(id) }
        commit {
            it.copy(
                notices = it.notices.map { item ->
                    if (item.id == id) item.mergeDetail(detail) else item
                },
            )
        }
    }

    private suspend fun ensureCasTickets(force: Boolean): Boolean {
        if (gzusCas.refreshTickets(force = force)) return true
        return trySilentRelogin()
    }

    private suspend fun trySilentRelogin(): Boolean {
        val snap = _state.value
        if (snap.settings.school() != School.Gzus || !snap.settings.gzusUsesCas()) return false
        val secret = loadReloginSecret() ?: return false
        return runCatching {
            gzusCas.loginSilent(secret.first, secret.second).getOrThrow()
            markLoggedIn(secret.first)
            true
        }.getOrElse { failed ->
            if (failed is JwxtNeedFirstLogin) {
                clearReloginSecret()
                throw failed
            }
            if (isTransientNetwork(failed)) throw failed
            if (isReloginCredentialBad(failed.message.orEmpty())) {
                clearReloginSecret()
            }
            false
        }
    }

    private suspend fun shouldExpireWholeSession(): Boolean {
        if (gzusCas.hasTgt()) return false
        if (runCatching { gzusCas.probeEcard() }.getOrDefault(false)) return false
        if (runCatching { gzusCas.probeJwxt() }.getOrDefault(false)) return false
        return true
    }

    private fun isReloginCredentialBad(message: String): Boolean {
        val text = message
        return text.contains("学号或密码") ||
            text.contains("密码不正确") ||
            text.contains("账号不存在") ||
            text.contains("已停用") ||
            text.contains("已锁定") ||
            text.contains("没有教务权限") ||
            text.contains("二次验证") ||
            text.contains("绑定微信") ||
            text.contains("网络承诺")
    }

    private suspend fun <T> withLiveSession(probe: Boolean = true, block: suspend () -> T): T {
        try {
            val snap = _state.value
            if (probe && snap.session.loggedIn && snap.settings.school() == School.Gzus && snap.settings.gzusUsesCas()) {
                runCatching { ensureCasTickets(force = false) }
            }
            return block()
        } catch (failed: Throwable) {
            if (failed is JwxtNeedFirstLogin) throw failed
            if (isTransientNetwork(failed)) throw failed
            val message = failed.message.orEmpty()
            if (isHallSessionHint(message) || isEcardSessionHint(message)) throw failed
            val cas = _state.value.settings.school() == School.Gzus && _state.value.settings.gzusUsesCas()
            if (cas && isSessionLost(message)) {
                val revived = runCatching { ensureCasTickets(force = true) }.getOrDefault(false)
                if (revived) {
                    return try {
                        block()
                    } catch (again: Throwable) {
                        if (again is JwxtNeedFirstLogin || isTransientNetwork(again)) throw again
                        if (isHallSessionHint(again.message.orEmpty()) || isEcardSessionHint(again.message.orEmpty())) {
                            throw again
                        }
                        if (isSessionLost(again.message.orEmpty()) && shouldExpireWholeSession()) {
                            markSessionExpired()
                            error(SESSION_LOST_HINT)
                        }
                        throw again
                    }
                }
                if (shouldExpireWholeSession()) {
                    markSessionExpired()
                    error(SESSION_LOST_HINT)
                }
                throw failed
            }
            if (isSessionLost(message) && shouldExpireWholeSession()) {
                markSessionExpired()
                error(SESSION_LOST_HINT)
            }
            throw failed
        }
    }

    private fun markSessionExpired() {
        gzusCas.forgetTickets()
        clearCookieStore()
        cancelLiveClass()
        commit {
            it.copy(
                session = SessionState(studentId = it.session.studentId),
                hall = HallSnapshot(),
            )
        }
    }

    suspend fun logout() {
        runCatching { portal().logout() }
        clearReloginSecret()
        clearCookieStore()
        commit {
            it.copy(
                session = SessionState(studentId = it.session.studentId),
                grades = emptyList(),
                exams = emptyList(),
                rooms = emptyList(),
                hall = HallSnapshot(),
            )
        }
        cancelLiveClass()
    }

    fun saveUtilityRoom(building: String, room: String) {
        updateSettings {
            it.copy(
                utilityBuilding = building.trim(),
                utilityRoom = room.trim(),
                utilityBind = it.utilityBind.copy(buildingName = building.trim(), roomName = room.trim()),
            )
        }
    }

    fun saveUtilityBind(bind: UtilityBind) {
        updateSettings {
            it.copy(
                utilityBind = bind,
                utilityBuilding = bind.buildingName,
                utilityRoom = bind.roomName,
            )
        }
    }

    suspend fun listUtilityOptions(level: String, parentId: String = ""): List<UtilityOption> {
        if (_state.value.settings.school() != School.Gzus) error("宿舍选择只属于广软")
        val query = parentId.trim()
        if (query.isBlank()) error("请输入楼栋或房间号。")
        val hit = ecard.search(query)
        if (hit.isEmpty()) error("未找到宿舍")
        return hit.map { it.toOption() }
    }

    fun saveUtilityPrice(useCustom: Boolean, water: Double, electric: Double) {
        updateSettings {
            it.copy(
                utilityUseCustomPrice = useCustom,
                utilityWaterPrice = if (water > 0) water else JIANGMEN_WATER_PRICE,
                utilityElectricPrice = if (electric > 0) electric else JIANGMEN_ELECTRIC_PRICE,
            )
        }
    }

    suspend fun syncHall() {
        val snap = _state.value
        if (snap.settings.school() != School.Gzus) error("请假和办事大厅只属于广软")
        if (!snap.session.loggedIn) error("登录后才能同步办事大厅")
        if (!snap.settings.gzusUsesCas()) error("要用统一身份认证登录才能同步办事大厅")
        withLiveSession {
            val hall = portal().fetchHall() ?: HallSnapshot(error = HALL_SESSION_HINT)
            commit { it.copy(hall = hall) }
            if (!hall.ready && hall.error.isNotBlank()) error(hall.error)
        }
    }

    suspend fun loadLeaveForm(affairId: String = ""): LeaveForm {
        val snap = _state.value
        if (snap.settings.school() != School.Gzus) error("请假只属于广软")
        if (!snap.session.loggedIn) error("登录后才能请假")
        if (!snap.settings.gzusUsesCas()) error("要用统一身份认证登录才能请假")
        return withLiveSession { portal().fetchLeaveForm(affairId) ?: error("大厅没有返回请假表单") }
    }

    suspend fun loadLeaveTrace(instanceId: String): List<LeaveStep> {
        val snap = _state.value
        if (snap.settings.school() != School.Gzus) error("请假只属于广软")
        if (!snap.session.loggedIn) error("登录后才能看审批进度")
        if (!snap.settings.gzusUsesCas()) error("要用统一身份认证登录才能看审批进度")
        return withLiveSession { portal().fetchLeaveTrace(instanceId) }
    }

    suspend fun submitLeave(form: LeaveForm, values: Map<String, String>): String {
        val snap = _state.value
        if (snap.settings.school() != School.Gzus) error("请假只属于广软")
        if (!snap.session.loggedIn) error("登录后才能请假")
        if (!snap.settings.gzusUsesCas()) error("要用统一身份认证登录才能请假")
        return withLiveSession {
            val message = portal().submitLeave(form, values)
            runCatching { syncHall() }
            message
        }
    }

    fun queueCoursePick(offer: CoursePickOffer, section: CoursePickSection): CoursePickTask {
        val task = coursePickTaskOf(offer, section)
        updateSettings { settings ->
            settings.copy(coursePickQueue = settings.coursePickQueue.filterNot { it.id == task.id } + task)
        }
        return task
    }

    fun removeCoursePick(id: String) {
        updateSettings { settings ->
            settings.copy(coursePickQueue = settings.coursePickQueue.filterNot { it.id == id })
        }
    }

    fun scheduleCoursePick(id: String, fireAt: Long) {
        if (fireAt <= 0L) error("时间填成 2026-09-01 和 13:00:00")
        patchCoursePick(id) {
            it.copy(fireAt = fireAt, status = "waiting", message = "到点自动提交")
        }
    }

    suspend fun loadCoursePickScopes(): List<CoursePickScope> = withLiveSession {
        requireCoursePick().fetchCoursePickScopes()
    }

    suspend fun searchCoursePicks(scope: CoursePickScope, keyword: String): List<CoursePickOffer> = withLiveSession {
        requireCoursePick().fetchCoursePickOffers(scope, keyword.trim())
    }

    suspend fun loadCoursePickSections(offer: CoursePickOffer): List<CoursePickSection> = withLiveSession {
        requireCoursePick().fetchCoursePickSections(offer)
    }

    suspend fun loadCoursePicked(scope: CoursePickScope): List<CoursePickOffer> = withLiveSession {
        runCatching { requireCoursePick().fetchCoursePicked(scope) }.getOrDefault(emptyList())
    }

    suspend fun selectCourseNow(offer: CoursePickOffer, section: CoursePickSection): String = pickMutex.withLock {
        withLiveSession {
            val message = selectCourseWithRetry(offer, section, attempts = 3)
            markCoursePickResult(offer, section, message, ok = true)
            message
        }
    }

    suspend fun runQueuedCoursePick(id: String): String = pickMutex.withLock {
        val task = _state.value.settings.coursePickQueue.firstOrNull { it.id == id }
            ?: error("队列里没有这门课")
        runQueuedTask(task, attempts = 3)
    }

    suspend fun runDueCoursePicks(): List<String> = pickMutex.withLock {
        val now = nowMillis()
        val due = _state.value.settings.coursePickQueue
            .filter { it.status == "waiting" && it.fireAt > 0L && it.fireAt <= now }
        val out = mutableListOf<String>()
        for (task in due) {
            val line = runCatching { runQueuedTask(task, attempts = 5) }
                .getOrElse { failed ->
                    if (isSessionLost(failed.message.orEmpty())) throw failed
                    "${task.courseName}：${failed.message ?: "选课失败"}"
                }
            out += if (line.contains(task.courseName)) line else "${task.courseName}：$line"
        }
        out
    }

    private fun requireCoursePick(): SchoolPortal {
        val snap = _state.value
        if (!snap.session.loggedIn) error("登录后才能选课")
        if (!snap.resolved().supportsCoursePick) error("${snap.resolved().jwxtName}没有正方自主选课")
        return portal()
    }

    private suspend fun runQueuedTask(task: CoursePickTask, attempts: Int): String = withLiveSession {
        patchCoursePick(task.id) {
            it.copy(status = "running", message = "正在提交", lastAttemptAt = nowMillis())
        }
        val result = runCatching {
            selectCourseWithRetry(offerFromTask(task), sectionFromTask(task), attempts)
        }
        val now = nowMillis()
        result.fold(
            onSuccess = { message ->
                patchCoursePick(task.id) { it.copy(status = "ok", message = message, lastAttemptAt = now) }
                message
            },
            onFailure = { failed ->
                val text = failed.message.orEmpty()
                if (isSessionLost(text)) {
                    patchCoursePick(task.id) {
                        it.copy(status = "waiting", message = SESSION_LOST_HINT, lastAttemptAt = now)
                    }
                    throw failed
                }
                patchCoursePick(task.id) { it.copy(status = "fail", message = text, lastAttemptAt = now) }
                throw failed
            },
        )
    }

    private suspend fun selectCourseWithRetry(
        offer: CoursePickOffer,
        section: CoursePickSection,
        attempts: Int,
    ): String {
        var last = ""
        val portal = requireCoursePick()
        val times = attempts.coerceIn(1, 5)
        repeat(times) { index ->
            val result = runCatching { portal.selectCoursePick(offer, section) }
            result.onSuccess { return it }
            last = result.exceptionOrNull()?.message.orEmpty()
            if (isSessionLost(last)) throw result.exceptionOrNull()!!
            if (!coursePickRetryable(last) || index == times - 1) error(last.ifBlank { "选课失败" })
            delay(2_000)
        }
        error(last.ifBlank { "选课失败" })
    }

    private fun markCoursePickResult(
        offer: CoursePickOffer,
        section: CoursePickSection,
        message: String,
        ok: Boolean,
    ) {
        val id = coursePickTaskId(offer.scopeId, offer.courseId, section.doJxbId.ifBlank { section.classId })
        val now = nowMillis()
        updateSettings { settings ->
            settings.copy(
                coursePickQueue = settings.coursePickQueue.map { task ->
                    if (task.id != id) task
                    else task.copy(status = if (ok) "ok" else "fail", message = message, lastAttemptAt = now)
                },
            )
        }
    }

    private fun patchCoursePick(id: String, transform: (CoursePickTask) -> CoursePickTask) {
        updateSettings { settings ->
            settings.copy(
                coursePickQueue = settings.coursePickQueue.map { task ->
                    if (task.id == id) transform(task) else task
                },
            )
        }
    }

    suspend fun syncUtility() {
        val snap = _state.value
        if (snap.settings.school() != School.Gzus) error("宿舍水电只属于广软")
        val utility = queryUtility(snap)
        commitUtility(utility)
        if (!utility.ready && utility.error.isNotBlank()) error(utility.error)
    }

    private fun commitUtility(utility: UtilitySnapshot) {
        commit { current ->
            // 查到的宿舍号补进设置里：没绑过就整份存，绑过就只补上缺的 id，
            // 这样下次刷新能直接查余额，不用再拉整份宿舍表。
            val nextSettings = when {
                !utility.bind.hasRoom() -> current.settings
                !current.settings.hasUtilityBind() -> current.settings.copy(
                    utilityBind = utility.bind,
                    utilityBuilding = utility.bind.buildingName,
                    utilityRoom = utility.bind.roomName,
                )
                else -> {
                    val merged = current.settings.resolvedUtilityBind().fillIdsFrom(utility.bind)
                    if (merged == current.settings.utilityBind) {
                        current.settings
                    } else {
                        current.settings.copy(
                            utilityBind = merged,
                            utilityBuilding = merged.buildingName,
                            utilityRoom = merged.roomName,
                        )
                    }
                }
            }
            current.copy(utility = utility, settings = nextSettings)
        }
    }

    fun startLiveTest(): String {
        resetLiveDismiss()
        val now = nowMillis()
        testStartMillis = now - 2 * 60_000
        testEndMillis = now + 8 * 60_000
        _liveTick.update { it + 1 }
        val posted = refreshLive()
        val status = liveUpdateStatus(_state.value.settings.resolvedRemindLead())
        return when {
            !posted && status.contains("权限") -> "没有通知权限，先到系统设置打开通知"
            !posted -> "没有发出通知：$status"
            status.contains("未开启") -> "通知已发出。进度在走，系统还没开 Live Update 的话，去设置打开。"
            else -> "通知已发出。这是上课中进度，大约 8 分钟走完。"
        }
    }

    fun stopLiveTest() {
        testStartMillis = 0L
        testEndMillis = 0L
        _liveTick.update { it + 1 }
        cancelLiveClass()
        refreshLive()
    }

    fun refreshLive(): Boolean {
        val nowMs = nowMillis()
        if (testEndMillis > nowMs) {
            val remain = ((testStartMillis - nowMs) / 60_000L).toInt()
            val progress = when {
                nowMs <= testStartMillis -> 0f
                nowMs >= testEndMillis -> 1f
                else -> ((nowMs - testStartMillis).toFloat() / (testEndMillis - testStartMillis).toFloat()).coerceIn(0f, 1f)
            }
            val inClass = nowMs >= testStartMillis
            val remainEnd = ((testEndMillis - nowMs) / 60_000L).toInt().coerceAtLeast(0)
            return notifyLiveClass(
                title = if (inClass) "正在上课（测试）" else "下一节（测试）",
                detail = "3-4节 · 测试教室",
                progress = progress,
                etaMinutes = if (inClass) remainEnd else remain.coerceAtLeast(0),
                startMillis = testStartMillis,
                endMillis = testEndMillis,
                chip = if (inClass) "下课 ${remainEnd}′" else "${remain.coerceAtLeast(1)}′后",
            )
        }
        if (testEndMillis > 0L && nowMs >= testEndMillis) {
            testStartMillis = 0L
            testEndMillis = 0L
            _liveTick.update { it + 1 }
        }
        val snap = _state.value
        if (!snap.resolved().hasPeriodClock) {
            cancelLiveClass()
            return false
        }
        if (!snap.settings.remindBeforeClass || snap.slots.isEmpty()) {
            cancelLiveClass()
            return false
        }
        val now = nowDateTime()
        val next = nextLiveLesson(snap.slots, now.date, snap.settings, now.date, now.time, snap.scheduleAdjust)
        if (next == null) {
            cancelLiveClass()
            return false
        }
        val slot = next.slot
        if (!next.inClass && next.minutesToStart > snap.settings.resolvedRemindLead()) {
            cancelLiveClass()
            return false
        }
        val startMillis = combineMillis(now.date, periodStart(slot.period))
        val endMillis = combineMillis(now.date, periodEnd(slot.period))
        if (startMillis <= 0L || endMillis <= startMillis) {
            cancelLiveClass()
            return false
        }
        val progress = when {
            nowMs <= startMillis -> 0f
            nowMs >= endMillis -> 1f
            else -> ((nowMs - startMillis).toFloat() / (endMillis - startMillis).toFloat()).coerceIn(0f, 1f)
        }
        val period = slot.periodLabel.ifBlank { slot.period }.let { if (it.endsWith("节")) it else "${it}节" }
        val clock = periodClockRange(slot.period).replace("-", "–")
        val room = slot.room.ifBlank { "教室待定" }
        val detail = listOf(period, clock, room).filter { it.isNotBlank() }.joinToString(" · ")
        val chip: String
        val etaMinutes: Int
        if (next.inClass) {
            chip = "下课 ${next.minutesToEnd}′"
            etaMinutes = next.minutesToEnd
        } else {
            chip = "${next.minutesToStart}′后"
            etaMinutes = next.minutesToStart
        }
        // 小组件上的"上课中/下一节"要跟着走，但没换课就别重画位图。
        val widgetKey = "${slot.courseId}/${slot.period}/${next.inClass}"
        if (widgetKey != liveWidgetKey) {
            liveWidgetKey = widgetKey
            refreshHomeWidgets()
        }
        return notifyLiveClass(
            title = slot.courseName,
            detail = detail,
            progress = progress,
            etaMinutes = etaMinutes,
            startMillis = startMillis,
            endMillis = endMillis,
            chip = chip,
        )
    }

    private fun coursePickTaskOf(offer: CoursePickOffer, section: CoursePickSection): CoursePickTask {
        val doJxb = section.doJxbId.ifBlank { section.classId }
        return CoursePickTask(
            id = coursePickTaskId(offer.scopeId, offer.courseId, doJxb),
            courseId = offer.courseId,
            courseName = offer.name,
            className = section.name.ifBlank { offer.className },
            teacher = section.teacher.ifBlank { offer.teacher },
            doJxbId = doJxb,
            classId = section.classId.ifBlank { offer.classId },
            scopeId = offer.scopeId,
            params = offer.params + section.params,
            status = "queued",
        )
    }
}

private fun coursePickTaskId(scopeId: String, courseId: String, doJxbId: String): String =
    listOf(scopeId, courseId, doJxbId).joinToString("|")

private fun offerFromTask(task: CoursePickTask): CoursePickOffer = CoursePickOffer(
    courseId = task.courseId,
    name = task.courseName,
    teacher = task.teacher,
    className = task.className,
    classId = task.classId,
    scopeId = task.scopeId,
    params = task.params,
)

private fun sectionFromTask(task: CoursePickTask): CoursePickSection = CoursePickSection(
    classId = task.classId,
    doJxbId = task.doJxbId,
    name = task.className,
    teacher = task.teacher,
    params = task.params,
)

private fun coursePickRetryable(message: String): Boolean {
    val text = message
    if (listOf("冲突", "已满", "学分", "已经选", "已选该", "重复", "无余量").any { text.contains(it) }) return false
    return listOf("未开始", "未开放", "不在选课", "选课时间", "系统繁忙", "稍后重试", "请稍后再试").any { text.contains(it) } ||
        text.contains("timeout", ignoreCase = true) ||
        text.contains("超时")
}

fun formatRemain(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60} 小时 ${minutes % 60} 分"
    minutes > 0 -> "${minutes} 分钟"
    else -> "进行中"
}

private fun AppSettings.mergeCalendar(calendar: TermCalendar?): AppSettings {
    if (calendar == null) return this
    return copy(
        yearCode = calendar.yearCode.ifBlank { yearCode },
        termCode = calendar.termCode.ifBlank { termCode },
        currentWeek = calendar.currentWeek.takeIf { it > 0 } ?: currentWeek,
        termStart = calendar.termStart.ifBlank { termStart },
        weekCount = calendar.weekCount.takeIf { it > 0 } ?: weekCount,
    )
}

fun formatSync(ts: Long): String {
    if (ts <= 0L) return "尚未同步"
    val delta = (nowMillis() - ts) / 1000
    return when {
        delta < 60 -> "同步于刚刚"
        delta < 3600 -> "同步于 ${delta / 60} 分钟前"
        delta < 86400 -> "同步于 ${delta / 3600} 小时前"
        else -> "同步于 ${delta / 86400} 天前"
    }
}

private fun AppSnapshot.widgetSignature(): Int {
    var h = slots.hashCode()
    h = 31 * h + holidays.days.hashCode()
    h = 31 * h + settings.termStart.hashCode()
    h = 31 * h + settings.currentWeek
    h = 31 * h + settings.weekCount
    h = 31 * h + settings.schoolId.hashCode()
    h = 31 * h + settings.courseAliases.hashCode()
    h = 31 * h + settings.scheduleShifts.hashCode()
    return h
}

private fun nowMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

