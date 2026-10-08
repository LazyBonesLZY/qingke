package cn.edu.gzus.qingke.data

enum class School(
    val id: String,
    val label: String,
    val jwxtName: String,
    val loginUrl: String,
    val changePasswordUrl: String,
    val supportsFreeRooms: Boolean,
    val hasPeriodClock: Boolean,
) {
    Gzus(
        id = "gzus",
        label = "广州软件学院",
        jwxtName = "正方",
        loginUrl = JWXT_LOGIN_URL,
        changePasswordUrl = JWXT_CHANGE_PASSWORD_URL,
        supportsFreeRooms = true,
        hasPeriodClock = true,
    ),
    Zhku(
        id = "zhku",
        label = "仲恺农业工程学院",
        jwxtName = "仲恺教务",
        loginUrl = ZHKU_LOGIN_URL,
        changePasswordUrl = ZHKU_CHANGE_PASSWORD_URL,
        supportsFreeRooms = false,
        hasPeriodClock = false,
    ),
    Gzist(
        id = "gzist",
        label = "广州理工学院",
        jwxtName = "理工教务",
        loginUrl = GZIST_CAS_LOGIN_URL,
        changePasswordUrl = GZIST_CHANGE_PASSWORD_URL,
        supportsFreeRooms = false,
        hasPeriodClock = false,
    ),
    Gdsty(
        id = "gdsty",
        label = "广东生态工程职业学院",
        jwxtName = "生态教务",
        loginUrl = GDTSY_AIC_LOGIN_URL,
        changePasswordUrl = GDTSY_CHANGE_PASSWORD_URL,
        supportsFreeRooms = false,
        hasPeriodClock = true,
    ),
    Custom(
        id = "custom",
        label = "自定义教务",
        jwxtName = "自定义",
        loginUrl = "",
        changePasswordUrl = "",
        supportsFreeRooms = false,
        hasPeriodClock = false,
    );

    val periodBlocks: List<PeriodBlock>
        get() = when (this) {
            Gzus, Gzist, Custom -> MajorPeriods
            Zhku -> ZhkuPeriods
            Gdsty -> GdstyPeriods
        }

    val showsCaptcha: Boolean
        get() = this == Gzist || this == Gzus || this == Gdsty

    val requiresCaptcha: Boolean
        get() = this == Gzist || this == Gdsty

    val captchaHint: String
        get() = when (this) {
            Gzist -> "门户算术验证码，填得数"
            Gzus -> "教务验证码，登录多了会要"
            Gdsty -> "综合系统验证码，4 位数字"
            Zhku, Custom -> ""
        }

    companion object {
        val Default = Gzus

        fun of(id: String): School = entries.firstOrNull { it.id == id } ?: Default
    }
}

val ZhkuPeriods = listOf(
    PeriodBlock(1, "1-2", "", "", "上午"),
    PeriodBlock(2, "3-4", "", "", "上午"),
    PeriodBlock(3, "5", "", "", "中午"),
    PeriodBlock(4, "6-7", "", "", "下午"),
    PeriodBlock(5, "8-9", "", "", "下午"),
    PeriodBlock(6, "10-12", "", "", "晚上"),
)

// 教务课表页每行节次都带着起止时间，照抄，不要自己编。
val GdstyPeriods = listOf(
    PeriodBlock(1, "1-2", "08:30", "09:50", "上午"),
    PeriodBlock(2, "3-4", "10:20", "11:50", "上午"),
    PeriodBlock(3, "中午", "12:20", "13:50", "中午"),
    PeriodBlock(4, "5-6", "14:10", "15:40", "下午"),
    PeriodBlock(5, "7-8", "16:00", "17:30", "下午"),
    PeriodBlock(6, "9-10", "18:30", "20:00", "晚上"),
    PeriodBlock(7, "11-12", "20:10", "21:40", "晚上"),
)

fun AppSettings.school(): School = School.of(schoolId)

fun AppSnapshot.school(): School = settings.school()

fun AppSettings.gzusUsesCas(): Boolean = school() == School.Gzus && gzusLoginChannel == GZUS_LOGIN_CAS

/**
 * 会话会自己过期的门户，前台要隔几分钟打一下。
 * 广软是 CAS 票，广生态是强智 jsxsd 的 JSESSIONID。
 */
fun AppSettings.needsKeepAlive(): Boolean = gzusUsesCas() || school() == School.Gdsty

fun AppSnapshot.showsGzusHall(): Boolean = settings.school() == School.Gzus

data class ResolvedSchool(
    val id: String,
    val enum: School,
    val label: String,
    val jwxtName: String,
    val loginUrl: String,
    val changePasswordUrl: String,
    val supportsFreeRooms: Boolean,
    val hasPeriodClock: Boolean,
    val showsCaptcha: Boolean,
    val requiresCaptcha: Boolean,
    val captchaHint: String,
    val periodBlocks: List<PeriodBlock>,
    val kind: String,
    val origin: String,
    val loginName: String,
) {
    val supportsCoursePick: Boolean get() = kind == "zhengfang"
}

