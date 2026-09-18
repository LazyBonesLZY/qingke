package cn.edu.gzus.qingke.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class StudentProfile(
    val name: String = "",
    val studentId: String = "",
    val studentKey: String = "",
    val className: String = "",
    val major: String = "",
    val campus: String = "",
    val yearName: String = "",
    val termCode: String = "3",
    val yearCode: String = "2026",
    val termLabel: String = "",
)

@Serializable
data class LessonSlot(
    val courseId: String,
    val courseName: String,
    val credit: String,
    val teacher: String,
    val room: String,
    val building: String = "",
    val campus: String = "",
    val weekday: Int,
    val weekdayName: String,
    val period: String,
    val periodLabel: String,
    val weeks: String,
    val className: String = "",
    val classId: String = "",
    val category: String = "",
    val required: String = "",
    val assess: String = "",
    val teachMode: String = "",
    val hours: String = "",
)

@Serializable
data class PracticeCourse(
    val name: String,
    val weeks: String,
    val teacher: String,
    val note: String = "",
)

@Serializable
data class GradeItem(
    val courseId: String,
    val courseName: String,
    val credit: String,
    val score: String = "",
    val gpa: String = "",
    val term: String = "",
    val assess: String = "",
)

@Serializable
data class ExamItem(
    val courseName: String,
    val time: String,
    val room: String,
    val seat: String = "",
    val status: String = "未安排",
)

@Serializable
sealed class NoticePart {
    @Serializable
    @kotlinx.serialization.SerialName("text")
    data class Text(val text: String = "") : NoticePart()

    @Serializable
    @kotlinx.serialization.SerialName("table")
    data class Table(
        val headers: List<String> = emptyList(),
        val rows: List<List<String>> = emptyList(),
    ) : NoticePart()
}

@Serializable
data class NoticeItem(
    val id: String,
    val title: String,
    val date: String = "",
    val category: String = "",
    val pinned: Boolean = false,
    val isNew: Boolean = false,
    val publisher: String = "",
    val content: String = "",
    val parts: List<NoticePart> = emptyList(),
) {
    fun bodyParts(): List<NoticePart> =
        parts.ifEmpty { if (content.isBlank()) emptyList() else listOf(NoticePart.Text(content)) }
}

@Serializable
data class FreeRoom(
    val name: String,
    val building: String,
    val campus: String,
    val capacity: String = "",
    val type: String = "",
)

@Serializable
data class TermCalendar(
    val yearCode: String = "",
    val termCode: String = "",
    val currentWeek: Int = 0,
    val termStart: String = "",
    val weekCount: Int = 0,
)

val RemindLeadMinutes = listOf(5, 10, 15, 20, 30, 45, 60)
const val DefaultRemindLeadMinutes = 20
const val GZUS_LOGIN_JWXT = "jwxt"
const val GZUS_LOGIN_CAS = "cas"
const val JIANGMEN_WATER_PRICE = 1.7
const val JIANGMEN_ELECTRIC_PRICE = 0.629
const val GZUS_AREA_GUANGZHOU = "81"
const val UTILITY_LOW_POWER = 10.0
const val UTILITY_LOW_WATER = 1.0

/** 正方学年代码：9 月到次年 1 月是上学期（xqm=3），2 月到 7 月是下学期（xqm=12）。 */
fun defaultZfYearCode(today: LocalDate = nowDateTime().date): String =
    (if (today.monthNumber >= 8) today.year else today.year - 1).toString()

fun defaultZfTermCode(today: LocalDate = nowDateTime().date): String =
    if (today.monthNumber >= 8 || today.monthNumber <= 1) "3" else "12"

@Serializable
data class AppSettings(
    val remindBeforeClass: Boolean = true,
    val darkModeFollowSystem: Boolean = false,
    val yearCode: String = defaultZfYearCode(),
    val termCode: String = defaultZfTermCode(),
    val currentWeek: Int = 1,
    val termStart: String = "",
    val weekCount: Int = 0,
    val courseAliases: Map<String, String> = emptyMap(),
    val xiaoaiEditUrl: String = "",
    val schoolId: String = School.Default.id,
    val gzusLoginChannel: String = GZUS_LOGIN_CAS,
    val remindLeadMinutes: Int = DefaultRemindLeadMinutes,
    val customJwxt: CustomJwxt = CustomJwxt(),
    val utilityUseCustomPrice: Boolean = false,
    val utilityWaterPrice: Double = JIANGMEN_WATER_PRICE,
    val utilityElectricPrice: Double = JIANGMEN_ELECTRIC_PRICE,
    val utilityBuilding: String = "",
    val utilityRoom: String = "",
    val utilityBind: UtilityBind = UtilityBind(),
    val scheduleShifts: List<ScheduleShift> = emptyList(),
    val autoSyncOnStart: Boolean = false,
    val coursePickQueue: List<CoursePickTask> = emptyList(),
)

