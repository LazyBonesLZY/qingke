package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 电子科技大学中山学院：强智 jsxsd 直登（jwgln.zsc.edu.cn）。
 *
 * 和仲恺/生态的强智是同源系统，页面骨架一致，但中山学院这套有几处自己的脾气：
 *
 * 1. 登录页表单只有 `encoded` 一个字段（`b64(账号) + "%%%" + b64(密码)`），**没有验证码**
 *    ——页面上残留的 ReShowCode() 是历史痕迹，服务端不校验。
 * 2. `Content-Type` 声明 GBK，实际内容是 UTF-8。按 UTF-8 解，失败退 gb18030。
 * 3. 课表页 `#kbtable` 的表头比仲恺多一个「第11，12节」行，所以不能按固定行数切。
 * 4. 课表页本身**没有日期**，周历在「我的桌面」`jxzl/jxzl_query`，week 0 是报到军训周，
 *    week 1 才是正式第一周——按周次偏移日期会整体差一周。
 *
 * 解析部分尽量复用 ZhkuClient 里的共用实现（格子、周次、成绩、考试、通知都一样），
 * 只有课表表格的 id 和节次标签写法需要单独适配。
 */
class ZscClient(
    origin: String = ZSC_ORIGIN,
    private val client: HttpClient = createHttpClient(),
) : SchoolPortal {
    override val supportsFreeRooms: Boolean = false

    private val host = origin.trim().trimEnd('/').removeSuffix("/jsxsd")
    private val HOST = host
    private val BASE = "$host/jsxsd"
    private val cookieHost = host.substringAfter("://").substringBefore("/")

    override suspend fun login(
        studentId: String,
        password: String,
        captcha: String,
        captchaId: String,
    ): Result<Unit> = runCatching {
        // 先开登录页拿 JSESSIONID。省掉这步也能登进去，但后面 xskb_list.do 会没有菜单权限
        // 上下文，容易被「非法访问」拦截页顶回来。
        client.get("$BASE/") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
        }.bodyAsText()
        val result = client.submitForm(
            url = "$BASE/xk/LoginToXk",
            formParameters = Parameters.build {
                append("encoded", zhkuEncoded(studentId, password))
            },
        ) {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$BASE/")
            header(HttpHeaders.Origin, HOST)
        }
        keepCookie()
        val body = result.bodyAsText()
        // 登录结果判定，两步都不能省：
        //
        // 1. 成功是 **302 + 空 body**，Location 指向 framework/xsMain.jsp；密码错是 200
        //    + 「提示：非法访问！」错误页。
        // 2. **Ktor 不会跟随 POST 的 302**（HttpRedirectConfig.checkHttpMethod 默认只放
        //    GET/HEAD 过，见 GdstyClient 里同样的坑），所以这里拿到的是空 body，不是
        //    跳转后的主页。踩过一次：只按 body 判断，登录成功也被判成「密码不正确」。
        val status = result.status.value
        val location = result.headers[HttpHeaders.Location].orEmpty()
        val ok = zscLoginSucceeded(status, location, body, studentId)
        if (!ok) {
            val tip = zscErrorTip(body)
            if (isFirstLoginSignal(tip) || isFirstLoginSignal(body)) throw JwxtNeedFirstLogin()
            error(tip.ifBlank { zscLoginFailureTip(status, location) })
        }
        // 302 之后会话是否真建起来，得自己跟一次主页确认，不能想当然。
        if (!hasSession()) error("登录没建起会话，再试一次")
    }

    override suspend fun ensureSession() {
        keepCookie()
        val home = fetchHome()
        if (isZscLoginPage(home)) error(SESSION_LOST_HINT)
    }

    /**
     * 强智每个页面自己都会每 10 分钟打一次 blankPage.jsp。App 不走页面，得自己打。
     * 正常只返回几个字符，掉线时整页换成登录页，比拉 xsMain.jsp 轻得多。
     */
    override suspend fun keepAlive(): Boolean {
        keepCookie()
        val body = client.get("$BASE/framework/blankPage.jsp") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
        }.bodyAsText()
        if (isZscLoginPage(body)) error(SESSION_LOST_HINT)
        return true
    }

    override suspend fun fetchTermCalendar(): TermCalendar {
        val page = post("$BASE/jxzl/jxzl_query", emptyMap())
        requireSession(page)
        val xnxq = htmlSelectedValue(page, "xnxq01id")
        val weeks = parseZscWeeks(page)
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
        // 先空手要一次：既确认会话，又能抄下它自己选中的学期。
        val first = get("$BASE/xskb/xskb_list.do")
        requireSession(first)
        val current = htmlSelectedValue(first, "xnxq01id")
        val termId = zhkuTermId(year, term).ifBlank { current }
        val html = if (termId == current) {
            first
        } else {
            // 必须 zc=0（全部周次）。按周筛选时服务端会把不在该周的课过滤掉，同时把
            // 格子里的周次标签抹成空串——那正好是我们算日期要用的元数据。
            post(
                "$BASE/xskb/xskb_list.do",
                mapOf("xnxq01id" to termId, "zc" to "0", "demo" to "", "sfFD" to "1", "cj0701id" to ""),
            ).also { requireSession(it) }
        }
        val slots = parseZscTimetable(html)
        val practices = parseZhkuPractices(html, emptyList(), slots)
        val profile = parseZhkuProfile(
            html = runCatching { get("$BASE/grxx/xsxx") }.getOrDefault(""),
            xnxq = htmlSelectedValue(html, "xnxq01id").ifBlank { termId },
            slots = slots,
        ).copy(periodTimes = parseZscPeriodTimes(html))
        return Triple(profile, slots, practices)
    }

    override suspend fun fetchGrades(): List<GradeItem> {
        val page = post(
            "$BASE/kscj/cjcx_list",
            mapOf("kksj" to "", "kcxz" to "", "kcsx" to "", "kcmc" to "", "xsfs" to "all"),
        )
        requireSession(page)
        return parseZhkuGrades(page)
    }

    override suspend fun fetchExams(year: String, term: String): List<ExamItem> {
        val page = post(
            "$BASE/xsks/xsksap_list",
            mapOf("xnxqid" to zhkuTermId(year, term), "xqlb" to "", "xqlbmc" to ""),
        )
        requireSession(page)
        return parseZhkuExams(page)
    }

    override suspend fun fetchFreeRooms(
        year: String,
        term: String,
        weekday: String,
        start: String,
        end: String,
    ): List<FreeRoom> {
        error("中山学院教务没有空教室查询")
    }

    override suspend fun fetchNotices(): List<NoticeItem> {
        val inbox = get("$BASE/ggly/ysgg_query")
        requireSession(inbox)
        val fromInbox = parseZhkuNotices(inbox)
        if (fromInbox.isNotEmpty()) return fromInbox
        val home = runCatching { get("$BASE/framework/main_index_loadtzgg.jsp") }.getOrDefault("")
        return parseZhkuHomeNotices(home)
    }

    override suspend fun fetchNoticeDetail(id: String): NoticeItem {
        val page = get("$BASE/ggly/ysgg_query?ggid=$id")
        requireSession(page)
        return parseZhkuNoticeDetail(id, page)
    }

    override suspend fun logout() {
        // 强智的退出就是回登录页；打 logout.do 再回一次，两处都试，失败也不吵用户。
        runCatching {
            client.get("$BASE/logout.do") {
                header(HttpHeaders.UserAgent, QINGKE_UA)
                header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
            }.bodyAsText()
        }
    }

    private suspend fun fetchHome(): String {
        keepCookie()
        return client.get("$BASE/framework/xsMain.jsp") {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$HOST/")
        }.bodyAsText()
    }

    private suspend fun hasSession(): Boolean {
        val home = fetchHome()
        if (isZscLoginPage(home)) return false
        return home.contains("xsMain") || home.length > 2_000
    }

    private suspend fun keepCookie() {
        retainLatestCookie(cookieHost, "JSESSIONID")
    }

    private suspend fun get(url: String): String {
        keepCookie()
        return client.get(url) {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
        }.bodyAsText()
    }

    private suspend fun post(url: String, fields: Map<String, String>): String {
        keepCookie()
        return client.submitForm(
            url = url,
            formParameters = Parameters.build {
                fields.forEach { (key, value) -> append(key, value) }
            },
        ) {
            header(HttpHeaders.UserAgent, QINGKE_UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.jsp")
            header(HttpHeaders.Origin, HOST)
        }.bodyAsText()
    }
}

