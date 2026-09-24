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
    private val cookies = mutableListOf<CookieRow>()

    init {
        load()
    }

    private fun load() {
        cookies.clear()
        readStore(COOKIE_FILE)?.let { raw ->
            runCatching { cookies += json.decodeFromString<List<CookieRow>>(raw) }
        }
    }

    private fun persist() {
        writeStore(COOKIE_FILE, json.encodeToString(cookies))
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        cookies.filter {
            matches(requestUrl.host, it.domain) && pathMatches(requestUrl.encodedPath, it.path)
        }.map {
            Cookie(name = it.name, value = it.value, domain = it.domain, path = it.path)
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        mutex.withLock {
            val domain = cookie.domain ?: requestUrl.host
            val path = cookie.path ?: "/"
            cookies.removeAll { it.name == cookie.name && it.domain == domain && it.path == path }
            cookies += CookieRow(cookie.name, cookie.value, domain, path)
            persist()
        }
    }

    override fun close() = Unit

    fun snapshot(): List<Pair<String, String>> = cookies.map { it.name to it.value }

    fun records(): List<CookieRecord> = cookies.map { CookieRecord(it.name, it.value, it.domain, it.path) }

    fun wipe() {
        cookies.clear()
        persist()
    }

    suspend fun retainLatest(host: String, name: String) = mutex.withLock {
        val keepAt = cookies.indexOfLast { it.name == name && matches(host, it.domain) }
        if (keepAt < 0) return@withLock
        val next = cookies.filterIndexed { index, row ->
            row.name != name || !matches(host, row.domain) || index == keepAt
        }
        if (next.size == cookies.size) return@withLock
        cookies.clear()
        cookies += next
        persist()
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
