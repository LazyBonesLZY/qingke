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

private const val TABLE_URL = "https://i.ai.mi.com/course-multi/table"
private const val TABLES_URL = "https://i.ai.mi.com/course-multi/tables"
private const val COURSE_URL = "https://i.ai.mi.com/course-multi/courseInfo"
private const val COURSES_URL = "https://i.ai.mi.com/course-multi/courseInfos"
private const val SOURCE = "course-app-browser"
private const val UA =
    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

class XiaoaiClient(
    private val client: HttpClient = createHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun replaceCourses(token: XiaoaiToken, courses: List<XiaoaiApiCourse>): String {
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
        val tables = loadTables(token)
        val current = tables.firstOrNull { it.current == 1 } ?: tables.firstOrNull()
        if (current != null) {
            return token.copy(ctId = current.id, tableName = current.name.ifBlank { token.tableName })
        }
        val created = createTable(token)
        return token.copy(ctId = created, tableName = token.tableName.ifBlank { "青课" })
    }

    private suspend fun loadTables(token: XiaoaiToken): List<XiaoaiTable> {
        val response = client.request(TABLES_URL) {
            method = HttpMethod.Get
            xiaoaiHeaders()
            parameter("userId", token.userId.toString())
            parameter("deviceId", token.deviceId)
            parameter("sourceName", SOURCE)
        }
        val root = parseXiaoaiRoot(response, "课表列表")
        return parseXiaoaiTables(root)
    }

    private suspend fun createTable(token: XiaoaiToken): Long {
        val body = XiaoaiCreateTableBody(
            userId = token.userId,
            deviceId = token.deviceId,
            name = token.tableName.ifBlank { "青课" },
            current = 1,
        )
        val response = client.request(TABLE_URL) {
            method = HttpMethod.Post
            xiaoaiHeaders()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(XiaoaiCreateTableBody.serializer(), body))
        }
        val root = parseXiaoaiRoot(response, "新建课表")
        val id = root.tableId()
            ?: error(tokenError("小爱里还没有课表，新建也失败了"))
        return id
    }

    private suspend fun loadCourseIds(token: XiaoaiToken): List<Long> {
        val response = client.request(TABLE_URL) {
            method = HttpMethod.Get
            xiaoaiHeaders()
            parameter("ctId", token.ctId.toString())
            parameter("userId", token.userId.toString())
            parameter("deviceId", token.deviceId)
            parameter("sourceName", SOURCE)
        }
        val root = parseXiaoaiRoot(response, "课表接口")
        val data = root["data"] as? JsonObject ?: return emptyList()
        val rows = data["courses"] as? JsonArray
            ?: data["courseList"] as? JsonArray
            ?: return emptyList()
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            obj["id"]?.jsonPrimitive?.longOrNull
                ?: obj["cId"]?.jsonPrimitive?.longOrNull
        }
    }

    private suspend fun deleteCourse(token: XiaoaiToken, courseId: Long) {
        val body = XiaoaiDeleteBody(
            userId = token.userId,
            deviceId = token.deviceId,
            ctId = token.ctId,
            cId = courseId,
        )
        val response = client.request(COURSE_URL) {
            method = HttpMethod.Delete
            xiaoaiHeaders()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(XiaoaiDeleteBody.serializer(), body))
        }
        val text = response.bodyAsText()
        if (response.status.value >= 400) error(tokenError("删除原课程失败（${response.status.value}）"))
        ensureOk(text, "删除原课程失败")
    }

    private suspend fun addCoursesBatch(token: XiaoaiToken, courses: List<XiaoaiApiCourse>): Boolean {
        if (courses.isEmpty()) return true
        val body = XiaoaiBatchBody(
            userId = token.userId,
            deviceId = token.deviceId,
            ctId = token.ctId,
            courses = courses,
        )
        val response = runCatching {
            client.request(COURSES_URL) {
                method = HttpMethod.Post
                xiaoaiHeaders()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(XiaoaiBatchBody.serializer(), body))
            }
        }.getOrNull() ?: return false
        if (response.status.value >= 400) return false
        val text = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        val code = root["code"]?.jsonPrimitive?.contentOrNull ?: return false
        return code == "0"
    }

    private suspend fun addCourse(token: XiaoaiToken, course: XiaoaiApiCourse) {
        val body = XiaoaiAddBody(
            userId = token.userId,
            deviceId = token.deviceId,
            ctId = token.ctId,
            course = course,
        )
        val response = client.request(COURSE_URL) {
            method = HttpMethod.Post
            xiaoaiHeaders()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(XiaoaiAddBody.serializer(), body))
        }
        val text = response.bodyAsText()
        if (response.status.value >= 400) error(tokenError("写入课程失败（${response.status.value}）"))
        ensureOk(text, "写入课程失败")
    }

    private fun ensureOk(text: String, fallback: String) {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val code = root["code"]?.jsonPrimitive?.contentOrNull ?: return
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
        val code = root["code"]?.jsonPrimitive?.contentOrNull
        if (code != null && code != "0") {
            error(tokenError(root.apiMessage() ?: "$what 失败"))
        }
        return root
    }

    private fun tokenError(message: String): String =
        if (message.contains("过期") || message.contains("不存在") || message.contains("登录") || message.contains("无效")) {
            "小爱授权失效。打开小爱课程表设置底栏连点 5 次，用 vConsole 复制一份新的 UserInfo。"
        } else {
            message
        }

    private fun io.ktor.client.request.HttpRequestBuilder.xiaoaiHeaders() {
        header(HttpHeaders.UserAgent, UA)
        header(HttpHeaders.Accept, "application/json")
        header(HttpHeaders.Origin, "https://i.ai.mi.com")
        header(HttpHeaders.Referrer, "https://i.ai.mi.com/h5/precache/ai-schedule/")
        header("access-control-allow-origin", "true")
    }
}

@Serializable
private data class XiaoaiCreateTableBody(
    val userId: Long,
    val deviceId: String,
    val name: String,
    val current: Int,
    val sourceName: String = SOURCE,
)

@Serializable
private data class XiaoaiBatchBody(
    val userId: Long,
    val deviceId: String,
    val ctId: Long,
    val courses: List<XiaoaiApiCourse>,
    val sourceName: String = SOURCE,
)

private data class XiaoaiTable(
    val id: Long,
    val name: String,
    val current: Int,
)

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
        val id = obj["id"]?.jsonPrimitive?.longOrNull
            ?: obj["ctId"]?.jsonPrimitive?.longOrNull
            ?: return@mapNotNull null
        XiaoaiTable(
            id = id,
            name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            current = obj["current"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }
}

private fun JsonObject.tableId(): Long? {
    val data = this["data"] ?: return null
    return when (data) {
        is JsonObject -> data["id"]?.jsonPrimitive?.longOrNull
            ?: data["ctId"]?.jsonPrimitive?.longOrNull
        is JsonPrimitive -> data.longOrNull
        else -> null
    }
}

@Serializable
private data class XiaoaiAddBody(
    val userId: Long,
    val deviceId: String,
    val ctId: Long,
    val course: XiaoaiApiCourse,
    val sourceName: String = SOURCE,
)

@Serializable
private data class XiaoaiDeleteBody(
    val userId: Long,
    val deviceId: String,
    val ctId: Long,
    val cId: Long,
    val sourceName: String = SOURCE,
)

private fun JsonObject.apiMessage(): String? =
    listOf("msg", "message", "desc", "error").firstNotNullOfOrNull {
        this[it]?.jsonPrimitive?.contentOrNull?.takeIf { text -> text.isNotBlank() }
    }
