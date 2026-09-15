package cn.edu.gzus.qingke.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

private const val STORE = "qingke-snapshot.json"

class AppRepository(
    private val gzus: JwxtClient = JwxtClient(),
    private val zhku: ZhkuClient = ZhkuClient(),
    private val gzist: GzistClient = GzistClient(),
    private val xiaoai: XiaoaiClient = XiaoaiClient(),
    private val holidays: HolidayClient = HolidayClient(),
    private val updates: UpdateClient = UpdateClient(),
) {
    private fun portal(): SchoolPortal {
        val settings = _state.value.settings
        return when (settings.school()) {
            School.Gzus -> gzus
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

    private fun persist(snapshot: AppSnapshot) {
        writeStore(STORE, json.encodeToString(AppSnapshot.serializer(), snapshot))
        refreshHomeWidgets()
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
        clearCookieStore()
        _captcha.value = null
        _captchaError.value = null
        cancelLiveClass()
        commit {
            AppSnapshot(
                settings = AppSettings(
                    remindBeforeClass = it.settings.remindBeforeClass,
                    darkModeFollowSystem = it.settings.darkModeFollowSystem,
                    schoolId = school.id,
                    yearCode = when {
                        school == School.Zhku -> ""
                        school == School.Custom && it.settings.customJwxt.normalizedKind() == "kingosoft" -> ""
                        else -> "2026"
                    },
                    termCode = when {
                        school == School.Zhku -> ""
                        school == School.Custom && it.settings.customJwxt.normalizedKind() == "kingosoft" -> ""
                        else -> "3"
                    },
                    xiaoaiEditUrl = it.settings.xiaoaiEditUrl,
                    remindLeadMinutes = it.settings.remindLeadMinutes,
                    customJwxt = it.settings.customJwxt,
                ),
                holidays = it.holidays,
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

    suspend fun checkGithubUpdate(): AppUpdate = updates.checkGithub()

    suspend fun loginAndSync(studentId: String, password: String, captcha: String = "", captchaId: String = "") {
        portal().login(studentId, password, captcha, captchaId).getOrThrow()
        _captcha.value = null
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

    suspend fun sync() {
        if (!_state.value.session.loggedIn) error("还没有登录")
        try {
            pullRemote(studentId = _state.value.session.studentId, keepGradesIfFail = true)
            runCatching { syncHolidays() }
            refreshLive()
        } catch (error: Throwable) {
            if (isSessionLost(error.message.orEmpty())) {
                markSessionExpired()
                error("登录已过期，请重新登录")
            }
            throw error
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
        val calendar = portal().fetchTermCalendar()
        commit { it.copy(settings = it.settings.mergeCalendar(calendar)) }
        refreshLive()
    }

    private suspend fun pullRemote(studentId: String, keepGradesIfFail: Boolean) {
        val snap = _state.value
        val calendar = runCatching { portal().fetchTermCalendar() }.getOrNull()
        val year = calendar?.yearCode?.ifBlank { null } ?: snap.settings.yearCode
        val term = calendar?.termCode?.ifBlank { null } ?: snap.settings.termCode
        val (profile, slots, practices) = portal().fetchTimetable(year, term)
        val grades = runCatching { portal().fetchGrades() }.getOrElse {
            if (keepGradesIfFail) snap.grades else emptyList()
        }
        val exams = runCatching { portal().fetchExams(year, term) }.getOrElse {
            if (keepGradesIfFail) snap.exams else emptyList()
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
        commit {
            it.copy(
                profile = profile.copy(studentId = studentId.ifBlank { profile.studentId }),
                slots = slots,
                practices = practices,
                grades = grades,
                exams = exams,
                notices = notices,
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
        val rooms = portal().fetchFreeRooms(snap.settings.yearCode, snap.settings.termCode, weekday, start, end)
        commit { it.copy(rooms = rooms) }
        return rooms
    }

    suspend fun loadNoticeBody(id: String) {
        val existing = _state.value.notices.firstOrNull { it.id == id }
        if (existing != null && existing.content.isNotBlank()) return
        if (id.startsWith("msg:")) return
        if (!_state.value.session.loggedIn) error("登录后才能看通知正文")
        val detail = portal().fetchNoticeDetail(id)
        commit {
            it.copy(
                notices = it.notices.map { item ->
                    if (item.id == id) item.mergeDetail(detail) else item
                },
            )
        }
    }

    private fun markSessionExpired() {
        clearCookieStore()
        cancelLiveClass()
        commit {
            it.copy(session = SessionState(studentId = it.session.studentId))
        }
    }

    suspend fun logout() {
        runCatching { portal().logout() }
        clearCookieStore()
        commit {
            it.copy(
                session = SessionState(studentId = it.session.studentId),
                grades = emptyList(),
                exams = emptyList(),
                rooms = emptyList(),
            )
        }
        cancelLiveClass()
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
                title = "下一节（测试）",
                detail = if (inClass) {
                    "测试下课 · 3-4节 · 测试教室\n还剩 ${formatRemain(remainEnd.coerceAtLeast(1))}"
                } else {
                    "测试上课 · 3-4节 · 测试教室\n还有 ${formatRemain(remain.coerceAtLeast(1))}"
                },
                progress = progress,
                etaMinutes = if (inClass) remainEnd else remain.coerceAtLeast(0),
                startMillis = testStartMillis,
                endMillis = testEndMillis,
                chip = if (inClass) "下课${remainEnd}分" else "${remain.coerceAtLeast(1)}分",
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
        val weekday = weekdayIndex(now.date)
        val next = nextLiveLesson(snap.slots, resolvedCurrentWeek(snap.settings, now.date), weekday, now.time)
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
        val period = slot.periodLabel.ifBlank { slot.period }
        val room = slot.room.ifBlank { "教室待定" }
        val detail: String
        val chip: String
        val etaMinutes: Int
        if (next.inClass) {
            detail = "${periodEnd(slot.period)} 下课 · $period · $room\n还剩 ${formatRemain(next.minutesToEnd)}"
            chip = "下课${next.minutesToEnd}分"
            etaMinutes = next.minutesToEnd
        } else {
            detail = "${periodStart(slot.period)} 上课 · $period · $room\n还有 ${formatRemain(next.minutesToStart)}"
            chip = "${next.minutesToStart}分"
            etaMinutes = next.minutesToStart
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

private fun nowMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

private fun isSessionLost(message: String): Boolean =
    message.contains("登录已过期") || message.contains("请重新登录")
