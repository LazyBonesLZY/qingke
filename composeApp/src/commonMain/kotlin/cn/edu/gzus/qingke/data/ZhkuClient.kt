package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
private const val DEFAULT_KBJCMSID = "3831EE04599D452CBFD0532E01DE52BA"

class ZhkuClient(
    origin: String = "https://edu-admin.zhku.edu.cn",
    private val client: HttpClient = createHttpClient(),
) : SchoolPortal {
    override val supportsFreeRooms: Boolean = false
    private val host = origin.trim().trimEnd('/').removeSuffix("/jsxsd")
    private val HOST = host
    private val BASE = "$host/jsxsd"

    override suspend fun login(
        studentId: String,
        password: String,
        captcha: String,
        captchaId: String,
    ): Result<Unit> = runCatching {
        var lastBody = loginJsxsd(studentId, password)
        if (hasZhkuSession()) return@runCatching
        lastBody = loginOfficial(studentId, password).ifBlank { lastBody }
        if (hasZhkuSession()) return@runCatching
        val home = fetchHome()
        throwZhkuLoginFailure(if (isZhkuLoginPage(home.body)) home.body else lastBody.ifBlank { home.body })
    }

    private suspend fun loginJsxsd(studentId: String, password: String): String {
        client.get("$BASE/") {
            header(HttpHeaders.UserAgent, UA)
        }
        val result = client.submitForm(
            url = "$BASE/xk/LoginToXk",
            formParameters = Parameters.build {
                append("loginMethod", "LoginToXk")
                append("userAccount", studentId)
                append("userPassword", "")
                append("encoded", zhkuEncoded(studentId, password))
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/")
            header(HttpHeaders.Origin, HOST)
        }
        val body = result.bodyAsText()
        if (isZhkuPasswordChange(body, result.request.url.toString())) throw JwxtNeedFirstLogin()
        return body
    }

    private suspend fun loginOfficial(studentId: String, password: String): String {
        client.get("$HOST/Logon.do?method=logon") {
            header(HttpHeaders.UserAgent, UA)
        }
        val sess = client.submitForm(
            url = "$HOST/Logon.do?method=logon&flag=sess",
            formParameters = Parameters.build {},
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$HOST/Logon.do?method=logon")
            header(HttpHeaders.Origin, HOST)
        }.bodyAsText().trim()
        if (sess == "no" || !sess.contains("#")) return ""
        val encoded = zhkuSessEncoded(studentId, password, sess)
        if (encoded.isBlank()) return ""
        val result = client.submitForm(
            url = "$HOST/Logon.do?method=logon",
            formParameters = Parameters.build {
                append("userAccount", studentId)
                append("userPassword", "")
                append("encoded", encoded)
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$HOST/Logon.do?method=logon")
            header(HttpHeaders.Origin, HOST)
        }
        val body = result.bodyAsText()
        if (isZhkuPasswordChange(body, result.request.url.toString())) throw JwxtNeedFirstLogin()
        return body
    }

    private fun throwZhkuLoginFailure(body: String): Nothing {
        val tip = zhkuShowMsg(body)
        when {
            isFirstLoginSignal(tip) || isFirstLoginSignal(body) -> throw JwxtNeedFirstLogin()
            tip.isNotBlank() -> error(tip)
            else -> error("学号或密码不正确")
        }
    }

    private suspend fun fetchHome(): ZhkuPage {
        val home = client.get("$BASE/framework/xsMain.htmlx") {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/")
        }
        return ZhkuPage(home.bodyAsText(), home.request.url.toString())
    }

    private suspend fun hasZhkuSession(): Boolean {
        val home = fetchHome()
        if (isZhkuPasswordChange(home.body, home.url)) throw JwxtNeedFirstLogin()
        if (isZhkuLoginPage(home.body) || isZhkuSessionLost(home.body)) return false
        return home.url.contains("xsMain", ignoreCase = true) ||
            home.body.contains("xsMain") ||
            home.body.contains("personal-center") ||
            home.body.contains("main_index")
    }

    private data class ZhkuPage(val body: String, val url: String)

    override suspend fun fetchTermCalendar(): TermCalendar {
        val page = get("$BASE/jxzl/jxzl_query")
        requireZhkuSession(page)
        val xnxq = htmlSelectedValue(page, "xnxq01id").ifBlank {
            Regex("""value="(\d{4}-\d{4}-\d)"[^>]*selected""").find(page)?.groupValues?.get(1).orEmpty()
        }
        val weeks = parseZhkuWeeks(page)
        val today = nowDateTime().date
        var current = weeks.firstOrNull { today >= it.monday && today <= it.monday.plus(DatePeriod(days = 6)) }?.week ?: 0
        if (current <= 0) {
            val home = runCatching { get("$BASE/framework/xsMain_new.jsp") }.getOrDefault("")
            current = parseZhkuCurrentWeek(home)
        }
        val termStart = weeks.firstOrNull { it.week == 1 }?.monday?.toString().orEmpty()
        val (year, term) = parseZhkuTerm(xnxq)
        if (xnxq.isBlank() && current <= 0 && termStart.isBlank()) error("仲恺周历没有当前周")
        return TermCalendar(
            yearCode = year,
            termCode = term,
            currentWeek = current,
            termStart = termStart,
            weekCount = weeks.maxOfOrNull { it.week } ?: 0,
        )
    }

    override suspend fun fetchTimetable(year: String, term: String): Triple<StudentProfile, List<LessonSlot>, List<PracticeCourse>> {
        val xnxq = zhkuTermId(year, term)
        val page = if (xnxq.isBlank()) {
            get("$BASE/xskb/xskb_list.do")
        } else {
            post(
                "$BASE/xskb/xskb_list.do",
                mapOf(
                    "xnxq01id" to xnxq,
                    "zc" to "",
                    "sfFD" to "1",
                    "kbjcmsid" to DEFAULT_KBJCMSID,
                ),
            )
        }
        requireZhkuSession(page)
        val kbjcmsid = htmlSelectedValue(page, "kbjcmsid").ifBlank { DEFAULT_KBJCMSID }
        val kbHtml = if (kbjcmsid == DEFAULT_KBJCMSID) page else post(
            "$BASE/xskb/xskb_list.do",
            mapOf(
                "xnxq01id" to xnxq,
                "zc" to "",
                "sfFD" to "1",
                "kbjcmsid" to kbjcmsid,
            ),
        )
        requireZhkuSession(kbHtml)
        val courses = runCatching {
            parseZhkuCourses(get("$BASE/framework/main_index_loadwdkc.htmlx?xnxq01id=$xnxq"))
        }.getOrDefault(emptyList())
        val slots = parseZhkuTimetable(kbHtml, courses)
        val remarkPage = runCatching {
            get("$BASE/framework/main_index_loadkb.htmlx?xnxqid=${htmlSelectedValue(kbHtml, "xnxq01id").ifBlank { xnxq }}")
        }.getOrDefault("")
        val practices = parseZhkuPractices(kbHtml + remarkPage, courses, slots)
        val profile = parseZhkuProfile(
            html = runCatching { get("$BASE/grxx/xsxx") }.getOrDefault(""),
            xnxq = htmlSelectedValue(kbHtml, "xnxq01id").ifBlank { xnxq },
            slots = slots,
        )
        return Triple(profile, slots, practices)
    }

    override suspend fun fetchGrades(): List<GradeItem> {
        val page = post(
            "$BASE/kscj/cjcx_list",
            mapOf("kksj" to "", "kcxz" to "", "kcsx" to "", "kcmc" to "", "xsfs" to "all"),
        )
        requireZhkuSession(page)
        return parseZhkuGrades(page)
    }

    override suspend fun fetchExams(year: String, term: String): List<ExamItem> {
        val xnxq = zhkuTermId(year, term)
        val page = post(
            "$BASE/xsks/xsksap_list",
            mapOf("xnxqid" to xnxq, "xqlb" to "", "xqlbmc" to ""),
        )
        requireZhkuSession(page)
        return parseZhkuExams(page)
    }

    override suspend fun fetchFreeRooms(
        year: String,
        term: String,
        weekday: String,
        start: String,
        end: String,
    ): List<FreeRoom> {
        error("仲恺教务没有空教室查询")
    }

    override suspend fun fetchNotices(): List<NoticeItem> {
        val inbox = get("$BASE/ggly/ysgg_query")
        requireZhkuSession(inbox)
        val fromInbox = parseZhkuNotices(inbox)
        if (fromInbox.isNotEmpty()) return fromInbox
        val home = runCatching { get("$BASE/framework/main_index_loadtzgg.jsp") }.getOrDefault("")
        return parseZhkuHomeNotices(home)
    }

    override suspend fun fetchNoticeDetail(id: String): NoticeItem {
        val page = get("$BASE/framework/main_index_tzgg.htmlx?id=$id")
        requireZhkuSession(page)
        return parseZhkuNoticeDetail(id, page)
    }

    override suspend fun logout() {
        runCatching {
            client.get("$BASE/xk/LoginToXk") {
                header(HttpHeaders.UserAgent, UA)
                parameter("method", "exit")
            }
        }
    }

    private suspend fun get(url: String): String =
        client.get(url) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.htmlx")
        }.bodyAsText()

    private suspend fun post(url: String, fields: Map<String, String>): String =
        client.submitForm(
            url = url,
            formParameters = Parameters.build {
                fields.forEach { (k, v) -> append(k, v) }
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/framework/xsMain.htmlx")
            header(HttpHeaders.Origin, HOST)
        }.bodyAsText()
}

@OptIn(ExperimentalEncodingApi::class)
internal fun zhkuEncoded(studentId: String, password: String): String =
    Base64.encode(studentId.encodeToByteArray()) + "%%%" + Base64.encode(password.encodeToByteArray())

internal fun zhkuSessEncoded(studentId: String, password: String, dataStr: String): String {
    val parts = dataStr.trim().split("#", limit = 2)
    if (parts.size != 2) return ""
    var scode = parts[0]
    val sxh = parts[1]
    val code = "$studentId%%%$password"
    val out = StringBuilder()
    var i = 0
    while (i < code.length) {
        if (i < 20 && i < sxh.length) {
            val n = sxh[i].digitToIntOrNull() ?: 0
            out.append(code[i])
            if (n > 0) {
                val take = n.coerceAtMost(scode.length)
                out.append(scode.substring(0, take))
                scode = scode.substring(take)
            }
            i++
        } else {
            out.append(code.substring(i))
            break
        }
    }
    return out.toString()
}

internal fun isZhkuLoginPage(text: String): Boolean {
    val t = text.lowercase()
    val hasForm = t.contains("id=\"loginform\"") || t.contains("name=\"loginform\"")
    val hasAccount = t.contains("name=\"useraccount\"") || t.contains("id=\"useraccount\"")
    val hasEncoded = t.contains("name=\"encoded\"")
    return hasForm && hasAccount && hasEncoded
}

internal fun isZhkuSessionLost(text: String): Boolean =
    text.contains("用户没有登录") || text.contains("请重新登录") && text.contains("出错")

internal fun isZhkuPasswordChange(body: String, url: String): Boolean {
    val u = url.lowercase()
    if (u.contains("grsz_xgmm") || u.contains("xgmm")) return true
    if (isFirstLoginSignal(body)) return true
    return body.contains("name=\"oldpassword\"") && (body.contains("新密码") || body.contains("name=\"password1\""))
}

internal fun zhkuShowMsg(html: String): String =
    Regex("""id="showMsg"[^>]*>([\s\S]*?)</""", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.get(1)
        ?.let { zhkuPlain(it) }
        .orEmpty()
        .ifBlank {
            Regex("""该帐?号不存在或密码错误[^<！!]*[！!]""").find(html)?.value.orEmpty()
        }

private fun requireZhkuSession(text: String) {
    if (isZhkuLoginPage(text) || isZhkuSessionLost(text)) error(SESSION_LOST_HINT)
}

internal data class ZhkuWeek(val week: Int, val monday: LocalDate)

internal data class ZhkuCourse(
    val courseId: String,
    val courseName: String,
    val credit: String,
    val hours: String,
    val teacher: String,
)

internal fun parseZhkuWeeks(html: String): List<ZhkuWeek> =
    Regex(
        """<tr[^>]*>\s*<td>(\d+)</td>\s*<td title='(\d{4})年(\d{2})月(\d{2})'>""",
        RegexOption.IGNORE_CASE,
    ).findAll(html).mapNotNull { match ->
        val week = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val date = runCatching {
            LocalDate(
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
            )
        }.getOrNull() ?: return@mapNotNull null
        ZhkuWeek(week, date)
    }.toList()

internal fun parseZhkuCurrentWeek(html: String): Int {
    val selected = Regex(
        """<option[^>]*selected[^>]*>\s*第?(\d+)周""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex(
            """<option[^>]*>\s*第?(\d+)周\s*</option>[^<]*selected""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1)?.toIntOrNull()
    if (selected != null) return selected
    return Regex("""\$\("#li_showWeek"\)[^;]*第(\d+)周""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

internal fun parseZhkuCourses(html: String): List<ZhkuCourse> =
    Regex(
        """title="([^"(]+)\((\d+)\)"[\s\S]*?学时：([^；;]+)[；;][\s\S]*?学分：([^；;]+)[；;][\s\S]*?上课教师：([^；;]+)[；;]""",
        setOf(RegexOption.IGNORE_CASE),
    ).findAll(html).map { match ->
        ZhkuCourse(
            courseId = match.groupValues[2].trim(),
            courseName = zhkuPlain(match.groupValues[1]),
            hours = zhkuPlain(match.groupValues[3]),
            credit = zhkuPlain(match.groupValues[4]),
            teacher = zhkuPlain(match.groupValues[5]).replace(Regex(",+"), ",").trim(','),
        )
    }.distinctBy { it.courseId }.toList()

internal fun parseZhkuTimetable(html: String, courses: List<ZhkuCourse>): List<LessonSlot> {
    val table = Regex(
        """<table[^>]*id="timetable"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return emptyList()
    val catalog = courses.associateBy { it.courseName }
    val slots = mutableListOf<LessonSlot>()
    val rows = table.split(Regex("""<tr\b""", RegexOption.IGNORE_CASE)).drop(1)
    for (row in rows) {
        val th = Regex("""<th[^>]*>([\s\S]*?)</th>""", RegexOption.IGNORE_CASE).find(row) ?: continue
        val periodLabel = zhkuPlain(th.groupValues[1]).replace("&nbsp;", "")
        if (periodLabel.isBlank() || periodLabel.contains("星期")) continue
        val cells = Regex("""<td\b[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE).findAll(row).toList()
        cells.take(7).forEachIndexed { index, cell ->
            val blocks = Regex(
                """<div id="([^"]+)"[^>]*class="kbcontent"[^>]*>([\s\S]*?)(?=<div id=|$)""",
                RegexOption.IGNORE_CASE,
            ).findAll(cell.groupValues[1])
            for (block in blocks) {
                val divId = block.groupValues[1]
                if (!divId.endsWith("-2")) continue
                val pieces = block.groupValues[2].split(Regex("-{5,}"))
                for (piece in pieces) {
                    val slot = parseZhkuCell(piece, index + 1, periodLabel, catalog) ?: continue
                    slots += slot
                }
            }
        }
    }
    return slots
}

private fun parseZhkuCell(
    inner: String,
    weekday: Int,
    periodLabel: String,
    catalog: Map<String, ZhkuCourse>,
): LessonSlot? {
    val fonts = Regex("""<font([^>]*)>([\s\S]*?)</font>""", RegexOption.IGNORE_CASE).findAll(inner).map { match ->
        val attrs = match.groupValues[1]
        ZhkuFont(
            title = Regex("""title=['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1).orEmpty(),
            name = Regex("""name=['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1).orEmpty(),
            text = zhkuPlain(match.groupValues[2]),
        )
    }.filter { it.text.isNotBlank() }.toList()
    if (fonts.isEmpty()) return null
    val courseName = fonts.firstOrNull { it.title.isBlank() && it.name.isBlank() }?.text.orEmpty()
    if (courseName.isBlank()) return null
    val weeksPeriod = fonts.firstOrNull { it.title.contains("周次") }?.text.orEmpty()
    val parsed = parseZhkuWeeksPeriod(weeksPeriod)
    val course = catalog[courseName]
    val room = fonts.firstOrNull { it.title == "教室" }?.text.orEmpty()
    val building = fonts.firstOrNull { it.title == "教学楼" || it.name == "jxlmc" }?.text
        .orEmpty()
        .trim('【', '】')
    val className = fonts.firstOrNull { it.title == "班级" }?.text.orEmpty().removePrefix("班级：")
    val notice = fonts.firstOrNull { it.name == "tzdbh" || it.title.contains("通知单") }?.text
        .orEmpty()
        .removePrefix("通知单编号：")
    val period = parsed.period.ifBlank { zhkuPeriodFromLabel(periodLabel) }
    return LessonSlot(
        courseId = course?.courseId?.ifBlank { null } ?: notice.ifBlank { courseName },
        courseName = courseName,
        credit = course?.credit.orEmpty(),
        teacher = fonts.firstOrNull { it.title == "教师" }?.text.orEmpty(),
        room = room,
        building = building,
        campus = zhkuCampus(room),
        weekday = weekday,
        weekdayName = WeekdayFull.getOrElse(weekday - 1) { "星期$weekday" },
        period = period,
        periodLabel = periodLabel.ifBlank { period },
        weeks = parsed.weeks,
        className = className,
        classId = notice,
        hours = course?.hours.orEmpty(),
    )
}

private data class ZhkuFont(val title: String, val name: String, val text: String)

internal data class ZhkuWeeksPeriod(val weeks: String, val period: String)

internal fun parseZhkuWeeksPeriod(raw: String): ZhkuWeeksPeriod {
    val match = Regex("""(.+?)\(周\)\[(.+?)节\]""").find(raw)
    if (match != null) {
        return ZhkuWeeksPeriod(
            weeks = match.groupValues[1].trim(),
            period = match.groupValues[2].replace(Regex("""0(\d)"""), "$1").replace("节", "").trim(),
        )
    }
    val weeksOnly = Regex("""(.+?)\(周\)""").find(raw)?.groupValues?.get(1)?.trim()
    return ZhkuWeeksPeriod(weeksOnly ?: raw.replace("周", "").trim(), "")
}

internal fun zhkuPeriodFromLabel(label: String): String = when {
    label.contains("十一十二") -> "10-12"
    label.contains("第一二") || label.contains("一二节") -> "1-2"
    label.contains("第三四") || label.contains("三四节") -> "3-4"
    label.contains("第六七") || label.contains("六七节") -> "6-7"
    label.contains("第八九") || label.contains("八九节") -> "8-9"
    label.contains("第五节") -> "5"
    label.contains("第十") -> "10-12"
    else -> ""
}

internal fun parseZhkuPractices(
    html: String,
    courses: List<ZhkuCourse>,
    slots: List<LessonSlot>,
): List<PracticeCourse> {
    val slotted = slots.map { it.courseName }.toSet()
    val fromRemark = mutableMapOf<String, String>()
    Regex("""备注[\s\S]*?<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.get(1)
        ?.let { zhkuPlain(it) }
        ?.split(";", "；")
        ?.forEach { piece ->
            val match = Regex("""(.+?)\s+(\d[\d,\-]*)\s*周""").find(piece.trim()) ?: return@forEach
            val name = match.groupValues[1].trim()
            val weeks = match.groupValues[2].trim()
            fromRemark[name] = listOfNotNull(fromRemark[name], weeks).joinToString(",")
        }
    val names = (fromRemark.keys + courses.map { it.courseName }.filter { it !in slotted }).distinct()
    return names.mapNotNull { name ->
        if (name in slotted && name !in fromRemark) return@mapNotNull null
        val course = courses.firstOrNull { it.courseName == name }
        PracticeCourse(
            name = name,
            weeks = fromRemark[name].orEmpty(),
            teacher = course?.teacher.orEmpty(),
            note = if (name !in slotted) "不在周课表格子里" else "",
        )
    }.filter { it.name.isNotBlank() }
}

internal fun parseZhkuProfile(html: String, xnxq: String, slots: List<LessonSlot>): StudentProfile {
    val (year, term) = parseZhkuTerm(xnxq)
    val yearName = if (year.length == 4) "$year-${year.toInt() + 1}" else xnxq.substringBeforeLast("-").ifBlank { year }
    val campus = slots.map { it.campus }.firstOrNull { it.isNotBlank() }.orEmpty()
    return StudentProfile(
        name = fieldAfter(html, "姓名").ifBlank { labeled(html, "姓名") },
        studentId = labeled(html, "学号"),
        studentKey = labeled(html, "学号"),
        className = labeled(html, "班级"),
        major = labeled(html, "专业"),
        campus = campus,
        yearName = yearName,
        termCode = term,
        yearCode = year,
        termLabel = if (term == "2") "第2学期" else "第1学期",
    )
}

internal fun parseZhkuGrades(html: String): List<GradeItem> {
    val rows = parseNamedTable(html)
    return rows.mapNotNull { row ->
        val name = row["课程名称"].orEmpty()
        if (name.isBlank()) return@mapNotNull null
        GradeItem(
            courseId = row["课程编号"].orEmpty(),
            courseName = name,
            credit = row["学分"].orEmpty(),
            score = row["成绩"].orEmpty(),
            gpa = row["绩点"].orEmpty(),
            term = row["开课学期"].orEmpty(),
            assess = row["考核方式"].orEmpty().ifBlank { row["考试性质"].orEmpty() },
        )
    }
}

internal fun parseZhkuExams(html: String): List<ExamItem> {
    val rows = parseNamedTable(html)
    return rows.mapNotNull { row ->
        val name = row["课程名称"].orEmpty()
        if (name.isBlank()) return@mapNotNull null
        ExamItem(
            courseName = name,
            time = row["考试时间"].orEmpty(),
            room = row["考场"].orEmpty(),
            seat = row["座位号"].orEmpty(),
            status = row["考试场次"].orEmpty().ifBlank { "已安排" },
        )
    }
}

internal fun parseZhkuNotices(html: String): List<NoticeItem> {
    val table = Regex(
        """<table[^>]*id="dataList"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return emptyList()
    if (table.contains("未查询到数据")) return emptyList()
    val headers = Regex("""<th[^>]*>([\s\S]*?)</th>""", RegexOption.IGNORE_CASE)
        .findAll(table).map { zhkuPlain(it.groupValues[1]) }.toList()
    return Regex("""<tr\b[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE).findAll(table)
        .drop(1)
        .mapNotNull { match ->
            val cells = Regex("""<td\b[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
                .findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
            if (cells.size < 2) return@mapNotNull null
            val mapped = headers.mapIndexed { i, key -> key to cells.getOrElse(i) { "" } }.toMap()
            val titleHtml = mapped["标题"] ?: cells.getOrNull(1) ?: return@mapNotNull null
            val title = zhkuPlain(titleHtml)
            if (title.isBlank() || title.contains("未查询")) return@mapNotNull null
            val id = Regex("""(?:id|ggid)=['"]?([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
                .find(titleHtml)?.groupValues?.get(1)
                ?: Regex("""['"]([A-Za-z0-9]{6,})['"]""").find(titleHtml)?.groupValues?.get(1)
                ?: title
            NoticeItem(
                id = id,
                title = title,
                date = zhkuPlain(mapped["发送时间"].orEmpty()).take(16),
                category = zhkuPlain(mapped["类别"].orEmpty()),
                publisher = zhkuPlain(mapped["发送人"].orEmpty()),
            )
        }.toList().sortedByDescending { it.date }
}

internal fun parseZhkuHomeNotices(html: String): List<NoticeItem> =
    Regex(
        """<li[^>]*>[\s\S]*?(?:id=([A-Za-z0-9]+))?[\s\S]*?>([^<]{2,80})</""",
        RegexOption.IGNORE_CASE,
    ).findAll(html).mapNotNull { match ->
        val title = zhkuPlain(match.groupValues.getOrElse(2) { "" })
        if (title.isBlank()) return@mapNotNull null
        NoticeItem(id = match.groupValues.getOrElse(1) { title }, title = title)
    }.distinctBy { it.id }.toList()

internal fun parseZhkuNoticeDetail(id: String, html: String): NoticeItem {
    val cleaned = html
        .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
    val title = Regex("""<h[1-4][^>]*>([\s\S]*?)</h[1-4]>""", RegexOption.IGNORE_CASE)
        .findAll(cleaned)
        .map { zhkuPlain(it.groupValues[1]) }
        .firstOrNull { it.isNotBlank() && it != "通知详情" }
        .orEmpty()
        .ifBlank {
            zhkuPlain(
                Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(cleaned)?.groupValues?.get(1).orEmpty(),
            )
        }
    val date = Regex("""(?:发送时间|发布时间)[:：]\s*([^<\s]+)""").find(cleaned)?.groupValues?.get(1).orEmpty()
    val publisher = Regex("""(?:发送人|发布人)[:：]\s*([^<\s]+)""").find(cleaned)?.groupValues?.get(1).orEmpty()
    val body = cleaned
        .replace(Regex("""<(br|BR)\s*/?>"""), "\n")
        .replace(Regex("""</(p|div|li|h[1-6])[^>]*>""", RegexOption.IGNORE_CASE), "\n")
    val (content, parts) = parseNoticeBody(body) { line ->
        val text = line.trim()
        text.isBlank() ||
            text == title ||
            text.contains("湖南强智") ||
            text.contains("Copyright")
    }
    return NoticeItem(
        id = id,
        title = title,
        date = date.take(16),
        publisher = publisher,
        content = content,
        parts = parts,
    )
}

private fun parseNamedTable(html: String): List<Map<String, String>> {
    if (html.contains("未查询到数据")) return emptyList()
    val table = Regex(
        """<table[^>]*id="dataList"[^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return emptyList()
    val headers = Regex("""<th[^>]*>([\s\S]*?)</th>""", RegexOption.IGNORE_CASE)
        .findAll(table).map { zhkuPlain(it.groupValues[1]) }.toList()
    if (headers.isEmpty()) return emptyList()
    return Regex("""<tr\b[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE).findAll(table)
        .drop(1)
        .mapNotNull { match ->
            val cells = Regex("""<td\b[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
                .findAll(match.groupValues[1]).map { zhkuPlain(it.groupValues[1]) }.toList()
            if (cells.isEmpty() || cells.joinToString("").contains("未查询")) return@mapNotNull null
            headers.mapIndexed { i, key -> key to cells.getOrElse(i) { "" } }.toMap()
        }.toList()
}

private fun labeled(html: String, label: String): String =
    Regex("""$label：\s*([^<]+)""").find(html)?.groupValues?.get(1)?.let { zhkuPlain(it) }.orEmpty()

private fun fieldAfter(html: String, label: String): String {
    val block = Regex(
        """>$label</td>\s*<td[^>]*>([\s\S]*?)</td>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return ""
    return zhkuPlain(block)
}

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

internal fun zhkuCampus(room: String): String = when {
    room.contains("白云") || room.contains("(白)") || room.contains("（白）") -> "白云"
    room.contains("海珠") || room.contains("(海)") || room.contains("（海）") -> "海珠"
    room.contains("信息学院") -> "信息学院"
    else -> ""
}

internal fun zhkuPlain(html: String): String =
    html
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("[\\t ]+"), " ")
        .trim()
