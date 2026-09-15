package cn.edu.gzus.qingke.data

const val JWXT_LOGIN_URL = "https://jwxt.gzus.edu.cn/jwglxt/xtgl/login_slogin.html"
const val JWXT_CHANGE_PASSWORD_URL = "https://jwxt.gzus.edu.cn/jwglxt/xtgl/mmgl_xgMm.html"
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
    "教务要求先完成首登认证。打开官方网页登录，按提示改完密码，再回青课登录。",
)

internal fun isPasswordChangeUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("mmgl_xgmm") ||
        u.contains("init_cxxgmm") ||
        u.contains("init_updatepassword") ||
        u.contains("xgmm.html")
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