/** 登录页特征：表单里只有 encoded，没有验证码。 */
internal fun isZscLoginPage(text: String): Boolean {
    if (text.isBlank()) return false
    if (text.length > 100_000) return false
    return text.contains("name=\"encoded\"") && text.contains("LoginToXk")
}

/**
 * 登录是否成功。纯粹按服务端回包判断，不碰网络，方便直接单测。
 *
 * 成功：302（或任意 3xx）+ Location 指向 xsMain.jsp，**此时 body 是空的**——
 *       Ktor 不跟随 POST 的 302，拿到的就是跳转前的响应。
 * 失败：200 + 「提示：非法访问！」错误页。
 * 个别部署会直接 200 渲染主页（不是 xsMain.jsp，里面也没有 "xsMain" 字样），
 * 所以再补一条「非登录页且有内容」的退路。
 */
internal fun zscLoginSucceeded(
    status: Int,
    location: String,
    body: String,
    studentId: String,
): Boolean {
    if (status in 300..399 && location.contains("xsMain", ignoreCase = true)) return true
    if (isZscLoginPage(body)) return false
    if (body.contains(studentId) && studentId.isNotBlank()) return true
    return body.length > 2_000
}

/** 失败时的提示。带了错误页就抽提示语，302 却跳到登录页说明是账号问题。 */
internal fun zscLoginFailureTip(status: Int, location: String): String = when {
    status in 300..399 && location.contains("login", ignoreCase = true) -> "学号或密码不正确"
    status in 300..399 -> "登录跳转异常，再试一次"
    status >= 500 -> "教务服务器出错，过一会儿再试"
    else -> "学号或密码不正确"
}

