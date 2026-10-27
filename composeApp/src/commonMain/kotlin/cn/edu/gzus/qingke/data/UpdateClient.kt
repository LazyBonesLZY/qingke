package cn.edu.gzus.qingke.data

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class UpdateClient(
    private val client: io.ktor.client.HttpClient = createHttpClient(),
) {
    suspend fun checkGithub(): AppUpdate {
        val text = client.get("https://api.github.com/repos/$GITHUB_REPO/releases/latest") {
            header(HttpHeaders.UserAgent, "Qingke/$APP_VERSION_NAME")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }.bodyAsText()
        return parseGithubRelease(text)
    }
}

// coerceInputValues：Release 没写说明时 GitHub 回 "body": null（name 也可能是 null），
// 不开的话非空字段遇到 null 直接解析失败，检查更新就整个报错。
private val GithubJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

internal fun parseGithubRelease(text: String): AppUpdate {
    if (text.contains("Not Found") && text.contains("\"message\"")) {
        error("GitHub 还没有 Release")
    }
    // 未登录的 API 每小时 60 次，超了回 {"message":"API rate limit exceeded …"}，
    // 按默认值解析会变成「没有版本号」，误导人。
    if (text.contains("rate limit", ignoreCase = true) && text.contains("\"message\"") && !text.contains("\"tag_name\"")) {
        error("GitHub 访问太频繁，过一会儿再试")
    }
    val release = GithubJson.decodeFromString(GithubRelease.serializer(), text)
    val name = release.tag_name.ifBlank { release.name }.trim()
    if (name.isBlank()) error("GitHub Release 没有版本号")
    val code = Regex("""(?:versionCode|version_code)[:\s=]+(\d+)""", RegexOption.IGNORE_CASE)
        .find(release.body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ?.browser_download_url.orEmpty()
    return AppUpdate(
        versionName = name.trimStart('v', 'V'),
        notes = release.body.trim(),
        pageUrl = release.html_url.ifBlank { GITHUB_RELEASES_URL },
        apkUrl = apk,
        newer = isRemoteNewer(name, code),
    )
}

@Serializable
private data class GithubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    val browser_download_url: String = "",
)
