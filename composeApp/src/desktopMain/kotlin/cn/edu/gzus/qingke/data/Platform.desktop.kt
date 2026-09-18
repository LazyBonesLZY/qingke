package cn.edu.gzus.qingke.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import org.jetbrains.skia.Image
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO

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

actual fun createBareHttpClient(): HttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 40_000
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

internal actual fun decodePngGray(bytes: ByteArray): GrayPng? {
    val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
    val width = image.width
    val height = image.height
    val gray = IntArray(width * height)
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = image.getRGB(x, y)
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            gray[i++] = (r * 30 + g * 59 + b * 11) / 100
        }
    }
    return GrayPng(width, height, gray)
}

private const val RELOGIN_FILE = "qingke-relogin.bin"
private const val RELOGIN_KEY = "qingke-relogin.key"

private fun desktopReloginKey(): SecretKeySpec {
    val file = File(storeDir(), RELOGIN_KEY)
    val bytes = if (file.exists() && file.length() == 16L) {
        file.readBytes()
    } else {
        ByteArray(16).also { SecureRandom().nextBytes(it); file.writeBytes(it) }
    }
    return SecretKeySpec(bytes, "AES")
}

internal actual fun saveReloginSecret(studentId: String, password: String) {
    val id = studentId.trim()
    if (id.isBlank() || password.isEmpty()) return
    runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, desktopReloginKey())
        File(storeDir(), RELOGIN_FILE).writeBytes(cipher.iv + cipher.doFinal("$id\n$password".toByteArray(Charsets.UTF_8)))
    }
}

internal actual fun loadReloginSecret(): Pair<String, String>? {
    val file = File(storeDir(), RELOGIN_FILE)
    if (!file.exists()) return null
    val raw = runCatching { file.readBytes() }.getOrNull() ?: return null
    if (raw.size <= 12) return null
    return runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, desktopReloginKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        val text = cipher.doFinal(raw.copyOfRange(12, raw.size)).toString(Charsets.UTF_8)
        val split = text.indexOf('\n')
        if (split <= 0) null else text.substring(0, split) to text.substring(split + 1)
    }.getOrNull()
}

internal actual fun clearReloginSecret() {
    File(storeDir(), RELOGIN_FILE).delete()
    File(storeDir(), RELOGIN_KEY).delete()
}

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
