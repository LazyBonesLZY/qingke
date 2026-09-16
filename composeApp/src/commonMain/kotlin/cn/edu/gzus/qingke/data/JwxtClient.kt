package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

class JwxtClient(
    private val origin: String = "https://jwxt.gzus.edu.cn",
    private val client: HttpClient = createHttpClient(),
    override val supportsFreeRooms: Boolean = true,
    override val supportsCoursePick: Boolean = true,
    private val paths: Map<String, String> = emptyMap(),
) : SchoolPortal {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val BASE = "$origin/jwglxt"

    private fun api(key: String, defaultPath: String): String {
        val raw = paths[key]?.trim().orEmpty().ifBlank { defaultPath }
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/") -> origin + raw
            else -> "$BASE/$raw"
        }
    }

    override suspend fun fetchCaptcha(): LoginCaptcha? {
        client.get(api("loginPage", "xtgl/login_slogin.html")) {
            header(HttpHeaders.UserAgent, UA)
        }
        val bytes = client.get(api("captcha", "kaptcha")) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/xtgl/login_slogin.html")
            parameter("time", nowMillis())
        }.readRawBytes()
        if (bytes.size < 40) return null
        return LoginCaptcha(id = nowMillis().toString(), bytes = bytes, hint = "图中字符")
    }

    override suspend fun login(
        studentId: String,
        password: String,
        captcha: String,
        captchaId: String,
    ): Result<Unit> = runCatching {
        val loginPage = client.get(api("loginPage", "xtgl/login_slogin.html")) {
            header(HttpHeaders.UserAgent, UA)
        }.bodyAsText()
        val csrf = Regex("""id="csrftoken"[^>]*value="([^"]+)"""").find(loginPage)?.groupValues?.get(1)
            ?: error("登录页没有 csrftoken，教务可能在维护")
        val pkText = client.get(api("publicKey", "xtgl/login_getPublicKey.html")) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, api("loginPage", "xtgl/login_slogin.html"))
            parameter("time", nowMillis())
        }.bodyAsText()
        val pk = json.parseToJsonElement(pkText).jsonObject
        val modulus = pk["modulus"]?.jsonPrimitive?.content ?: error("公钥失败")
        val exponent = pk["exponent"]?.jsonPrimitive?.content ?: "AQAB"
        val encrypted = rsaEncrypt(password, modulus, exponent)
        client.submitForm(
            url = "$BASE/xtgl/login_logoutAccount.html",
            formParameters = Parameters.build { append("csrfTokenLogout", "") },
        )
        val result = client.submitForm(
            url = "${api("login", "xtgl/login_slogin.html")}?time=${nowMillis()}",
            formParameters = Parameters.build {
                append("csrftoken", csrf)
                append("language", "zh_CN")
                append("ydType", "")
                append("yhm", studentId)
                append("mm", encrypted)
                if (captcha.isNotBlank()) append("yzm", captcha.trim())
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/xtgl/login_slogin.html")
            header(HttpHeaders.Origin, origin)
        }
        val body = result.bodyAsText()
        val finalUrl = result.request.url.toString()
        if (isPasswordChangePage(body, finalUrl)) throw JwxtNeedFirstLogin()
        if (isLoginForm(body)) {
            val tip = loginFormTip(body)
            when {
                isFirstLoginSignal(tip) || isFirstLoginSignal(body) -> throw JwxtNeedFirstLogin()
                tip.isNotBlank() -> error(tip)
                else -> error("学号或密码不正确")
            }
        }
        val home = client.get("$BASE/xtgl/index_initMenu.html") {
            header(HttpHeaders.UserAgent, UA)
            parameter("jsdm", "xs")
        }
        val homeBody = home.bodyAsText()
        val homeUrl = home.request.url.toString()
        if (isPasswordChangePage(homeBody, homeUrl)) throw JwxtNeedFirstLogin()
        if (isLoginForm(homeBody)) {
            if (isFirstLoginSignal(homeBody)) throw JwxtNeedFirstLogin()
            error("登录没有成功，请重试")
        }
    }

    override suspend fun fetchTermCalendar(): TermCalendar {
        val page = client.get(api("calendar", "kbcx/xskbcxZccx_cxXskbcxIndex.html")) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/xtgl/index_initMenu.html?jsdm=xs")
            parameter("gnmkdm", "N2154")
            parameter("layout", "default")
        }.bodyAsText()
        requireSession(page)
        val year = htmlInputValue(page, "xnm_hide").ifBlank { htmlSelectedValue(page, "xnm") }
        val term = htmlInputValue(page, "xqm_hide").ifBlank { htmlSelectedValue(page, "xqm") }
        var current = htmlInputValue(page, "dqzc_hide").toIntOrNull()
            ?: htmlSelectedValue(page, "zs").toIntOrNull()
            ?: 0
        val weeks = parseWeekSpans(page)
        val today = nowDateTime().date
        val fromTable = weeks.firstOrNull { today >= it.start && today <= it.end }?.week ?: 0
        if (fromTable > 0) current = fromTable
        if (current <= 0 && weeks.isNotEmpty()) {
            current = htmlSelectedValue(page, "zs").toIntOrNull() ?: weeks.maxOf { it.week }
        }
        var termStart = weeks.firstOrNull { it.week == 1 }?.start?.toString().orEmpty()
        if (termStart.isBlank() && weeks.isNotEmpty()) {
            val known = weeks.minBy { it.week }
            termStart = known.start.minus(DatePeriod(days = (known.week - 1) * 7)).toString()
        }
        if (year.isBlank() || term.isBlank() || current <= 0) {
            val home = client.get("$BASE/xtgl/index_cxAreaOne.html") {
                header(HttpHeaders.UserAgent, UA)
                header("X-Requested-With", "XMLHttpRequest")
            }.bodyAsText()
            if (current <= 0) current = htmlInputValue(home, "dqzc").toIntOrNull() ?: 0
        }
        if (termStart.isBlank() && current > 0) {
            termStart = termStartFromCurrentWeek(today, current).toString()
        }
        if (current <= 0 && termStart.isBlank()) error("正方周历没有当前周")
        return TermCalendar(
            yearCode = year,
            termCode = term,
            currentWeek = current,
            termStart = termStart,
            weekCount = weeks.maxOfOrNull { it.week } ?: 0,
        )
    }

    override suspend fun fetchTimetable(year: String, term: String): Triple<StudentProfile, List<LessonSlot>, List<PracticeCourse>> {
        val text = client.submitForm(
            url = api("timetable", "kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"),
            formParameters = Parameters.build {
                append("xnm", year)
                append("xqm", term)
                append("kzlx", "ck")
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151")
            header("X-Requested-With", "XMLHttpRequest")
        }.bodyAsText()
        val root = requireJsonObject(text, emptyHint = "这一学期还没有课表")
        val xs = root["xsxx"]?.jsonObject ?: JsonObject(emptyMap())
        val slots = (root["kbList"] as? JsonArray)?.map { el ->
            val o = el.jsonObject
            val weekdayName = o.str("xqjmc")
            LessonSlot(
                courseId = o.str("kch"),
                courseName = o.str("kcmc"),
                credit = o.str("xf"),
                teacher = o.str("xm"),
                room = o.str("cdmc"),
                building = o.str("lh"),
                campus = o.str("xqmc"),
                weekday = parseWeekday(o.str("xqj"), weekdayName),
                weekdayName = weekdayName,
                period = o.str("jcor").ifBlank { o.str("jcs") },
                periodLabel = o.str("jc"),
                weeks = o.str("zcd"),
                className = o.str("jxbmc"),
                classId = o.str("jxb_id"),
                category = o.str("kclb"),
                required = o.str("kcxz"),
                assess = o.str("khfsmc"),
                teachMode = o.str("skfsmc"),
                hours = o.str("kczxs"),
            )
        } ?: emptyList()
        val practices = (root["sjkList"] as? JsonArray)?.map { el ->
            val o = el.jsonObject
            PracticeCourse(
                name = o.str("kcmc"),
                weeks = o.str("qsjsz"),
                teacher = o.str("jsxm"),
                note = o.str("sjkcgs").ifBlank { o.str("qtkcgs") },
            )
        } ?: emptyList()
        val yearName = xs.str("XNMC").ifBlank {
            year.toIntOrNull()?.let { "$it-${it + 1}" }.orEmpty()
        }
        val termCode = xs.str("XQM").ifBlank { term }
        val profile = StudentProfile(
            name = xs.str("XM"),
            studentId = xs.str("XH"),
            studentKey = xs.str("XH_ID"),
            className = xs.str("BJMC"),
            major = xs.str("ZYMC"),
            campus = xs.str("XQMC").ifBlank { xs.str("xqmc") }.ifBlank { slots.firstOrNull()?.campus.orEmpty() },
            yearName = yearName,
            termCode = termCode,
            yearCode = xs.str("XNM").ifBlank { year },
            termLabel = when {
                xs.str("XQMMC") == "2" || termCode == "12" -> "第2学期"
                else -> "第1学期"
            },
        )
        return Triple(profile, slots, practices)
    }

    override suspend fun fetchGrades(): List<GradeItem> {
        val text = postQuery(api("grades", "cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005"))
        val root = requireJsonObject(text, emptyHint = "成绩还没出来")
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.map { el ->
            val o = el.jsonObject
            GradeItem(
                courseId = o.str("kch"),
                courseName = o.str("kcmc"),
                credit = o.str("xf"),
                score = o.str("cj"),
                gpa = o.str("jd"),
                term = "${o.str("xnmmc")} ${o.str("xqmmc")}",
                assess = o.str("ksxz"),
            )
        }
    }

    override suspend fun fetchExams(year: String, term: String): List<ExamItem> {
        val text = postQuery(
            api("exams", "kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105"),
            extra = mapOf("xnm" to year, "xqm" to term),
        )
        val root = requireJsonObject(text, emptyHint = "考试还没安排")
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.map { el ->
            val o = el.jsonObject
            ExamItem(
                courseName = o.str("kcmc"),
                time = o.str("kssj"),
                room = o.str("cdmc"),
                seat = o.str("zwh"),
                status = o.str("ksmc").ifBlank { "已安排" },
            )
        }
    }

    override suspend fun fetchFreeRooms(year: String, term: String, weekday: String, start: String, end: String): List<FreeRoom> {
        val text = postQuery(
            api("rooms", "cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155"),
            extra = mapOf(
                "xnm" to year,
                "xqm" to term,
                "xqj" to weekday,
                "ksjc" to start,
                "jsjc" to end,
            ),
        )
        val root = requireJsonObject(text, emptyHint = "空教室没有结果")
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.map { el ->
            val o = el.jsonObject
            FreeRoom(
                name = o.str("cdmc"),
                building = o.str("jxlmc"),
                campus = o.str("xqmc"),
                capacity = o.str("zws"),
                type = o.str("cdlbmc"),
            )
        }
    }

    override suspend fun fetchNotices(): List<NoticeItem> {
        val widget = fetchNewsWidget()
        val morePage = runCatching { fetchNewsMorePage() }.getOrDefault(emptyList())
        val moreQuery = runCatching { fetchNewsQuery() }.getOrDefault(emptyList())
        val todos = runCatching { fetchTodoMessages() }.getOrDefault(emptyList())
        return mergeNoticeLists(widget, morePage, moreQuery, todos)
    }

    override suspend fun fetchNoticeDetail(id: String): NoticeItem {
        val page = client.get(api("noticeDetail", "xtgl/xwck_ckXw.html")) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/xtgl/index_cxNews.html")
            parameter("xwbh", id)
            parameter("doType", "save")
        }.bodyAsText()
        requireSession(page)
        return parseNoticeDetail(id, page)
    }

    override suspend fun logout() {
        runCatching {
            client.post(api("logout", "xtgl/login_logout.html")) {
                header(HttpHeaders.UserAgent, UA)
            }
        }
    }

    override suspend fun fetchCoursePickScopes(): List<CoursePickScope> {
        val gnmkdm = xsxkGnmkdm()
        val indexUrl = api("courseIndex", "xsxk/zzxkyzb_cxZzxkYzbIndex.html")
        val index = client.get(indexUrl) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/xtgl/index_initMenu.html?jsdm=xs")
            parameter("gnmkdm", gnmkdm)
            parameter("layout", "default")
        }.bodyAsText()
        requireSession(index)
        if (looksLikeCoursePickClosed(index)) error("现在不是选课时间")
        val indexFields = htmlHiddenFields(index)
        val categories = QueryCourse4.findAll(index).map { it.groupValues }.toList()
        val categoryRows = if (categories.isNotEmpty()) {
            categories
        } else {
            val firstKklxdm = indexFields["firstKklxdm"].orEmpty()
            val firstXkkzId = indexFields["firstXkkzId"].orEmpty()
            if (firstKklxdm.isNotBlank() && firstXkkzId.isNotBlank()) {
                listOf(
                    listOf(
                        "",
                        firstKklxdm,
                        firstXkkzId,
                        indexFields["firstNjdmId"].orEmpty().ifBlank { indexFields["njdm_id"].orEmpty() },
                        indexFields["firstZyhId"].orEmpty().ifBlank { indexFields["zyh_id"].orEmpty() },
                    ),
                )
            } else {
                emptyList()
            }
        }
        if (categoryRows.isEmpty() && indexFields["xkxnm"].isNullOrBlank() && indexFields["xkkz_id"].isNullOrBlank()) {
            error("教务没有打开自主选课")
        }
        val rows = categoryRows.ifEmpty {
            listOf(
                listOf(
                    "",
                    indexFields["kklxdm"].orEmpty().ifBlank { "default" },
                    indexFields["xkkz_id"].orEmpty(),
                    indexFields["njdm_id"].orEmpty(),
                    indexFields["zyh_id"].orEmpty(),
                ),
            )
        }
        val displayUrl = api("courseDisplay", "xsxk/zzxkyzb_cxZzxkYzbDisplay.html")
        return rows.mapIndexed { indexNo, row ->
            val kklxdm = row.getOrElse(1) { "" }
            val xkkzId = row.getOrElse(2) { "" }
            val njdm = row.getOrElse(3) { "" }
            val zyh = row.getOrElse(4) { "" }
            val fields = indexFields.toMutableMap()
            fields["kklxdm"] = kklxdm
            fields["xkkz_id"] = xkkzId
            fields["njdm_id"] = njdm
            fields["zyh_id"] = zyh
            val display = postXsxk(
                "$displayUrl?gnmkdm=$gnmkdm",
                mapOf(
                    "xkkz_id" to xkkzId,
                    "kklxdm" to kklxdm,
                    "xszxzt" to "1",
                    "njdm_id" to njdm,
                    "zyh_id" to zyh,
                    "kspage" to "0",
                    "jspage" to "0",
                ),
            )
            requireSession(display)
            fields.putAll(htmlHiddenFields(display))
            indexFields["firstXkkzXh"]?.takeIf { it.isNotBlank() }?.let { fields["xkkz_xh"] = it }
            indexFields["firstKklxmc"]?.takeIf { it.isNotBlank() }?.let { fields["kklxmc"] = it }
            if (fields["jg_id"].isNullOrBlank()) {
                fields["jg_id_1"]?.takeIf { it.isNotBlank() }?.let { fields["jg_id"] = it }
            }
            val name = fields["kklxmc"].orEmpty()
                .ifBlank { categoryLabel(index, kklxdm, xkkzId) }
                .ifBlank { fields["xklcmc"].orEmpty() }
                .ifBlank { if (kklxdm == "default") "选课" else kklxdm }
            CoursePickScope(
                id = "zf-$indexNo-$kklxdm",
                name = name,
                params = fields,
            )
        }
    }

    override suspend fun fetchCoursePickOffers(
        scope: CoursePickScope,
        keyword: String,
        start: Int,
        pageSize: Int,
    ): List<CoursePickOffer> {
        val gnmkdm = xsxkGnmkdm()
        val url = api("courseList", "xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html")
        val size = pageSize.coerceIn(1, 50)
        val params = zzxkRequestParams(
            scope.params,
            keyword = keyword,
            kspage = (start + 1).toString(),
            jspage = (start + size).toString(),
        )
        val text = postXsxk("$url?gnmkdm=$gnmkdm", params)
        return xsxkObjects(text, "tmpList", "courses", "items").map { item ->
            offerFromJson(item, scope)
        }
    }

    override suspend fun fetchCoursePickSections(offer: CoursePickOffer): List<CoursePickSection> {
        val gnmkdm = xsxkGnmkdm()
        val url = api("courseDetails", "xsxk/zzxkyzbjk_cxJxbWithKchZzxkYzb.html")
        val params = zzxkRequestParams(
            offer.params,
            keyword = "",
            kspage = "1",
            jspage = "200",
            extra = mapOf("kch_id" to offer.courseId),
        )
        val text = postXsxk("$url?gnmkdm=$gnmkdm", params)
        return xsxkObjects(text, "tmpList", "data", "courses", "jxbList").map { item ->
            sectionFromJson(item, offer)
        }
    }

    override suspend fun selectCoursePick(offer: CoursePickOffer, section: CoursePickSection): String {
        val gnmkdm = xsxkGnmkdm()
        val fresh = runCatching { fetchCoursePickSections(offer) }.getOrDefault(emptyList())
        val hit = fresh.firstOrNull { row ->
            row.classId.isNotBlank() && (row.classId == section.classId || row.classId == section.doJxbId) ||
                row.doJxbId.isNotBlank() && (row.doJxbId == section.doJxbId || row.doJxbId == section.classId)
        } ?: section
        val jxbIds = hit.doJxbId.ifBlank { hit.classId }
        if (jxbIds.isBlank()) error("没有拿到教学班编号")
        val url = api("courseSelect", "xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html")
        val text = postXsxk("$url?gnmkdm=$gnmkdm", selectFields(offer, hit.copy(doJxbId = jxbIds)))
        return parseSelectResult(text)
    }

    override suspend fun fetchCoursePicked(scope: CoursePickScope): List<CoursePickOffer> {
        val gnmkdm = xsxkGnmkdm()
        val url = api("courseChosen", "xsxk/zzxkyzb_cxZzxkYzbChoosedDisplay.html")
        val params = zzxkRequestParams(scope.params, keyword = "", kspage = "1", jspage = "200")
        val text = postXsxk("$url?gnmkdm=$gnmkdm", params)
        return xsxkObjects(text, "tmpList", "courses", "items", "data").map { item ->
            offerFromJson(item, scope).copy(selected = true)
        }
    }

    private fun xsxkGnmkdm(): String = paths["courseGnmkdm"]?.trim().orEmpty().ifBlank { "N253512" }

    private suspend fun postXsxk(url: String, fields: Map<String, String>): String {
        val gnmkdm = xsxkGnmkdm()
        return client.submitForm(
            url,
            Parameters.build { fields.forEach { (k, v) -> append(k, v) } },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header(HttpHeaders.Referrer, "$BASE/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=$gnmkdm&layout=default")
            header(HttpHeaders.Origin, origin)
        }.bodyAsText()
    }

    private fun zzxkRequestParams(
        source: Map<String, String>,
        keyword: String,
        kspage: String,
        jspage: String,
        extra: Map<String, String> = emptyMap(),
    ): LinkedHashMap<String, String> {
        val params = linkedMapOf<String, String>()
        for (key in ZZXK_REQUEST_FIELDS) {
            source[key]?.let { params[key] = it }
        }
        source["jg_id_1"]?.takeIf { it.isNotBlank() }?.let { params["jg_id"] = it }
        if (source["jxbzbkg"] == "1") params["jxbzb"] = source["jxbzb"].orEmpty()
        if (source["jxbzhkg"] == "1") params["zh"] = source["zh"].orEmpty()
        if (keyword.isNotBlank()) params["filter_list[0]"] = keyword.trim()
        params["kspage"] = kspage
        params["jspage"] = jspage
        params.putAll(extra)
        return params
    }

    private fun selectFields(offer: CoursePickOffer, section: CoursePickSection): Map<String, String> {
        val src = offer.params + section.params
        val kch = offer.courseId.ifBlank { src["kch_id"].orEmpty() }
        val name = offer.name.ifBlank { src["kcmc"].orEmpty() }
        val out = linkedMapOf<String, String>()
        out["jxb_ids"] = section.doJxbId.ifBlank { section.classId }
        out["kch_id"] = kch
        out["kcmc"] = if (name.startsWith("(")) name else "($kch)$name"
        out["rwlx"] = src["rwlx"].orEmpty()
        out["rlkz"] = src["rlkz"].orEmpty()
        out["rlzlkz"] = src["rlzlkz"].orEmpty()
        out["sxbj"] = src["sxbj"].orEmpty()
        out["xxkbj"] = src["xxkbj"].orEmpty()
        out["qz"] = src["qz"].orEmpty().ifBlank { "0" }
        out["cxbj"] = src["cxbj"].orEmpty()
        out["xkkz_id"] = src["xkkz_id"].orEmpty()
        out["njdm_id"] = src["njdm_id"].orEmpty()
        out["zyh_id"] = src["zyh_id"].orEmpty()
        out["kklxdm"] = src["kklxdm"].orEmpty()
        out["xklc"] = src["xklc"].orEmpty()
        out["xkxnm"] = src["xkxnm"].orEmpty()
        out["xkxqm"] = src["xkxqm"].orEmpty()
        out["jcxx_id"] = src["jcxx_id"].orEmpty()
        return out
    }

    private fun offerFromJson(item: JsonObject, scope: CoursePickScope): CoursePickOffer {
        val raw = scope.params.toMutableMap()
        for ((key, value) in item.flat()) {
            if (value.isNotBlank()) raw[key] = value
        }
        val jsxx = item.str("jsxx")
        val teacher = item.str("jsxm").ifBlank { teacherFromJsxx(jsxx) }
        return CoursePickOffer(
            courseId = item.str("kch_id").ifBlank { item.str("kch") },
            name = stripXsxkHtml(item.str("kcmc")),
            credit = item.str("xf").ifBlank { item.str("jxbxf") },
            teacher = teacher,
            className = item.str("jxbmc"),
            classId = item.str("jxb_id"),
            time = stripXsxkHtml(item.str("sksj")),
            place = stripXsxkHtml(item.str("jxdd")),
            capacity = item.str("jxbrl").ifBlank { item.str("jxbrs") },
            taken = item.str("yxzrs"),
            selected = item.str("sfxkbj") == "1",
            scopeId = scope.id,
            params = raw,
        )
    }

    private fun sectionFromJson(item: JsonObject, offer: CoursePickOffer): CoursePickSection {
        val raw = offer.params.toMutableMap()
        for ((key, value) in item.flat()) {
            if (value.isNotBlank()) raw[key] = value
        }
        val jsxx = item.str("jsxx")
        val classId = item.str("jxb_id")
        return CoursePickSection(
            classId = classId,
            doJxbId = item.str("do_jxb_id").ifBlank { classId },
            name = item.str("jxbmc").ifBlank { offer.className }.ifBlank { offer.name },
            teacher = item.str("jsxm").ifBlank { teacherFromJsxx(jsxx) }.ifBlank { offer.teacher },
            time = stripXsxkHtml(item.str("sksj")).ifBlank { offer.time },
            place = stripXsxkHtml(item.str("jxdd")).ifBlank { offer.place },
            credit = item.str("xf").ifBlank { item.str("jxbxf") }.ifBlank { offer.credit },
            capacity = item.str("jxbrl").ifBlank { item.str("jxbrs") }.ifBlank { offer.capacity },
            taken = item.str("yxzrs").ifBlank { offer.taken },
            params = raw,
        )
    }

    private fun parseSelectResult(text: String): String {
        requireSession(text)
        val body = text.trim().trim('"')
        if (body == "1") return "选课成功"
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val flag = root?.str("flag").orEmpty()
        val msg = root?.str("msg").orEmpty().ifBlank { root?.str("message").orEmpty() }
        if (flag == "1" || body.contains("\"flag\":\"1\"")) return msg.ifBlank { "选课成功" }
        if (msg.contains("已经选") || msg.contains("已选该") || msg.contains("重复选课")) return msg
        if (msg.isNotBlank()) error(msg)
        if (body.contains("成功")) return "选课成功"
        error(body.take(120).ifBlank { "选课失败" })
    }

    private fun xsxkObjects(text: String, vararg keys: String): List<JsonObject> {
        requireSession(text)
        val body = text.trim()
        if (body.isEmpty() || body == "null" || body == "[]") return emptyList()
        if (body.startsWith("<")) {
            requireSession(body)
            error("教务这次没返回数据")
        }
        val el = runCatching { json.parseToJsonElement(body) }.getOrElse {
            error("教务返回了无法识别的列表")
        }
        when (el) {
            is JsonArray -> return el.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val flag = el.str("flag")
                val msg = el.str("msg").ifBlank { el.str("message") }
                for (key in keys) {
                    val arr = el[key] as? JsonArray
                    if (arr != null) return arr.mapNotNull { it as? JsonObject }
                }
                if (flag in setOf("0", "false") && msg.isNotBlank()) error(msg)
                return emptyList()
            }
            else -> error("教务返回了无法识别的列表")
        }
    }

    private suspend fun fetchNewsWidget(): List<NoticeItem> {
        val posted = runCatching {
            val page = client.submitForm(
                api("news", "xtgl/index_cxNews.html"),
                Parameters.build {
                    append("localeKey", "zh_CN")
                    append("gnmkdm", "index")
                    append("limit", "200")
                },
            ) {
                header(HttpHeaders.UserAgent, UA)
                header(HttpHeaders.Referrer, "$BASE/xtgl/index_initMenu.html?jsdm=xs")
                header("X-Requested-With", "XMLHttpRequest")
            }.bodyAsText()
            requireSession(page)
            parseNewsList(page)
        }.getOrDefault(emptyList())
        val page = client.get(api("news", "xtgl/index_cxNews.html")) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/xtgl/index_initMenu.html?jsdm=xs")
            header("X-Requested-With", "XMLHttpRequest")
            parameter("localeKey", "zh_CN")
            parameter("gnmkdm", "index")
            parameter("limit", "200")
        }.bodyAsText()
        requireSession(page)
        return mergeNoticeLists(posted, parseNewsList(page))
    }

    private suspend fun fetchNewsMorePage(): List<NoticeItem> {
        val page = client.get(api("newsMore", "xtgl/xwck_cxMoreXwList.html")) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$BASE/xtgl/index_initMenu.html?jsdm=xs")
            parameter("localeKey", "zh_CN")
            parameter("gnmkdm", "index")
        }.bodyAsText()
        requireSession(page)
        return parseNewsList(page)
    }

    private suspend fun fetchNewsQuery(): List<NoticeItem> {
        val candidates = listOfNotNull(
            this.paths["newsQuery"]?.let { api("newsQuery", it) },
            "$BASE/xtgl/xwck_cxXw.html?doType=query",
            "$BASE/xtgl/xwck_cxMoreXwList.html?doType=query",
            "$BASE/xwck/xwck_cxXw.html?doType=query",
            "$BASE/xwck/xwck_cxMoreXwList.html?doType=query",
        ).distinct()
        for (path in candidates) {
            val text = runCatching {
                postQuery(
                    path,
                    mapOf(
                        "queryModel.sortName" to "fbsj",
                        "queryModel.sortOrder" to "desc",
                        "queryModel.showCount" to "200",
                    ),
                )
            }.getOrNull() ?: continue
            val items = parseNewsJson(text)
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    private suspend fun fetchTodoMessages(): List<NoticeItem> {
        val text = postQuery(
            api("todos", "xtgl/index_cxDbsy.html?doType=query"),
            mapOf(
                "sfyy" to "0",
                "flag" to "1",
                "queryModel.sortName" to "cjsj",
                "queryModel.sortOrder" to "desc",
                "queryModel.showCount" to "200",
            ),
        )
        return parseDbsyJson(text)
    }

    private suspend fun postQuery(url: String, extra: Map<String, String> = emptyMap()): String {
        val sortName = extra["queryModel.sortName"].orEmpty()
        val sortOrder = extra["queryModel.sortOrder"] ?: "asc"
        val showCount = extra["queryModel.showCount"] ?: "100"
        val currentPage = extra["queryModel.currentPage"] ?: "1"
        val params = Parameters.build {
            extra.forEach { (k, v) ->
                if (k !in QUERY_MODEL_KEYS) append(k, v)
            }
            append("_search", "false")
            append("nd", nowMillis().toString())
            append("queryModel.showCount", showCount)
            append("queryModel.currentPage", currentPage)
            append("queryModel.sortName", sortName)
            append("queryModel.sortOrder", sortOrder)
            append("time", "0")
        }
        return client.submitForm(url, params) {
            header(HttpHeaders.UserAgent, UA)
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.Referrer, "$BASE/xtgl/index_initMenu.html")
        }.bodyAsText()
    }

    private fun requireJsonObject(text: String, emptyHint: String): JsonObject {
        val body = text.trim()
        requireSession(body)
        if (body.isEmpty() || body == "null") error(emptyHint)
        if (body.startsWith("<")) {
            requireSession(body)
            error("教务这次没返回数据")
        }
        return json.parseToJsonElement(body).jsonObject
    }
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.flat(): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for ((key, value) in this) {
        val primitive = value as? JsonPrimitive ?: continue
        out[key] = primitive.contentOrNull?.trim().orEmpty()
    }
    return out
}