@Serializable
data class CustomJwxt(
    val name: String = "自定义教务",
    val kind: String = "zhengfang",
    val origin: String = "",
    val loginUrl: String = "",
    val changePasswordUrl: String = "",
    val casOrigin: String = "",
    val casService: String = "",
    val supportsFreeRooms: Boolean = false,
    val hasPeriodClock: Boolean = false,
    val showsCaptcha: Boolean = false,
    val requiresCaptcha: Boolean = false,
    val captchaHint: String = "",
    val pathsText: String = "",
)

fun CustomJwxt.normalizedKind(): String = when (kind.trim().lowercase()) {
    "kingosoft", "zhku", "强智" -> "kingosoft"
    "lyuap", "cas", "联奕" -> "lyuap"
    else -> "zhengfang"
}

fun CustomJwxt.pathMap(): Map<String, String> =
    pathsText.lineSequence().mapNotNull { line ->
        val text = line.trim()
        if (text.isEmpty() || text.startsWith("#")) return@mapNotNull null
        val idx = text.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        val key = text.take(idx).trim()
        val value = text.substring(idx + 1).trim()
        if (key.isBlank() || value.isBlank()) null else key to value
    }.toMap()

fun CustomJwxt.originClean(): String = origin.trim().trimEnd('/')

/**
 * 换学校时保留所有个人偏好，只清掉跟原学校教务绑在一起的东西：
 * 学年学期、周历、选课队列。课表缩写和调课留着，换回去还能用。
 */
fun AppSettings.forSchool(school: School): AppSettings {
    val kingosoft = school == School.Zhku ||
        (school == School.Custom && customJwxt.normalizedKind() == "kingosoft")
    return copy(
        schoolId = school.id,
        yearCode = if (kingosoft) "" else defaultZfYearCode(),
        termCode = if (kingosoft) "" else defaultZfTermCode(),
        currentWeek = 1,
        termStart = "",
        weekCount = 0,
        coursePickQueue = emptyList(),
    )
}

fun AppSettings.resolvedRemindLead(): Int =
    if (remindLeadMinutes in RemindLeadMinutes) remindLeadMinutes else DefaultRemindLeadMinutes

@Serializable
data class ScheduleShift(
    val id: String,
    val fromDate: String,
    val toDate: String,
)

@Serializable
data class HolidayDay(
    val date: String,
    val name: String,
    val off: Boolean,
)

@Serializable
data class HolidayCalendar(
    val days: List<HolidayDay> = emptyList(),
    val years: List<Int> = emptyList(),
    val fetchedAt: Long = 0L,
)

@Serializable
data class SessionState(
    val loggedIn: Boolean = false,
    val cookies: Map<String, String> = emptyMap(),
    val lastSyncAt: Long = 0L,
    val studentId: String = "",
)

@Serializable
data class AppSnapshot(
    val profile: StudentProfile = StudentProfile(),
    val slots: List<LessonSlot> = emptyList(),
    val practices: List<PracticeCourse> = emptyList(),
    val grades: List<GradeItem> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val notices: List<NoticeItem> = emptyList(),
    val hall: HallSnapshot = HallSnapshot(),
    val utility: UtilitySnapshot = UtilitySnapshot(),
    val rooms: List<FreeRoom> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val holidays: HolidayCalendar = HolidayCalendar(),
    val session: SessionState = SessionState(),
    val seeded: Boolean = false,
) {
    val hasTimetable: Boolean get() = slots.isNotEmpty() || practices.isNotEmpty()
}

@Serializable
data class HallTodo(
    val id: String,
    val title: String,
    val time: String = "",
    val status: String = "",
    val url: String = "",
)

@Serializable
data class HallMessage(
    val id: String,
    val title: String,
    val time: String = "",
    val content: String = "",
)

@Serializable
data class HallAffair(
    val id: String,
    val name: String,
    val type: String = "",
    val url: String = "",
    val kind: String = "",
)

fun HallAffair.isLeave(): Boolean =
    kind == "leave" || name.contains("请假") || name.contains("销假") || type.contains("请假")

fun HallTodo.isLeave(): Boolean =
    title.contains("请假") || title.contains("销假") || status.contains("请假")

