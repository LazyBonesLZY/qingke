package cn.edu.gzus.qingke.data

const val SESSION_LOST_HINT = "登录过期"
const val HALL_SESSION_HINT = "办事大厅这次没登录上"

fun isHallSessionHint(message: String): Boolean {
    if (message.isBlank()) return false
    return message.contains("办事大厅这次") ||
        message.contains("大厅会话") ||
        message.contains(HALL_SESSION_HINT)
}

fun isSessionLost(message: String): Boolean {
    if (message.isBlank()) return false
    if (isTransientNetworkText(message)) return false
    if (isHallSessionHint(message)) return false
    if (message.contains("要用统一身份认证")) return false
    if (message.contains("登录后才能")) return false
    if (message.contains("还没有登录")) return false
    return listOf(
        "登录过期",
        "登录已过期",
        "登录失效",
        "会话失效",
    ).any { message.contains(it) }
}
