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
    if (trimmed.startsWith("ST-")) return CasTickets(serviceTicket = trimmed)
    val root = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
        ?: error("门户没有返回票据")
    val data = root["data"]
    val bag = if (data is JsonObject) data else root
    val code = bag.lyuapStr("code").ifBlank { root.lyuapStr("code") }
    when (code) {
        "CODEFALSE" -> error("验证码不对，点图片换一张")
        "NOUSER", "USERDISABLED", "USERLOCK" -> error("学号或密码不正确")
        "ISMODIFYPASS", "ISPHONEOREMAILORANSWER", "TWOVERIFY",
        "LOGINFIRST", "MUSTCHANGEPASSWORD", "FRISTMODIFYPWD", "FIRSTMODIFYPWD",
        -> throw JwxtNeedFirstLogin()
        "NOAUTHORIZATION" -> error("这个账号没有教务权限")
    }
    val ticket = bag.lyuapStr("ticket")
    val tgt = bag.lyuapStr("tgt").ifBlank {
        (data as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    }
    if (ticket.startsWith("ST-")) {
        return CasTickets(serviceTicket = ticket, ticketGrantingTicket = tgt)
    }
    if (tgt.startsWith("TGT-")) {
        error("门户要二次验证。打开官方门户按提示完成后再来。")
    }
    val message = (root["meta"] as? JsonObject)?.lyuapStr("message").orEmpty()
    error(message.ifBlank { "门户登录失败" })
}

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeLyuapCaptcha(value: String): ByteArray {
    val payload = value.substringAfter("base64,", value)
    return runCatching { Base64.decode(payload) }.getOrDefault(ByteArray(0))
}

internal fun JsonObject.lyuapStr(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