fun HallSnapshot.leaveAffairs(): List<HallAffair> = affairs.filter { it.isLeave() }

fun HallSnapshot.leaveSummary(): String {
    if (leaves.isNotEmpty()) {
        val latest = leaves.first()
        val line = listOf(latest.status, latest.title).filter { it.isNotBlank() }.joinToString(" · ")
        return if (leaves.size > 1) "$line · 共 ${leaves.size} 条" else line
    }
    if (leaveAffairs().isNotEmpty()) return "发起请假、看申请进度"
    if (error.isNotBlank() && !ready) return error
    return "办事大厅请假接口"
}

@Serializable
data class LeaveApplication(
    val id: String,
    val title: String,
    val status: String = "",
    val time: String = "",
    val start: String = "",
    val end: String = "",
    val reason: String = "",
    val node: String = "",
    /** 大厅流程实例号（orunid/docUnid），查审批进度要用。 */
    val instanceId: String = "",
) {
    fun traceId(): String = instanceId.ifBlank { id }
}

/** 审批流程里的一步。 */
@Serializable
data class LeaveStep(
    val name: String = "",
    val handler: String = "",
    val status: String = "",
    val time: String = "",
    val comment: String = "",
) {
    fun brief(): String =
        listOf(handler, status, time, comment).filter { it.isNotBlank() }.joinToString(" · ")
}

@Serializable
data class LeaveField(
    val key: String,
    val label: String,
    val type: String = "text",
    val required: Boolean = false,
    val value: String = "",
    val options: List<String> = emptyList(),
)

@Serializable
data class LeaveForm(
    val affairId: String = "",
    val affairName: String = "",
    val processId: String = "",
    val taskId: String = "",
    val cardId: String = "",
    val fields: List<LeaveField> = emptyList(),
)

fun defaultLeaveFields(): List<LeaveField> = listOf(
    LeaveField("QJLX", "请假类型", type = "choice", required = true, value = "事假", options = listOf("事假", "病假", "公假", "其他")),
    LeaveField("KSSJ", "开始时间", type = "datetime", required = true),
    LeaveField("JSSJ", "结束时间", type = "datetime", required = true),
    LeaveField("QJLY", "请假事由", type = "textarea", required = true),
)

@Serializable
data class UtilityBind(
    val areaId: String = "",
    val areaName: String = "",
    val buildingId: String = "",
    val buildingName: String = "",
    val floorId: String = "",
    val floorName: String = "",
    val roomId: String = "",
    val roomName: String = "",
) {
    val label: String
        get() = listOf(areaName, buildingName, floorName, roomName).filter { it.isNotBlank() }.joinToString(" · ")
}

@Serializable
data class UtilityOption(
    val id: String,
    val name: String,
    val areaId: String = "",
    val areaName: String = "",
    val buildingId: String = "",
    val buildingName: String = "",
    val roomId: String = "",
    val roomName: String = "",
)

fun AppSettings.resolvedUtilityBind(): UtilityBind {
    if (utilityBind.roomId.isNotBlank() || utilityBind.roomName.isNotBlank() || utilityBind.buildingName.isNotBlank()) {
        return utilityBind
    }
    return UtilityBind(buildingName = utilityBuilding, roomName = utilityRoom)
}

fun AppSettings.hasUtilityBind(): Boolean {
    val bind = resolvedUtilityBind()
    return bind.roomId.isNotBlank() || bind.roomName.isNotBlank()
}

fun AppSnapshot.utilityBrief(): String {
    if (!settings.hasUtilityBind()) return "未绑定宿舍"
    if (!utility.ready) return utility.error.ifBlank { "查询失败" }
    val bind = utility.bind.takeIf { it.hasRoom() } ?: settings.resolvedUtilityBind()
    val waterPrice = settings.resolvedWaterPrice(bind)
    val electricPrice = settings.resolvedElectricPrice(bind)
    return buildList {
        utilityLine("电", utility.power, "度", electricPrice)?.let(::add)
        utilityLine("冷水", utility.coldWater, "吨", waterPrice)?.let(::add)
        utilityLine("热水", utility.hotWater, "吨", waterPrice)?.let(::add)
        if (utility.lowPower()) add("电量不到 ${UTILITY_LOW_POWER.toInt()} 度，记得充值")
        else if (utility.lowWater()) add("水量不到 ${UTILITY_LOW_WATER.toInt()} 吨，记得充值")
    }.joinToString("\n").ifBlank { "查询失败" }
}

fun UtilitySnapshot.lowPower(): Boolean = parseAmount(power)?.let { it < UTILITY_LOW_POWER } == true

