package cn.edu.gzus.qingke.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.GradeItem
import cn.edu.gzus.qingke.data.currentTermKey
import cn.edu.gzus.qingke.data.formatGpa
import cn.edu.gzus.qingke.data.formatScore
import cn.edu.gzus.qingke.data.gradeTermKey
import cn.edu.gzus.qingke.data.inTerm
import cn.edu.gzus.qingke.data.numericScore
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.resolvedGpa
import cn.edu.gzus.qingke.data.summarizeGpa
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import cn.edu.gzus.qingke.ui.components.Radius
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatRow
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import cn.edu.gzus.qingke.ui.components.Type
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

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
    val loggedIn = snapshot.session.loggedIn
    val goLogin: () -> Unit = { nav.goTab(TabDest.Mine) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(Space.l))
        ScreenHeader(
            "",
            "成绩",
            when {
                snapshot.grades.isNotEmpty() -> "绩点按学分加权"
                loggedIn -> "出分后自动同步"
                else -> "登录后同步"
            },
        )
        Spacer(Modifier.height(Space.m))
        GpaHero(
            value = gpa.weighted?.let { formatGpa(it) },
            summary = when {
                gpa.counted > 0 -> "已计 ${gpa.counted} 门 · ${formatScore(gpa.credits)} 学分"
                planned > 0.0 -> "本学期 ${formatScore(planned)} 学分，出分后统计"
                loggedIn -> "${jwxt}还没有成绩"
                else -> "登录${jwxt}后同步成绩"
            },
            progress = progress,
            progressLabel = if (planned > 0.0) {
                "本学期已出分 ${formatScore(termGpa.credits)}/${formatScore(planned)} 学分"
            } else {
                ""
            },
            actionText = if (!loggedIn) "去登录" else "",
            onClick = if (!loggedIn) goLogin else null,
        )
        Spacer(Modifier.height(Space.s))
        StatRow(
            items = listOf(
                (termGpa.weighted?.let { formatGpa(it) } ?: "—") to "本学期绩点",
                (gpa.averageScore?.let { formatScore(it) } ?: "—") to "算术均分",
                scored.size.toString() to "已出成绩",
                formatScore(gpa.credits) to "已计学分",
            ),
            onClicks = emptyList(),
        )
        if (courses.isEmpty()) {
            SmallTitle(text = "本学期")
            EmptyState(
                title = "本学期还没有课程",
                detail = if (loggedIn) "同步课表后，这学期的课和出分进度会列在这里" else "登录${jwxt}后同步课表和成绩",
                actionText = if (loggedIn) "去课表" else "去登录",
                onAction = if (loggedIn) {
                    { nav.goTab(TabDest.Timetable) }
                } else {
                    goLogin
                },
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
            val out = termRows.count { (_, grade) -> grade?.score?.isNotBlank() == true }
            SmallTitle(text = "本学期 · 已出分 $out/${termRows.size} 门")
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
            // 没登录且上面已经有「去登录」时，这里不再重复放同一个按钮。
            val showLogin = !loggedIn && courses.isNotEmpty()
            EmptyState(
                title = "还没有成绩",
                detail = if (loggedIn) "${jwxt}出分后按学期列出分数和绩点" else "登录后按学期列出分数和绩点",
                actionText = if (showLogin) "去登录" else "",
                onAction = if (showLogin) goLogin else null,
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
                            title = item.courseName.ifBlank { item.courseId.ifBlank { "未命名课程" } },
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
                        title = item.name.ifBlank { "未命名实践" },
                        summary = listOf(item.weeks, item.teacher, item.note).filter { it.isNotBlank() }.joinToString(" · "),
                    )
                },
            )
        }
        Spacer(Modifier.height(Space.xl))
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

/** 绩点进度条的粗细：比列表分割线醒目，又不抢大数字。 */
private val ProgressHeight = 6.dp

/** 成绩页头卡：绩点放大到一眼能看到，下面是统计口径和本学期学分进度。没登录时右上角给「去登录」。 */
@Composable
private fun GpaHero(
    value: String?,
    summary: String,
    progress: Float?,
    progressLabel: String,
    actionText: String,
    onClick: (() -> Unit)?,
) {
    val muted = Tone.muted()
    val primary = MiuixTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = Inset.card,
        pressFeedbackType = if (onClick != null) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("学分绩点", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.footnote1, color = muted)
            if (actionText.isNotBlank()) StatusTag(actionText)
        }
        Spacer(Modifier.height(Space.xs))
        // 还没有绩点时用弱色破折号，别拿主题色去强调一个空值。
        Text(
            value ?: "—",
            fontSize = Type.display,
            lineHeight = Type.displayLine,
            fontWeight = FontWeight.SemiBold,
            color = if (value != null) primary else muted,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.xs))
        Text(summary, style = MiuixTheme.textStyles.body2, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (progress != null) {
            Spacer(Modifier.height(Space.m))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .clip(Radius.pill)
                    .background(primary.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(Radius.pill)
                        .background(primary),
                )
            }
            if (progressLabel.isNotBlank()) {
                Spacer(Modifier.height(Space.s))
                Text(
                    progressLabel,
                    style = MiuixTheme.textStyles.footnote1,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
