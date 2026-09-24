package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.layout.Arrangement
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
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.InfoCard
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
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoCard(
                title = "请假",
                summary = hall.leaveSummary(),
                onClick = { nav.open(Route.Leave) },
            )
        }
        when {
            !cas -> {
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    title = "还不能同步大厅",
                    summary = "「我的」里把广软登录渠道换成统一身份认证，再登录一次。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
            !snapshot.session.loggedIn -> {
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    title = "还没有大厅数据",
                    summary = "用统一身份认证登录后，待办、消息和办事会同步到这里。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
            hall.error.isNotBlank() && !hall.ready -> {
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    title = "大厅这次没同步上",
                    summary = hall.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { nav.goTab(TabDest.Mine) },
                )
            }
        }
        if (cas && snapshot.session.loggedIn && (hall.ready || hall.todos.isNotEmpty() || hall.messages.isNotEmpty() || hall.affairs.isNotEmpty() || hall.events.isNotEmpty() || hall.leaves.isNotEmpty())) {
            if (hall.leaves.isNotEmpty()) {
                SmallTitle(text = "最近请假")
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hall.leaves.take(3).forEach { item ->
                        InfoCard(
                            title = item.title,
                            summary = listOf(item.status, item.time).filter { it.isNotBlank() }.joinToString(" · "),
                            height = null,
                            onClick = { nav.open(Route.Leave) },
                        )
                    }
                }
            }
            if (hall.todos.isEmpty()) {
                SmallTitle(text = "待办")
                EmptyHint("没有待办或申请")
            } else {
                SmallTitle(text = "待办 · ${hall.todos.size}")
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hall.todos.forEach { item ->
                        InfoCard(
                            title = item.title,
                            summary = listOf(item.status, item.time).filter { it.isNotBlank() }.joinToString(" · "),
                            height = null,
                            onClick = {
                                if (item.isLeave()) nav.open(Route.Leave) else openUrl(item.url.ifBlank { GZUS_EHALL_HOME })
                            },
                        )
                    }
                }
            }
            if (hall.messages.isEmpty()) {
                SmallTitle(text = "消息")
                EmptyHint("没有大厅消息")
            } else {
                SmallTitle(text = "消息 · ${hall.messages.size}")
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hall.messages.forEach { item ->
                        InfoCard(
                            title = item.title,
                            summary = listOf(item.time, item.content.takeIf { it.isNotBlank() }).filter { !it.isNullOrBlank() }.joinToString("\n"),
                            height = null,
                            onClick = { openUrl(GZUS_EHALL_MESSAGE) },
                        )
                    }
                }
            }
            if (hall.events.isNotEmpty()) {
                SmallTitle(text = "日程 · ${hall.events.size}")
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hall.events.forEach { item ->
                        InfoCard(
                            title = item.title,
                            summary = listOf(item.time, item.place).filter { it.isNotBlank() }.joinToString(" · "),
                            height = null,
                            onClick = { openUrl(GZUS_EHALL_HOME) },
                        )
                    }
                }
            }
            if (hall.affairs.isEmpty()) {
                SmallTitle(text = "办事")
                EmptyHint("没有办事事项")
            } else {
                SmallTitle(text = "办事 · ${hall.affairs.size}")
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hall.affairs.forEach { item ->
                        InfoCard(
                            title = item.name,
                            summary = item.type.ifBlank { "办事大厅" },
                            height = null,
                            onClick = {
                                if (item.isLeave()) nav.open(Route.Leave) else openUrl(item.url.ifBlank { GZUS_EHALL_HOME })
                            },
                        )
                    }
                }
            }
        }
    }
}