/** 「提示：非法访问！」这种错误页只留提示语。 */
internal fun zscErrorTip(html: String): String {
    Regex("""提示[：:]\s*([^<]{1,60})""").find(html)?.groupValues?.get(1)?.trim()?.let { if (it.isNotBlank()) return it }
    return Regex("""alert\('([^']*)'\)""").find(html)?.groupValues?.get(1)
        ?.replace(Regex("""\\[rn]"""), "")
        ?.trim()
        .orEmpty()
}

private fun requireSession(text: String) {
    if (isZscLoginPage(text) && text.length < 6_000) error(SESSION_LOST_HINT)
}

/**
 * 「我的桌面」周历：每行第一个 <td> 是周次号，后面各格是那周的日期，
 * 单元格上带 `title='2026年08月31'`。第 0 周是报到/军训周，所以按 title 里的日期
 * 反查周一，别用 week-1 去偏移。
 *
 * 不能按 `</tr>` 切行：这套强智的输出里只有第一行写了 `</tr>`，2..N 周的行是
 * `<tr height='28'>…<tr height='28'>…` 一路串下去的（还混着 rowspan 的备注格子）。
 * 按开始标签切才不会只解析出第 0 周。
 */
internal fun parseZscWeeks(html: String): List<ZhkuWeek> {
    val head = html.indexOf("<table", ignoreCase = true).takeIf { it >= 0 } ?: 0
    val table = Regex(
        """<table[^>]*id="kbtable"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: html.substring(head)
    return table
        .split(Regex("""<tr\b""", RegexOption.IGNORE_CASE))
        .drop(1)
        .mapNotNull { row ->
            val week = Regex("""^\s*(?:[^>]*>\s*)?<td[^>]*>\s*(\d+)\s*</td>""", RegexOption.IGNORE_CASE)
                .find(row)?.groupValues?.get(1)?.toIntOrNull()
                // 行首可能先有几个空白或属性，退一步按「第一个 <td> 就是周次」取。
                ?: Regex("""<td[^>]*>\s*(\d+)\s*</td>""", RegexOption.IGNORE_CASE)
                    .find(row)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val monday = Regex("""title\s*=\s*['"](\d{4})年(\d{1,2})月(\d{1,2})['"]""")
                .findAll(row)
                .mapNotNull { match ->
                    runCatching {
                        LocalDate(
                            match.groupValues[1].toInt(),
                            match.groupValues[2].toInt(),
                            match.groupValues[3].toInt(),
                        )
                    }.getOrNull()
                }
                // 0 是星期一，见 Schedule.kt 的 weekdayIndex。
                .firstOrNull { it.dayOfWeek.ordinal == 0 }
                ?: return@mapNotNull null
            ZhkuWeek(week, monday)
        }
        .toList()
}

/**
 * 中山学院的课表格子：`<th>第1，2节&nbsp;</th>` 加 7 个 `<td>`，每格成对出现
 * `kbcontent1`（简版）和 `kbcontent`（详版，才带老师），多门课用 16 个短横线分隔。
 *
 * 和仲恺的差别只在表 id（这里是 `kbtable`）和节次标签写法（「第1，2节」而
 * 不是「1、2节」），格子内部结构可以走共用的 parseZhkuCell。
 */
