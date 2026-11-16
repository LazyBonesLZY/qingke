package cn.edu.gzus.qingke.data

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals

class CookieSelectionTest {
    @Test fun expiresCookiesAndRespectsSecureFlag() {
        val cookies = listOf(
            CookieRow("session", "old", "school.edu.cn", "/", expiresAt = 999),
            CookieRow("secure", "new", "school.edu.cn", "/", expiresAt = 5000, secure = true),
        )
        assertEquals(emptyList(), selectCookies(cookies, Url("http://school.edu.cn/a"), 1000))
        assertEquals(listOf("secure"), selectCookies(cookies, Url("https://school.edu.cn/a"), 1000).map { it.name })
        assertEquals(emptyList(), selectCookies(cookies, Url("https://school.edu.cn/a"), 5000))
    }

    @Test fun hostOnlyCookieStaysOnExactHost() {
        val cookie = CookieRow("sid", "value", "school.edu.cn", "/", hostOnly = true)
        assertEquals(1, selectCookies(listOf(cookie), Url("https://school.edu.cn/"), 1000).size)
        assertEquals(0, selectCookies(listOf(cookie), Url("https://sub.school.edu.cn/"), 1000).size)
    }
}