private fun AppSettings.resolvedBase(): ResolvedSchool {
    val item = school()
    if (item == School.Gzus && gzusUsesCas()) {
        return ResolvedSchool(
            id = item.id,
            enum = item,
            label = item.label,
            jwxtName = item.jwxtName,
            loginUrl = GZUS_CAS_LOGIN_URL,
            changePasswordUrl = GZUS_CAS_PASSWORD_URL,
            supportsFreeRooms = item.supportsFreeRooms,
            hasPeriodClock = item.hasPeriodClock,
            showsCaptcha = true,
            requiresCaptcha = true,
            captchaHint = "门户算术验证码，填得数",
            periodBlocks = item.periodBlocks,
            kind = "zhengfang",
            origin = JWXT_ORIGIN,
            loginName = "统一身份认证",
        )
    }
    if (item != School.Custom) {
        return ResolvedSchool(
            id = item.id,
            enum = item,
            label = item.label,
            jwxtName = item.jwxtName,
            loginUrl = item.loginUrl,
            changePasswordUrl = item.changePasswordUrl,
            supportsFreeRooms = item.supportsFreeRooms,
            hasPeriodClock = item.hasPeriodClock,
            showsCaptcha = item.showsCaptcha,
            requiresCaptcha = item.requiresCaptcha,
            captchaHint = item.captchaHint,
            periodBlocks = item.periodBlocks,
            kind = when (item) {
                School.Zhku -> "kingosoft"
                School.Gzist -> "lyuap"
                School.Gdsty -> "kingosoft"
                else -> "zhengfang"
            },
            origin = when (item) {
                School.Gzus -> JWXT_ORIGIN
                School.Zhku -> "https://edu-admin.zhku.edu.cn"
                School.Gzist -> GZIST_JWXT_ORIGIN
                School.Gdsty -> GDTSY_JWXT_ORIGIN
                School.Custom -> ""
            },
            loginName = item.jwxtName,
        )
    }
    val cfg = customJwxt
    val origin = cfg.originClean()
    val kind = cfg.normalizedKind()
    val login = cfg.loginUrl.trim().ifBlank {
        when (kind) {
            "kingosoft" -> if (origin.isBlank()) "" else "$origin/Logon.do?method=logon"
            "lyuap" -> cfg.casOrigin.trim().ifBlank { origin }
            else -> if (origin.isBlank()) "" else "$origin/jwglxt/xtgl/login_slogin.html"
        }
    }
    val change = cfg.changePasswordUrl.trim().ifBlank {
        when (kind) {
            "kingosoft" -> if (origin.isBlank()) "" else "$origin/jsxsd/grsz/grsz_xgmm"
            "lyuap" -> cfg.casOrigin.trim()
            else -> if (origin.isBlank()) "" else "$origin/jwglxt/xtgl/mmgl_xgMm.html"
        }
    }
    return ResolvedSchool(
        id = item.id,
        enum = item,
        label = cfg.name.trim().ifBlank { item.label },
        jwxtName = cfg.name.trim().ifBlank { "自定义教务" },
        loginUrl = login,
        changePasswordUrl = change,
        supportsFreeRooms = cfg.supportsFreeRooms && kind == "zhengfang",
        hasPeriodClock = cfg.hasPeriodClock,
        showsCaptcha = cfg.showsCaptcha || kind == "lyuap" || (kind == "zhengfang" && cfg.requiresCaptcha),
        requiresCaptcha = cfg.requiresCaptcha || kind == "lyuap",
        captchaHint = cfg.captchaHint.trim().ifBlank {
            when (kind) {
                "lyuap" -> "门户验证码"
                "zhengfang" -> "教务验证码"
                else -> ""
            }
        },
        periodBlocks = if (kind == "kingosoft") ZhkuPeriods else MajorPeriods,
        kind = kind,
        origin = origin,
        loginName = cfg.name.trim().ifBlank { "自定义教务" },
    )
}

/**
 * 把 "HH:mm-HH:mm" 形式的节次时间套到预设块上。返回是否真的改到了东西。
 */
private fun ResolvedSchool.applyPeriodTimes(times: Map<String, String>): Pair<ResolvedSchool, Boolean> {
    if (times.isEmpty()) return this to false
    var changed = false
    val patched = periodBlocks.map { block ->
        val parts = times[block.label]?.trim().orEmpty().split("-", limit = 2)
        // 两侧都必须能解析成 HH:mm 才采纳。半截值（"14:10-"）会让
        // periodEndOf 拿到真值、clockRangeOf 却整块跳过，两个 helper 互相打架。
        val start = parts.getOrNull(0)?.trim().orEmpty().let(::normalizeClock)
        val end = parts.getOrNull(1)?.trim().orEmpty().let(::normalizeClock)
        if (parts.size != 2 || start == null || end == null) {
            block
        } else {
            changed = true
            block.copy(start = start, end = end)
        }
    }
    return copy(periodBlocks = patched) to changed
}

