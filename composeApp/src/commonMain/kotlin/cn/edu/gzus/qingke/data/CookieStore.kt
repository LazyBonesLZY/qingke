package cn.edu.gzus.qingke.data

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val COOKIE_FILE = "qingke-cookies.json"

@Serializable
internal data class CookieRow(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
)

class PersistCookieStorage : CookiesStorage {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    // 写时复制：读（snapshot/records/wipe/get 快照）直接读引用，
    // 写（addCookie/retainLatest/load）在锁里整体替换引用。
    // 这样非 suspend 的 snapshot/records/wipe 不会和并发写入撞出 CME，
    // 最多读到稍旧的一份，而持久化文件保证重启后一致。
    @Volatile
    private var cookies: List<CookieRow> = emptyList()

    init {
        load()
    }

    private fun load() {
        val raw = readStore(COOKIE_FILE) ?: return
        runCatching { cookies = json.decodeFromString<List<CookieRow>>(raw) }
    }

    private fun persist(rows: List<CookieRow>) {
        writeStore(COOKIE_FILE, json.encodeToString(rows))
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        cookies.filter {
            matches(requestUrl.host, it.domain) && pathMatches(requestUrl.encodedPath, it.path)
        }.sortedWith(
            // 同名 cookie 只留一份：path 越长、domain 越具体越优先，
            // 服务端一般取第一份，旧的宽泛值就不会盖掉新的精确值。
            compareByDescending<CookieRow> { it.path.length }
                .thenByDescending { it.domain.trimStart('.').length },
        ).distinctBy { it.name }
            .map {
                Cookie(name = it.name, value = it.value, domain = it.domain, path = it.path)
            }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        mutex.withLock {
            val domain = cookie.domain ?: requestUrl.host
            val path = cookie.path ?: "/"
            val next = cookies.filterNot {
                it.name == cookie.name && it.domain == domain && it.path == path
            } + CookieRow(cookie.name, cookie.value, domain, path)
            cookies = next
            persist(next)
        }
    }

    override fun close() = Unit

    fun snapshot(): List<Pair<String, String>> = cookies.map { it.name to it.value }

    fun records(): List<CookieRecord> = cookies.map { CookieRecord(it.name, it.value, it.domain, it.path) }

    fun wipe() {
        // 自旋拿锁：addCookie 的 persist 在做文件 IO，窗口虽小但撞上时
        // 无锁清空会被随后落盘的旧引用复活。登出路径低频，自旋可接受。
        while (!mutex.tryLock()) {
            // busy wait
        }
        try {
            cookies = emptyList()
            persist(emptyList())
        } finally {
            mutex.unlock()
        }
    }

    suspend fun retainLatest(host: String, name: String) = mutex.withLock {
        val rows = cookies
        val keepAt = rows.indexOfLast { it.name == name && matches(host, it.domain) }
        if (keepAt < 0) return@withLock
        val next = rows.filterIndexed { index, row ->
            row.name != name || !matches(host, row.domain) || index == keepAt
        }
        if (next.size == rows.size) return@withLock
        cookies = next
        persist(next)
    }

    companion object {
        val shared = PersistCookieStorage()
    }
}

private fun matches(host: String, domain: String): Boolean {
    if (domain.isBlank()) return true
    val d = domain.trimStart('.')
    return host == d || host.endsWith(".$d")
}

private fun pathMatches(requestPath: String, cookiePath: String): Boolean {
    val path = requestPath.ifBlank { "/" }
    val prefix = cookiePath.ifBlank { "/" }
    if (prefix == "/") return true
    return path == prefix || path.startsWith(if (prefix.endsWith("/")) prefix else "$prefix/")
}

fun wipeCookies() {
    PersistCookieStorage.shared.wipe()
}

internal suspend fun retainLatestCookie(host: String, name: String) {
    PersistCookieStorage.shared.retainLatest(host, name)
}

fun cookiePairs(): List<Pair<String, String>> = PersistCookieStorage.shared.snapshot()

data class CookieRecord(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
)

fun cookieRecords(): List<CookieRecord> = PersistCookieStorage.shared.records()