private val QueryCourse4 = Regex(
    """queryCourse\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]*)['"]\s*,\s*['"]([^'"]*)['"]""",
)

private val HiddenInput = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)

private val ZZXK_REQUEST_FIELDS = listOf(
    "rwlx", "xklc", "xkly", "bklx_id", "sfkkjyxdxnxq", "kzkcgs",
    "xqh_id", "njdm_id_1", "zyh_id_1", "gnjkxdnj", "zyh_id", "zyfx_id", "njdm_id", "bh_id",
    "bjgkczxbbjwcx", "xbm", "xslbdm", "mzm", "xz", "ccdm", "xsbj", "sfkknj", "sfkkzy", "kzybkxy",
    "sfznkx", "zdkxms", "sfkxq", "bhbcyxkjxb", "sfkcfx", "kkbk", "kkbkdj", "bklbkcj", "sfkgbcx",
    "sfrxtgkcxd", "xkkz_xh", "tykczgxdcs", "xkxnm", "xkxqm", "kklxdm", "bbhzxjxb", "zxgbxkkg",
    "xkkz_id", "rlkz", "xkzgbj",
)

private fun htmlHiddenFields(html: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (tag in HiddenInput.findAll(html).map { it.value }) {
        val type = htmlAttr(tag, "type").lowercase()
        if (type.isNotBlank() && type != "hidden") continue
        val name = htmlAttr(tag, "name").ifBlank { htmlAttr(tag, "id") }
        if (name.isBlank()) continue
        out[name] = htmlAttr(tag, "value")
    }
    return out
}

