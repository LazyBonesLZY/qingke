package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.CoursePickOffer
import cn.edu.gzus.qingke.data.CoursePickScope
import cn.edu.gzus.qingke.data.CoursePickSection
import cn.edu.gzus.qingke.data.CoursePickTask
import cn.edu.gzus.qingke.data.brief
import cn.edu.gzus.qingke.data.coursePickStatusLabel
import cn.edu.gzus.qingke.data.formatCoursePickWhen
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.parseCoursePickWhen
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.ConfirmDialog
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 列表最多画多少门；再多就让人缩小关键词。 */
private const val MaxOffersShown = 30

/** 整宽表单按钮的高度；卡片内并排的小按钮用 [CompactButtonHeight]。 */
private val FormButtonHeight = 44.dp
private val CompactButtonHeight = 40.dp

@Composable
fun CoursePickScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
    busy: Boolean,
    scopes: List<CoursePickScope>,
    offers: List<CoursePickOffer>,
    sections: List<CoursePickSection>,
    picked: List<CoursePickOffer>,
    scopesBusy: Boolean,
    searchBusy: Boolean,
    sectionsBusy: Boolean,
    error: String?,
    onLoadScopes: () -> Unit,
    onSearch: (CoursePickScope, String) -> Unit,
    onOpenSections: (CoursePickOffer) -> Unit,
    onSelectNow: (CoursePickOffer, CoursePickSection) -> Unit,
    onQueue: (CoursePickOffer, CoursePickSection) -> Unit,
    onSchedule: (String, Long) -> Unit,
    onRemove: (String) -> Unit,
    onRunQueued: (String) -> Unit,
) {
    val school = snapshot.resolved()
    val queue = snapshot.settings.coursePickQueue
    val now = nowDateTime()
    var query by remember { mutableStateOf("") }
    var scopeIndex by remember(scopes.joinToString { it.id }) { mutableIntStateOf(0) }
    var openedId by remember { mutableStateOf("") }
    var clockTaskId by remember { mutableStateOf<String?>(null) }
    var dateText by remember(now.date) { mutableStateOf(now.date.toString()) }
    var timeText by remember {
        mutableStateOf(
            listOf(now.hour, now.minute, now.second).joinToString(":") { it.toString().padStart(2, '0') },
        )
    }
    // 提交到教务是收不回的操作，先弹确认框
    var confirmPick by remember { mutableStateOf<Pair<CoursePickOffer, CoursePickSection>?>(null) }
    var confirmRun by remember { mutableStateOf<CoursePickTask?>(null) }
    val scope = scopes.getOrNull(scopeIndex.coerceIn(0, (scopes.size - 1).coerceAtLeast(0)))
    LaunchedEffect(snapshot.session.loggedIn, school.supportsCoursePick) {
        if (snapshot.session.loggedIn && school.supportsCoursePick) onLoadScopes()
    }
    LaunchedEffect(scope?.id) {
        val current = scope ?: return@LaunchedEffect
        onSearch(current, query.trim())
        openedId = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = Space.xl),
    ) {
        Spacer(Modifier.height(Space.s))
        Text(
            "搜到课可以立刻提交，也可以排队到点自动提交。到点那一刻应用要开着。",
            modifier = Modifier.padding(horizontal = Inset.page),
            color = Tone.muted(),
            style = MiuixTheme.textStyles.body2,
        )
        if (!school.supportsCoursePick) {
            Spacer(Modifier.height(Space.m))
            EmptyState(
                title = "暂不支持选课",
                detail = "${school.jwxtName}没有自主选课。",
            )
        } else if (!snapshot.session.loggedIn) {
            Spacer(Modifier.height(Space.m))
            EmptyState(
                title = "还不能选课",
                detail = "选课要先登录教务。",
                actionText = "去登录",
                onAction = { nav.goTab(TabDest.Mine) },
            )
        } else {
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(Space.m))
                ErrorLine(error, Modifier.padding(horizontal = Inset.page))
            }
            if (queue.isNotEmpty()) {
                SmallTitle(text = "队列 ${queue.size}")
                Column(
                    modifier = Modifier.padding(horizontal = Inset.page),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    queue.forEach { task ->
                        QueueCard(
                            task = task,
                            busy = busy,
                            editing = clockTaskId == task.id,
                            dateText = dateText,
                            timeText = timeText,
                            onDate = { dateText = it },
                            onTime = { timeText = it },
                            onToggleClock = {
                                clockTaskId = if (clockTaskId == task.id) null else task.id
                                if (task.fireAt > 0L) {
                                    val stamp = formatCoursePickWhen(task.fireAt)
                                    if (stamp.contains(' ')) {
                                        dateText = stamp.substringBefore(' ')
                                        timeText = stamp.substringAfter(' ')
                                    }
                                }
                            },
                            onConfirmClock = { fireAt ->
                                onSchedule(task.id, fireAt)
                                clockTaskId = null
                            },
                            onRun = { confirmRun = task },
                            onRemove = {
                                if (clockTaskId == task.id) clockTaskId = null
                                onRemove(task.id)
                            },
                        )
                    }
                }
            }
            if (picked.isNotEmpty()) {
                SmallTitle(text = "已选 ${picked.size}")
                ListGroup(
                    picked.map { item ->
                        ListRow(title = item.name, summary = item.brief())
                    },
                )
            }
            SmallTitle(text = "选课轮次")
            if (scopesBusy && scopes.isEmpty()) {
                LoadingCard("正在打开选课页")
            } else if (scopes.isEmpty()) {
                // 错误原文已经在顶部红字里，这里只给出路，别把同一句话说两遍
                EmptyState(
                    title = if (error.isNullOrBlank()) "教务没有打开自主选课" else "选课页没打开",
                    detail = if (error.isNullOrBlank()) "等选课开放后再来看看。" else "网络或登录状态可能有问题，可以再试一次。",
                    actionText = "重新打开选课页",
                    onAction = onLoadScopes,
                )
            } else {
                TabRow(
                    tabs = scopes.map { it.name.ifBlank { it.id } },
                    selectedTabIndex = scopeIndex.coerceIn(0, scopes.lastIndex),
                    onTabSelected = { scopeIndex = it },
                    modifier = Modifier.padding(horizontal = Inset.page),
                )
            }
            SmallTitle(text = "搜索")
            Column(
                modifier = Modifier.padding(horizontal = Inset.page),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "课程名或课号，留空列出全部",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope?.let {
                            openedId = ""
                            onSearch(it, query.trim())
                        }
                    },
                    enabled = !searchBusy && !scopesBusy && scope != null,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = FormButtonHeight,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    if (searchBusy) {
                        InfiniteProgressIndicator()
                        Spacer(Modifier.width(Space.s))
                    }
                    Text(if (searchBusy) "查询中" else "查询")
                }
            }
            SmallTitle(text = if (offers.isEmpty()) "可选课程" else "可选 ${offers.size} 门")
            if (searchBusy && offers.isEmpty()) {
                LoadingCard("正在查询")
            } else if (offers.isEmpty()) {
                EmptyState(
                    title = "没有找到课程",
                    detail = "换个关键词，或等选课开放后再查。",
                )
            } else {
                // 课程用分组列表；点开的那门拆出来单独一张卡，下面挂教学班和按钮
                val shown = offers.take(MaxOffersShown)
                val openedIndex = shown.indexOfFirst { offerKey(it) == openedId }
                val toRow: (CoursePickOffer) -> ListRow = { offer ->
                    val key = offerKey(offer)
                    ListRow(
                        title = offer.name,
                        summary = offer.brief(),
                        badge = if (offer.selected) "已选" else "",
                        tone = BadgeTone.Good,
                        onClick = {
                            openedId = key
                            onOpenSections(offer)
                        },
                    )
                }
                if (openedIndex < 0) {
                    ListGroup(shown.map(toRow))
                } else {
                    val opened = shown[openedIndex]
                    val before = shown.subList(0, openedIndex)
                    val after = shown.subList(openedIndex + 1, shown.size)
                    if (before.isNotEmpty()) {
                        ListGroup(before.map(toRow))
                        Spacer(Modifier.height(Space.s))
                    }
                    OfferPanel(
                        offer = opened,
                        sections = sections,
                        sectionsBusy = sectionsBusy,
                        error = error,
                        busy = busy,
                        queue = queue,
                        onCollapse = { openedId = "" },
                        onRetry = { onOpenSections(opened) },
                        onSelectNow = { section -> confirmPick = opened to section },
                        onQueue = { section -> onQueue(opened, section) },
                    )
                    if (after.isNotEmpty()) {
                        Spacer(Modifier.height(Space.s))
                        ListGroup(after.map(toRow))
                    }
                }
                if (offers.size > MaxOffersShown) {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "只显示前 $MaxOffersShown 门，缩小关键词再查。",
                        modifier = Modifier.padding(horizontal = Inset.page),
                        color = Tone.muted(),
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
        }
    }

    confirmPick?.let { (offer, section) ->
        ConfirmDialog(
            title = "提交选课",
            message = listOf(
                offer.name,
                section.name.takeIf { it.isNotBlank() && it != offer.name }.orEmpty(),
                section.brief(),
                "会直接提交到教务，选上后要退课得去教务系统。",
            ).filter { it.isNotBlank() }.joinToString("\n"),
            confirmText = "确认提交",
            onConfirm = { onSelectNow(offer, section) },
            onDismiss = { confirmPick = null },
        )
    }
    confirmRun?.let { task ->
        ConfirmDialog(
            title = "立即提交",
            message = listOf(
                task.courseName,
                task.className.ifBlank { task.teacher },
                "会马上提交到教务，不再等到点。",
            ).filter { it.isNotBlank() }.joinToString("\n"),
            confirmText = "确认提交",
            onConfirm = { onRunQueued(task.id) },
            onDismiss = { confirmRun = null },
        )
    }
}

