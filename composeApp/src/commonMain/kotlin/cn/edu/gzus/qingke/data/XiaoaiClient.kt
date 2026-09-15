package cn.edu.gzus.qingke.data

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val MULTI = "https://i.ai.mi.com/course-multi"
private const val MULTI_AUTH = "https://i.ai.mi.com/course-multi-auth"
private const val UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

class XiaoaiClient(
    private val client: HttpClient = createHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun replaceCourses(token: XiaoaiToken, courses: List<XiaoaiApiCourse>): String {
        requireUserInfoAuth(token)
        val resolved = resolveTable(token)
        val existing = loadCourseIds(resolved)
        existing.forEach { id ->
            deleteCourse(resolved, id)
            delay(250)
        }
        if (!addCoursesBatch(resolved, courses)) {
            courses.forEachIndexed { index, course ->
                addCourse(resolved, course)
                if (index != courses.lastIndex) delay(250)
            }
        }
        val target = resolved.tableName.ifBlank { "小爱课程表" }
        val removed = if (existing.isEmpty()) "" else "，清掉原来 ${existing.size} 门"
        return "已导入 ${courses.size} 门到$target$removed"
    }

    private suspend fun resolveTable(token: XiaoaiToken): XiaoaiToken {
        if (token.ctId > 0L) return token
        loadTables(token).pickCurrent()?.let { found ->
            return token.copy(ctId = found.id, tableName = found.name.ifBlank { token.tableName })
        }
        val created = runCatching { createTable(token) }.getOrElse { error ->
            loadTables(token).pickCurrent()?.let { found ->
                return token.copy(ctId = found.id, tableName = found.name.ifBlank { token.tableName })
            }
            throw error
        }
        if (created > 0L) {
            return token.copy(ctId = created, tableName = token.tableName.ifBlank { "青课" })
        }
        val again = loadTables(token).pickCurrent()
            ?: error(tokenError("小爱里还没有课表，新建也失败了"))
        return token.copy(ctId = again.id, tableName = again.name.ifBlank { token.tableName })
    }

    private suspend fun loadTables(token: XiaoaiToken): List<XiaoaiTable> {
        val response = client.request(token.api("tables")) {
            method = HttpMethod.Get
            xiaoaiHeaders(token)
            token.queryIdentity(this)
            parameter("sourceName", token.sourceName())
        }
        val root = parseXiaoaiRoot(response, "课表列表")
        return parseXiaoaiTables(root)
    }

    private suspend fun createTable(token: XiaoaiToken): Long {
        val body = XiaoaiCreateBody(
            name = token.tableName.ifBlank { "青课" },
            current = 1,
            sourceName = token.sourceName(),
            userId = if (token.authorization.isBlank()) token.userId else null,
            deviceId = if (token.authorization.isBlank()) token.deviceId else null,
        )
        val response = client.request(token.api("table")) {
            method = HttpMethod.Post
            xiaoaiHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(XiaoaiCreateBody.serializer(), body))
        }
        val root = parseXiaoaiRoot(response, "新建课表")
        return root.tableId() ?: 0L
    }

    private suspend fun loadCourseIds(token: XiaoaiToken): List<Long> {
        val response = client.request(token.api("table")) {
            method = HttpMethod.Get
            xiaoaiHeaders(token)
            if (token.ctId > 0L) parameter("ctId", token.ctId.toString())
            token.queryIdentity(this)
            parameter("sourceName", token.sourceName())
        }
        val root = parseXiaoaiRoot(response, "课表接口")
        val data = root["data"] as? JsonObject ?: return emptyList()
        val rows = data["courses"] as? JsonArray
            ?: data["courseList"] as? JsonArray
            ?: return emptyList()
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            obj.longish("id", "cId")
        }
    }

    private suspend fun deleteCourse(token: XiaoaiToken, courseId: Long) {
        val body = token.writeBody(cId = courseId)
        val response = client.request(token.api("courseInfo")) {
            method = HttpMethod.Delete
            xiaoaiHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(XiaoaiWriteBody.serializer(), body))
        }
        val text = response.bodyAsText()
        if (response.status.value >= 400) error(tokenError("删除原课程失败（${response.status.value}）"))
        ensureOk(text, "删除原课程失败")
    }

    private suspend fun addCoursesBatch(token: XiaoaiToken, courses: List<XiaoaiApiCourse>): Boolean {
        if (courses.isEmpty()) return true
        val body = token.batchBody(courses)
        val response = runCatching {
            client.request(token.api("courseInfos")) {
                method = HttpMethod.Post
                xiaoaiHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(XiaoaiBatchBody.serializer(), body))
            }
        }.getOrNull() ?: return false
        if (response.status.value >= 400) return false
        val text = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        return root.apiCode() == "0"
    }

    private suspend fun addCourse(token: XiaoaiToken, course: XiaoaiApiCourse) {
        val body = token.writeBody(course = course)
        val response = client.request(token.api("courseInfo")) {
            method = HttpMethod.Post
            xiaoaiHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(XiaoaiWriteBody.serializer(), body))
        }
        val text = response.bodyAsText()
        if (response.status.value >= 400) error(tokenError("写入课程失败（${response.status.value}）"))
        ensureOk(text, "写入课程失败")
    }

    private fun ensureOk(text: String, fallback: String) {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val code = root.apiCode() ?: return
        if (code != "0") error(tokenError(root.apiMessage() ?: fallback))
    }

    private suspend fun parseXiaoaiRoot(
        response: io.ktor.client.statement.HttpResponse,
        what: String,
    ): JsonObject {
        val text = response.bodyAsText()
        if (response.status.value >= 400) {
            error(tokenError("$what ${response.status.value}，UserInfo 可能无效"))
        }
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            error(tokenError("$what 没有返回 JSON，UserInfo 可能无效"))
        }
        val code = root.apiCode()
        if (code != null && code != "0") {
            if (code == "-1") {
                error(tokenError("小爱没认出登录态，重新复制整段 UserInfo"))
            }
            val message = root.apiMessage().orEmpty()
            if (message.contains("exist", ignoreCase = true) || code == "502") {
                error(tokenError("小爱已经有当前课表，但这次没读到编号"))
            }
            error(tokenError(message.ifBlank { "$what 失败" }))
        }
        return root
    }

    private fun tokenError(message: String): String =
        if (
            message.contains("过期") ||
            message.contains("不存在") ||
            message.contains("登录") ||
            message.contains("无效") ||
            message.contains("authorization")
        ) {
            "小爱授权失效。打开小爱课程表设置底栏连点 5 次，用 vConsole 复制一份新的 UserInfo。"
        } else {
            message
        }

    private fun io.ktor.client.request.HttpRequestBuilder.xiaoaiHeaders(token: XiaoaiToken) {
        header(HttpHeaders.UserAgent, UA)
        header(HttpHeaders.Accept, "application/json")
        header(HttpHeaders.Origin, "https://i.ai.mi.com")
        header(HttpHeaders.Referrer, "https://i.ai.mi.com/h5/precache/ai-schedule/")
        header("access-control-allow-origin", "true")
        if (token.authorization.isNotBlank()) {
            header(HttpHeaders.Authorization, token.authorization)
        }
    }
}

