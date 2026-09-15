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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
private const val LYUAP_EXP = "010001"
private const val LYUAP_MOD =
    "00b5eeb166e069920e80bebd1fea4829d3d1f3216f2aabe79b6c47a3c18dcee5fd22c2e7ac519cab59198ece036dcf289ea8201e2a0b9ded307f8fb704136eaeb670286f5ad44e691005ba9ea5af04ada5367cd724b5a26fdb5120cc95b6431604bd219c6b7d83a6f8f24b43918ea988a76f93c333aa5a20991493d4eb1117e7b1"

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
        val uid = root.str("uid")
        val content = root.str("content")
        val bytes = decodeDataUri(content)
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
        val ticket = parseCasTicket(raw)
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

    private fun parseCasTicket(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("ST-")) return trimmed.trim('"')
        val root = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            ?: error("门户没有返回票据")
        val data = root["data"]
        val bag = if (data is JsonObject) data else root
        val code = bag.str("code").ifBlank { root.str("code") }
        when (code) {
            "CODEFALSE" -> error("验证码不对，点图片换一张")
            "NOUSER", "USERDISABLED", "USERLOCK" -> error("学号或密码不正确")
            "ISMODIFYPASS", "ISPHONEOREMAILORANSWER", "TWOVERIFY" -> throw JwxtNeedFirstLogin()
            "NOAUTHORIZATION" -> error("这个账号没有教务权限")
        }
        val ticket = bag.str("ticket")
        if (ticket.startsWith("ST-")) return ticket
        val tgt = bag.str("tgt").ifBlank { if (data is JsonPrimitive) data.content else "" }
        if (tgt.startsWith("TGT-")) error("门户要二次验证。打开官方门户按提示完成后再来。")
        val message = root["meta"]?.jsonObject?.str("message").orEmpty()
        error(message.ifBlank { "门户登录失败" })
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeDataUri(value: String): ByteArray {
    val payload = value.substringAfter("base64,", value)
    return runCatching { Base64.decode(payload) }.getOrDefault(ByteArray(0))
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
