package cn.edu.gzus.qingke.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.CloudScheduleAdjust
import cn.edu.gzus.qingke.data.CourseDetail
import cn.edu.gzus.qingke.data.GradeItem
import cn.edu.gzus.qingke.data.LessonSlot
import cn.edu.gzus.qingke.data.PeriodBlock
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.clockRangeIn
import cn.edu.gzus.qingke.data.formatGpa
import cn.edu.gzus.qingke.data.numericScore
import cn.edu.gzus.qingke.data.periodText
import cn.edu.gzus.qingke.data.resolvedGpa
import cn.edu.gzus.qingke.data.shiftNoteFor
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.FactGrid
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import kotlinx.datetime.LocalDate
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CourseDetailScreen(
    detail: CourseDetail?,
    contentPadding: PaddingValues,
    hasClock: Boolean = false,
    settings: AppSettings = AppSettings(),
    blocks: List<PeriodBlock> = emptyList(),
    today: LocalDate? = null,
    adjust: CloudScheduleAdjust = CloudScheduleAdjust(),
    /** 这门课最好的那次成绩；从成绩页点进来时也要能看到分数。 */
    grade: GradeItem? = null,
    /** 找不到课程时空状态上的「返回」；不传就不给按钮。 */
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = Space.xl),
    ) {
        if (detail == null) {
            Spacer(Modifier.height(Space.l))
            EmptyState(
                title = "没有这门课",
                detail = "课表和成绩里都找不到这个课程号，重新同步后再试",
                actionText = "返回",
                onAction = onBack,
            )
            return
        }
        // 课表里所有时段的老师 / 校区去重后拼起来，详情头和下面每行都要用。
        val teachers = detail.teacher.ifBlank {
            detail.slots.map { it.teacher }.filter { it.isNotBlank() }.distinct().joinToString("、")
        }
        val campuses = detail.campus.ifBlank {
            detail.slots.map { it.campus }.filter { it.isNotBlank() }.distinct().joinToString("、")
        }
        // 按星期、节次排好：教务给的顺序常常是乱的。调课说明先算出来，头部要标「有调课」。
        val slots = detail.slots
            .sortedWith(
                compareBy<LessonSlot>({ it.weekday }, { it.period.substringBefore("-").trim().toIntOrNull() ?: Int.MAX_VALUE }),
            )
            .map { slot -> slot.toLine(teachers, campuses, hasClock, blocks, today?.let { settings.shiftNoteFor(slot, it, adjust) }.orEmpty()) }
        val shifts = slots.filter { it.note.isNotBlank() }.distinctBy { it.title to it.note }

        Spacer(Modifier.height(Space.m))
        CourseHeader(
            name = detail.courseName.ifBlank { detail.courseId.ifBlank { "未命名课程" } },
            courseId = detail.courseId,
            teachers = teachers,
            kind = detail.required.ifBlank { detail.category },
            hasShift = shifts.isNotEmpty(),
            grade = grade,
        )
        Spacer(Modifier.height(Space.l))
        // 课程名、课程号、教师、属性、成绩已在头部，这里只放需要查的事实，空值不占格子。
        FactGrid(
            fields = listOf(
                "学分" to detail.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" }.orEmpty(),
                "考核" to detail.assess,
                "学时" to detail.hours,
                "校区" to campuses,
                "教学班" to detail.className,
            ),
            modifier = Modifier.padding(horizontal = Inset.page),
        )

        if (slots.isEmpty()) {
            SmallTitle(text = "上课安排")
            // 只从成绩里来的课：分数已在头部，这里只说明为什么没有时段。
            val fromGrades = grade != null || detail.highlight.startsWith("成绩") || detail.highlight.startsWith("绩点")
            EmptyState(
                title = if (fromGrades) "不在本学期课表" else "没有上课安排",
                detail = if (fromGrades) {
                    listOfNotNull(
                        grade?.term?.takeIf { it.isNotBlank() }?.let { "成绩来自 $it" },
                        "这门课只同步到了成绩，没有上课时段",
                    ).joinToString("，")
                } else {
                    "课表里找不到这门课的时段，可以回课表页重新同步"
                },
            )
        } else {
            SmallTitle(text = "上课安排 · ${slots.size} 个时段")
            // 每个时段一行：星期节次作标题，时间地点第一行，周次和不同于课程的老师 / 校区第二行。
            ListGroup(
                rows = slots.map { line ->
                    ListRow(
                        title = line.title,
                        summary = line.summary,
                        badge = if (line.note.isNotBlank()) "调课" else "",
                        tone = BadgeTone.Warn,
                    )
                },
            )
            if (shifts.isNotEmpty()) {
                SmallTitle(text = "调课 · ${shifts.size} 处")
                ListGroup(rows = shifts.map { ListRow(title = it.title, summary = it.note) })
            }
        }
    }
}