private fun requireUserInfoAuth(token: XiaoaiToken) {
    if (token.fromUserInfo && token.authorization.isBlank()) {
        error("这份 UserInfo 没有 authorization。打开小爱课程表设置底栏连点 5 次，把 vConsole 里整段 JSON 复制过来。")
    }
}

private fun XiaoaiToken.api(path: String): String {
    val root = if (authorization.isNotBlank()) MULTI_AUTH else MULTI
    return "$root/$path"
}

private fun XiaoaiToken.sourceName(): String =
    if (authorization.isNotBlank()) "course-app-miui" else "course-app-browser"

private fun XiaoaiToken.queryIdentity(builder: io.ktor.client.request.HttpRequestBuilder) {
    if (authorization.isNotBlank()) return
    builder.parameter("userId", userId.toString())
    builder.parameter("deviceId", deviceId)
}

private fun XiaoaiToken.writeBody(
    cId: Long? = null,
    course: XiaoaiApiCourse? = null,
): XiaoaiWriteBody = XiaoaiWriteBody(
    ctId = ctId,
    sourceName = sourceName(),
    userId = if (authorization.isBlank()) userId else null,
    deviceId = if (authorization.isBlank()) deviceId else null,
    cId = cId,
    course = course,
)

private fun XiaoaiToken.batchBody(courses: List<XiaoaiApiCourse>): XiaoaiBatchBody = XiaoaiBatchBody(
    ctId = ctId,
    courses = courses,
    sourceName = sourceName(),
    userId = if (authorization.isBlank()) userId else null,
    deviceId = if (authorization.isBlank()) deviceId else null,
)

@Serializable
private data class XiaoaiCreateBody(
    val name: String,
    val current: Int,
    val sourceName: String,
    val userId: Long? = null,
    val deviceId: String? = null,
)

@Serializable
private data class XiaoaiBatchBody(
    val ctId: Long,
    val courses: List<XiaoaiApiCourse>,
    val sourceName: String,
    val userId: Long? = null,
    val deviceId: String? = null,
)

private data class XiaoaiTable(
    val id: Long,
    val name: String,
    val current: Int,
)

private fun List<XiaoaiTable>.pickCurrent(): XiaoaiTable? =
    firstOrNull { it.current == 1 } ?: firstOrNull()

private fun parseXiaoaiTables(root: JsonObject): List<XiaoaiTable> {
    val data = root["data"]
    val rows = when (data) {
        is JsonArray -> data
        is JsonObject -> data["tables"] as? JsonArray
            ?: data["list"] as? JsonArray
            ?: data["data"] as? JsonArray
        else -> null
    } ?: return emptyList()
    return rows.mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val id = obj.longish("id", "ctId") ?: return@mapNotNull null
        XiaoaiTable(
            id = id,
            name = obj.stringish("name").orEmpty(),
            current = obj.longish("current")?.toInt() ?: 0,
        )
    }
}

private fun JsonObject.tableId(): Long? {
    val data = this["data"] ?: return null
    return when (data) {
        is JsonObject -> data.longish("id", "ctId")
        is JsonPrimitive -> data.asLong()?.takeIf { it > 0L }
        else -> null
    }
}

@Serializable
private data class XiaoaiWriteBody(
    val ctId: Long,
    val sourceName: String,
    val userId: Long? = null,
    val deviceId: String? = null,
    val cId: Long? = null,
    val course: XiaoaiApiCourse? = null,
)

private fun JsonObject.apiCode(): String? {
    this["code"]?.jsonPrimitive?.let { primitive ->
        return primitive.contentOrNull ?: primitive.asLong()?.toString() ?: primitive.content
    }
    val status = this["status"]?.jsonPrimitive?.asLong() ?: return null
    return if (status == 0L) "0" else status.toString()
}

private fun JsonObject.apiMessage(): String? =
    listOf("desc", "msg", "message", "error").firstNotNullOfOrNull {
        this[it]?.jsonPrimitive?.contentOrNull?.takeIf { text -> text.isNotBlank() }
    }

private fun JsonObject.longish(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.asLong() }

private fun JsonObject.stringish(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }

private fun JsonPrimitive.asLong(): Long? = longOrNull ?: contentOrNull?.toLongOrNull()