/**
 * 套用同步时从教务抄下来的节次时间（优先级低于手动覆盖）。
 * 教务页里有时间就说明这所学校确实有作息，顺手把 hasPeriodClock 打开。
 */
fun ResolvedSchool.withFetchedPeriodTimes(times: Map<String, String>): ResolvedSchool {
    val (patched, changed) = applyPeriodTimes(times)
    return if (changed) patched.copy(hasPeriodClock = true) else this
}

/**
 * 在学校预设之上套用用户自定义的节次时间（键为节次块标签，值为 "HH:mm-HH:mm"）。
 * 只要用户改过任意一节，就认为他要看时间，把 hasPeriodClock 打开。
 */
fun ResolvedSchool.withPeriodTimeOverrides(overrides: Map<String, String>): ResolvedSchool {
    val (patched, changed) = applyPeriodTimes(overrides)
    return if (changed) patched.copy(hasPeriodClock = true) else this
}

/** 只看学校预设，不叠加任何覆盖。 */
fun AppSettings.resolved(): ResolvedSchool = resolvedBase()

/**
 * 完整解析：学校预设 → 教务自动获取 → 用户手动覆盖。
 */
fun AppSnapshot.resolved(): ResolvedSchool = settings.resolvedBase()
    .withFetchedPeriodTimes(profile.periodTimes)
    .withPeriodTimeOverrides(settings.periodTimeOverrides)

interface SchoolPortal {
    val supportsFreeRooms: Boolean
    suspend fun fetchCaptcha(): LoginCaptcha? = null
    suspend fun login(
        studentId: String,
        password: String,
        captcha: String = "",
        captchaId: String = "",
    ): Result<Unit>
    suspend fun fetchTermCalendar(): TermCalendar
    suspend fun fetchTimetable(year: String, term: String): Triple<StudentProfile, List<LessonSlot>, List<PracticeCourse>>
    suspend fun fetchGrades(): List<GradeItem>
    suspend fun fetchExams(year: String, term: String): List<ExamItem>
    suspend fun fetchFreeRooms(year: String, term: String, weekday: String, start: String, end: String): List<FreeRoom>
    suspend fun fetchNotices(): List<NoticeItem>
    suspend fun fetchNoticeDetail(id: String): NoticeItem
    suspend fun keepAlive(): Boolean = true
    /** 强智那套会话是页面上探的，别的门户不用管。 */
    suspend fun ensureSession() {}
    suspend fun fetchHall(): HallSnapshot? = null
    suspend fun fetchLeaveForm(affairId: String = ""): LeaveForm? = null
    suspend fun submitLeave(form: LeaveForm, values: Map<String, String>): String =
        error("这所学校没有请假接口")
    suspend fun fetchLeaveTrace(instanceId: String): List<LeaveStep> = emptyList()
    suspend fun fetchUtility(bind: UtilityBind = UtilityBind(), sno: String = ""): UtilitySnapshot? = null
    suspend fun fetchUtilityOptions(level: String, parentId: String = ""): List<UtilityOption> = emptyList()
    val supportsCoursePick: Boolean get() = false
    suspend fun fetchCoursePickScopes(): List<CoursePickScope> = emptyList()
    suspend fun fetchCoursePickOffers(
        scope: CoursePickScope,
        keyword: String = "",
        start: Int = 0,
        pageSize: Int = 50,
    ): List<CoursePickOffer> = emptyList()
    suspend fun fetchCoursePickSections(offer: CoursePickOffer): List<CoursePickSection> = emptyList()
    suspend fun selectCoursePick(offer: CoursePickOffer, section: CoursePickSection): String =
        error("这所学校没有正方自主选课")
    suspend fun fetchCoursePicked(scope: CoursePickScope): List<CoursePickOffer> = emptyList()
    suspend fun logout()
}

fun zhkuTermId(year: String, term: String): String {
    val compact = year.trim()
    val half = when (term.trim()) {
        "12", "2" -> "2"
        else -> "1"
    }
    val parts = compact.split("-").filter { it.isNotBlank() }
    if (parts.size >= 3) return compact
    if (parts.size == 2) return "${parts[0]}-${parts[1]}-$half"
    val start = compact.toIntOrNull() ?: return ""
    return "$start-${start + 1}-$half"
}

fun parseZhkuTerm(xnxqid: String): Pair<String, String> {
    val parts = xnxqid.trim().split("-")
    if (parts.size < 3) return xnxqid.trim() to "1"
    return parts[0] to parts.last()
}