fun UtilitySnapshot.lowWater(): Boolean {
    val cold = parseAmount(coldWater) ?: return false
    return cold < UTILITY_LOW_WATER
}

fun UtilitySnapshot.isLow(): Boolean = ready && (lowPower() || lowWater())

/** 一卡通里 81 是广州校区，82 是江门校区。只有江门公布了单价。 */
fun UtilityBind.isGuangzhou(): Boolean =
    areaId.trim() == GZUS_AREA_GUANGZHOU || areaName.contains("广州")

private fun utilityLine(name: String, amount: String, unit: String, price: Double?): String? {
    if (amount.isBlank()) return null
    val yuan = if (price == null) null else parseAmount(amount)?.times(price)
    return buildString {
        append(name)
        append(' ')
        append(amount)
        append(' ')
        append(unit)
        if (yuan != null) {
            append(" · ")
            append(formatMoney(yuan))
            append(" 元")
        }
    }
}

@Serializable
data class UtilitySnapshot(
    val ready: Boolean = false,
    val building: String = "",
    val room: String = "",
    val power: String = "",
    val coldWater: String = "",
    val hotWater: String = "",
    val error: String = "",
    val bind: UtilityBind = UtilityBind(),
)

fun UtilityBind.hasRoom(): Boolean = roomId.isNotBlank() || roomName.isNotBlank()

/** 只有江门校区有公布的默认单价；广州校区没自定义就不折算成钱。 */
fun AppSettings.resolvedWaterPrice(bind: UtilityBind): Double? = when {
    utilityUseCustomPrice && utilityWaterPrice > 0 -> utilityWaterPrice
    bind.isGuangzhou() -> null
    else -> JIANGMEN_WATER_PRICE
}

fun AppSettings.resolvedElectricPrice(bind: UtilityBind): Double? = when {
    utilityUseCustomPrice && utilityElectricPrice > 0 -> utilityElectricPrice
    bind.isGuangzhou() -> null
    else -> JIANGMEN_ELECTRIC_PRICE
}

fun AppSettings.utilityPriceLabel(bind: UtilityBind): String = when {
    utilityUseCustomPrice -> "按下面填的算"
    bind.isGuangzhou() -> "广州校区没有公布单价，只显示度数和吨数；要算钱就自定义"
    else -> "江门校区 · 水 $JIANGMEN_WATER_PRICE 元/吨 · 电 $JIANGMEN_ELECTRIC_PRICE 元/度"
}

fun formatMoney(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val neg = cents < 0
    val abs = if (neg) -cents else cents
    val text = "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    return if (neg) "-$text" else text
}

fun parseAmount(text: String): Double? {
    val compact = text.trim().replace(",", "")
    val match = Regex("""-?\d+(?:\.\d+)?""").find(compact) ?: return null
    return match.value.toDoubleOrNull()
}

@Serializable
data class HallEvent(
    val id: String,
    val title: String,
    val time: String = "",
    val place: String = "",
)

@Serializable
data class HallSnapshot(
    val ready: Boolean = false,
    val userName: String = "",
    val todos: List<HallTodo> = emptyList(),
    val messages: List<HallMessage> = emptyList(),
    val affairs: List<HallAffair> = emptyList(),
    val events: List<HallEvent> = emptyList(),
    val leaves: List<LeaveApplication> = emptyList(),
    val error: String = "",
)

