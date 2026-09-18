package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import cn.edu.gzus.qingke.data.CoursePickOffer
import cn.edu.gzus.qingke.data.CoursePickScope
import cn.edu.gzus.qingke.data.CoursePickSection
import cn.edu.gzus.qingke.data.CoursePickTask
import cn.edu.gzus.qingke.data.brief
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.parseCoursePickWhen
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.InfoCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "搜到课可以立刻提交，也可以排队到点自动提交。到点那一刻应用要开着。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        if (!school.supportsCoursePick) {
            Spacer(Modifier.height(12.dp))
            EmptyHint("${school.jwxtName}没有自主选课。")
        } else if (!snapshot.session.loggedIn) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "还不能选课",
                summary = "去「我的」登录后再搜课。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        } else {
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        if (queue.isNotEmpty()) {
            SmallTitle(text = "队列 ${queue.size}")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                val stamp = cn.edu.gzus.qingke.data.formatCoursePickWhen(task.fireAt)
                                if (stamp.contains(' ')) {
                                    dateText = stamp.substringBefore(' ')
                                    timeText = stamp.substringAfter(' ')
                                }
                            }
                        },
                        onConfirmClock = {
                            onSchedule(task.id, parseCoursePickWhen(dateText, timeText))
                            clockTaskId = null
                        },
                        onRun = { onRunQueued(task.id) },
                        onRemove = { onRemove(task.id) },
                    )
                }
            }
        }
        if (picked.isNotEmpty()) {
            SmallTitle(text = "已选 ${picked.size}")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                picked.take(20).forEach { item ->
                    InfoCard(
                        title = item.name,
                        summary = item.brief(),
                        height = null,
                    )
                }
            }
        }
        SmallTitle(text = "选课轮次")
        if (scopesBusy && scopes.isEmpty()) {
            EmptyHint("正在打开选课页")
        } else if (scopes.isEmpty()) {
            EmptyHint(error?.ifBlank { "教务没有打开自主选课" } ?: "教务没有打开自主选课")
        } else {
            TabRow(
                tabs = scopes.map { it.name.ifBlank { it.id } },
                selectedTabIndex = scopeIndex.coerceIn(0, scopes.lastIndex),
                onTabSelected = { scopeIndex = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        SmallTitle(text = "搜索")
        TextField(
            value = query,
            onValueChange = { query = it },
            label = "课程名或课号",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { scope?.let { onSearch(it, query.trim()) } },
            enabled = !searchBusy && !scopesBusy && scope != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (searchBusy) InfiniteProgressIndicator()
            Text(if (searchBusy) "查询中" else "查询")
        }
        SmallTitle(text = if (offers.isEmpty()) "可选课程" else "可选 ${offers.size} 门")
        if (searchBusy && offers.isEmpty()) {
            EmptyHint("正在查询")
        } else if (offers.isEmpty()) {
            EmptyHint("没有结果。换个关键词，或等选课开放后再查。")
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                offers.take(30).forEach { offer ->
                    val key = offerKey(offer)
                    InfoCard(
                        title = offer.name,
                        summary = offer.brief(),
                        height = null,
                        tintTitle = openedId == key,
                        onClick = {
                            openedId = if (openedId == key) "" else key
                            if (openedId == key) onOpenSections(offer)
                        },
                    )
                    if (openedId == key) {
                        if (sectionsBusy && sections.isEmpty()) {
                            Text(
                                "正在拉教学班",
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        } else if (sections.isEmpty()) {
                            Text(
                                "这门课还没有教学班",
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        } else {
                            sections.forEach { section ->
                                InfoCard(
                                    title = section.name.ifBlank { offer.name },
                                    summary = section.brief(),
                                    height = null,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { onSelectNow(offer, section) },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 40.dp,
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                    ) { Text("立即选课") }
                                    Button(
                                        onClick = { onQueue(offer, section) },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 40.dp,
                                    ) { Text("加入队列") }
                                }
                            }
                        }
                    }
                }
                if (offers.size > 30) {
                    Text(
                        "只显示前 30 门，缩小关键词再查。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        style = MiuixTheme.textStyles.footnote1,
                    )
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
    onConfirmClock: () -> Unit,
    onRun: () -> Unit,
    onRemove: () -> Unit,
) {
    InfoCard(
        title = task.courseName,
        summary = task.brief(),
        height = null,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onRun,
            enabled = !busy && task.status != "ok" && task.status != "running",
            modifier = Modifier.weight(1f),
            minHeight = 40.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("立即提交") }
        Button(
            onClick = onToggleClock,
            enabled = !busy && task.status != "ok",
            modifier = Modifier.weight(1f),
            minHeight = 40.dp,
        ) { Text(if (editing) "收起" else "到点") }
        Button(
            onClick = onRemove,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            minHeight = 40.dp,
        ) { Text("删除") }
    }
    if (editing) {
        Spacer(Modifier.height(8.dp))
        TextField(
            value = dateText,
            onValueChange = onDate,
            label = "日期 yyyy-MM-dd",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = timeText,
            onValueChange = onTime,
            label = "时间 HH:mm:ss",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConfirmClock,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 40.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("确认到点自动选") }
    }
}

private fun offerKey(offer: CoursePickOffer): String =
    listOf(offer.scopeId, offer.courseId, offer.classId, offer.name).joinToString("|")
