package cn.edu.gzus.qingke.data

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val LYUAP_EXP = "010001"
internal const val LYUAP_MOD =
    "00b5eeb166e069920e80bebd1fea4829d3d1f3216f2aabe79b6c47a3c18dcee5fd22c2e7ac519cab59198ece036dcf289ea8201e2a0b9ded307f8fb704136eaeb670286f5ad44e691005ba9ea5af04ada5367cd724b5a26fdb5120cc95b6431604bd219c6b7d83a6f8f24b43918ea988a76f93c333aa5a20991493d4eb1117e7b1"

internal data class CasTickets(
    val serviceTicket: String,
    val ticketGrantingTicket: String = "",
)

internal fun parseCasTickets(text: String, json: Json): CasTickets {
    val trimmed = text.trim().trim('"')
    val root = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
    if (root != null) {
        val data = root["data"]
        val bag = data as? JsonObject
        val code = bag?.lyuapStr("code").orEmpty().ifBlank { root.lyuapStr("code") }
        casLoginCodeError(code, bag)?.let { throw it }
        val ticket = findServiceTicket(
            listOf(
                root.lyuapStr("ticket"),
                root.lyuapStr("serviceTicket"),
                bag?.lyuapStr("ticket").orEmpty(),
                bag?.lyuapStr("serviceTicket").orEmpty(),
                (data as? JsonPrimitive)?.contentOrNull.orEmpty(),
                trimmed,
            ).joinToString(" "),
        )
        val tgt = listOf(
            root.lyuapStr("tgt"),
            root.lyuapStr("TGT"),
            bag?.lyuapStr("tgt").orEmpty(),
            bag?.lyuapStr("TGT").orEmpty(),
        ).firstOrNull { it.startsWith("TGT-") }.orEmpty()
        if (ticket != null) {
            return CasTickets(serviceTicket = ticket, ticketGrantingTicket = tgt)
        }
        if (tgt.startsWith("TGT-")) {
            return CasTickets(serviceTicket = "", ticketGrantingTicket = tgt)
        }
        error(casEnvelopeFallback(root))
    }
    findServiceTicket(trimmed)?.let { return CasTickets(serviceTicket = it) }
    if (trimmed.startsWith("TGT-")) {
        return CasTickets(serviceTicket = "", ticketGrantingTicket = trimmed)
    }
    error("门户没有返回票据")
}

private fun casLoginCodeError(code: String, bag: JsonObject?): Throwable? {
    val extra = bag?.lyuapStr("data").orEmpty().ifBlank { bag?.lyuapStr("tips").orEmpty() }
    return when (code) {
        "", "0", "200" -> null
        "CODEFALSE" -> IllegalStateException("验证码不对，点图片换一张")
        "FALSE" -> IllegalStateException("学号或密码不正确")
        "PASSERROR" -> IllegalStateException(passErrorMessage(extra))
        "NOUSER" -> IllegalStateException("账号不存在")
        "USERDISABLED" -> IllegalStateException("账号已停用，联系学校处理")
        "USERLOCK" -> IllegalStateException("账号已锁定，稍后再试或联系学校")
        "NOAUTHORIZATION", "NOREGISTER" -> IllegalStateException("这个账号没有教务权限")
        "ISMODIFYPASS", "ISPHONEOREMAILORANSWER", "LOGINFIRST",
        "MUSTCHANGEPASSWORD", "FRISTMODIFYPWD", "FIRSTMODIFYPWD",
        -> JwxtNeedFirstLogin()
        "TWOVERIFY" -> IllegalStateException("门户要二次验证。打开官方门户按提示完成后再来。")
        "ISBINDWX" -> IllegalStateException("门户要绑定微信。打开官方门户完成后回来。")
        "NETWORKCOMMITMENT" -> IllegalStateException("门户要先确认网络承诺。打开官方门户完成后回来。")
        "PEOPLEMOREACCOUNT", "USERNOTONLY" -> IllegalStateException("这个证件号对应多个账号，打开官方门户选一个后再来。")
        else -> IllegalStateException("门户登录失败")
    }
}

private fun passErrorMessage(extra: String): String {
    val parts = extra.split(",").map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size >= 2) {
        val lockAt = parts[0]
        val count = parts[1]
        return if (lockAt == count) {
            "密码已连续错误${count}次，账号已锁定"
        } else {
            "密码已连续错误${count}次，再错到${lockAt}次会锁定"
        }
    }
    return "学号或密码不正确"
}

private fun casEnvelopeFallback(root: JsonObject): String {
    val message = (root["meta"] as? JsonObject)?.lyuapStr("message").orEmpty()
        .ifBlank { root.lyuapStr("message") }
        .ifBlank { root.lyuapStr("msg") }
    return if (isCasEnvelopeMessage(message)) "门户没有返回票据" else message
}

internal fun isCasEnvelopeMessage(message: String): Boolean {
    val t = message.trim().lowercase()
    return t.isEmpty() || t == "ok" || t == "success" || t == "true" || t == "200"
}

private fun findServiceTicket(text: String): String? =
    Regex("""ST-[A-Za-z0-9._-]+""").find(text)?.value

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeLyuapCaptcha(value: String): ByteArray {
    val payload = value.substringAfter("base64,", value)
    return runCatching { Base64.decode(payload) }.getOrDefault(ByteArray(0))
}

internal fun JsonObject.lyuapStr(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
