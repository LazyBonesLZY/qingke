package cn.edu.gzus.qingke.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParsingEdgeTest {
    @Test
    fun blankKingosoftTermStaysBlank() {
        // 周历页没选中学期时不能凭空回「第 1 学期」，否则 mergeCalendar 会把下学期改回上学期。
        assertEquals("" to "", parseZhkuTerm(""))
        assertEquals("" to "", parseZhkuTerm("   "))
        assertEquals("2025" to "2", parseZhkuTerm("2025-2026-2"))
        assertEquals("2025" to "1", parseZhkuTerm("2025"))
    }

    @Test
    fun kingosoftParityStaysWithItsSegment() {
        val mixed = parseZhkuWeeksPeriod("1-7(单),8-16(双)[01-02节]")
        assertEquals("1-7单,8-16双", mixed.weeks)
        assertEquals("1-2", mixed.period)
        assertEquals(setOf(1, 3, 5, 7, 8, 10, 12, 14, 16), parseWeekSet(mixed.weeks))

        val odd = parseZhkuWeeksPeriod("1-15(单周)[03-04节]")
        assertEquals("1-15单", odd.weeks)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), parseWeekSet(odd.weeks))

        assertEquals("1-16", parseZhkuWeeksPeriod("1-16(周)[05-06节]").weeks)
        assertEquals(setOf(3, 5, 7), parseWeekSet(parseZhkuWeeksPeriod("3,5,7(单周)").weeks))
    }

    @Test
    fun htmlInsteadOfJsonGetsAFriendlyMessage() {
        val decode = assertNotNull(
            runCatching {
                Json.parseToJsonElement("<html><body>502 Bad Gateway</body></html>").jsonObject
            }.exceptionOrNull(),
        )
        assertEquals(UNREADABLE_RESPONSE_HINT, decode.friendlyNetworkMessage())

        val cast = assertNotNull(runCatching { Json.parseToJsonElement("\"ok\"").jsonObject }.exceptionOrNull())
        assertEquals(UNREADABLE_RESPONSE_HINT, cast.friendlyNetworkMessage())

        assertEquals(
            UNREADABLE_RESPONSE_HINT,
            IllegalStateException("<!DOCTYPE html><html><title>维护中</title></html>").friendlyNetworkMessage(),
        )
        // 自己写的中文提示照旧原样给出。
        assertEquals("学号或密码不正确", IllegalStateException("学号或密码不正确").friendlyNetworkMessage())
        assertEquals(SESSION_LOST_HINT, IllegalStateException("登录已过期，请重新登录").friendlyNetworkMessage())
        // 没带说明的异常不该被说成「门户没有返回票据」。
        assertEquals("请求失败", IllegalStateException().friendlyNetworkMessage())
        assertEquals("门户没有返回票据", IllegalStateException("success").friendlyNetworkMessage())
    }

    @Test
    fun githubReleaseWithNullBodyStillParses() {
        val update = parseGithubRelease(
            """{"tag_name":"v99.0.0","name":null,"body":null,"html_url":"https://example.com/r",""" +
                """"assets":[{"name":"qingke.apk","browser_download_url":"https://example.com/q.apk"}]}""",
        )
        assertEquals("99.0.0", update.versionName)
        assertEquals("", update.notes)
        assertEquals("https://example.com/q.apk", update.apkUrl)
        assertTrue(update.newer)
    }

    @Test
    fun githubRateLimitIsNotReportedAsMissingVersion() {
        val failed = assertFailsWith<IllegalStateException> {
            parseGithubRelease("""{"message":"API rate limit exceeded for 1.2.3.4.","documentation_url":"https://docs.github.com"}""")
        }
        assertEquals("GitHub 访问太频繁，过一会儿再试", failed.message)
    }
}