private fun htmlAttr(tag: String, name: String): String =
    Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(tag)?.groupValues?.get(1)?.trim().orEmpty()

private fun categoryLabel(html: String, kklxdm: String, xkkzId: String): String {
    if (kklxdm.isBlank()) return ""
    val needle = Regex(
        """queryCourse\s*\(\s*['"]${Regex.escape(kklxdm)}['"]\s*,\s*['"]${Regex.escape(xkkzId)}['"][^)]*\)""",
    ).find(html) ?: return ""
    return Regex(""">\s*([^<>]{1,20}?)\s*<""")
        .find(html, needle.range.last + 1)
        ?.groupValues?.get(1)
        ?.trim()
        .orEmpty()
        .takeIf { it.isNotBlank() && !it.contains("javascript", ignoreCase = true) }
        .orEmpty()
}

private fun looksLikeCoursePickClosed(html: String): Boolean {
    val text = html.replace(Regex("<[^>]+>"), " ")
    return listOf("不属于选课", "当前不是选课", "不在选课时间", "未开放选课", "选课时间已过").any { text.contains(it) }
}

private fun stripXsxkHtml(raw: String): String =
    raw.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " · ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('-', '·', ' ')

private fun teacherFromJsxx(raw: String): String {
    val parts = raw.split('/').map { it.trim() }.filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> parts[1]
        else -> raw.trim()
    }
}