/** 一个上课时段排好版后的文字；调课说明为空表示这周没变动。 */
private class SlotLine(val title: String, val summary: String, val note: String)

private fun LessonSlot.toLine(
    teachers: String,
    campuses: String,
    hasClock: Boolean,
    blocks: List<PeriodBlock>,
    note: String,
): SlotLine {
    val weekday = weekdayName.ifBlank { "周${WeekdayNames.getOrElse(this.weekday - 1) { "?" }}" }
    val clock = if (hasClock) clockRangeIn(period, blocks) else ""
    // 教室名里常已带楼栋（「教学楼A101」），带了就不再重复拼。
    val place = when {
        room.isBlank() -> building
        building.isBlank() || building in room -> room
        else -> "$building $room"
    }.ifBlank { "教室待定" }
    val line1 = listOf(clock, place).filter { it.isNotBlank() }.joinToString(" · ")
    val line2 = listOf(
        weeks.takeIf { it.isNotBlank() }?.let { if ("周" in it) it else "${it}周" }.orEmpty(),
        teacher.takeIf { it.isNotBlank() && it != teachers }.orEmpty(),
        campus.takeIf { it.isNotBlank() && it != campuses }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return SlotLine(
        title = "$weekday ${periodText(period, periodLabel)}",
        summary = listOf(line1, line2).filter { it.isNotBlank() }.joinToString("\n"),
        note = note,
    )
}

/**
 * 详情页头：课程名就是页面主标题（顶栏只写「课程详情」），
 * 右侧放成绩，下面一行是课程号 / 教师和属性、调课标签，关键事实不用往下翻。
 */
@Composable
private fun CourseHeader(
    name: String,
    courseId: String,
    teachers: String,
    kind: String,
    hasShift: Boolean,
    grade: GradeItem?,
) {
    val muted = Tone.muted()
    val score = grade?.score?.takeIf { it.isNotBlank() }
    val gpa = grade?.resolvedGpa()?.let { "绩点 ${formatGpa(it)}" }
    Column(Modifier.fillMaxWidth().padding(horizontal = Inset.page)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (score != null || gpa != null) {
                Spacer(Modifier.width(Space.m))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        score ?: gpa.orEmpty(),
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.SemiBold,
                        color = grade?.let { scoreColor(it) } ?: MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                    )
                    if (score != null && gpa != null) {
                        Text(gpa, style = MiuixTheme.textStyles.footnote1, color = muted, maxLines = 1)
                    }
                }
            }
        }
        val sub = listOf(courseId, teachers).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(sub, style = MiuixTheme.textStyles.body2, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (kind.isNotBlank() || hasShift) {
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (kind.isNotBlank()) StatusTag(kind, color = muted)
                if (hasShift) StatusTag("有调课", color = Tone.warning())
            }
        }
    }
}

/** 分数着色和成绩页一致：90 以上 / 优秀标绿，不及格标红，其余正常色。 */
@Composable
private fun scoreColor(grade: GradeItem): Color {
    val score = grade.numericScore() ?: return MiuixTheme.colorScheme.onBackground
    return when {
        grade.resolvedGpa() == 0.0 || score < 60.0 -> Tone.danger()
        score >= 90.0 -> Tone.success()
        else -> MiuixTheme.colorScheme.onBackground
    }
}
