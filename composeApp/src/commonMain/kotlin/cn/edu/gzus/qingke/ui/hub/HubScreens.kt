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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import cn.edu.gzus.qingke.ui.components.Radius
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
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

/** 二级页统一外壳：可滚动、顶部留 [Space.s]、底部留 [Space.xl]。 */
@Composable
private fun HubColumn(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = Space.xl),
    ) {
        Spacer(Modifier.height(Space.s))
        content()
    }
}

/** 页首一句说明：和卡片左缘对齐，最多两行。 */
@Composable
private fun PageLead(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        style = MiuixTheme.textStyles.body2,
        color = Tone.muted(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 加载中：和 [EmptyState] 同样的卡片尺寸，只是把结论换成转圈 + 一句话，
 * 这样「加载中 → 有结果 / 空 / 失败」切换时页面不跳。
 */
@Composable
private fun LoadingState(text: String, modifier: Modifier = Modifier.padding(horizontal = Inset.page)) {
    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfiniteProgressIndicator()
            Spacer(Modifier.width(Space.s))
            Text(text, style = MiuixTheme.textStyles.body2, color = Tone.muted())
        }
    }
}

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
        HubColumn(contentPadding) {
            EmptyState(
                title = "暂不支持空教室查询",
                detail = "${school.jwxtName}的教务没有空教室接口，换回广软才能查。",
            )
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
    val runQuery = {
        val start = period.label.substringBefore("-")
        val end = period.label.substringAfter("-")
        queried = true
        onQuery((dayIndex + 1).toString(), start, end)
    }
    HubColumn(contentPadding) {
        PageLead("选星期和大节，查那个时段空着的教室。")
        Spacer(Modifier.height(Space.m))
        TabRow(
            tabs = WeekdayNames,
            selectedTabIndex = dayIndex,
            onTabSelected = { if (!busy) dayIndex = it },
            modifier = Modifier.padding(horizontal = Inset.page),
        )
        SmallTitle(text = "大节")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            insideMargin = PaddingValues(vertical = Space.xs),
        ) {
            val divider = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
            MajorPeriods.forEachIndexed { index, item ->
                val selected = index == periodIndex
                // 查今天时，已经下课的大节淡一点，但仍可选（比如想看刚才那节）。
                val over = dayIndex == todayIndex &&
                    runCatching { LocalTime.parse(item.end) }.getOrNull()?.let { now.time >= it } == true
                if (index > 0) {
                    Box(Modifier.padding(horizontal = Space.l).fillMaxWidth().height(0.5.dp).background(divider))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { periodIndex = index }
                        .heightIn(min = Inset.rowMin)
                        .padding(horizontal = Space.l, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "第${item.label}节",
                            style = MiuixTheme.textStyles.main,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = when {
                                selected -> MiuixTheme.colorScheme.primary
                                over -> Tone.muted()
                                else -> MiuixTheme.colorScheme.onBackground
                            },
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOfNotNull("${item.start}-${item.end}", item.band).joinToString(" · "),
                            style = MiuixTheme.textStyles.footnote1,
                            color = Tone.muted(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (over) {
                        StatusTag("已下课", color = Tone.muted(), modifier = Modifier.padding(start = Space.s))
                    }
                    if (selected) {
                        Text(
                            "✓",
                            modifier = Modifier.padding(start = Space.m),
                            style = MiuixTheme.textStyles.main,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        Button(
            onClick = runQuery,
            enabled = !busy && loggedIn,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            minHeight = 50.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (busy) {
                InfiniteProgressIndicator()
                Spacer(Modifier.width(Space.s))
            }
            Text(
                when {
                    !loggedIn -> "登录后查询"
                    busy -> "查询中"
                    else -> "查询 周${WeekdayNames[dayIndex]} 第${period.label}节"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SmallTitle(text = if (displayedRooms.isEmpty()) "结果" else "找到 ${displayedRooms.size} 间")
        if (displayedRooms.isEmpty()) {
            when {
                !loggedIn -> EmptyState(
                    title = "还没登录",
                    detail = "登录${school.jwxtName}后才能查空教室。",
                    actionText = "去登录",
                    onAction = onLogin,
                )
                queried && busy -> LoadingState("正在查询周${WeekdayNames[dayIndex]} 第${period.label}节的空教室")
                queried && !queryError.isNullOrBlank() -> EmptyState(
                    title = "查询失败",
                    detail = queryError.orEmpty(),
                    actionText = "重试",
                    onAction = runQuery,
                )
                queried -> EmptyState(
                    title = "这个时段没有空教室",
                    detail = "换个大节或星期再试试。",
                )
                else -> EmptyState(
                    title = "还没查询",
                    detail = "选好星期和大节后点上方的查询。",
                )
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
                    modifier = Modifier.padding(horizontal = Inset.page, vertical = Space.s),
                    style = MiuixTheme.textStyles.footnote1,
                    color = Tone.muted(),
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
    HubColumn(contentPadding) {
        if (notices.isEmpty()) {
            if (snapshot.session.loggedIn) {
                EmptyState(
                    title = "暂时没有通知",
                    detail = "下拉同步或稍后再来看看。",
                )
            } else {
                EmptyState(
                    title = "还没登录",
                    detail = "登录后同步教务通知。",
                    actionText = "去登录",
                    onAction = onLogin,
                )
            }
            return@HubColumn
        }
        val fresh = notices.count { it.isNew }
        PageLead(listOfNotNull("共 ${notices.size} 条", if (fresh > 0) "$fresh 条新通知" else null, "点开看正文").joinToString(" · "))
        Spacer(Modifier.height(Space.m))
        Column(
            modifier = Modifier.padding(horizontal = Inset.page),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            notices.forEach { notice ->
                NoticeCard(
                    notice = notice,
                    open = expandedId == notice.id,
                    loading = notice.id in bodyLoading,
                    onToggle = { expandedId = if (expandedId == notice.id) null else notice.id },
                    onRetry = { onOpen(notice.id) },
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: NoticeItem,
    open: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
) {
    // 站内消息只有标题，仓库层不会去拉正文。
    val titleOnly = notice.id.startsWith("msg:")
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = Inset.card,
        onClick = onToggle,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                notice.title.ifBlank { "（无标题）" },
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = if (open) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                // 收起时最多两行，长标题不再把列表撑得很长；展开后给全。
                maxLines = if (open) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (notice.pinned || notice.isNew) {
                Row(
                    modifier = Modifier.padding(start = Space.s, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    if (notice.pinned) StatusTag("置顶")
                    if (notice.isNew) StatusTag("新", color = Tone.danger())
                }
            }
        }
        val meta = listOf(
            notice.date,
            notice.category.ifBlank { "通知" },
            notice.publisher,
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                meta,
                style = MiuixTheme.textStyles.footnote1,
                color = Tone.muted(),
                maxLines = if (open) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!open) return@Card
        Spacer(Modifier.height(Space.m))
        val parts = notice.bodyParts()
        if (parts.isEmpty()) {
            NoticeBodyStatus(loading = loading, titleOnly = titleOnly, onRetry = onRetry)
        } else {
            parts.forEachIndexed { index, part ->
                if (index > 0) Spacer(Modifier.height(Space.m))
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

/** 正文还没到时的三种状态：加载中 / 只有标题 / 拿不到可重试。 */
@Composable
private fun NoticeBodyStatus(loading: Boolean, titleOnly: Boolean, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> {
                InfiniteProgressIndicator()
                Spacer(Modifier.width(Space.s))
                Text("正在加载正文", style = MiuixTheme.textStyles.body2, color = Tone.muted())
            }
            titleOnly -> Text("这条消息只有标题，没有正文。", style = MiuixTheme.textStyles.body2, color = Tone.muted())
            else -> {
                Text(
                    "没有拿到正文",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                    color = Tone.muted(),
                )
                // 没拿到正文时直接点这里重试，不用先收起再点开。
                Text(
                    "重试",
                    modifier = Modifier
                        .clip(Radius.pill)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = Space.m, vertical = Space.xs),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                )
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
    // 外框圆角裁切放在横向滚动之外，滚动时边框不跟着跑。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.small)
            .border(0.5.dp, line, Radius.small)
            .then(if (cellWidth != null) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
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
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(line.copy(alpha = 0.18f)) else Modifier),
    ) {
        repeat(columns) { index ->
            Text(
                text = cells.getOrElse(index) { "" },
                modifier = Modifier
                    .then(if (cellWidth != null) Modifier.widthIn(min = cellWidth) else Modifier.weight(1f))
                    .border(0.5.dp, line)
                    .padding(Space.s),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
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
    // 列表行副标题最多两行：第一行时间，第二行考场 · 座位（· 状态），三段不会被截掉一段。
    val where = listOf(
        exam.room.ifBlank { "考场待定" },
        seat,
        // 日期认不出时状态（未安排 / 已安排）是唯一线索，留着。
        if (days == null) exam.status.trim() else "",
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return ListRow(
        title = exam.courseName.ifBlank { "未命名考试" },
        summary = listOf(exam.time.ifBlank { "时间待定" }, where).filter { it.isNotBlank() }.joinToString("\n"),
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
    HubColumn(contentPadding) {
        if (snapshot.exams.isEmpty()) {
            if (snapshot.session.loggedIn) {
                EmptyState(
                    title = "暂无考试安排",
                    detail = "教务排考后会同步到这里。",
                )
            } else {
                EmptyState(
                    title = "还没登录",
                    detail = "登录后同步考试安排。",
                    actionText = "去登录",
                    onAction = onLogin,
                )
            }
            return@HubColumn
        }
        PageLead(
            when {
                upcoming.isNotEmpty() -> {
                    val next = upcoming.first()
                    val gap = when (next.days) {
                        0 -> "今天就考"
                        1 -> "明天考"
                        else -> "${next.days} 天后"
                    }
                    "还有 ${upcoming.size} 场待考，最近一场「${next.exam.courseName.ifBlank { "未命名考试" }}」$gap。"
                }
                undated.isNotEmpty() -> "有 ${undated.size} 场考试时间还没定。"
                else -> "本学期的考试都考完了。"
            },
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
