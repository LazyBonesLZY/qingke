package cn.edu.gzus.qingke.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.ExamItem
import cn.edu.gzus.qingke.data.FreeRoom
import cn.edu.gzus.qingke.data.NoticeItem
import cn.edu.gzus.qingke.data.NoticePart
import cn.edu.gzus.qingke.data.MajorPeriods
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.examSortKey
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import kotlinx.datetime.LocalTime
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 空教室结果一次最多画这么多行；整列在滚动 Column 里一次性组合，太多会卡。 */
private const val RoomLimit = 200

@Composable
fun EmptyRoomScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    busy: Boolean,
    rooms: List<FreeRoom>,
    error: String?,
    onQuery: (weekday: String, start: String, end: String) -> Unit,
    /** 未登录时空状态卡点了去登录；不传就只显示提示。 */
    onLogin: (() -> Unit)? = null,
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
    val todayIndex = (weekdayIndex(now.date) - 1).coerceIn(0, 6)
    var dayIndex by remember { mutableIntStateOf(todayIndex) }
    var periodIndex by remember {
        val idx = MajorPeriods.indexOfFirst { block ->
            runCatching { LocalTime.parse(block.end) }.getOrNull()?.let { now.time < it } == true
        }.takeIf { it >= 0 } ?: 0
        mutableIntStateOf(idx)
    }
    val period = MajorPeriods[periodIndex.coerceIn(0, MajorPeriods.lastIndex)]
    var queried by remember(dayIndex, periodIndex) { mutableStateOf(false) }
    // 同楼栋的排在一起、房号按自然顺序，找起来比教务原顺序快。
    val displayedRooms = remember(queried, rooms) {
        if (!queried) emptyList() else rooms.sortedWith(compareBy<FreeRoom>({ it.building }, { it.name.length }, { it.name }))
    }
    val queryError = if (queried) error else null
    val loggedIn = snapshot.session.loggedIn
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "选星期和大节，查那个时段空着的教室。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        TabRow(
            tabs = WeekdayNames,
            selectedTabIndex = dayIndex,
            onTabSelected = { if (!busy) dayIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SmallTitle(text = "大节")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(vertical = 4.dp),
        ) {
            val divider = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
            MajorPeriods.forEachIndexed { index, item ->
                val selected = index == periodIndex
                // 查今天时，已经下课的大节淡一点，但仍可选（比如想看刚才那节）。
                val over = dayIndex == todayIndex &&
                    runCatching { LocalTime.parse(item.end) }.getOrNull()?.let { now.time >= it } == true
                if (index > 0) {
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(divider))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { periodIndex = index }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "第${item.label}节",
                            style = MiuixTheme.textStyles.main,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                selected -> MiuixTheme.colorScheme.primary
                                over -> MiuixTheme.colorScheme.onSurfaceContainerVariant
                                else -> MiuixTheme.colorScheme.onBackground
                            },
                        )
                        Text(
                            listOfNotNull("${item.start}-${item.end}", item.band, if (over) "已下课" else null).joinToString(" · "),
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                    }
                    if (selected) {
                        Text("✓", style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.primary)
                    }
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
            enabled = !busy && loggedIn,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minHeight = 50.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (busy) {
                InfiniteProgressIndicator()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when {
                    !loggedIn -> "登录后查询"
                    busy -> "查询中"
                    else -> "查询 周${WeekdayNames[dayIndex]} 第${period.label}节"
                },
            )
        }
        if (!queryError.isNullOrBlank()) {
            Text(queryError, modifier = Modifier.padding(16.dp), color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1)
        }
        SmallTitle(text = if (displayedRooms.isEmpty()) "结果" else "找到 ${displayedRooms.size} 间")
        if (displayedRooms.isEmpty()) {
            when {
                !loggedIn -> InfoCard(
                    title = "还没登录",
                    summary = if (onLogin != null) "点这里去「我的」登录${school.jwxtName}" else "去「我的」登录${school.jwxtName}后再查",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = onLogin,
                )
                queried && busy -> EmptyHint("正在查询…")
                queried && queryError.isNullOrBlank() -> EmptyHint("这个时段没有空教室，换个大节试试。")
                queried -> EmptyHint("查询失败，可以再点一次查询。")
                else -> EmptyHint("选好星期和大节后点查询。")
            }
        } else {
            ListGroup(
                rows = displayedRooms.take(RoomLimit).map { room ->
                    ListRow(
                        title = room.name.ifBlank { "未命名教室" },
                        summary = listOf(room.building, room.campus, room.type)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" · "),
                        badge = room.capacity.takeIf { it.isNotBlank() }?.let { "${it}座" }.orEmpty(),
                        tone = BadgeTone.Muted,
                    )
                },
            )
            if (displayedRooms.size > RoomLimit) {
                Text(
                    "只列出前 $RoomLimit 间，共 ${displayedRooms.size} 间。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}

/** 通知日期 → 可比较的天数；认不出返回 null。 */
private val NoticeDate = Regex("""(\d{4})[-年./](\d{1,2})[-月./](\d{1,2})""")

private fun noticeDateKey(date: String): Int? {
    val m = NoticeDate.find(date) ?: return null
    val y = m.groupValues[1].toIntOrNull() ?: return null
    val mo = m.groupValues[2].toIntOrNull() ?: return null
    val d = m.groupValues[3].toIntOrNull() ?: return null
    return y * 10000 + mo * 100 + d
}

/**
 * 置顶在前，其余按日期新到旧。日期认不出的不参与重排：
 * 全部认不出时保持教务原顺序（sortedWith 是稳定排序）。
 */
private fun sortNotices(notices: List<NoticeItem>): List<NoticeItem> {
    val anyDated = notices.any { noticeDateKey(it.date) != null }
    return notices.sortedWith(
        compareBy<NoticeItem> { if (it.pinned) 0 else 1 }
            .thenByDescending { if (anyDated) noticeDateKey(it.date) ?: -1 else 0 },
    )
}

@Composable
fun NoticesScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    bodyLoading: Set<String> = emptySet(),
    onOpen: (String) -> Unit,
    /** 未登录时空状态卡点了去登录；不传就只显示提示。 */
    onLogin: (() -> Unit)? = null,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expandedId) {
        val id = expandedId ?: return@LaunchedEffect
        onOpen(id)
    }
    val notices = remember(snapshot.notices) { sortNotices(snapshot.notices) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        if (notices.isEmpty()) {
            if (snapshot.session.loggedIn) {
                EmptyHint("暂时没有通知，下拉同步或稍后再来看看。")
            } else {
                InfoCard(
                    title = "还没登录",
                    summary = if (onLogin != null) "点这里去「我的」登录后同步通知" else "去「我的」登录后同步通知",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = onLogin,
                )
            }
        } else {
            val fresh = notices.count { it.isNew }
            Text(
                listOfNotNull("共 ${notices.size} 条", if (fresh > 0) "$fresh 条新通知" else null, "点开看正文").joinToString(" · "),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                notices.forEach { notice ->
                    val open = expandedId == notice.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        onClick = { expandedId = if (open) null else notice.id },
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                notice.title.ifBlank { "（无标题）" },
                                modifier = Modifier.weight(1f),
                                style = MiuixTheme.textStyles.title3,
                                color = if (open) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                                // 收起时最多两行，长标题不再把列表撑得很长；展开后给全。
                                maxLines = if (open) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (notice.pinned) NoticeTag("置顶", MiuixTheme.colorScheme.primary)
                            if (notice.isNew) NoticeTag("新", Color(0xFFF2544B))
                        }
                        val meta = listOf(
                            notice.date,
                            notice.category.ifBlank { "通知" },
                            notice.publisher,
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                meta,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                maxLines = if (open) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (open) {
                            Spacer(Modifier.height(10.dp))
                            val parts = notice.bodyParts()
                            val loading = notice.id in bodyLoading
                            if (parts.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // 没拿到正文时直接点这一行重试，不用先收起再点开。
                                        .then(
                                            if (!loading && !notice.id.startsWith("msg:")) {
                                                Modifier.clickable { onOpen(notice.id) }
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (loading) InfiniteProgressIndicator()
                                    Text(
                                        when {
                                            loading -> "正在加载正文"
                                            // 站内消息只有标题，仓库层不会去拉正文。
                                            notice.id.startsWith("msg:") -> "这条消息只有标题，没有正文。"
                                            else -> "没有拿到正文，点这里重试"
                                        },
                                        style = MiuixTheme.textStyles.body2,
                                        color = if (loading || notice.id.startsWith("msg:")) {
                                            MiuixTheme.colorScheme.onSurfaceContainerVariant
                                        } else {
                                            MiuixTheme.colorScheme.primary
                                        },
                                    )
                                }
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

/** 通知标题右侧的小标签：置顶 / 新。 */
@Composable
private fun NoticeTag(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .padding(start = 8.dp, top = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = MiuixTheme.textStyles.footnote1,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
    )
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
    Row(Modifier.fillMaxWidth()) {
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

/** 一场考试和距今天数；日期解析不出来时 days 为 null。 */
private data class ExamEntry(val exam: ExamItem, val days: Int?)

private fun examRow(entry: ExamEntry): ListRow {
    val exam = entry.exam
    val days = entry.days
    val seat = exam.seat.trim().let { if (it.isNotBlank() && it.all(Char::isDigit)) "${it}号座" else it }
    val where = listOf(exam.room.ifBlank { "考场待定" }, seat).filter { it.isNotBlank() }.joinToString(" · ")
    return ListRow(
        title = exam.courseName.ifBlank { "未命名考试" },
        summary = listOf(
            exam.time.ifBlank { "时间待定" },
            where,
            // 日期认不出时状态（未安排 / 已安排）是唯一线索，留着。
            if (days == null) exam.status else "",
        ).filter { it.isNotBlank() }.joinToString("\n"),
        badge = when {
            days == null -> "待定"
            days < 0 -> "已结束"
            days == 0 -> "今天"
            days == 1 -> "明天"
            else -> "$days 天后"
        },
        tone = when {
            days == null || days < 0 -> BadgeTone.Muted
            days <= 3 -> BadgeTone.Warn
            else -> BadgeTone.Accent
        },
    )
}

@Composable
fun ExamsScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    /** 未登录时空状态卡点了去登录；不传就只显示提示。 */
    onLogin: (() -> Unit)? = null,
) {
    val today = nowDateTime().date
    // 分三组：待考（近的在前）/ 时间待定 / 已结束（刚考完的在前）。
    // 以前全部按日期正序，学期末一打开先看到一屏考完的。
    val groups = remember(snapshot.exams, today) {
        val todayKey = today.toEpochDays().toLong()
        val entries = snapshot.exams.map { exam ->
            val key = examSortKey(exam.time)
            ExamEntry(exam, if (key == Long.MAX_VALUE) null else (key - todayKey).toInt())
        }
        Triple(
            entries.filter { it.days != null && it.days >= 0 }.sortedBy { examSortKey(it.exam.time) },
            entries.filter { it.days == null },
            entries.filter { it.days != null && it.days < 0 }.sortedByDescending { examSortKey(it.exam.time) },
        )
    }
    val (upcoming, undated, past) = groups
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        if (snapshot.exams.isEmpty()) {
            if (snapshot.session.loggedIn) {
                EmptyHint("暂无考试安排，教务排考后会同步到这里。")
            } else {
                InfoCard(
                    title = "还没登录",
                    summary = if (onLogin != null) "点这里去「我的」登录后同步考试" else "去「我的」登录后同步考试",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = onLogin,
                )
            }
            return@Column
        }
        Text(
            when {
                upcoming.isNotEmpty() -> {
                    val next = upcoming.first()
                    val gap = when (next.days) {
                        0 -> "今天就考"
                        1 -> "明天考"
                        else -> "${next.days} 天后"
                    }
                    "还有 ${upcoming.size} 场待考，最近一场「${next.exam.courseName}」$gap。"
                }
                undated.isNotEmpty() -> "有 ${undated.size} 场考试时间还没定。"
                else -> "本学期的考试都考完了。"
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        if (upcoming.isNotEmpty()) {
            SmallTitle(text = "待考 · ${upcoming.size} 场")
            ListGroup(rows = upcoming.map(::examRow))
        }
        if (undated.isNotEmpty()) {
            SmallTitle(text = "时间待定 · ${undated.size} 场")
            ListGroup(rows = undated.map(::examRow))
        }
        if (past.isNotEmpty()) {
            SmallTitle(text = "已结束 · ${past.size} 场")
            ListGroup(rows = past.map(::examRow))
        }
    }
}
