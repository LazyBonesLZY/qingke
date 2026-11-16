package cn.edu.gzus.qingke.data

import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CookiePersistenceTest {
    @Test fun maxAgeOverridesExpiresAndSecurityAttributesSurviveReload() = runBlocking {
        val previous = readStore(COOKIE_FILE)
        try {
            writeStore(COOKIE_FILE, "[]")
            val url = Url("https://school.example.edu/account")
            val storage = PersistCookieStorage()
            storage.addCookie(url, Cookie(
                name = "session", value = "test", path = "/", maxAge = 600,
                expires = GMTDate(1L), secure = true, httpOnly = true,
            ))
            val restored = PersistCookieStorage()
            val cookie = restored.get(url).single()
            assertTrue(cookie.secure)
            assertTrue(cookie.httpOnly)
            assertTrue(restored.get(Url("http://school.example.edu/account")).isEmpty())
            assertTrue(restored.get(Url("https://sub.school.example.edu/account")).isEmpty())
            restored.addCookie(url, Cookie(name = "session", value = "", path = "/", maxAge = 0))
            assertEquals(emptyList(), PersistCookieStorage().get(url))
        } finally {
            writeStore(COOKIE_FILE, previous ?: "[]")
        }
    }
}
