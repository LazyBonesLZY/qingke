package cn.edu.gzus.qingke.ui.detail

import cn.edu.gzus.qingke.data.periodText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.CloudScheduleAdjust
import cn.edu.gzus.qingke.data.CourseDetail
import cn.edu.gzus.qingke.data.GradeItem
import cn.edu.gzus.qingke.data.LessonSlot
import cn.edu.gzus.qingke.data.formatGpa
import cn.edu.gzus.qingke.data.resolvedGpa
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.clockRangeIn
import cn.edu.gzus.qingke.data.PeriodBlock
import cn.edu.gzus.qingke.data.shiftNoteFor
import kotlinx.datetime.LocalDate
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.FactGrid
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import top.yukonga.miuix.kmp.basic.SmallTitle

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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        if (detail == null) {
            Spacer(Modifier.height(16.dp))
            InfoCard("没有这门课", "课表和成绩里都找不到这个课程号，重新同步后再试。", modifier = Modifier.padding(horizontal = 16.dp), height = null)
            return
        }
        Spacer(Modifier.height(12.dp))
        SmallTitle(text = "课程信息")
        // 课表里所有时段的老师 / 校区去重后拼起来，详情头和下面每行都要用。
        val teachers = detail.teacher.ifBlank {
            detail.slots.map { it.teacher }.filter { it.isNotBlank() }.distinct().joinToString("、")
        }
        val campuses = detail.campus.ifBlank {
            detail.slots.map { it.campus }.filter { it.isNotBlank() }.distinct().joinToString("、")
        }
        FactGrid(
            fields = listOf(
                "课程" to detail.courseName,
                "教师" to teachers,
                "成绩" to grade?.let { g ->
                    listOfNotNull(
                        g.score.takeIf { it.isNotBlank() },
                        g.resolvedGpa()?.let { "绩点 ${formatGpa(it)}" },
                    ).joinToString(" · ")
                }.orEmpty(),
                "课程号" to detail.courseId,
                "学分" to detail.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" }.orEmpty(),
                "考核" to detail.assess,
                "属性" to detail.required.ifBlank { detail.category },
                "学时" to detail.hours,
                "校区" to campuses,
                "教学班" to detail.className,
            ),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (detail.slots.isEmpty()) {
            SmallTitle(text = "上课安排")
            // 只从成绩里来的课：分数已在上面「成绩」格里，这里只说明为什么没有时段。
            val fromGrades = grade != null || detail.highlight.startsWith("成绩") || detail.highlight.startsWith("绩点")
            InfoCard(
                title = if (fromGrades) "不在本学期课表" else "没有上课安排",
                summary = if (fromGrades) {
                    listOfNotNull(
                        grade?.term?.takeIf { it.isNotBlank() }?.let { "成绩来自 $it" },
                        "这门课只同步到了成绩，没有上课时段。",
                    ).joinToString("\n")
                } else {
                    "课表里找不到这门课的时段，可以回课表页重新同步。"
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                height = null,
            )
        } else {
            // 按星期、节次排好：教务给的顺序常常是乱的。
            val slots = detail.slots.sortedWith(
                compareBy<LessonSlot>({ it.weekday }, { it.period.substringBefore("-").trim().toIntOrNull() ?: Int.MAX_VALUE }),
            )
            SmallTitle(text = "上课安排 · ${slots.size} 个时段")
            // 以前每个时段一张大卡、七行字段，三四个时段就要翻好几屏；改成一组列表，
            // 老师 / 校区和课程信息一样时不再重复写。
            val shiftNotes = mutableListOf<String>()
            ListGroup(
                rows = slots.map { slot ->
                    val weekday = slot.weekdayName.ifBlank {
                        "周${WeekdayNames.getOrElse(slot.weekday - 1) { "?" }}"
                    }
                    val clock = if (hasClock) clockRangeIn(slot.period, blocks) else ""
                    val note = today?.let { settings.shiftNoteFor(slot, it, adjust) }.orEmpty()
                    if (note.isNotBlank()) shiftNotes += "$weekday ${periodText(slot.period, slot.periodLabel)}：$note"
                    // 教室名里常已带楼栋（「教学楼A101」），带了就不再重复拼。
                    val place = when {
                        slot.room.isBlank() -> slot.building
                        slot.building.isBlank() || slot.building in slot.room -> slot.room
                        else -> "${slot.building} ${slot.room}"
                    }.ifBlank { "教室待定" }
                    val line1 = listOf(clock, place).filter { it.isNotBlank() }.joinToString(" · ")
                    val line2 = listOf(
                        slot.weeks.takeIf { it.isNotBlank() }?.let { if ("周" in it) it else "${it}周" }.orEmpty(),
                        slot.teacher.takeIf { it.isNotBlank() && it != teachers }.orEmpty(),
                        slot.campus.takeIf { it.isNotBlank() && it != campuses }.orEmpty(),
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    ListRow(
                        title = "$weekday  ${periodText(slot.period, slot.periodLabel)}",
                        summary = listOf(line1, line2).filter { it.isNotBlank() }.joinToString("\n"),
                        badge = if (note.isNotBlank()) "有调课" else "",
                        tone = BadgeTone.Warn,
                    )
                },
            )
            if (shiftNotes.isNotEmpty()) {
                SmallTitle(text = "调课")
                InfoCard(
                    title = "这门课有调课",
                    summary = shiftNotes.distinct().joinToString("\n"),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    height = null,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
