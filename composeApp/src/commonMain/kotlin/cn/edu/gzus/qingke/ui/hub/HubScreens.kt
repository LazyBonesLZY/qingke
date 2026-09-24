package cn.edu.gzus.qingke.ui.hub

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.FreeRoom
import cn.edu.gzus.qingke.data.NoticePart
import cn.edu.gzus.qingke.data.MajorPeriods
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.examSortKey
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.InfoCard
import kotlinx.datetime.LocalTime
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EmptyRoomScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    busy: Boolean,
    rooms: List<FreeRoom>,
    error: String?,
    onQuery: (weekday: String, start: String, end: String) -> Unit,
) {
    val school = snapshot.resolved()
    if (!school.supportsFreeRooms) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${school.jwxtName}的教务没有空教室查询。",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                style = MiuixTheme.textStyles.body2,
            )
            Spacer(Modifier.height(12.dp))
            EmptyHint("换回广软才能查空教室。")
        }
        return
    }
    val now = nowDateTime()
    var dayIndex by remember { mutableIntStateOf((weekdayIndex(now.date) - 1).coerceIn(0, 6)) }
    var periodIndex by remember {
        val idx = MajorPeriods.indexOfFirst { block ->
            runCatching { LocalTime.parse(block.end) }.getOrNull()?.let { now.time < it } == true
        }.takeIf { it >= 0 } ?: 0
        mutableIntStateOf(idx)
    }
    val period = MajorPeriods[periodIndex]
    var queried by remember(dayIndex, periodIndex) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "选星期和大节，查当下空着的教室。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        TabRow(
            tabs = WeekdayNames,
            selectedTabIndex = dayIndex,
            onTabSelected = { dayIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SmallTitle(text = "大节")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            MajorPeriods.forEachIndexed { index, item ->
                val selected = index == periodIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { periodIndex = index }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "${item.label}  ${item.start}-${item.end}",
                        style = MiuixTheme.textStyles.body2,
                        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                    )
                    Text(
                        item.band,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val start = period.label.substringBefore("-")
                val end = period.label.substringAfter("-")
                queried = true
                onQuery((dayIndex + 1).toString(), start, end)
            },
            enabled = !busy && snapshot.session.loggedIn,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minHeight = 50.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (busy) InfiniteProgressIndicator()
            Text(if (!snapshot.session.loggedIn) "登录后查询" else if (busy) "查询中" else "查询空教室")
        }
        if (!error.isNullOrBlank()) {
            Text(error, modifier = Modifier.padding(16.dp), color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.footnote1)
        }
        SmallTitle(text = if (rooms.isEmpty()) "结果" else "找到 ${rooms.size} 间")
        if (rooms.isEmpty()) {
            EmptyHint(
                when {
                    !snapshot.session.loggedIn -> "未登录时不能查${school.jwxtName}空教室。"
                    queried && !busy && error.isNullOrBlank() -> "这个时段没有空教室。"
                    else -> "还没有结果。选好星期和大节后查询。"
                },
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rooms.take(80).forEach { room ->
                    InfoCard(
                        title = room.name,
                        summary = listOf(room.building, room.campus, room.type, room.capacity.takeIf { it.isNotBlank() }?.let { "${it}座" })
                            .filter { !it.isNullOrBlank() }
                            .joinToString(" · "),
                    )
                }
            }
        }
    }
}

@Composable
fun NoticesScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    bodyLoading: Set<String> = emptySet(),
    onOpen: (String) -> Unit,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expandedId) {
        val id = expandedId ?: return@LaunchedEffect
        onOpen(id)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        if (snapshot.notices.isEmpty()) {
            EmptyHint(if (snapshot.session.loggedIn) "暂时没有通知" else "登录后同步通知")
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.notices.forEach { notice ->
                    val open = expandedId == notice.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        onClick = { expandedId = if (open) null else notice.id },
                    ) {
                        Text(
                            notice.title,
                            style = MiuixTheme.textStyles.title3,
                            color = if (open) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                        )
                        val meta = listOf(
                            notice.date,
                            notice.category.ifBlank { "通知" },
                            if (notice.pinned) "置顶" else null,
                            notice.publisher,
                        ).filter { !it.isNullOrBlank() }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                meta,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            )
                        }
                        if (open) {
                            Spacer(Modifier.height(10.dp))
                            val parts = notice.bodyParts()
                            if (parts.isEmpty()) {
                                Text(
                                    if (notice.id in bodyLoading) "正在加载正文" else "没有拿到正文，收起再点开重试",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                )
                            } else {
                                parts.forEachIndexed { index, part ->
                                    if (index > 0) Spacer(Modifier.height(10.dp))
                                    when (part) {
                                        is NoticePart.Text -> Text(
                                            part.text,
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onBackground,
                                        )
                                        is NoticePart.Table -> NoticeTableView(part)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeTableView(table: NoticePart.Table) {
    val columns = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0, 1)
    val header = if (table.headers.isNotEmpty()) table.headers else null
    val line = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
    val cellWidth = if (columns <= 3) null else 104.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (cellWidth != null) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .border(1.dp, line),
    ) {
        if (header != null) {
            NoticeTableRow(header, columns, cellWidth, header = true, line = line)
        }
        table.rows.forEach { row ->
            NoticeTableRow(row, columns, cellWidth, header = false, line = line)
        }
    }
}

@Composable
private fun NoticeTableRow(
    cells: List<String>,
    columns: Int,
    cellWidth: Dp?,
    header: Boolean,
    line: Color,
) {
    Row(Modifier.fillMaxWidth().border(width = 0.dp, color = Color.Transparent)) {
        repeat(columns) { index ->
            Text(
                text = cells.getOrElse(index) { "" },
                modifier = Modifier
                    .then(if (cellWidth != null) Modifier.widthIn(min = cellWidth) else Modifier.weight(1f))
                    .border(1.dp, line)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun ExamsScreen(snapshot: AppSnapshot, contentPadding: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        if (snapshot.exams.isEmpty()) {
            EmptyHint(if (snapshot.session.loggedIn) "暂无考试安排" else "登录后同步考试")
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.exams.sortedBy { examSortKey(it.time) }.forEach { exam ->
                    InfoCard(
                        title = exam.courseName,
                        summary = listOf(exam.time, exam.room, exam.seat, exam.status).filter { it.isNotBlank() }.joinToString("\n"),
                        height = null,
                    )
                }
            }
        }
    }
}
