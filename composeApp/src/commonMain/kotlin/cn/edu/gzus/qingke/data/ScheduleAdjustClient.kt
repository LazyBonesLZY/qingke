package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

const val SCHEDULE_ADJUST_URL = "https://qingke.lazzyy.cn/schedule-adjust.json"

class ScheduleAdjustClient(
    private val client: HttpClient = createBareHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(): CloudScheduleAdjust {
        val text = client.get(SCHEDULE_ADJUST_URL) {
            header(HttpHeaders.UserAgent, "Qingke/$APP_VERSION_NAME")
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.CacheControl, "no-cache")
        }.bodyAsText()
        val parsed = json.decodeFromString(CloudScheduleAdjust.serializer(), text)
        if (parsed.offs.isEmpty() && parsed.shifts.isEmpty()) error("调休配置是空的")
        return parsed
    }
}
