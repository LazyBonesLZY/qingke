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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.LeaveApplication
import cn.edu.gzus.qingke.data.LeaveField
import cn.edu.gzus.qingke.data.LeaveForm
import cn.edu.gzus.qingke.data.LeaveStep
import cn.edu.gzus.qingke.data.brief
import cn.edu.gzus.qingke.data.defaultLeaveFields
import cn.edu.gzus.qingke.data.gzusUsesCas
import cn.edu.gzus.qingke.data.leaveAffairs
import cn.edu.gzus.qingke.data.leaveSummary
import cn.edu.gzus.qingke.data.parseCoursePickWhen
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.TabDest
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 主操作按钮（提交）和次要按钮（刷新、重试）的高度，全页统一。 */
private val PrimaryButtonHeight = 48.dp
private val SecondaryButtonHeight = 44.dp
private val CompactButtonHeight = 40.dp

@Composable
fun LeaveScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
    busy: Boolean,
    form: LeaveForm?,
    formBusy: Boolean,
    formError: String?,
    onLoadForm: (String) -> Unit,
    onSubmit: (LeaveForm, Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    traces: Map<String, List<LeaveStep>> = emptyMap(),
    traceBusy: String = "",
    onLoadTrace: (String) -> Unit = {},
) {
    val hall = snapshot.hall
    val cas = snapshot.settings.gzusUsesCas()
    val waitingForm = cas && snapshot.session.loggedIn && (formBusy || (form == null && formError.isNullOrBlank()))
    val fields = if (waitingForm) emptyList() else form?.fields.orEmpty().ifEmpty { defaultLeaveFields() }
    var values by remember(form?.affairId, fields.joinToString { it.key + it.value }) {
        mutableStateOf(fields.associate { it.key to it.value })
    }
    var picking by remember { mutableStateOf<String?>(null) }
    var openedTrace by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    // 提交和刷新共用 busy，记一下是不是自己点的提交，按钮文案才不会乱
    var submitting by remember { mutableStateOf(false) }
    // 提交前最新一条申请的 id；提交结束后列表头变了，说明大厅收到了
    var baselineLeave by remember { mutableStateOf<String?>(null) }
    var justSubmitted by remember { mutableStateOf(false) }
    val missing = fields.filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }
    val timeError = leaveTimeError(fields, values)
    val canSubmit = !busy && fields.isNotEmpty() && missing.isEmpty() && timeError == null

    LaunchedEffect(busy) {
        if (busy || !submitting) return@LaunchedEffect
        submitting = false
        val latest = hall.leaves.firstOrNull()?.traceId().orEmpty()
        if (latest.isNotBlank() && latest != baselineLeave) {
            // 大厅已经出现新申请：清空表单，免得同一份假条再点一次
            values = fields.associate { it.key to it.value }
            justSubmitted = true
        }
        baselineLeave = null
    }

    LaunchedEffect(snapshot.session.loggedIn, cas, hall.leaveAffairs().firstOrNull()?.id) {
        if (snapshot.session.loggedIn && cas) {
            onLoadForm(hall.leaveAffairs().firstOrNull()?.id.orEmpty())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = Space.xl),
    ) {
        Spacer(Modifier.height(Space.s))
        if (waitingForm) {
            JwxtLoadingCard("正在拉取大厅请假表单")
        }
        when {
            waitingForm -> Unit
            !cas -> {
                EmptyState(
                    title = "还不能请假",
                    detail = "「我的」里把广软登录渠道换成统一身份认证，再登录一次。",
                    actionText = "去切换",
                    onAction = { nav.goTab(TabDest.Mine) },
                )
            }
            !snapshot.session.loggedIn -> {
                EmptyState(
                    title = "还没有请假数据",
                    detail = "用统一身份认证登录后，申请记录和请假表单会同步到这里。",
                    actionText = "去登录",
                    onAction = { nav.goTab(TabDest.Mine) },
                )
            }
            hall.error.isNotBlank() && !hall.ready && hall.leaves.isEmpty() -> {
                EmptyState(
                    title = "大厅这次没同步上",
                    detail = hall.error.trim().take(120),
                    actionText = "重新登录",
                    onAction = { nav.goTab(TabDest.Mine) },
                )
            }
        }
        if (cas && snapshot.session.loggedIn && !waitingForm) {
            SmallTitle(text = form?.affairName?.ifBlank { "发起请假" } ?: "发起请假")
            if (!formError.isNullOrBlank()) {
                // 大厅表单没拉到不算致命：先用通用表单顶上，提示用警示色而不是报错红
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${formError.trimEnd('。')}。先用通用表单，也可以重新拉取。",
                        modifier = Modifier.weight(1f),
                        color = Tone.warning(),
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(Space.m))
                    Button(
                        onClick = { onLoadForm(hall.leaveAffairs().firstOrNull()?.id.orEmpty()) },
                        enabled = !formBusy,
                        minHeight = CompactButtonHeight,
                    ) { Text("重新拉取") }
                }
                Spacer(Modifier.height(Space.m))
            }
            Column(
                modifier = Modifier.padding(horizontal = Inset.page),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                fields.forEach { field ->
                    LeaveFieldBlock(
                        field = field,
                        value = values[field.key].orEmpty(),
                        expanded = picking == field.key,
                        // 时间先后不对，就把错误挂在「结束时间」下面，而不是只在页底说一句
                        error = if (field.isEndTime()) timeError else null,
                        onToggle = { picking = if (picking == field.key) null else field.key },
                        onChange = { next ->
                            values = values + (field.key to next)
                            justSubmitted = false
                        },
                    )
                }
            }
            // 以前「任一项非空」就能点，而请假类型默认就有值，等于必填项全空也能提交
            val tip = when {
                justSubmitted -> "已提交，下面「我的请假」能看到这条申请"
                missing.isNotEmpty() -> "还差：${missing.joinToString("、")}"
                else -> ""
            }
            if (tip.isNotBlank()) {
                Spacer(Modifier.height(Space.m))
                Text(
                    tip,
                    color = if (justSubmitted) Tone.success() else Tone.muted(),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = if (justSubmitted) FontWeight.Medium else null,
                    modifier = Modifier.padding(horizontal = Inset.page + Space.xs),
                )
            }
            Spacer(Modifier.height(Space.m))
            Button(
                onClick = { confirming = true },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                minHeight = PrimaryButtonHeight,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (submitting) {
                    InfiniteProgressIndicator()
                    Spacer(Modifier.width(Space.s))
                }
                Text(if (submitting) "正在提交" else "提交请假")
            }
        }
        if (cas && snapshot.session.loggedIn && !waitingForm) {
            SmallTitle(text = if (hall.leaves.isEmpty()) "我的请假" else "我的请假 · ${hall.leaves.size}")
            if (hall.leaves.isEmpty()) {
                EmptyState(
                    title = if (hall.ready) "还没有请假记录" else "申请记录还没同步",
                    detail = if (hall.ready) "提交后的申请和审批进度会出现在这里。" else hall.leaveSummary(),
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = Inset.page),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    hall.leaves.forEach { item ->
                        val traceId = item.traceId()
                        val open = openedTrace == traceId
                        LeaveCard(
                            item = item,
                            open = open,
                            onClick = {
                                openedTrace = if (open) "" else traceId
                                if (!open && traces[traceId] == null) onLoadTrace(traceId)
                            },
                        )
                        if (open) {
                            val steps = traces[traceId]
                            when {
                                traceBusy == traceId && steps == null -> Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                                ) {
                                    InfiniteProgressIndicator()
                                    Text(
                                        "正在读审批进度",
                                        color = Tone.muted(),
                                        style = MiuixTheme.textStyles.footnote1,
                                    )
                                }
                                // 读失败时 traces 里没有这条；以前也显示「没有返回审批环节」，像是流程空了
                                steps == null -> Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = Space.l),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "审批进度没读到",
                                        modifier = Modifier.weight(1f),
                                        color = Tone.danger(),
                                        style = MiuixTheme.textStyles.footnote1,
                                    )
                                    Button(
                                        onClick = { onLoadTrace(traceId) },
                                        enabled = traceBusy.isBlank(),
                                        minHeight = CompactButtonHeight,
                                    ) { Text("再试一次") }
                                }
                                steps.isEmpty() -> Text(
                                    "大厅没有返回审批环节",
                                    color = Tone.muted(),
                                    style = MiuixTheme.textStyles.footnote1,
                                    modifier = Modifier.padding(horizontal = Space.l, vertical = Space.xs),
                                )
                                else -> ListGroup(
                                    steps.map { step ->
                                        ListRow(
                                            title = step.name.ifBlank { step.handler.ifBlank { "审批" } },
                                            summary = step.brief(),
                                        )
                                    },
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Button(
                onClick = onRefresh,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                minHeight = SecondaryButtonHeight,
            ) {
                val syncing = busy && !submitting
                if (syncing) {
                    InfiniteProgressIndicator()
                    Spacer(Modifier.width(Space.s))
                }
                Text(if (syncing) "正在同步" else "刷新申请")
            }
        }
    }

    if (confirming) {
        JwxtConfirmDialog(
            title = "提交请假",
            message = fields
                .mapNotNull { field ->
                    values[field.key]?.takeIf { it.isNotBlank() }?.let { "${field.label}：$it" }
                }
                .joinToString("\n") + "\n\n提交后进入大厅审批，撤回要去大厅操作。",
            confirmText = "确认提交",
            onConfirm = {
                val current = form ?: LeaveForm(
                    affairId = hall.leaveAffairs().firstOrNull()?.id.orEmpty(),
                    affairName = "请假",
                    fields = fields,
                )
                baselineLeave = hall.leaves.firstOrNull()?.traceId().orEmpty()
                submitting = true
                justSubmitted = false
                onSubmit(current, values)
            },
            onDismiss = { confirming = false },
        )
    }
}