private fun requireSession(text: String) {
    val body = text.trim()
    if (body.contains("用户登录") && (body.contains("name=\"yhm\"") || body.contains("name='yhm'"))) {
        error(SESSION_LOST_HINT)
    }
}

private fun htmlInputValue(html: String, id: String): String {
    val tag = Regex(
        """<input\b[^>]*\bid\s*=\s*"${Regex.escape(id)}"[^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.value ?: return ""
    return Regex("""\bvalue\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        .find(tag)?.groupValues?.get(1)?.trim().orEmpty()
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

private data class WeekSpan(val week: Int, val start: LocalDate, val end: LocalDate)

private fun parseWeekSpans(html: String): List<WeekSpan> {
    val block = Regex(
        """<select\b[^>]*\bid\s*=\s*"zs"[^>]*>[\s\S]*?</select>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.value ?: return emptyList()
    val ranged = Regex(
        """<option\b[^>]*\bvalue\s*=\s*"(\d+)"[^>]*>\s*\d+\s*\((\d{4}-\d{2}-\d{2})至(\d{4}-\d{2}-\d{2})""",
        RegexOption.IGNORE_CASE,
    ).findAll(block).mapNotNull { match ->
        val week = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val start = parseIsoDate(match.groupValues[2]) ?: return@mapNotNull null
        val end = parseIsoDate(match.groupValues[3]) ?: start.plus(DatePeriod(days = 6))
        WeekSpan(week, start, end)
    }.toList()
    if (ranged.isNotEmpty()) return ranged
    return Regex(
        """<option\b[^>]*\bvalue\s*=\s*"(\d+)"[^>]*>\s*\d+\s*\((\d{4}-\d{2}-\d{2})""",
        RegexOption.IGNORE_CASE,
    ).findAll(block).mapNotNull { match ->
        val week = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val start = parseIsoDate(match.groupValues[2]) ?: return@mapNotNull null
        WeekSpan(week, start, start.plus(DatePeriod(days = 6)))
    }.toList()
}

private fun nowMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

private val NewsLink = Regex(
    """<a\b[^>]*\bhref="([^"]*xwck_ckXw\.html[^"]*)"[^>]*>([\s\S]*?)</a>""",
    RegexOption.IGNORE_CASE,
)

private val QUERY_MODEL_KEYS = setOf(
    "queryModel.sortName",
    "queryModel.sortOrder",
    "queryModel.showCount",
    "queryModel.currentPage",
)

internal fun parseNewsList(html: String): List<NoticeItem> =
    NewsLink.findAll(html).mapNotNull { match ->
        val href = match.groupValues[1]
        val inner = match.groupValues[2]
        val id = Regex("""[?&]xwbh=([^&"']+)""", RegexOption.IGNORE_CASE)
            .find(href)?.groupValues?.get(1)?.trim().orEmpty()
        if (id.isBlank()) return@mapNotNull null
        val open = Regex("""<a\b[^>]*>""", RegexOption.IGNORE_CASE).find(match.value)?.value.orEmpty()
        val attrTitle = htmlUnescape(
            Regex("""\btitle="([^"]*)"""").find(open)?.groupValues?.get(1).orEmpty(),
        ).trim()
        val innerTitle = htmlUnescape(stripTags(inner))
            .replace("【置顶】", "")
            .replace(Regex("""【[^】]+】"""), "")
            .trim()
        val title = listOf(attrTitle, innerTitle).maxBy { it.length }
        if (title.isBlank()) return@mapNotNull null
        val date = Regex(
            """<span[^>]*class="time"[^>]*>([^<]+)</span>""",
            RegexOption.IGNORE_CASE,
        ).find(inner)?.groupValues?.get(1)?.trim().orEmpty()
        NoticeItem(
            id = id,
            title = title,
            date = date,
            category = Regex("""【(?!置顶)([^】]+)】""").find(inner)?.groupValues?.get(1).orEmpty(),
            pinned = inner.contains("【置顶】"),
            isNew = inner.contains("index_png new"),
        )
    }.distinctBy { it.id }.toList()

internal fun parseNewsJson(text: String): List<NoticeItem> {
    val body = text.trim()
    if (body.isEmpty() || body == "null" || body.startsWith("<")) return emptyList()
    val root = runCatching { Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(body).jsonObject }
        .getOrNull() ?: return emptyList()
    val items = root["items"] as? JsonArray ?: return emptyList()
    return items.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.str("xwbh").ifBlank { o.str("id") }
        val title = htmlUnescape(
            o.str("xwbt").ifBlank { o.str("title") }.ifBlank { o.str("bt") }.ifBlank { o.str("xxbt") },
        )
        if (id.isBlank() || title.isBlank()) return@mapNotNull null
        val date = o.str("fbsj").ifBlank { o.str("fbsjStr") }.ifBlank { o.str("cjsj") }.take(10)
        NoticeItem(
            id = id,
            title = title,
            date = date,
            category = o.str("xwlbmc").ifBlank { o.str("xwlbm") }.ifBlank { "通知" },
            pinned = o.str("sfzd") == "1" || o.str("sfzd").equals("true", ignoreCase = true),
            publisher = o.str("fbr").ifBlank { o.str("fbrmc") },
        )
    }
}

internal fun parseDbsyJson(text: String): List<NoticeItem> {
    val body = text.trim()
    if (body.isEmpty() || body == "null" || body.startsWith("<")) return emptyList()
    val root = runCatching { Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(body).jsonObject }
        .getOrNull() ?: return emptyList()
    val items = root["items"] as? JsonArray ?: return emptyList()
    return items.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val raw = o.str("xxnr").ifBlank { o.str("xxbt") }
        if (raw.isBlank()) return@mapNotNull null
        val rawId = o.str("xxbh").ifBlank { o.str("xxid") }.ifBlank { o.str("id") }
        val id = "msg:" + rawId.ifBlank { raw.hashCode().toString() }
        val parts = raw.split(":", limit = 2)
        val title = if (parts.size == 2 && parts[0].length in 1..12) {
            parts[0].trim() + " · " + parts[1].trim()
        } else {
            raw
        }
        NoticeItem(
            id = id,
            title = title,
            date = o.str("cjsj").take(10),
            category = "消息",
            content = raw,
        )
    }
}

internal fun parseNoticeDetail(id: String, html: String): NoticeItem {
    val cleaned = html
        .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
    val heading = Regex("""<h[1-4][^>]*>([\s\S]*?)</h[1-4]>""", RegexOption.IGNORE_CASE)
        .findAll(cleaned)
        .map { htmlUnescape(stripTags(it.groupValues[1])).trim() }
        .firstOrNull { it.isNotBlank() && it != "通知详情" && !it.contains("正方软件") }
        .orEmpty()
    val centerTitle = Regex(
        """<(?:div|p|h[1-4])[^>]*class=["'][^"']*text-center[^"']*["'][^>]*>([\s\S]*?)</(?:div|p|h[1-4])>""",
        RegexOption.IGNORE_CASE,
    ).findAll(cleaned)
        .map { htmlUnescape(stripTags(it.groupValues[1])).trim() }
        .firstOrNull { candidate ->
            candidate.isNotBlank() &&
                candidate != "通知详情" &&
                !candidate.contains("发布人") &&
                !candidate.contains("发布时间") &&
                !candidate.contains("正方软件")
        }
        .orEmpty()
    val title = heading.ifBlank { centerTitle }.ifBlank {
        htmlUnescape(
            Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                .find(cleaned)?.groupValues?.get(1).orEmpty(),
        ).replace("通知详情", "").trim()
    }
    val metaText = htmlToText(
        extractHtmlBlock(cleaned, "news_title1").ifBlank { extractHtmlBlock(cleaned, "news-title") },
    )
    val haystack = "$cleaned\n$metaText"
    val published = Regex("""发布(?:时间|日期)[:：]\s*(\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2}(?::\d{2})?)?)""")
        .find(haystack)?.groupValues?.get(1)
        ?: Regex("""发布时间[:：]\s*([^<\s]+)""").find(haystack)?.groupValues?.get(1).orEmpty()
    val publisher = Regex("""发布人[:：]\s*([^\s<]+)""").find(haystack)?.groupValues?.get(1).orEmpty()
    val bodyHtml = sequenceOf("news_con", "xwcon", "news-con", "newscontent", "sl_mod_content")
        .map { extractHtmlBlock(cleaned, it) }
        .firstOrNull { it.isNotBlank() }
        ?: cleaned
    val (content, parts) = parseNoticeBody(bodyHtml) { line ->
        val t = line.trim()
        t.isBlank() ||
            t == "通知详情" ||
            t == title ||
            t.startsWith("版权所有") ||
            t.contains("正方软件股份有限公司")
    }
    return NoticeItem(
        id = id,
        title = title,
        date = published.take(10),
        publisher = publisher,
        content = content,
        parts = parts,
    )
}

internal fun NoticeItem.mergeDetail(other: NoticeItem): NoticeItem = copy(
    title = title.ifBlank { other.title },
    date = date.ifBlank { other.date },
    publisher = publisher.ifBlank { other.publisher },
    content = other.content.ifBlank { content },
    parts = other.parts.ifEmpty { parts },
)

internal fun mergeNoticeLists(vararg lists: List<NoticeItem>): List<NoticeItem> {
    val byId = linkedMapOf<String, NoticeItem>()
    for (item in lists.flatMap { it }) {
        val prev = byId[item.id]
        if (prev == null) {
            byId[item.id] = item
            continue
        }
        byId[item.id] = prev.mergeDetail(item).copy(
            pinned = prev.pinned || item.pinned,
            isNew = prev.isNew || item.isNew,
            category = prev.category.ifBlank { item.category },
            title = listOf(prev.title, item.title).maxBy { it.length },
            date = listOf(prev.date, item.date).maxBy { it.length },
        )
    }
    return byId.values.sortedWith(
        compareByDescending<NoticeItem> { it.pinned }
            .thenByDescending { it.date }
            .thenByDescending { it.isNew },
    )
}

internal fun mergeNoticeCache(fresh: List<NoticeItem>, old: List<NoticeItem>): List<NoticeItem> {
    val prev = old.associateBy { it.id }
    return fresh.map { item ->
        val cached = prev[item.id] ?: return@map item
        if (item.content.isNotBlank()) item
        else item.copy(
            content = cached.content,
            parts = if (item.parts.isNotEmpty()) item.parts else cached.parts,
            publisher = item.publisher.ifBlank { cached.publisher },
        )
    }
}

private fun extractHtmlBlock(html: String, marker: String): String {
    val open = Regex(
        """<(div|td)\b[^>]*(?:class|id)\s*=\s*["'][^"']*${Regex.escape(marker)}[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(html) ?: return ""
    return extractBalancedInner(html, open.groupValues[1], open.range.last + 1)
}

private fun extractBalancedInner(html: String, tag: String, start: Int): String {
    val openPat = Regex("""<$tag\b""", RegexOption.IGNORE_CASE)
    val closePat = Regex("""</$tag\s*>""", RegexOption.IGNORE_CASE)
    var depth = 1
    var i = start
    while (i < html.length && depth > 0) {
        val nextOpen = openPat.find(html, i)
        val nextClose = closePat.find(html, i) ?: return html.substring(start)
        val openAt = nextOpen?.range?.first ?: Int.MAX_VALUE
        if (openAt < nextClose.range.first) {
            depth++
            i = nextOpen!!.range.last + 1
        } else {
            depth--
            if (depth == 0) return html.substring(start, nextClose.range.first)
            i = nextClose.range.last + 1
        }
    }
    return html.substring(start)
}

internal fun parseNoticeBody(html: String, drop: (String) -> Boolean = { false }): Pair<String, List<NoticePart>> {
    val parts = mutableListOf<NoticePart>()
    val tablePat = Regex("""<table\b[\s\S]*?</table>""", RegexOption.IGNORE_CASE)
    var last = 0
    for (match in tablePat.findAll(html)) {
        val before = noticePlain(html.substring(last, match.range.first), drop)
        if (before.isNotBlank()) parts += NoticePart.Text(before)
        parseHtmlTable(match.value)?.let { parts += it }
        last = match.range.last + 1
    }
    val after = noticePlain(html.substring(last), drop)
    if (after.isNotBlank()) parts += NoticePart.Text(after)
    val content = parts.joinToString("\n\n") { part ->
        when (part) {
            is NoticePart.Text -> part.text
            is NoticePart.Table -> formatNoticeTable(part)
        }
    }
    return content to parts
}

private fun noticePlain(html: String, drop: (String) -> Boolean): String =
    htmlToText(html).lines()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() && !drop(it) }
        .joinToString("\n")
        .trim()

private fun parseHtmlTable(tableHtml: String): NoticePart.Table? {
    val rows = Regex("""<tr\b[\s\S]*?</tr>""", RegexOption.IGNORE_CASE).findAll(tableHtml).map { row ->
        Regex("""<t[hd]\b[^>]*>([\s\S]*?)</t[hd]>""", RegexOption.IGNORE_CASE)
            .findAll(row.value)
            .map { htmlUnescape(stripTags(it.groupValues[1])).trim() }
            .toList()
    }.filter { row -> row.any { it.isNotBlank() } }.toList()
    if (rows.isEmpty()) return null
    val header = if (
        tableHtml.contains(Regex("""<th\b""", RegexOption.IGNORE_CASE)) ||
        rows.size > 1 && rows.first().all { it.isNotBlank() }
    ) {
        rows.first()
    } else {
        emptyList()
    }
    val body = if (header.isNotEmpty()) rows.drop(1) else rows
    return NoticePart.Table(headers = header, rows = body)
}

private fun formatNoticeTable(table: NoticePart.Table): String {
    val cols = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    val all = buildList {
        if (table.headers.isNotEmpty()) add(table.headers)
        addAll(table.rows)
    }
    return all.joinToString("\n") { row ->
        (0 until cols).joinToString("  ") { index -> row.getOrElse(index) { "" } }
    }
}

private fun htmlToText(html: String): String {
    var text = html
        .replace(Regex("""<(br|BR)\s*/?>"""), "\n")
        .replace(Regex("""</(p|div|tr|li|h[1-6]|table|section)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "· ")
    text = stripTags(text)
    text = htmlUnescape(text)
    return text
        .replace(Regex("[\\t ]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun stripTags(html: String): String =
    html.replace(Regex("<[^>]+>"), " ").replace(Regex("[\\t ]{2,}"), " ").trim()

private fun htmlUnescape(text: String): String =
    text.replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

