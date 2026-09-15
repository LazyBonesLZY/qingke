package cn.edu.gzus.qingke.data

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient

expect fun rsaEncrypt(password: String, modulusB64: String, exponentB64: String): String

expect fun lyuapEncrypt(password: String, modulusHex: String, exponentHex: String): String

expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap

expect fun readStore(name: String): String?

expect fun writeStore(name: String, value: String)

expect fun notifyLiveClass(
    title: String,
    detail: String,
    progress: Float,
    etaMinutes: Int,
    startMillis: Long,
    endMillis: Long,
    chip: String,
): Boolean

expect fun refreshHomeWidgets()

expect fun cancelLiveClass()

expect fun resetLiveDismiss()

expect fun liveUpdateStatus(leadMinutes: Int = DefaultRemindLeadMinutes): String

expect fun openLiveUpdateSettings()

expect fun openUrl(url: String)

expect fun copyToClipboard(text: String)

expect fun openXiaoaiSchedule()

expect fun clearCookieStore()

expect fun currentCookies(): List<Pair<String, String>>

expect fun requestLiveUpdatePermission()