/** 页面级错误：危险色正文，长原文最多三行。 */
@Composable
private fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Tone.danger(),
        style = MiuixTheme.textStyles.body2,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 加载中占位：转圈 + 一句在做什么，和 EmptyState 同宽同边距。 */
@Composable
private fun LoadingCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = Inset.card,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            InfiniteProgressIndicator()
            Text(text, style = MiuixTheme.textStyles.body2, color = Tone.muted())
        }
    }
}

/** 点开的课程：标题行可收起，下面列出教学班，每个班带「立即选课 / 加入队列」。 */
@Composable
private fun OfferPanel(
    offer: CoursePickOffer,
    sections: List<CoursePickSection>,
    sectionsBusy: Boolean,
    error: String?,
    busy: Boolean,
    queue: List<CoursePickTask>,
    onCollapse: () -> Unit,
    onRetry: () -> Unit,
    onSelectNow: (CoursePickSection) -> Unit,
    onQueue: (CoursePickSection) -> Unit,
) {
    val muted = Tone.muted()
    val divider = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = PaddingValues(vertical = Space.xs),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCollapse)
                .padding(horizontal = Space.l, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    offer.name,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val brief = offer.brief()
                if (brief.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        brief,
                        style = MiuixTheme.textStyles.footnote1,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Space.m))
            Text("收起", style = MiuixTheme.textStyles.footnote1, color = muted)
        }
        when {
            sectionsBusy && sections.isEmpty() -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                InfiniteProgressIndicator()
                Text("正在拉教学班", color = muted, style = MiuixTheme.textStyles.body2)
            }
            sections.isEmpty() -> Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
                // 拉失败时 App 只把错误写进顶部红字，这里给个就近的重试
                val failed = !error.isNullOrBlank()
                Text(
                    if (failed) "教学班没拉到" else "这门课还没有教学班",
                    color = if (failed) Tone.danger() else muted,
                    style = MiuixTheme.textStyles.body2,
                )
                if (failed) {
                    Spacer(Modifier.height(Space.m))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = CompactButtonHeight,
                    ) { Text("重新拉取") }
                }
            }
            else -> sections.forEach { section ->
                Box(Modifier.padding(horizontal = Space.l).fillMaxWidth().height(0.5.dp).background(divider))
                val queued = queue.any { it.matches(offer, section) }
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            section.name.ifBlank { offer.name },
                            modifier = Modifier.weight(1f, fill = false),
                            style = MiuixTheme.textStyles.main,
                            color = MiuixTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (queued) {
                            Spacer(Modifier.width(Space.s))
                            StatusTag("已在队列")
                        }
                    }
                    val brief = section.brief()
                    if (brief.isNotBlank()) {
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            brief,
                            style = MiuixTheme.textStyles.footnote1,
                            color = muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(Space.m))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        Button(
                            onClick = { onSelectNow(section) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            minHeight = CompactButtonHeight,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) { Text(if (busy) "提交中" else "立即选课") }
                        // 重复加入会把已设好的「到点」状态冲掉（仓库按 id 覆盖），所以已在队列就禁用
                        Button(
                            onClick = { onQueue(section) },
                            enabled = !busy && !queued,
                            modifier = Modifier.weight(1f),
                            minHeight = CompactButtonHeight,
                        ) { Text(if (queued) "已在队列" else "加入队列") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueCard(
    task: CoursePickTask,
    busy: Boolean,
    editing: Boolean,
    dateText: String,
    timeText: String,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    onToggleClock: () -> Unit,
    onConfirmClock: (Long) -> Unit,
    onRun: () -> Unit,
    onRemove: () -> Unit,
) {
    val muted = Tone.muted()
    val running = task.status == "running"
    val done = task.status == "ok"
    val failed = task.status == "fail"
    val statusColor = when (task.status) {
        "ok" -> Tone.success()
        "fail" -> Tone.danger()
        "waiting", "running" -> MiuixTheme.colorScheme.primary
        else -> muted
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = Inset.card,
    ) {
        // 标题行：课名 + 状态胶囊；状态不再混在灰字里
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                task.courseName,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Space.s))
            StatusTag(coursePickStatusLabel(task.status), color = statusColor)
        }
        val meta = listOf(
            task.className.ifBlank { task.teacher },
            if (task.fireAt > 0 && task.status == "waiting") formatCoursePickWhen(task.fireAt) else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(meta, style = MiuixTheme.textStyles.footnote1, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (task.message.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                task.message,
                style = MiuixTheme.textStyles.footnote1,
                color = if (failed) Tone.danger() else muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Space.m))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Button(
                onClick = onRun,
                enabled = !busy && !done && !running,
                modifier = Modifier.weight(1f),
                minHeight = CompactButtonHeight,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (running) "提交中" else "立即提交") }
            Button(
                onClick = onToggleClock,
                enabled = !busy && !done && !running,
                modifier = Modifier.weight(1f),
                minHeight = CompactButtonHeight,
            ) { Text(if (editing) "收起" else "到点") }
            // 删除是危险操作：危险色文字；不可点时退回灰色，别让禁用态看着还能点
            val removable = !busy && !running
            Button(
                onClick = onRemove,
                enabled = removable,
                modifier = Modifier.weight(1f),
                minHeight = CompactButtonHeight,
            ) { Text("删除", color = if (removable) Tone.danger() else muted) }
        }
        if (editing) {
            // 先在本地解析一遍，格式不对或时间已过就别让人点确认
            val fireAt = parseCoursePickWhen(dateText, timeText)
            val nowMillis = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val hint = when {
                fireAt <= 0L -> "日期写成 2026-09-01，时间写成 12:30:00"
                fireAt <= nowMillis -> "这个时间已经过去了"
                else -> "将在 ${formatCoursePickWhen(fireAt)} 自动提交，到点时应用要开着"
            }
            val valid = fireAt > nowMillis
            Spacer(Modifier.height(Space.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                TextField(
                    value = dateText,
                    onValueChange = onDate,
                    label = "日期 yyyy-MM-dd",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextField(
                    value = timeText,
                    onValueChange = onTime,
                    label = "时间 HH:mm:ss",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.s))
            Text(
                hint,
                style = MiuixTheme.textStyles.footnote1,
                color = if (valid) muted else Tone.danger(),
                modifier = Modifier.padding(horizontal = Space.xs),
            )
            Spacer(Modifier.height(Space.m))
            Button(
                onClick = { onConfirmClock(fireAt) },
                enabled = !busy && valid,
                modifier = Modifier.fillMaxWidth(),
                minHeight = FormButtonHeight,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("确认到点自动选") }
        }
    }
}

private fun offerKey(offer: CoursePickOffer): String =
    listOf(offer.scopeId, offer.courseId, offer.classId, offer.name).joinToString("|")

/** 与仓库 coursePickTaskOf 的 id 规则对齐：轮次 + 课号 + 教学班。 */
private fun CoursePickTask.matches(offer: CoursePickOffer, section: CoursePickSection): Boolean =
    scopeId == offer.scopeId &&
        courseId == offer.courseId &&
        doJxbId == section.doJxbId.ifBlank { section.classId }
