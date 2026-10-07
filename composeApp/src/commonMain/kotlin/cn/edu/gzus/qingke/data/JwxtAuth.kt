package cn.edu.gzus.qingke.data

/** 所有教务/门户请求统一用这个 UA。一卡通那边另有微信 UA。 */
const val QINGKE_UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

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
const val GZUS_ECARD_WX_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.38 NetType/WIFI Language/zh_CN"
const val GZUS_EHALL_LEAVE = "$GZUS_EHALL_ORIGIN/#/affairs"
const val GZUS_EHALL_MESSAGE = "$GZUS_EHALL_ORIGIN/#/message"

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
fun gzusEcardCasService(): String {
    val redirect = kotlin.io.encoding.Base64.encode("https://ecarduser.gzus.edu.cn/".encodeToByteArray())
    return "$GZUS_SSO_ORIGIN//login?redirectUrl=BASE64$redirect&t=12&"
}
const val ZHKU_LOGIN_URL = "https://edu-admin.zhku.edu.cn/Logon.do?method=logon"
const val ZHKU_CHANGE_PASSWORD_URL = "https://edu-admin.zhku.edu.cn/jsxsd/grsz/grsz_xgmm"
const val GZIST_CAS_LOGIN_URL =
    "https://ids.gzist.edu.cn/lyuapServer/login?service=https://portal.gzist.edu.cn/shiro-cas"
const val GZIST_CHANGE_PASSWORD_URL = "https://ids.gzist.edu.cn/lyuapServer/loginFirst"
const val GZIST_JWXT_ORIGIN = "https://jw.gzist.edu.cn"
const val GZIST_SSO_SERVICE = "http://jw.gzist.edu.cn/sso/lyiotlogin"

// 广东生态工程职业学院：综合系统（AIC，ASP.NET）出票，教务本身是强智 jsxsd。
// 教务不给直登——jsxsd 登录页要验证码，而且账号只认 SSO 过来的票。
const val GDTSY_AIC_ORIGIN = "https://aic.gdsty.edu.cn"
const val GDTSY_JWXT_ORIGIN = "https://jw.gdsty.edu.cn"
const val GDTSY_AIC_LOGIN_URL = "$GDTSY_AIC_ORIGIN/xsgl/xs/login/login.aspx"
const val GDTSY_AIC_SLOGIN_URL =
    "$GDTSY_AIC_ORIGIN/xsgl/xs/index/ilogin/slogin.aspx?AppId=410bcb4493ca4c05bfacf70585f04c47"
const val GDTSY_CHANGE_PASSWORD_URL = "$GDTSY_AIC_ORIGIN/wx_xgmm/views/ewm.html"
/** 课表页下拉里唯一的节次模板，取不到选中值时的兜底。 */
const val GDTSY_KBJCMSID = "97AEEC590D96F04DE053110AA8C0F71A"
/** 登录页 JsEncryptHelper.js 里的 1024 位公钥，拆成模数和指数喂 rsaEncrypt。 */
const val GDTSY_RSA_MODULUS_B64 =
    "gtIa0SI2956A1jbWw6QDW47eSMLtjRXg8FNehHPO06KnqCJtiiPnDrPNST/Wkw5P45AJVPe5jfMGZBDA6+brQGbTFYbexi1991sfV46sVLA37BpKjS/HVyZJVg7vWYfNFItlIPt+NHeXWbSrXfp79PUGaho3Z0qfT03xQwWNd90="
const val GDTSY_RSA_EXPONENT_B64 = "AQAB"
/** 登录按钮的 value 里是全角空格，服务端按这个字符串认提交。 */
const val GDTSY_LOGIN_BUTTON = " 登\u3000录"

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
