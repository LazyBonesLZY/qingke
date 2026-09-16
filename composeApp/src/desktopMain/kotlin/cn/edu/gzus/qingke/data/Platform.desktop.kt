package cn.edu.gzus.qingke.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import org.jetbrains.skia.Image
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import java.awt.Desktop
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

private fun storeDir(): File {
    val dir = File(System.getProperty("user.home"), ".qingke")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun createHttpClient(): HttpClient = HttpClient(CIO) {
    install(HttpCookies) { storage = PersistCookieStorage.shared }
    install(HttpTimeout) {
        requestTimeoutMillis = 25_000
        connectTimeoutMillis = 15_000
    }
    followRedirects = true
}

actual fun lyuapEncrypt(password: String, modulusHex: String, exponentHex: String): String =
    lyuapEncryptJvm(password, modulusHex, exponentHex)

actual fun md5Hex(text: String): String {
    val digest = java.security.MessageDigest.getInstance("MD5").digest(text.toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()

actual fun rsaEncrypt(password: String, modulusB64: String, exponentB64: String): String {
    val decoder = Base64.getDecoder()
    val modulus = BigInteger(1, decoder.decode(modulusB64))
    val exponent = BigInteger(1, decoder.decode(exponentB64))
    val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
}

actual fun readStore(name: String): String? {
    val file = File(storeDir(), name)
    return if (file.exists()) file.readText() else null
}

actual fun writeStore(name: String, value: String) {
    File(storeDir(), name).writeText(value)
}

actual fun clearCookieStore() {
    wipeCookies()
}

actual fun currentCookies(): List<Pair<String, String>> = cookiePairs()

actual fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

actual fun copyToClipboard(text: String) {
    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
}

actual fun openXiaoaiSchedule() {
    openUrl(XIAOAI_SCHEDULE_HOME)
}

actual fun requestLiveUpdatePermission() = Unit

actual fun notifyLiveClass(
    title: String,
    detail: String,
    progress: Float,
    etaMinutes: Int,
    startMillis: Long,
    endMillis: Long,
    chip: String,
): Boolean = false

actual fun refreshHomeWidgets() = Unit

actual fun cancelLiveClass() = Unit

actual fun resetLiveDismiss() = Unit

actual fun liveUpdateStatus(leadMinutes: Int): String = "仅 Android 支持 Live Update"

actual fun openLiveUpdateSettings() = Unit
