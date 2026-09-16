package cn.edu.gzus.qingke.data

const val JWXT_ORIGIN = "https://jwxt.gzus.edu.cn"
const val JWXT_LOGIN_URL = "$JWXT_ORIGIN/jwglxt/xtgl/login_slogin.html"
const val JWXT_CHANGE_PASSWORD_URL = "$JWXT_ORIGIN/jwglxt/xtgl/mmgl_xgMm.html"
const val GZUS_CAS_ORIGIN = "https://cas.gzus.edu.cn/lyuapServer"
const val GZUS_CAS_LOGIN_URL = "$GZUS_CAS_ORIGIN/login"
const val GZUS_CAS_PASSWORD_URL = "https://cas.gzus.edu.cn/aqzx/#/password/passwordModify"
const val GZUS_JWXT_SSO_SERVICE = "$JWXT_ORIGIN/sso/lyiotlogin"
const val GZUS_EHALL_ORIGIN = "https://ehall.gzus.edu.cn"
const val GZUS_EHALL_CAS_SERVICE = "$GZUS_EHALL_ORIGIN/shiro-cas"
const val GZUS_EHALL_HOME = "$GZUS_EHALL_ORIGIN/#/index"
const val GZUS_SSO_ORIGIN = "https://sso.gzus.edu.cn"
const val GZUS_ECARD_ORIGIN = "https://ecarduser.gzus.edu.cn"
const val GZUS_EHALL_LEAVE = "$GZUS_EHALL_ORIGIN/#/affairs"
const val GZUS_EHALL_MESSAGE = "$GZUS_EHALL_ORIGIN/#/message"

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
fun gzusEcardCasService(): String {
    val redirect = kotlin.io.encoding.Base64.encode("https://ecarduser.gzus.edu.cn/".encodeToByteArray())
    return "$GZUS_SSO_ORIGIN/login?redirectUrl=BASE64$redirect&t=12"
}
const val ZHKU_LOGIN_URL = "https://edu-admin.zhku.edu.cn/Logon.do?method=logon"
const val ZHKU_CHANGE_PASSWORD_URL = "https://edu-admin.zhku.edu.cn/jsxsd/grsz/grsz_xgmm"
const val GZIST_CAS_LOGIN_URL =
    "https://ids.gzist.edu.cn/lyuapServer/login?service=https://portal.gzist.edu.cn/shiro-cas"
const val GZIST_CHANGE_PASSWORD_URL = "https://ids.gzist.edu.cn/lyuapServer/loginFirst"
const val GZIST_JWXT_ORIGIN = "https://jw.gzist.edu.cn"
const val GZIST_SSO_SERVICE = "http://jw.gzist.edu.cn/sso/lyiotlogin"

data class LoginCaptcha(
    val id: String,
    val bytes: ByteArray,
    val hint: String,
)

internal fun isCaptchaTip(text: String): Boolean {
    val t = text.replace(" ", "")
    return t.contains("验证码") || t.contains("CODEFALSE")
}

class JwxtNeedFirstLogin : RuntimeException(
    "门户或教务要求先完成首登改密。打开官方网页按提示改完，再回青课登录。",
)

internal fun isPasswordChangeUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("mmgl_xgmm") ||
        u.contains("init_cxxgmm") ||
        u.contains("init_updatepassword") ||
        u.contains("xgmm.html") ||
        u.contains("loginfirst") ||
        u.contains("/aqzx/")
}

internal fun isWrongPasswordTip(tip: String): Boolean {
    val t = tip.replace(" ", "")
    return t.contains("用户名或密码") ||
        t.contains("学号或密码") ||
        t.contains("账号或密码") ||
        t.contains("密码不正确") ||
        t.contains("密码错误") ||
        t.contains("用户不存在")
}

internal fun isCasFirstLogin(text: String, url: String = ""): Boolean {
    val u = url.lowercase()
    if (u.contains("loginfirst") || u.contains("/aqzx/#/password") || u.contains("/aqzx/")) return true
    val t = text
    if (t.contains("\"ISMODIFYPASS\"") || t.contains("\"LOGINFIRST\"") || t.contains("\"MUSTCHANGEPASSWORD\"")) return true
    if (t.contains("loginFirst") && (t.contains("请修改") || t.contains("初始密码") || t.contains("强制"))) return true
    return isFirstLoginSignal(text)
}

internal fun isFirstLoginSignal(text: String): Boolean {
    if (text.isBlank()) return false
    return listOf(
        "初始密码",
        "请修改初始",
        "请先修改密码",
        "请您修改密码",
        "必须修改密码",
        "须修改密码",
        "密码已过期",
        "密码过期",
        "首次登录",
        "第一次登录",
        "强制修改密码",
        "密码不符合",
        "密码强度不够",
        "密码过于简单",
        "密码太简单",
    ).any { text.contains(it) }
}

internal fun isPasswordChangePage(body: String, url: String): Boolean {
    if (isPasswordChangeUrl(url)) return true
    if (isFirstLoginSignal(body)) return true
    val changeForm = (body.contains("name=\"ymm\"") || body.contains("id=\"ymm\"") || body.contains("id='ymm'")) &&
        (body.contains("新密码") || body.contains("name=\"xmm\"") || body.contains("id=\"xmm\""))
    return changeForm && !body.contains("index_initMenu")
}

internal fun loginFormTip(body: String): String =
    Regex("""id="tips"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        .find(body)?.groupValues?.get(1)
        ?.replace(Regex("<[^>]+>"), "")
        ?.trim()
        .orEmpty()

internal fun isLoginForm(body: String): Boolean =
    body.contains("用户登录") && (body.contains("name=\"yhm\"") || body.contains("name='yhm'"))
