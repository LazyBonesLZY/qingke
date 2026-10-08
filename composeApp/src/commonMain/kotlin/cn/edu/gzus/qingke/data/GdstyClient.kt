package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 广东生态工程职业学院：综合系统（AIC）登录拿票，再跳进强智 jsxsd 取数据。
 *
 * 教务不给直登——jsxsd 登录页自己带验证码，而且账号只认 AIC 签发的票，
 * 所以密码得先在 AIC 用它的 1024 位公钥加密，再从 slogin.aspx 换出跳转票。
 * 票之后的课表、成绩、考试、周历、学籍全是标准强智页面，解析复用仲恺那套。
 */
class GdstyClient(
    private val client: HttpClient = createHttpClient(),
    private val aicOrigin: String = GDTSY_AIC_ORIGIN,
    private val jwOrigin: String = GDTSY_JWXT_ORIGIN,
) : SchoolPortal {
    override val supportsFreeRooms: Boolean = false

    private val AIC = aicOrigin.trim().trimEnd('/')
    private val JW = jwOrigin.trim().trimEnd('/')
    private val BASE = "$JW/jsxsd"
    private val cookieHost = JW.substringAfter("://").substringBefore("/")

    // 验证码和 __VIEWSTATE 是同一张登录页的产物：重开页面会让用户刚填的码作废，
    // 所以这两样必须一起留到提交那一刻。按 captchaId 分桶存：并发两次登录
    //（自动重登撞上用户手动点）不会互相覆盖，对方拿旧码提交只会拿到自己的 VIEWSTATE。
    private val pendingLogins = mutableMapOf<String, Pair<String, String>>()
    private var lastStudentId = ""

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun fetchCaptcha(): LoginCaptcha? {
        val html = client.get("$AIC$AIC_LOGIN_PATH") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
        }.bodyAsText()
        val viewState = hiddenField(html, "__VIEWSTATE")
        val viewStateGen = hiddenField(html, "__VIEWSTATEGENERATOR")
        val encoded = AIC_CAPTCHA.find(html)?.groupValues?.get(1) ?: return null
        val bytes = runCatching { Base64.decode(encoded) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val id = md5Hex(encoded)
        pendingLogins[id] = viewState to viewStateGen
        if (pendingLogins.size > 5) {
            val drop = pendingLogins.keys.firstOrNull()
            if (drop != null && drop != id) pendingLogins.remove(drop)
        }
        return LoginCaptcha(id = id, bytes = bytes, hint = "综合系统验证码，4 位数字")
    }

    override suspend fun login(
        studentId: String,
        password: String,
        captcha: String,
        captchaId: String,
    ): Result<Unit> = runCatching {
        if (captcha.isBlank()) error("这所学校要从综合系统进，先填验证码")
        val pending = pendingLogins[captchaId]
        if (pending == null) {
            error("验证码过期了，点一下验证码换一张再登")
        }
        val (viewState, viewStateGen) = pending
        if (viewState.isBlank()) error("验证码过期了，点一下验证码换一张再登")
        val response = client.submitForm(
            url = "$AIC$AIC_LOGIN_PATH",
            formParameters = Parameters.build {
                append("__VIEWSTATE", viewState)
                append("__VIEWSTATEGENERATOR", viewStateGen)
                append("signupInputUserName", studentId)
                append("HideUserName", rsaEncrypt(studentId, GDTSY_RSA_MODULUS_B64, GDTSY_RSA_EXPONENT_B64))
                append("signupInputPassword", rsaEncrypt(password, GDTSY_RSA_MODULUS_B64, GDTSY_RSA_EXPONENT_B64))
                append("loginCodeInput", captcha.trim())
                append("login_new", GDTSY_LOGIN_BUTTON)
            },
        ) {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$AIC$AIC_LOGIN_PATH")
            header(HttpHeaders.Origin, AIC)
        }
        val body = response.bodyAsText()
        // AIC 登录成功回的是 302，跳到 index.aspx 再进 Default.aspx。Ktor 的
        // HttpRedirectConfig.checkHttpMethod 默认只放 GET/HEAD 过，POST 的 302 会
        // 原样返回，所以这里看状态码和 Location，不能看最终 URL——否则登录成功也
        // 会被当成失败。失败时是 200 加一段 alert 脚本。
        val location = response.headers[HttpHeaders.Location].orEmpty()
        if (response.status.value !in 300..399 || location.contains("login.aspx", ignoreCase = true)) {
            throw error(gdstyLoginTip(body).ifBlank { "学号或密码不正确" })
        }
        pendingLogins.remove(captchaId)
        lastStudentId = studentId

        if (!enterJwxt()) error("综合系统登录了，教务会话没建起来。再试一次。")
    }

    /**
     * 换票 → 进教务 → 确认会话建起来了。
     *
     * AIC 那侧一直很稳，卡住的多半是这一步：票据是一次性的、还有时效，
     * 教务偶尔会把带票的请求打回登录页。所以失败就重新换一张票再来，别让用户手动重登。
     */
    private suspend fun enterJwxt(attempts: Int = 3): Boolean {
        for (attempt in 0 until attempts) {
            if (attempt > 0) delay(300L * attempt)
            val ticket = runCatching { fetchTicket() }.getOrDefault("")
            if (ticket.isBlank()) continue
            runCatching {
                client.get(ticket) {
                    header(HttpHeaders.UserAgent, QINGKE_UA)
                    header(HttpHeaders.Referrer, "$AIC/")
                }
            }
            keepJwCookie()
            val home = runCatching { fetchHome() }.getOrDefault("")
            if (home.isNotBlank() && !isGdstyLoginPage(home)) return true
        }
        return false
    }

    /** slogin.aspx 返回的页面里，跳转地址写在 meta refresh 的 content 里。 */
    private suspend fun fetchTicket(): String =
        client.get("$AIC$AIC_SLOGIN_PATH") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$AIC/")
        }.bodyAsText()
            .let { AIC_TICKET.find(it)?.groupValues?.get(1).orEmpty() }
            .replace("&amp;", "&")
            .trim()

    override suspend fun ensureSession() {
        keepJwCookie()
        if (isGdstyLoginPage(fetchHome())) error(SESSION_LOST_HINT)
    }

    /**
     * 教务每个页面自己都会每 10 分钟打一次 blankPage.jsp，App 不走页面，得自己打。
     * 正常就返回几个字符，掉线时整页换成 jsxsd 的登录页，比拉 xsMain.jsp 轻得多。
     */
    override suspend fun keepAlive(): Boolean {
        keepJwCookie()
        val body = client.get("$BASE/framework/blankPage.jsp") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
        }.bodyAsText()
        if (isGdstyLoginPage(body)) error(SESSION_LOST_HINT)
        return true
    }

    override suspend fun fetchTermCalendar(): TermCalendar {
        val page = get("$BASE/jxzl/jxzl_query")
        requireGdstySession(page)
        val xnxq = htmlSelectedValue(page, "xnxq01id")
        val weeks = parseGdstyWeeks(page)
        val today = nowDateTime().date
        val current = weeks
            .firstOrNull { today >= it.monday && today <= it.monday.plus(DatePeriod(days = 6)) }
            ?.week
            ?: 0
        val termStart = weeks.firstOrNull { it.week == 1 }?.monday?.toString().orEmpty()
        val (year, term) = parseZhkuTerm(xnxq)
        if (xnxq.isBlank() && current <= 0 && termStart.isBlank()) error("教务周历没有当前周")
        return TermCalendar(
            yearCode = year,
            termCode = term,
            currentWeek = current,
            termStart = termStart,
            weekCount = weeks.maxOfOrNull { it.week } ?: 0,
        )
    }

    override suspend fun fetchTimetable(
        year: String,
        term: String,
    ): Triple<StudentProfile, List<LessonSlot>, List<PracticeCourse>> {
        // 先空手要一次，既确认会话，又能抄下它自己选中的学期和节次模板。
        val first = get("$BASE/xskb/xskb_list.do")
        requireGdstySession(first)
        val current = htmlSelectedValue(first, "xnxq01id")
        val template = htmlSelectedValue(first, "kbjcmsid").ifBlank { GDTSY_KBJCMSID }
        val termId = zhkuTermId(year, term).ifBlank { current }
        val html = if (termId == current) {
            first
        } else {
            post(
                "$BASE/xskb/xskb_list.do",
                mapOf("xnxq01id" to termId, "zc" to "", "sfFD" to "1", "kbjcmsid" to template),
            ).also { requireGdstySession(it) }
        }
        // 教务没有课程目录页（main_index_loadwdkc 是错误页），学分学时只在成绩表里，
        // 课表格子本身不带，所以这里不编一个目录出来。
        val slots = parseGdstyTimetable(html)
        val practices = parseZhkuPractices(html, emptyList(), slots)
        val profile = parseGdstyProfile(
            html = runCatching { get("$BASE/grxx/xsxx") }.getOrDefault(""),
            xnxq = htmlSelectedValue(html, "xnxq01id").ifBlank { termId },
        ).copy(periodTimes = parseGdstyPeriodTimes(html))
        return Triple(profile, slots, practices)
    }

    override suspend fun fetchGrades(): List<GradeItem> {
        val page = post(
            "$BASE/kscj/cjcx_list",
            mapOf("kksj" to "", "kcxz" to "", "kcsx" to "", "kcmc" to "", "xsfs" to "all"),
        )
        requireGdstySession(page)
        return parseZhkuGrades(page)
    }

    override suspend fun fetchExams(year: String, term: String): List<ExamItem> {
        val page = post(
            "$BASE/xsks/xsksap_list",
            mapOf("xnxqid" to zhkuTermId(year, term), "xqlb" to "", "xqlbmc" to ""),
        )
        requireGdstySession(page)
        return parseZhkuExams(page)
    }

    override suspend fun fetchFreeRooms(
        year: String,
        term: String,
        weekday: String,
        start: String,
        end: String,
    ): List<FreeRoom> {
        error("生态教务没有空教室查询")
    }

    override suspend fun fetchNotices(): List<NoticeItem> {
        val page = get("$BASE/ggly/ysgg_query")
        requireGdstySession(page)
        return parseZhkuNotices(page)
    }

    /**
     * 通知详情地址只有列表里那一行才知道，所以先回列表页把 href 抠出来再跟过去，
     * 不猜路由。列表空的时候自然什么也拿不到。
     */
    override suspend fun fetchNoticeDetail(id: String): NoticeItem {
        val list = get("$BASE/ggly/ysgg_query")
        requireGdstySession(list)
        val href = Regex(
            """['"]([^'"]*[?&](?:ggid|id)=${Regex.escape(id)}[^'"]*)['"]""",
            RegexOption.IGNORE_CASE,
        ).find(list)?.groupValues?.get(1) ?: return NoticeItem(id = id, title = "")
        val url = when {
            href.startsWith("http") -> href
            href.startsWith("/") -> "$JW$href"
            else -> "$BASE/${href.removePrefix("./")}"
        }
        val page = get(url)
        requireGdstySession(page)
        return parseZhkuNoticeDetail(id, page)
    }

    override suspend fun logout() {
        runCatching {
            client.submitForm(
                url = "$JW/Logon.do?method=logoutFromJsxsd",
                formParameters = Parameters.build { append("useraccount", lastStudentId) },
            ) {
                header(HttpHeaders.UserAgent, QINGKE_UA)
                header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
                header(HttpHeaders.Origin, JW)
            }
        }
    }

    private suspend fun fetchHome(): String {
        keepJwCookie()
        return client.get("$BASE/framework/xsMain.jsp") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$JW/")
        }.bodyAsText()
    }

    private suspend fun keepJwCookie() {
        retainLatestCookie(cookieHost, "JSESSIONID")
    }

    private suspend fun get(url: String): String {
        keepJwCookie()
        return client.get(url) {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
        }.bodyAsText()
    }

    private suspend fun post(url: String, fields: Map<String, String>): String {
        keepJwCookie()
        return client.submitForm(
            url = url,
            formParameters = Parameters.build {
                fields.forEach { (key, value) -> append(key, value) }
            },
        ) {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
            header(HttpHeaders.Origin, JW)
        }.bodyAsText()
    }

    private fun hiddenField(html: String, name: String): String =
        Regex("""name="$name"[^>]*value="([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1).orEmpty()

    private companion object {
        const val AIC_LOGIN_PATH = "/xsgl/xs/login/login.aspx"
        const val AIC_SLOGIN_PATH =
            "/xsgl/xs/index/ilogin/slogin.aspx?AppId=410bcb4493ca4c05bfacf70585f04c47"
        val AIC_CAPTCHA =
            Regex("""id="loginCode"[^>]*src="data:image/png;base64,([^"]+)""", RegexOption.IGNORE_CASE)
        val AIC_TICKET =
            Regex("""content="\d+\s*;\s*URL='([^']+)'""", RegexOption.IGNORE_CASE)
    }
}

