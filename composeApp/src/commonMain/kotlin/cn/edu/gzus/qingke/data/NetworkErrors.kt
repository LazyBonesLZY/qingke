package cn.edu.gzus.qingke.data

fun Throwable.friendlyNetworkMessage(): String {
    val text = listOfNotNull(message, cause?.message, this::class.simpleName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
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
            text.contains("Timeout", ignoreCase = false) ->
            "教务超时，过一会儿再试。"
        text.contains("Cleartext HTTP", ignoreCase = true) ||
            text.contains("CLEARTEXT", ignoreCase = true) ->
            "这所学校的登录需要明文跳转，当前网络策略拦了。"
        text.contains("Connection reset", ignoreCase = true) ||
            text.contains("Software caused connection abort", ignoreCase = true) ->
            "连接被断开，换网络再试。"
        !message.isNullOrBlank() -> message.orEmpty()
        else -> "网络请求失败"
    }
}
