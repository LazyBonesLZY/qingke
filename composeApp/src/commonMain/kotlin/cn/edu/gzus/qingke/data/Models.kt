package cn.edu.gzus.qingke.data

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
data class NoticeItem(
    val id: String,
    val title: String,
    val date: String = "",
    val category: String = "",
    val pinned: Boolean = false,
    val isNew: Boolean = false,
    val publisher: String = "",
    val content: String = "",
)

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

@Serializable
data class AppSettings(
    val remindBeforeClass: Boolean = true,
    val darkModeFollowSystem: Boolean = false,
    val yearCode: String = "2026",
    val termCode: String = "3",
    val currentWeek: Int = 1,
    val termStart: String = "",
    val weekCount: Int = 0,
    val courseAliases: Map<String, String> = emptyMap(),
    val xiaoaiEditUrl: String = "",
    val schoolId: String = School.Default.id,
    val remindLeadMinutes: Int = DefaultRemindLeadMinutes,
    val customJwxt: CustomJwxt = CustomJwxt(),
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

fun AppSettings.resolvedRemindLead(): Int =
    if (remindLeadMinutes in RemindLeadMinutes) remindLeadMinutes else DefaultRemindLeadMinutes

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
    val rooms: List<FreeRoom> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val holidays: HolidayCalendar = HolidayCalendar(),
    val session: SessionState = SessionState(),
    val seeded: Boolean = false,
) {
    val hasTimetable: Boolean get() = slots.isNotEmpty() || practices.isNotEmpty()
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
