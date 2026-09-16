package cn.edu.gzus.qingke.data

const val NETWORK_HINT = "网络连不上，过一会儿再试。"

fun Throwable.friendlyNetworkMessage(): String {
    val text = listOfNotNull(message, cause?.message, this::class.simpleName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (isSessionLost(message.orEmpty()) && !isTransientNetwork(this)) return SESSION_LOST_HINT
    if (!isTransientNetwork(this) && !isTransientNetworkText(message.orEmpty())) {
        val raw = message?.trim().orEmpty()
        if (isCasEnvelopeMessage(raw)) return "门户没有返回票据"
        return raw.takeIf { it.isNotBlank() } ?: "请求失败"
    }
    return when {
        text.contains("checkServerTrusted", ignoreCase = true) ||
            text.contains("Domain specific configurations", ignoreCase = true) ->
            "HTTPS 证书校验失败。请更新到最新版后再试。"
        text.contains("Trust anchor", ignoreCase = true) ||
            text.contains("CertPath", ignoreCase = true) ||
            text.contains("SSLHandshake", ignoreCase = true) ||
            text.contains("SSLPeerUnverified", ignoreCase = true) ||
            text.contains("CertificateException", ignoreCase = true) ->
            "教务 HTTPS 连不上，换网络或稍后再试。"
        text.contains("Unable to resolve host", ignoreCase = true) ||
            text.contains("UnknownHost", ignoreCase = true) ||
            text.contains("No address associated", ignoreCase = true) ->
            "解析不到教务地址，检查一下网络。"
        text.contains("timeout", ignoreCase = true) ||
            text.contains("timed out", ignoreCase = true) ||
            text.contains("Timeout") ->
            "教务超时，过一会儿再试。"
        text.contains("Cleartext HTTP", ignoreCase = true) ||
            text.contains("CLEARTEXT", ignoreCase = true) ->
            "这所学校的登录需要明文跳转，当前网络策略拦了。"
        text.contains("Connection reset", ignoreCase = true) ||
            text.contains("Software caused connection abort", ignoreCase = true) ->
            "连接被断开，换网络再试。"
        else -> NETWORK_HINT
    }
}

fun isTransientNetwork(error: Throwable): Boolean {
    val text = listOfNotNull(error.message, error.cause?.message, error::class.simpleName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    return isTransientNetworkText(text)
}

fun isTransientNetworkText(text: String): Boolean {
    if (text.isBlank()) return false
    return NETWORK_MARKERS.any { text.contains(it, ignoreCase = true) }
}

private val NETWORK_MARKERS = listOf(
    "timeout",
    "timed out",
    "Unable to resolve host",
    "UnknownHost",
    "No address associated",
    "SSLHandshake",
    "SSLPeerUnverified",
    "CertificateException",
    "Trust anchor",
    "CertPath",
    "checkServerTrusted",
    "Cleartext",
    "CLEARTEXT",
    "Connection reset",
    "Connection refused",
    "Software caused connection abort",
    "Failed to connect",
    "Unable to connect",
    "Network is unreachable",
    "No route to host",
    "SocketTimeout",
    "ConnectTimeout",
    "HttpRequestTimeout",
    "ConnectException",
    "UnknownHostException",
    "SocketException",
    "UnresolvedAddress",
    "ClosedChannel",
    "Broken pipe",
    "Connection closed",
    "connection abort",
    "unexpected end",
    "EOFException",
    "ENETUNREACH",
    "EHOSTUNREACH",
    "ECONNRESET",
    "ECONNREFUSED",
    "ETIMEDOUT",
    "解析不到",
    "检查一下网络",
    "连接被断开",
    "网络请求失败",
    "网络连不上",
)
