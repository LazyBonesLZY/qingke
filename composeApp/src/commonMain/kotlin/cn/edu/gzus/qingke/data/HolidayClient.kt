package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class HolidayClient(
    private val client: HttpClient = createHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchYear(year: Int): HolidayCalendar {
        val urls = listOf(
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
            "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$year.json",
        )
        var last: Throwable? = null
        for (url in urls) {
            val text = runCatching {
                client.get(url) { header(HttpHeaders.UserAgent, QINGKE_UA) }.bodyAsText()
            }.onFailure { last = it }.getOrNull() ?: continue
            val parsed = runCatching { parseYear(text, year) }.getOrNull()
            if (parsed != null && parsed.days.isNotEmpty()) return parsed
        }
        throw last ?: error("国务院节假日安排暂时读不到")
    }

    private fun parseYear(text: String, year: Int): HolidayCalendar {
        val file = json.decodeFromString(HolidayFile.serializer(), text)
        val days = file.days.mapNotNull { item ->
            val date = item.date.trim()
            if (date.length < 10) return@mapNotNull null
            HolidayDay(date = date, name = item.name.trim(), off = item.isOffDay)
        }
        if (days.isEmpty()) error("节假日文件是空的")
        return HolidayCalendar(
            days = days,
            years = listOf(file.year.takeIf { it > 0 } ?: year),
        )
    }
}

@Serializable
private data class HolidayFile(
    val year: Int = 0,
    val papers: List<String> = emptyList(),
    val days: List<HolidayFileDay> = emptyList(),
)

@Serializable
private data class HolidayFileDay(
    val name: String = "",
    val date: String = "",
    val isOffDay: Boolean = true,
)
