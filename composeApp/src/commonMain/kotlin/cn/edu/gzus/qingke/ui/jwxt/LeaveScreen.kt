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
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.InfoCard
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
                    formError,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
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
                        onChange = { next -> values = values + (field.key to next) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val current = form ?: LeaveForm(
                        affairId = hall.leaveAffairs().firstOrNull()?.id.orEmpty(),
                        affairName = "请假",
                        fields = fields,
                    )
                    onSubmit(current, values)
                },
                enabled = !busy && values.values.any { it.isNotBlank() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minHeight = 50.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (busy) InfiniteProgressIndicator()
                Text(if (busy) "正在提交" else "提交请假")
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
                        InfoCard(
                            title = item.title,
                            summary = listOf(
                                item.status,
                                item.node,
                                item.time,
                                listOf(item.start, item.end).filter { it.isNotBlank() }.joinToString(" ~ "),
                                item.reason,
                                if (open) "" else "点开看审批到哪了",
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
                                )
                                steps.isNullOrEmpty() -> Text(
                                    "大厅没有返回审批环节",
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                                else -> steps.forEach { step ->
                                    InfoCard(
                                        title = step.name.ifBlank { step.handler.ifBlank { "审批" } },
                                        summary = step.brief(),
                                        height = null,
                                    )
                                }
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
            ) { Text(if (busy) "正在同步" else "刷新申请") }
        }
    }
}

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
        field.key.equals("KSSJ", true) || field.label.contains("开始") -> "例如 2026-09-16 08:00"
        field.key.equals("JSSJ", true) || field.label.contains("结束") -> "例如 2026-09-16 17:00"
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