/**
 * 一条请假申请：标题 + 状态标签一行，时间、当前环节、事由依次往下，
 * 最后一行是展开审批进度的入口。以前全塞进一段多行灰字里，状态一眼看不出来。
 */
@Composable
private fun LeaveCard(item: LeaveApplication, open: Boolean, onClick: () -> Unit) {
    val range = listOf(item.start, item.end).filter { it.isNotBlank() }.joinToString(" ~ ")
    val whenText = range.ifBlank { item.time }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = Inset.card,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.status.isNotBlank()) {
                Spacer(Modifier.width(Space.s))
                StatusTag(item.status.take(8), color = leaveStatusColor(item.status))
            }
        }
        if (whenText.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                whenText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.node.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "当前环节：${item.node}",
                style = MiuixTheme.textStyles.footnote1,
                color = Tone.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.reason.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                item.reason,
                style = MiuixTheme.textStyles.footnote1,
                color = Tone.muted(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Space.s))
        Text(
            if (open) "收起审批进度" else "查看审批进度 ›",
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

/** 审批状态的语气：先判否定（「不通过」里也有「通过」），再判通过，进行中用警示色。 */
@Composable
private fun leaveStatusColor(status: String): Color = when {
    listOf("不通过", "驳回", "拒绝", "退回", "撤回", "作废", "取消").any { status.contains(it) } -> Tone.danger()
    listOf("通过", "同意", "完成", "结束", "已销假").any { status.contains(it) } -> Tone.success()
    listOf("待", "中", "审").any { status.contains(it) } -> Tone.warning()
    else -> Tone.muted()
}

/** 开始、结束都按「yyyy-MM-dd HH:mm」写了，才比较先后；格式不标准的交给大厅判断。 */
private fun leaveTimeError(fields: List<LeaveField>, values: Map<String, String>): String? {
    val start = fields.firstOrNull { it.isStartTime() }?.let { values[it.key] }.orEmpty()
    val end = fields.firstOrNull { it.isEndTime() }?.let { values[it.key] }.orEmpty()
    val from = leaveMillis(start)
    val to = leaveMillis(end)
    if (from <= 0L || to <= 0L) return null
    return if (to <= from) "结束时间要晚于开始时间" else null
}

private val LeaveTimePattern = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}:\d{2}(:\d{2})?)""")

private fun leaveMillis(text: String): Long {
    val match = LeaveTimePattern.matchEntire(text.trim()) ?: return 0L
    val g = match.groupValues
    val date = "${g[1]}-${g[2].padStart(2, '0')}-${g[3].padStart(2, '0')}"
    return parseCoursePickWhen(date, g[4])
}

private fun LeaveField.isStartTime(): Boolean = key.equals("KSSJ", true) || label.contains("开始")

private fun LeaveField.isEndTime(): Boolean = key.equals("JSSJ", true) || label.contains("结束")

/**
 * 一个表单项 + 它下面的一行说明。说明平时是灰色的填写示例，
 * 有 [error] 时换成危险色的错误原因，位置不跳。
 */
@Composable
private fun LeaveFieldBlock(
    field: LeaveField,
    value: String,
    expanded: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
) {
    val label = field.label + if (field.required) " *" else ""
    val choice = field.options.isNotEmpty() || field.type.contains("choice", ignoreCase = true) ||
        field.type.contains("select", ignoreCase = true)
    if (choice && field.options.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            ArrowPreference(
                title = label,
                summary = value.ifBlank { "点这里选" },
                onClick = onToggle,
            )
            if (expanded) {
                field.options.forEach { option ->
                    ArrowPreference(
                        title = option,
                        summary = if (option == value) "已选" else "",
                        onClick = {
                            onChange(option)
                            onToggle()
                        },
                    )
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onChange,
            label = label,
            singleLine = !field.type.contains("textarea", ignoreCase = true) &&
                !field.label.contains("事由") &&
                !field.label.contains("原因"),
            modifier = Modifier.fillMaxWidth(),
        )
        val hint = when {
            !error.isNullOrBlank() -> error
            field.isStartTime() -> "例如 2026-09-16 08:00"
            field.isEndTime() -> "例如 2026-09-16 17:00"
            field.label.contains("事由") || field.label.contains("原因") -> "写清楚请假原因"
            else -> ""
        }
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                hint,
                color = if (!error.isNullOrBlank()) Tone.danger() else Tone.muted(),
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = Space.l),
            )
        }
    }
}
