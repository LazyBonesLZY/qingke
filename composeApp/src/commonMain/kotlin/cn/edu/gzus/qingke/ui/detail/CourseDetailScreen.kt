package cn.edu.gzus.qingke.ui.detail

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.CourseDetail
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.ui.components.FactGrid
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.LabeledCard
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun CourseDetailScreen(
    detail: CourseDetail?,
    contentPadding: PaddingValues,
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
            InfoCard("没有这门课", "课表里找不到对应课程号", modifier = Modifier.padding(horizontal = 16.dp))
            return
        }
        Spacer(Modifier.height(12.dp))
        SmallTitle(text = "课程信息")
        FactGrid(
            fields = listOf(
                "课程" to detail.courseName,
                "教师" to detail.teacher.ifBlank {
                    detail.slots.map { it.teacher }.filter { it.isNotBlank() }.distinct().joinToString("、")
                },
                "课程号" to detail.courseId,
                "学分" to detail.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" }.orEmpty(),
                "考核" to detail.assess,
                "属性" to detail.required.ifBlank { detail.category },
                "学时" to detail.hours,
                "校区" to detail.campus.ifBlank {
                    detail.slots.map { it.campus }.filter { it.isNotBlank() }.distinct().joinToString("、")
                },
                "教学班" to detail.className,
            ),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SmallTitle(text = "上课安排")
        if (detail.slots.isEmpty()) {
            InfoCard(
                title = if (detail.highlight.isNotBlank()) detail.highlight else "没有上课安排",
                summary = if (detail.highlight.isNotBlank()) "这门课不在本学期课表里，只同步到了成绩。" else "课表里找不到对应课程号",
                modifier = Modifier.padding(horizontal = 16.dp),
                height = null,
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.slots.forEach { slot ->
                    val weekday = slot.weekdayName.ifBlank {
                        "周${WeekdayNames.getOrElse(slot.weekday - 1) { "?" }}"
                    }
                    LabeledCard(
                        title = "$weekday  ${slot.periodLabel.ifBlank { slot.period }}",
                        rows = listOf(
                            "教室" to slot.room,
                            "教师" to slot.teacher,
                            "周次" to slot.weeks,
                            "楼栋" to slot.building,
                            "校区" to slot.campus,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
