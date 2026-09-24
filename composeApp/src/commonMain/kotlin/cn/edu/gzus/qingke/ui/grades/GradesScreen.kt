package cn.edu.gzus.qingke.ui.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.formatGpa
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.formatScore
import cn.edu.gzus.qingke.data.resolvedGpa
import cn.edu.gzus.qingke.data.summarizeGpa
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.StatRow
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun GradesScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
) {
    val courses = remember(snapshot.slots) { uniqueCourses(snapshot.slots) }
    val planned = remember(courses) { courses.sumOf { it.credit.toDoubleOrNull() ?: 0.0 } }
    val scored = remember(snapshot.grades) {
        snapshot.grades.filter { it.score.isNotBlank() || it.gpa.isNotBlank() }
    }
    val gpa = remember(snapshot.grades) { summarizeGpa(snapshot.grades) }
    // 进度条要比的是本学期：拿全部学年的已修学分去除本学期计划学分没有意义。
    val termGpa = remember(snapshot.grades, courses) {
        val ids = courses.mapNotNull { it.courseId.takeIf(String::isNotBlank) }.toSet()
        summarizeGpa(snapshot.grades.filter { it.courseId in ids })
    }
    val progress = if (planned <= 0.0) null else (termGpa.credits / planned).toFloat().coerceIn(0f, 1f)
    val jwxt = snapshot.resolved().jwxtName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(16.dp))
        ScreenHeader(
            "",
            "成绩",
            when {
                snapshot.grades.isNotEmpty() -> "绩点按学分加权"
                snapshot.session.loggedIn -> "出分后自动同步"
                else -> "登录后同步"
            },
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            title = gpa.weighted?.let { "学分绩点  ${formatGpa(it)}" } ?: "学分绩点  —",
            summary = when {
                gpa.counted > 0 -> "已计 ${gpa.counted} 门 · ${formatScore(gpa.credits)} 学分"
                planned > 0.0 -> "本学期 ${planned.toInt()} 学分，出分后统计"
                snapshot.session.loggedIn -> "${jwxt}还没有成绩"
                else -> "去「我的」登录${jwxt}"
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            height = null,
            progress = progress,
            progressLabel = if (planned > 0.0) {
                "本学期 ${formatScore(termGpa.credits)}/${planned.toInt()} 学分"
            } else {
                ""
            },
            onClick = if (!snapshot.session.loggedIn) {
                { nav.goTab(TabDest.Mine) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(12.dp))
        StatRow(
            items = listOf(
                (termGpa.weighted?.let { formatGpa(it) } ?: "—") to "本学期绩点",
                (gpa.averageScore?.let { formatScore(it) } ?: "—") to "算术均分",
                scored.size.toString() to "已出成绩",
                formatScore(gpa.credits) to "已计学分",
            ),
            onClicks = emptyList(),
        )
        SmallTitle(text = "本学期")
        if (courses.isEmpty()) {
            InfoCard(
                title = "还没有课程",
                summary = "同步课表后会列在这里",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                courses.forEach { course ->
                    val grade = snapshot.grades.firstOrNull { it.courseId == course.courseId }
                    val point = grade?.resolvedGpa()
                    val mark = when {
                        grade?.score?.isNotBlank() == true && point != null ->
                            "${grade.score} · 绩点 ${formatGpa(point)} · ${course.credit} 学分"
                        grade?.score?.isNotBlank() == true -> "${grade.score} · ${course.credit} 学分"
                        course.assess.contains("考查") -> "考查 · ${course.credit} 学分"
                        course.credit.isNotBlank() -> "未出分 · ${course.credit} 学分"
                        else -> "未出分"
                    }
                    InfoCard(
                        title = course.courseName,
                        summary = mark,
                        onClick = { nav.open(Route.Course(course.courseId)) },
                    )
                }
            }
        }
        SmallTitle(text = "全部成绩")
        if (scored.isEmpty()) {
            InfoCard(
                title = "还没有绩点",
                summary = "同步成绩后按课程列出分数和绩点",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scored.forEach { item ->
                    val point = item.resolvedGpa()
                    val mark = buildList {
                        if (item.score.isNotBlank()) add(item.score)
                        if (point != null) add("绩点 ${formatGpa(point)}")
                        if (item.credit.isNotBlank()) add("${item.credit} 学分")
                        if (item.term.isNotBlank()) add(item.term)
                    }.joinToString(" · ")
                    InfoCard(
                        title = item.courseName.ifBlank { item.courseId },
                        summary = mark.ifBlank { "已出分" },
                        onClick = item.courseId.takeIf { it.isNotBlank() }?.let { id ->
                            { nav.open(Route.Course(id)) }
                        },
                    )
                }
            }
        }
        if (snapshot.practices.isNotEmpty()) {
            SmallTitle(text = "实践课")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.practices.forEach { item ->
                    InfoCard(
                        title = item.name,
                        summary = listOf(item.weeks, item.teacher, item.note).filter { it.isNotBlank() }.joinToString("\n"),
                        height = null,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
