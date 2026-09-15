package cn.edu.gzus.qingke.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.FreeRoom
import cn.edu.gzus.qingke.data.MajorPeriods
import cn.edu.gzus.qingke.data.WeekdayNames
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
                "${school.jwxtName}没有空教室接口，青课不会编一份假的查询。",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                style = MiuixTheme.textStyles.body2,
            )
            Spacer(Modifier.height(12.dp))
            EmptyHint("这所学校的教务没有空教室。")
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
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "按星期和大节向${school.jwxtName}查空闲教室。",
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
            EmptyHint(if (snapshot.session.loggedIn) "还没有结果。选好星期和大节后查询。" else "未登录时不能查${school.jwxtName}空教室。")
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
    onOpen: (String) -> Unit,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expandedId) {
        val id = expandedId ?: return@LaunchedEffect
        onOpen(id)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "${snapshot.resolved().jwxtName}通知。点一条展开正文。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        if (snapshot.notices.isEmpty()) {
            EmptyHint(if (snapshot.session.loggedIn) "暂时没有通知" else "登录后同步通知")
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.notices.forEach { notice ->
                    val open = expandedId == notice.id
                    InfoCard(
                        title = notice.title,
                        summary = buildString {
                            val meta = listOf(
                                notice.date,
                                notice.category.ifBlank { "通知" },
                                if (notice.pinned) "置顶" else null,
                                notice.publisher,
                            ).filter { !it.isNullOrBlank() }.joinToString(" · ")
                            append(meta)
                            if (open) {
                                append('\n')
                                append(notice.content.ifBlank { "正在加载正文" })
                            }
                        },
                        height = null,
                        tintTitle = open,
                        onClick = { expandedId = if (open) null else notice.id },
                    )
                }
            }
        }
    }
}

@Composable
fun ExamsScreen(snapshot: AppSnapshot, contentPadding: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "考试安排来自${snapshot.resolved().jwxtName}同步。本学期还没出安排时保持空状态。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        if (snapshot.exams.isEmpty()) {
            EmptyHint(if (snapshot.session.loggedIn) "暂无考试安排" else "登录后同步考试")
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.exams.forEach { exam ->
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
