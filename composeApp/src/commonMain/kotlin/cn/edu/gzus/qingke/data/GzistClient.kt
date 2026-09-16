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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private const val UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

class GzistClient(
    private val client: HttpClient = createHttpClient(),
    private val casOrigin: String = "https://ids.gzist.edu.cn/lyuapServer",
    private val casService: String = GZIST_SSO_SERVICE,
    jwxtOrigin: String = GZIST_JWXT_ORIGIN,
    override val supportsFreeRooms: Boolean = false,
    private val jwxt: JwxtClient = JwxtClient(
        origin = jwxtOrigin,
        client = client,
        supportsFreeRooms = supportsFreeRooms,
    ),
) : SchoolPortal by jwxt {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val CAS = casOrigin.trim().trimEnd('/')
    private val service = casService.trim().ifBlank { GZIST_SSO_SERVICE }
    private val jwOrigin = jwxtOrigin.trim().trimEnd('/')

    override suspend fun fetchCaptcha(): LoginCaptcha? {
        client.get("$CAS/login") {
            header(HttpHeaders.UserAgent, UA)
            parameter("service", service)
        }
        val text = client.get("$CAS/kaptcha") {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$CAS/login?service=$service")
        }.bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        val uid = root.lyuapStr("uid")
        val content = root.lyuapStr("content")
        val bytes = decodeLyuapCaptcha(content)
        if (uid.isBlank() || bytes.isEmpty()) return null
        return LoginCaptcha(id = uid, bytes = bytes, hint = "算术题得数")
    }

    override suspend fun login(
        studentId: String,
        password: String,
        captcha: String,
        captchaId: String,
    ): Result<Unit> = runCatching {
        if (captcha.isBlank() || captchaId.isBlank()) error("这个教务要从门户进，先填验证码")
        val encrypted = lyuapEncrypt(password, LYUAP_MOD, LYUAP_EXP)
        val raw = client.submitForm(
            url = "$CAS/v1/tickets",
            formParameters = Parameters.build {
                append("username", studentId)
                append("password", encrypted)
                append("service", service)
                append("loginType", "")
                append("id", captchaId)
                append("code", captcha.trim())
            },
        ) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Referrer, "$CAS/login?service=$service")
            header(HttpHeaders.Origin, CAS.substringBefore("/lyuapServer").ifBlank { CAS })
        }.bodyAsText()
        val ticket = parseCasTickets(raw, json).serviceTicket
        val jump = client.get(service) {
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
            val home = client.get("$jwOrigin/jwglxt/xtgl/index_initMenu.html") {
                header(HttpHeaders.UserAgent, UA)
                parameter("jsdm", "xs")
            }
            val homeBody = home.bodyAsText()
            val homeUrl = home.request.url.toString()
            if (isPasswordChangePage(homeBody, homeUrl)) throw JwxtNeedFirstLogin()
            if (isLoginForm(homeBody)) error("门户登录了，教务会话没建起来")
        }
    }

    override suspend fun logout() {
        runCatching { jwxt.logout() }
        runCatching {
            client.get("$CAS/logout") {
                header(HttpHeaders.UserAgent, UA)
            }
        }
    }

}