@Serializable
data class CoursePickScope(
    val id: String = "",
    val name: String = "",
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class CoursePickOffer(
    val courseId: String = "",
    val name: String = "",
    val credit: String = "",
    val teacher: String = "",
    val className: String = "",
    val classId: String = "",
    val time: String = "",
    val place: String = "",
    val capacity: String = "",
    val taken: String = "",
    val selected: Boolean = false,
    val scopeId: String = "",
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class CoursePickSection(
    val classId: String = "",
    val doJxbId: String = "",
    val name: String = "",
    val teacher: String = "",
    val time: String = "",
    val place: String = "",
    val credit: String = "",
    val capacity: String = "",
    val taken: String = "",
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class CoursePickTask(
    val id: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val className: String = "",
    val teacher: String = "",
    val doJxbId: String = "",
    val classId: String = "",
    val scopeId: String = "",
    val params: Map<String, String> = emptyMap(),
    val fireAt: Long = 0L,
    val status: String = "queued",
    val message: String = "",
    val lastAttemptAt: Long = 0L,
)

fun CoursePickOffer.brief(): String = listOf(
    teacher,
    credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
    time,
    place,
    seatsLabel(taken, capacity),
    if (selected) "已选" else "",
).filter { !it.isNullOrBlank() }.joinToString(" · ")

fun CoursePickSection.brief(): String = listOf(
    teacher,
    time,
    place,
    credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
    seatsLabel(taken, capacity),
).filter { !it.isNullOrBlank() }.joinToString(" · ")

fun CoursePickTask.brief(): String = listOf(
    className.ifBlank { teacher },
    coursePickStatusLabel(status),
    if (fireAt > 0 && status == "waiting") formatCoursePickWhen(fireAt) else "",
    message,
).filter { it.isNotBlank() }.joinToString(" · ")

fun coursePickStatusLabel(status: String): String = when (status) {
    "waiting" -> "到点自动选"
    "running" -> "正在提交"
    "ok" -> "已选上"
    "fail" -> "未选上"
    else -> "队列中"
}

fun AppSnapshot.coursePickBrief(): String {
    if (!resolved().supportsCoursePick) return "${resolved().jwxtName}没有自主选课"
    if (!session.loggedIn) return "登录后搜索、排队、到点提交"
    val queue = settings.coursePickQueue
    val waiting = queue.count { it.status == "waiting" }
    val ok = queue.count { it.status == "ok" }
    return when {
        waiting > 0 -> "$waiting 门到点自动选"
        ok > 0 -> "队列 ${queue.size} 门 · 已选上 $ok"
        queue.isNotEmpty() -> "队列 ${queue.size} 门"
        else -> "搜索、立即选、到点自动选"
    }
}

fun seatsLabel(taken: String, capacity: String): String {
    if (taken.isBlank() && capacity.isBlank()) return ""
    if (capacity.isBlank()) return "已选 $taken"
    if (taken.isBlank()) return "容量 $capacity"
    return "$taken / $capacity"
}

fun formatCoursePickWhen(millis: Long): String {
    if (millis <= 0L) return ""
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val h = dt.hour.toString().padStart(2, '0')
    val m = dt.minute.toString().padStart(2, '0')
    val s = dt.second.toString().padStart(2, '0')
    return "${dt.date} $h:$m:$s"
}

/** 解析手填的开选时间。填错返回 0，由调用方提示，别在点击回调里抛异常。 */
fun parseCoursePickWhen(date: String, time: String): Long {
    val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: return 0L
    val parts = time.trim().split(':', '：', '.', '-', ' ')
        .mapNotNull { it.toIntOrNull() }
    if (parts.isEmpty()) return 0L
    val clock = LocalTime(
        hour = parts[0].coerceIn(0, 23),
        minute = parts.getOrElse(1) { 0 }.coerceIn(0, 59),
        second = parts.getOrElse(2) { 0 }.coerceIn(0, 59),
    )
    return runCatching {
        LocalDateTime(day, clock).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }.getOrDefault(0L)
}

/** 最近一场还没过去的考试和距今天数；解析不出日期的排最后。 */
fun nextExam(exams: List<ExamItem>, today: LocalDate): Pair<ExamItem, Int>? {
    val todayKey = today.toEpochDays().toLong()
    return exams
        .mapNotNull { exam ->
            val key = examSortKey(exam.time)
            if (key == Long.MAX_VALUE || key < todayKey) null else exam to (key - todayKey).toInt()
        }
        .minByOrNull { it.second }
}

data class CourseDetail(
    val courseId: String,
    val courseName: String,
    val credit: String,
    val teacher: String,
    val category: String,
    val required: String,
    val assess: String,
    val className: String,
    val rooms: String,
    val times: String,
    val weeks: String,
    val campus: String,
    val hours: String,
    val slots: List<LessonSlot>,
    val highlight: String,
)

data class PeriodBlock(
    val index: Int,
    val label: String,
    val start: String,
    val end: String,
    val band: String,
)

val MajorPeriods = listOf(
    PeriodBlock(1, "1-2", "09:00", "10:20", "上午"),
    PeriodBlock(2, "3-4", "10:40", "12:00", "上午"),
    PeriodBlock(3, "5-6", "12:30", "13:50", "中午"),
    PeriodBlock(4, "7-8", "14:00", "15:20", "下午"),
    PeriodBlock(5, "9-10", "15:30", "16:50", "下午"),
    PeriodBlock(6, "11-12", "17:00", "18:20", "下午"),
    PeriodBlock(7, "13-14", "19:00", "20:20", "晚上"),
    PeriodBlock(8, "15-16", "20:30", "21:50", "晚上"),
)

val WeekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
val WeekdayFull = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
