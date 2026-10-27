@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.edu.gzus.qingke.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cn.edu.gzus.qingke.QingkeTheme
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.ExamItem
import cn.edu.gzus.qingke.data.GradeItem
import cn.edu.gzus.qingke.data.LessonSlot
import cn.edu.gzus.qingke.data.School
import cn.edu.gzus.qingke.data.SessionState
import cn.edu.gzus.qingke.data.mondayOf
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.ui.grades.GradesScreen
import cn.edu.gzus.qingke.ui.jwxt.JwxtScreen
import cn.edu.gzus.qingke.ui.mine.MineScreen
import cn.edu.gzus.qingke.ui.timetable.TimetableScreen
import cn.edu.gzus.qingke.ui.today.TodayScreen
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/** 页面渲染冒烟：围绕当前时刻造一份课表 / 成绩 / 考试，各页出图到 build/ui-preview/。 */
class TodayRenderTest {
    @Test
    fun rendersToday() = render("today") { TodayScreen(sample(), QingkeNavigator(), PaddingValues(), onSync = {}) }

    @Test
    fun rendersTimetable() = render("timetable") { TimetableScreen(sample(), QingkeNavigator(), PaddingValues()) }

    @Test
    fun rendersGrades() = render("grades") { GradesScreen(sample(), QingkeNavigator(), PaddingValues()) }

    @Test
    fun rendersServices() = render("services") { JwxtScreen(sample(), QingkeNavigator(), PaddingValues()) }

    @Test
    fun rendersMine() = render("mine", height = 2600) {
        MineScreen(
            snapshot = sample(),
            nav = QingkeNavigator(),
            contentPadding = PaddingValues(),
            busy = false,
            error = null,
            onLogin = { _, _, _ -> },
            onSync = {},
            onToggleRemind = {},
            onPickRemindLead = {},
            onPickTermStart = {},
            onPickWeek = {},
            onLogout = {},
            liveTesting = false,
            onLiveTest = {},
            onStopLiveTest = {},
        )
    }

    private fun render(name: String, height: Int = 1100, content: @Composable () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val scene = ImageComposeScene(width = 400, height = height, density = Density(1f)) {
                    QingkeTheme(settings = sample().settings) { content() }
                }
                try {
                    var bytes = ByteArray(0)
                    repeat(6) { frame ->
                        scene.render(frame * 16_000_000L).use { image -> bytes = image.encodeToData()!!.bytes }
                    }
                    val output = File("build/ui-preview/$name.png")
                    output.parentFile.mkdirs()
                    output.writeBytes(bytes)
                    assertTrue(output.length() > 0)
                } finally {
                    scene.close()
                }
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun sample(): AppSnapshot {
        val now = nowDateTime()
        val minute = (now.hour * 60 + now.minute).coerceIn(150, 24 * 60 - 130)
        fun clock(m: Int) = "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
        val today = now.date
        val other = (weekdayIndex(today) % 7) + 1
        return AppSnapshot(
            settings = AppSettings(
                schoolId = School.Gzus.id,
                currentWeek = 1,
                termStart = mondayOf(today).toString(),
                yearCode = "2026",
                termCode = "3",
                periodTimeOverrides = mapOf(
                    "1-2" to "${clock(minute - 140)}-${clock(minute - 60)}",
                    "3-4" to "${clock(minute - 30)}-${clock(minute + 15)}",
                    "5-6" to "${clock(minute + 40)}-${clock(minute + 120)}",
                ),
            ),
            session = SessionState(loggedIn = true),
            slots = listOf(
                slot("1-2", "高等数学", "H1-208", "张老师", weekdayIndex(today)),
                slot("3-4", "计算机与人工智能导论", "H3-104", "李老师", weekdayIndex(today)),
                slot("5-6", "大学英语", "H1-105", "王老师", weekdayIndex(today)),
                slot("1-2", "中国近现代史纲要", "H2-301", "赵老师", other),
                slot("1-2", "形势与政策", "H2-302", "钱老师", other),
                slot("3-4", "大学体育", "操场", "孙老师", other),
            ),
            grades = listOf(
                GradeItem("高等数学", "高等数学", "4", "92", term = "2026-2027 1"),
                GradeItem("大学英语", "大学英语", "3", "良好", term = "2026-2027 1"),
                GradeItem("线性代数", "线性代数", "3", "78", term = "2025-2026 2"),
            ),
            exams = listOf(
                ExamItem("线性代数", "${today.plus(DatePeriod(days = 2))} 09:00-11:00", "H2-301", "18"),
                ExamItem("大学物理", "${today.plus(DatePeriod(days = 12))} 14:00-16:00", "H1-101"),
            ),
        )
    }

    private fun slot(period: String, name: String, room: String, teacher: String, weekday: Int) = LessonSlot(
        courseId = name,
        courseName = name,
        credit = "2",
        teacher = teacher,
        room = room,
        weekday = weekday,
        weekdayName = "",
        period = period,
        periodLabel = period,
        weeks = "1-16",
    )
}
