package cn.edu.gzus.qingke.ui.grades

import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import cn.edu.gzus.qingke.data.GradeItem
import cn.edu.gzus.qingke.data.gradeTermKey
import cn.edu.gzus.qingke.data.numericScore
import cn.edu.gzus.qingke.data.currentTermKey
import cn.edu.gzus.qingke.data.formatGpa
import cn.edu.gzus.qingke.data.inTerm
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.formatScore
import cn.edu.gzus.qingke.data.resolvedGpa
import cn.edu.gzus.qingke.data.summarizeGpa
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
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
    // 还要按学期筛：重修课本学期没出分时，上学期那条旧成绩不能算进「本学期」。
    val termGrades = remember(snapshot.grades, courses, snapshot.settings.yearCode, snapshot.settings.termCode) {
        val ids = courses.mapNotNull { it.courseId.takeIf(String::isNotBlank) }.toSet()
        snapshot.grades.filter { it.courseId in ids }.inTerm(snapshot.settings.currentTermKey())
    }
    val termGpa = remember(termGrades) { summarizeGpa(termGrades) }
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
        GpaHero(
            value = gpa.weighted?.let { formatGpa(it) } ?: "—",
            summary = when {
                gpa.counted > 0 -> "已计 ${gpa.counted} 门 · ${formatScore(gpa.credits)} 学分"
                planned > 0.0 -> "本学期 ${formatScore(planned)} 学分，出分后统计"
                snapshot.session.loggedIn -> "${jwxt}还没有成绩"
                else -> "去「我的」登录${jwxt}"
            },
            progress = progress,
            progressLabel = if (planned > 0.0) {
                "本学期已出分 ${formatScore(termGpa.credits)}/${formatScore(planned)} 学分"
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
            // 重修 / 补考会有多条：显示最好的那次，和绩点统计的取法一致。
            // 已出分的排前面，未出分的沉底，一眼看出还差几门。
            val termRows = courses
                .map { course ->
                    course to termGrades.filter { course.courseId.isNotBlank() && it.courseId == course.courseId }
                        .maxByOrNull { it.resolvedGpa() ?: -1.0 }
                }
                .sortedBy { (_, grade) -> if (grade?.score?.isNotBlank() == true) 0 else 1 }
            ListGroup(
                rows = termRows.map { (course, grade) ->
                    val shown = grade?.takeIf { it.score.isNotBlank() }
                    ListRow(
                        title = course.courseName.ifBlank { course.courseId.ifBlank { "未命名课程" } },
                        summary = listOfNotNull(
                            grade?.resolvedGpa()?.let { "绩点 ${formatGpa(it)}" },
                            course.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
                            course.assess.takeIf { shown == null && it.isNotBlank() },
                        ).joinToString(" · "),
                        badge = shown?.score ?: "未出分",
                        tone = shown?.let { scoreTone(it) } ?: BadgeTone.Muted,
                        // 课程号为空时详情页一定是「没有这门课」，不如不让点。
                        onClick = course.courseId.takeIf { it.isNotBlank() }?.let { id -> { nav.open(Route.Course(id)) } },
                    )
                },
            )
        }
        if (scored.isEmpty()) {
            SmallTitle(text = "全部成绩")
            InfoCard(
                title = "还没有绩点",
                summary = "同步成绩后按学期列出分数和绩点",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            // 按学期分组，新学期在前；学期认不出来的归到最后一组，组内保持教务原顺序。
            // 组标题带上该学期的加权绩点，比每行重复写学期名省地方。
            val termGroups = remember(scored) {
                scored.groupBy { gradeTermKey(it.term) }
                    .entries
                    .sortedByDescending { entry -> entry.key?.let { (y, t) -> y * 10 + t } ?: -1 }
            }
            termGroups.forEach { entry ->
                val items = entry.value
                val groupGpa = summarizeGpa(items).weighted
                SmallTitle(
                    text = listOfNotNull(
                        // 认不出的学期可能混着好几种写法，那就不挑其中一个当标题。
                        termLabel(entry.key, items.map { it.term }.distinct().singleOrNull().orEmpty()),
                        groupGpa?.let { "绩点 ${formatGpa(it)}" },
                        "${items.size} 门",
                    ).joinToString(" · "),
                )
                ListGroup(
                    rows = items.map { item ->
                        ListRow(
                            title = item.courseName.ifBlank { item.courseId },
                            summary = listOfNotNull(
                                item.resolvedGpa()?.let { "绩点 ${formatGpa(it)}" },
                                item.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
                                item.assess.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            badge = item.score.ifBlank { "已出分" },
                            tone = scoreTone(item),
                            onClick = item.courseId.takeIf { it.isNotBlank() }?.let { id -> { nav.open(Route.Course(id)) } },
                        )
                    },
                )
            }
        }
        if (snapshot.practices.isNotEmpty()) {
            SmallTitle(text = "实践课 · ${snapshot.practices.size} 门")
            ListGroup(
                rows = snapshot.practices.map { item ->
                    ListRow(
                        title = item.name,
                        summary = listOf(item.weeks, item.teacher, item.note).filter { it.isNotBlank() }.joinToString(" · "),
                    )
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 学期组标题：认得出的统一写成「2025-2026 第1学期」，认不出的原样显示。 */
private fun termLabel(key: Pair<Int, Int>?, raw: String): String =
    if (key != null) "${key.first}-${key.first + 1} 第${key.second}学期" else raw.ifBlank { "其他学期" }

/** 分数着色：90 以上 / 优秀标绿，不及格标红，其余正常色。 */
private fun scoreTone(item: GradeItem): BadgeTone {
    val score = item.numericScore() ?: return BadgeTone.Neutral
    return when {
        item.resolvedGpa() == 0.0 || score < 60.0 -> BadgeTone.Warn
        score >= 90.0 -> BadgeTone.Good
        else -> BadgeTone.Neutral
    }
}

/** 成绩页头卡：绩点放大到一眼能看到，下面是统计口径和本学期学分进度。 */
@Composable
private fun GpaHero(
    value: String,
    summary: String,
    progress: Float?,
    progressLabel: String,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        pressFeedbackType = if (onClick != null) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = onClick,
    ) {
        Text("学分绩点", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        Text(
            value,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
        )
        Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        if (progress != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
            if (progressLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(progressLabel, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
            }
        }
    }
}