internal fun parseZscTimetable(html: String): List<LessonSlot> {
    val table = Regex(
        """<table[^>]*id="kbtable"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return emptyList()
    val slots = mutableListOf<LessonSlot>()
    for (row in table.split(Regex("""<tr\b""", RegexOption.IGNORE_CASE)).drop(1)) {
        val head = Regex("""<th[^>]*>([\s\S]*?)</th>""", RegexOption.IGNORE_CASE).find(row) ?: continue
        val periodLabel = zscPeriodLabel(head.groupValues[1])
        // 表头那一行（星期一…）和备注行的 <th> 都长得不像节次，直接跳过。
        if (periodLabel.isBlank() || periodLabel.contains("星期") || periodLabel.contains("备注")) continue
        val cells = Regex("""<td\b[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE).findAll(row).toList()
        cells.take(7).forEachIndexed { index, cell ->
            val blocks = Regex(
                """<div id="([^"]+)"[^>]*class="kbcontent"[^>]*>([\s\S]*?)(?=<div id=|$)""",
                RegexOption.IGNORE_CASE,
            ).findAll(cell.groupValues[1])
            for (block in blocks) {
                // 只认 -2 那份详版：老师名字只写在它里面。
                if (!block.groupValues[1].endsWith("-2")) continue
                for (piece in block.groupValues[2].split(Regex("-{4,}"))) {
                    // 切完第二段的段首还挂着上一个 <br>，不清掉课程名会是空串。
                    val cleaned = piece.replace(Regex("""^(?:\s|<br\s*/?>|&nbsp;)+""", RegexOption.IGNORE_CASE), "")
                    if (cleaned.isBlank()) continue
                    val slot = parseZscCell(cleaned, index + 1, periodLabel) ?: continue
                    slots += slot
                }
            }
        }
    }
    // 9-12 节的长课教务会在两行各画一遍，div 前缀不同，只能按内容去重。
    return slots.distinctBy {
        listOf(it.weekday, it.courseName, it.weeks, it.period, it.room, it.teacher, it.className)
    }
}

/** 中山学院把节次写成「第1，2节」「第9，10节」，去掉时间后缀和 &nbsp; 留干净的节次名。 */
internal fun zscPeriodLabel(raw: String): String =
    zhkuPlain(raw)
        .replace(Regex("""\d{1,2}:\d{2}\s*[-—~]\s*\d{1,2}:\d{2}"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

/** 「第1，2节」→ "1-2"，「第9，10节」→ "9-10"，「第5节」→ "5"。 */
internal fun zscPeriodFromLabel(label: String): String {
    if (label.contains("中午")) return "中午"
    val nums = Regex("""\d+""").findAll(label).map { it.value.toInt() }.toList()
    if (nums.isEmpty()) return ""
    val lo = nums.min()
    val hi = nums.max()
    return if (lo == hi) "$lo" else "$lo-$hi"
}

/**
 * 课表格子里课程名是 `<font>` 外面的纯文本（「英语Ⅰ<br/>」），而 parseZhkuCell 要求
 * 课程名包在无属性 `<font>` 里。把前面那段文本补成 `<font>` 再交给它。
 * 教师标签中山学院写 `title='老师'`，仲恺写 `title='教师'`，统一成后者。
 */
internal fun parseZscCell(inner: String, weekday: Int, periodLabel: String): LessonSlot? {
    val cut = inner.indexOf("<font", ignoreCase = true)
    val head = zhkuPlain(if (cut < 0) inner else inner.substring(0, cut))
    if (head.isBlank()) return null
    val tail = if (cut < 0) "" else inner.substring(cut)
    val normalized = "<font>$head</font>" + tail
        .replace("title='老师'", "title='教师'")
        .replace("title=\"老师\"", "title=\"教师\"")
    return parseZhkuCell(
        inner = normalized,
        weekday = weekday,
        periodLabel = periodLabel,
        catalog = emptyMap(),
        periodFromLabel = ::zscPeriodFromLabel,
    )
}

/** 中山学院课表 <th> 里只写节次不写时间，抄不到就返回空表，别拿别的学校时间顶。 */
internal fun parseZscPeriodTimes(html: String): Map<String, String> =
    kingosoftPeriodTimes(html, "kbtable") { raw ->
        val label = zscPeriodLabel(raw)
        if (label.isBlank() || label.contains("星期") || label.contains("备注")) "" else zscPeriodFromLabel(label)
    }

/** 各校 select 都是这一套，但同名函数在别的文件里是私有的，只能再放一份。 */
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