/** 教务会话掉了会整页换成 jsxsd 自己的登录页，那页有 encoded 和 userAccount。 */
internal fun isGdstyLoginPage(text: String): Boolean =
    text.contains("name=\"encoded\"") &&
        text.contains("id=\"userAccount\"") &&
        text.contains("/jsxsd/xk/LoginToXk")

internal fun gdstyLoginTip(html: String): String =
    Regex("""alert\('([^']*)'\)""").find(html)?.groupValues?.get(1)
        ?.replace(Regex("""\\[rn]"""), "")
        ?.trim()
        .orEmpty()

private fun requireGdstySession(text: String) {
    if (isGdstyLoginPage(text)) error(SESSION_LOST_HINT)
}

/**
 * 周历表每行第 1 格是周次，后面跟着那一周的日期，星期日排在最前。
 * 第一周从周一开始，所以那一行没有星期日——按星期几挑出周一，别按位置取。
 */
internal fun parseGdstyWeeks(html: String): List<ZhkuWeek> {
    val table = Regex(
        """<table[^>]*id="kbtable"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return emptyList()
    return Regex("""<tr[^>]*>\s*<td>\s*(\d+)\s*</td>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        .findAll(table)
        .mapNotNull { row ->
            val week = row.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val monday = Regex("""title='(\d{4})年(\d{2})月(\d{2})'""")
                .findAll(row.groupValues[2])
                .mapNotNull { match ->
                    val parts = match.groupValues
                    runCatching {
                        LocalDate(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
                    }.getOrNull()
                }
                // 0 是星期一，见 Schedule.kt 的 weekdayIndex。
                .firstOrNull { it.dayOfWeek.ordinal == 0 }
                ?: return@mapNotNull null
            ZhkuWeek(week, monday)
        }.toList()
}

/**
 * 课表格子是 <table id="kbtable">，每行开头 <th> 写节次（有的还带起止时间），
 * 后面 7 个 <td> 对应星期一到星期日。格子里成对出现 -1/-2 两个 div，
 * 带 -2 的那个才写了老师。
 */
internal fun parseGdstyTimetable(html: String): List<LessonSlot> {
    val table = Regex(
        """<table[^>]*id="kbtable"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return emptyList()
    val slots = mutableListOf<LessonSlot>()
    val rows = table.split(Regex("""<tr\b""", RegexOption.IGNORE_CASE)).drop(1)
    for (row in rows) {
        val head = Regex("""<th[^>]*>([\s\S]*?)</th>""", RegexOption.IGNORE_CASE).find(row) ?: continue
        val periodLabel = gdstyPeriodLabel(head.groupValues[1])
        if (periodLabel.isBlank() || periodLabel.contains("星期") || periodLabel.contains("备注")) continue
        val cells = Regex("""<td\b[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE).findAll(row).toList()
        cells.take(7).forEachIndexed { index, cell ->
            val blocks = Regex(
                """<div id="([^"]+)"[^>]*class="kbcontent"[^>]*>([\s\S]*?)(?=<div id=|$)""",
                RegexOption.IGNORE_CASE,
            ).findAll(cell.groupValues[1])
            for (block in blocks) {
                if (!block.groupValues[1].endsWith("-2")) continue
                val pieces = block.groupValues[2].split(Regex("-{5,}"))
                for (piece in pieces) {
                    val slot = parseZhkuCell(
                        inner = normalizeGdstyCell(piece),
                        weekday = index + 1,
                        periodLabel = periodLabel,
                        catalog = emptyMap(),
                        periodFromLabel = ::gdstyPeriodFromLabel,
                    ) ?: continue
                    slots += slot
                }
            }
        }
    }
    // 一节连上 9-12 节的课，教务会在「9、10节」和「11、12节」两行各画一遍，
    // 两行的 div id 前缀还不一样，只能按内容去重；节次保留 9-12，两行格子都点得亮。
    return slots.distinctBy {
        listOf(it.weekday, it.courseName, it.weeks, it.period, it.room, it.teacher, it.className)
    }
}

/**
 * 生态的格子里，课程名是 <font> 外面的纯文本，仲恺那边却是包在无属性 <font> 里的。
 * 把前面那段文本补成 <font>，后面就能走同一套格子解析。
 */
private fun normalizeGdstyCell(piece: String): String {
    val cut = piece.indexOf("<font", ignoreCase = true)
    val head = zhkuPlain(if (cut < 0) piece else piece.substring(0, cut))
    if (head.isBlank()) return piece
    val tail = if (cut < 0) "" else piece.substring(cut)
    // 仲恺把教师标成 title='教师'，生态标成 title='老师'，统一成前者走共用解析。
    return "<font>$head</font>" + tail
        .replace("title='老师'", "title='教师'")
        .replace("title=\"老师\"", "title=\"教师\"")
}

/**
 * 节次行的写法是「1、2节&nbsp;<br/>08:30-09:50」，时间和换行都会跟着 <th> 一起被
 * 抽出来。时间另有 GdstyPeriods 提供，UI 那边还会往标签尾巴补个「节」字，
 * 所以这里只留「1、2节」这种干净的节次名。
 */
internal fun gdstyPeriodLabel(raw: String): String =
    zhkuPlain(raw)
        .replace(Regex("""\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

/** 节次行的写法是「1、2节 08:30-09:50」，单节是「5节」，中午那行没有编号。 */
internal fun gdstyPeriodFromLabel(label: String): String {
    if (label.contains("中午")) return "中午"
    val span = Regex("""(\d+)\s*[、,，\-－]\s*(\d+)""").find(label)
    if (span != null) return "${span.groupValues[1]}-${span.groupValues[2]}"
    val single = Regex("""(?:第\s*)?(\d+)\s*节""").find(label) ?: return ""
    return single.groupValues[1]
}

private val ClockRange = Regex("""(\d{1,2}:\d{2})\s*[-—~～]\s*(\d{1,2}:\d{2})""")

/**
 * 从强智课表的节次行抄时间。表头写法各校不同，但都长这样：
 * 「1、2节 08:30-09:50」——节次名和时间在同一个 <th> 里。
 *
 * @param tableId 课表表格的 id（生态 kbtable / 仲恺 timetable）
 * @param keyOf 把 <th> 原文映射成节次块标签（对不上预设 label 就返回空串，该行跳过）
 */
internal fun kingosoftPeriodTimes(
    html: String,
    tableId: String,
    keyOf: (rawTh: String) -> String,
): Map<String, String> {
    fun scan(source: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (row in source.split(Regex("""<tr\b""", RegexOption.IGNORE_CASE)).drop(1)) {
            val th = Regex("""<th[^>]*>([\s\S]*?)</th>""", RegexOption.IGNORE_CASE).find(row) ?: continue
            val raw = th.groupValues[1]
            val key = keyOf(raw)
            if (key.isBlank()) continue
            val hit = ClockRange.find(zhkuPlain(raw)) ?: continue
            out[key] = "${hit.groupValues[1]}-${hit.groupValues[2]}"
        }
        return out
    }

    val table = Regex(
        """<table[^>]*id="${Regex.escape(tableId)}"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1)
    // 有的教务把节次时间挪到另一张表里，表 id 对不上就整页兜底扫一遍。
    // 匹配要求「N、M节 … HH:mm-HH:mm」同时出现，误抓的概率很低。
    return if (table != null) scan(table).ifEmpty { scan(html) } else scan(html)
}

/** 生态：节次行是「1、2节 08:30-09:50」，键与 GdstyPeriods 的 label 对齐。 */
internal fun parseGdstyPeriodTimes(html: String): Map<String, String> =
    kingosoftPeriodTimes(html, "kbtable") { raw ->
        val label = gdstyPeriodLabel(raw)
        if (label.isBlank() || label.contains("星期") || label.contains("备注")) "" else gdstyPeriodFromLabel(label)
    }

/** 学籍卡片把院系/专业/班级/学号写成「标签：值」，姓名是「姓名</td><td>值</td>」。 */
internal fun parseGdstyProfile(html: String, xnxq: String): StudentProfile {
    val (year, term) = parseZhkuTerm(xnxq)
    val yearStart = year.takeIf { it.length == 4 }?.toIntOrNull()
    val yearName = if (yearStart != null) {
        "$year-${yearStart + 1}"
    } else {
        xnxq.substringBeforeLast("-").ifBlank { year }
    }
    val name = Regex(""">姓名</td>\s*<td[^>]*>\s*(?:&nbsp;)?([^<]*)</td>""", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.get(1)
        ?.let { zhkuPlain(it) }
        .orEmpty()
    return StudentProfile(
        name = name,
        studentId = gdstyField(html, "学号"),
        studentKey = gdstyField(html, "学号"),
        className = gdstyField(html, "班级"),
        major = gdstyField(html, "专业"),
        yearName = yearName,
        termCode = term,
        yearCode = year,
        termLabel = if (term == "2") "第2学期" else "第1学期",
    )
}

private fun gdstyField(html: String, label: String): String =
    Regex("""$label[：:]\s*([^<\s]+)""").find(html)?.groupValues?.get(1)?.let { zhkuPlain(it) }.orEmpty()

/** 各校的 select 都是这一套，但 JwxtClient / ZhkuClient 里的同名函数是文件私有的，只能再放一份。 */
private fun htmlSelectedValue(html: String, id: String): String {
    val block = Regex(
        """<select\b[^>]*\bid\s*=\s*"${Regex.escape(id)}"[^>]*>[\s\S]*?</select>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.value ?: return ""
    return Regex(
        """<option\b[^>]*\bvalue\s*=\s*"([^"]*)"[^>]*selected""",
        RegexOption.IGNORE_CASE,
    ).find(block)?.groupValues?.get(1)?.trim()
        ?: Regex("""<option\b[^>]*selected[^>]*\bvalue\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(block)?.groupValues?.get(1)?.trim()
            .orEmpty()
}
