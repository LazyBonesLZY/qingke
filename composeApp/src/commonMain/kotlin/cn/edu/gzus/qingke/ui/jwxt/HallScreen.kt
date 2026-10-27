package cn.edu.gzus.qingke.ui.jwxt

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
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.GZUS_EHALL_HOME
import cn.edu.gzus.qingke.data.GZUS_EHALL_MESSAGE
import cn.edu.gzus.qingke.data.gzusUsesCas
import cn.edu.gzus.qingke.data.isLeave
import cn.edu.gzus.qingke.data.leaveSummary
import cn.edu.gzus.qingke.data.openUrl
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HallScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
) {
    val hall = snapshot.hall
    val cas = snapshot.settings.gzusUsesCas()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "广软事务中心的待办、消息和办事。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        // 请假入口和最近几条申请放一组，不再每条一张大卡
        ListGroup(
            buildList {
                add(ListRow(title = "请假", summary = hall.leaveSummary(), onClick = { nav.open(Route.Leave) }))
                hall.leaves.take(3).forEach { item ->
                    add(
                        ListRow(
                            title = item.title,
                            summary = item.time,
                            badge = item.status.take(8),
                            tone = BadgeTone.Muted,
                            onClick = { nav.open(Route.Leave) },
                        ),
                    )
                }
            },
        )
        when {
            !cas -> {
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    title = "还不能同步大厅",
                    summary = "「我的」里把广软登录渠道换成统一身份认证，再登录一次。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    height = null,
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
            !snapshot.session.loggedIn -> {
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    title = "还没有大厅数据",
                    summary = "用统一身份认证登录后，待办、消息和办事会同步到这里。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    height = null,
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
            hall.error.isNotBlank() && !hall.ready -> {
                Spacer(Modifier.height(12.dp))
                // 错误原文可能很长，固定高度的卡会把它压成跑马灯；改成自适应高度
                InfoCard(
                    title = "大厅这次没同步上",
                    summary = hall.error + "\n去「我的」重新登录后再同步",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    height = null,
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
        }
        if (cas && snapshot.session.loggedIn && (hall.ready || hall.todos.isNotEmpty() || hall.messages.isNotEmpty() || hall.affairs.isNotEmpty() || hall.events.isNotEmpty() || hall.leaves.isNotEmpty())) {
            if (hall.todos.isEmpty()) {
                SmallTitle(text = "待办")
                EmptyLine("没有待办或申请")
            } else {
                SmallTitle(text = "待办 · ${hall.todos.size}")
                ListGroup(
                    hall.todos.map { item ->
                        ListRow(
                            title = item.title,
                            summary = listOf(item.status, item.time).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = {
                                if (item.isLeave()) nav.open(Route.Leave) else openUrl(item.url.ifBlank { GZUS_EHALL_HOME })
                            },
                        )
                    },
                )
            }
            if (hall.messages.isEmpty()) {
                SmallTitle(text = "消息")
                EmptyLine("没有大厅消息")
            } else {
                SmallTitle(text = "消息 · ${hall.messages.size}")
                ListGroup(
                    hall.messages.map { item ->
                        ListRow(
                            title = item.title,
                            // 行内摘要最多两行：时间在前，正文压成一行接在后面
                            summary = listOf(item.time, item.content.replace('\n', ' ').trim())
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            onClick = { openUrl(GZUS_EHALL_MESSAGE) },
                        )
                    },
                )
            }
            if (hall.events.isNotEmpty()) {
                SmallTitle(text = "日程 · ${hall.events.size}")
                ListGroup(
                    hall.events.map { item ->
                        ListRow(
                            title = item.title,
                            summary = listOf(item.time, item.place).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = { openUrl(GZUS_EHALL_HOME) },
                        )
                    },
                )
            }
            if (hall.affairs.isEmpty()) {
                SmallTitle(text = "办事")
                EmptyLine("没有办事事项")
            } else {
                SmallTitle(text = "办事 · ${hall.affairs.size}")
                ListGroup(
                    hall.affairs.map { item ->
                        ListRow(
                            title = item.name,
                            summary = item.type.ifBlank { "办事大厅" },
                            onClick = {
                                if (item.isLeave()) nav.open(Route.Leave) else openUrl(item.url.ifBlank { GZUS_EHALL_HOME })
                            },
                        )
                    },
                )
            }
        }
    }
}

/** 空分组只占一行小字，不再用 88dp 的空卡把真正有内容的分组挤到屏幕外。 */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        style = MiuixTheme.textStyles.footnote1,
    )
}
