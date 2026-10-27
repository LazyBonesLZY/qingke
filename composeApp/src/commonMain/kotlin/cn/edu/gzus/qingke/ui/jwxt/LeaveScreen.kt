package cn.edu.gzus.qingke.ui.jwxt

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
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
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        if (waitingForm) {
            EmptyHint("正在拉取大厅请假表单")
        }
        when {
            waitingForm -> Unit
            !cas -> {
                InfoCard(
                    title = "还不能请假",
                    summary = "「我的」里把广软登录渠道换成统一身份认证，再登录一次。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
            !snapshot.session.loggedIn -> {
                InfoCard(
                    title = "还没有请假数据",
                    summary = "用统一身份认证登录后，申请记录和请假表单会同步到这里。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
            hall.error.isNotBlank() && !hall.ready && hall.leaves.isEmpty() -> {
                InfoCard(
                    title = "大厅这次没同步上",
                    summary = hall.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
        }
        if (cas && snapshot.session.loggedIn && !waitingForm) {
            SmallTitle(text = form?.affairName?.ifBlank { "发起请假" } ?: "发起请假")
            if (!formError.isNullOrBlank()) {
                Text(
                    "${formError.trimEnd('。')}。下面是通用表单，也可以重新拉取大厅的。",
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Button(
                    onClick = { onLoadForm(hall.leaveAffairs().firstOrNull()?.id.orEmpty()) },
                    enabled = !formBusy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    minHeight = 40.dp,
                ) { Text("重新拉取表单") }
                Spacer(Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fields.forEach { field ->
                    LeaveFieldBlock(
                        field = field,
                        value = values[field.key].orEmpty(),
                        expanded = picking == field.key,
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
                timeError != null -> timeError
                missing.isNotEmpty() -> "还差：${missing.joinToString("、")}"
                else -> ""
            }
            if (tip.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tip,
                    color = if (timeError != null && !justSubmitted) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { confirming = true },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minHeight = 50.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (submitting) InfiniteProgressIndicator()
                Text(if (submitting) "正在提交" else "提交请假")
            }
        }
        if (cas && snapshot.session.loggedIn && !waitingForm) {
            SmallTitle(text = "我的请假")
            if (hall.leaves.isEmpty()) {
                EmptyHint(if (hall.ready) "还没有请假记录" else hall.leaveSummary())
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hall.leaves.forEach { item ->
                        val traceId = item.traceId()
                        val open = openedTrace == traceId
                        val range = listOf(item.start, item.end).filter { it.isNotBlank() }.joinToString(" ~ ")
                        InfoCard(
                            title = item.title,
                            summary = listOf(
                                listOf(item.status, item.node).filter { it.isNotBlank() }.joinToString(" · "),
                                range.ifBlank { item.time },
                                item.reason,
                                if (open) "" else "点开看审批进度",
                            ).filter { it.isNotBlank() }.joinToString("\n"),
                            height = null,
                            tintTitle = open,
                            onClick = {
                                openedTrace = if (open) "" else traceId
                                if (!open && traces[traceId] == null) onLoadTrace(traceId)
                            },
                        )
                        if (open) {
                            val steps = traces[traceId]
                            when {
                                traceBusy == traceId && steps == null -> Text(
                                    "正在读审批进度",
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                    style = MiuixTheme.textStyles.footnote1,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                                // 读失败时 traces 里没有这条；以前也显示「没有返回审批环节」，像是流程空了
                                steps == null -> Button(
                                    onClick = { onLoadTrace(traceId) },
                                    enabled = traceBusy.isBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    minHeight = 40.dp,
                                ) { Text("审批进度没读到，再试一次") }
                                steps.isEmpty() -> Text(
                                    "大厅没有返回审批环节",
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                    style = MiuixTheme.textStyles.footnote1,
                                    modifier = Modifier.padding(horizontal = 4.dp),
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
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRefresh,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minHeight = 44.dp,
            ) { Text(if (busy && !submitting) "正在同步" else "刷新申请") }
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

@Composable
private fun LeaveFieldBlock(
    field: LeaveField,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
) {
    val choice = field.options.isNotEmpty() || field.type.contains("choice", ignoreCase = true) ||
        field.type.contains("select", ignoreCase = true)
    if (choice && field.options.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            ArrowPreference(
                title = field.label + if (field.required) " *" else "",
                summary = value.ifBlank { "点这里选" },
                onClick = onToggle,
            )
            if (expanded) {
                field.options.forEach { option ->
                    ArrowPreference(
                        title = option,
                        summary = if (option == value) "当前" else "",
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
    TextField(
        value = value,
        onValueChange = onChange,
        label = field.label + if (field.required) " *" else "",
        singleLine = !field.type.contains("textarea", ignoreCase = true) &&
            !field.label.contains("事由") &&
            !field.label.contains("原因"),
        modifier = Modifier.fillMaxWidth(),
    )
    val hint = when {
        field.isStartTime() -> "例如 2026-09-16 08:00"
        field.isEndTime() -> "例如 2026-09-16 17:00"
        field.label.contains("事由") || field.label.contains("原因") -> "写清楚请假原因"
        else -> ""
    }
    if (hint.isNotBlank()) {
        Text(
            hint,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
