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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.coursePickBrief
import cn.edu.gzus.qingke.data.isLow
import cn.edu.gzus.qingke.data.leaveSummary
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.showsGzusHall
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.data.utilityBrief
import cn.edu.gzus.qingke.data.xiaoaiImportSummary
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.BadgeTone
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun JwxtScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
) {
    val courses = uniqueCourses(snapshot.slots)
    val school = snapshot.resolved()
    val jwxt = school.jwxtName
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(16.dp))
        ScreenHeader(
            "",
            "服务",
            if (snapshot.session.loggedIn) "" else "多数功能要先登录",
        )
        if (!snapshot.session.loggedIn && !snapshot.hasTimetable) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "还没有服务数据",
                summary = "去「我的」登录$jwxt。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        }
        if (snapshot.showsGzusHall()) {
            SmallTitle(text = "常用")
            ListGroup(
                listOf(
                    ListRow(
                        title = "请假",
                        summary = when {
                            snapshot.hall.leaves.isNotEmpty() || snapshot.session.loggedIn -> snapshot.hall.leaveSummary()
                            else -> "门户登录后同步"
                        },
                        onClick = { nav.open(Route.Leave) },
                    ),
                    ListRow(
                        title = "宿舍水电",
                        summary = snapshot.utilityBrief(),
                        badge = if (snapshot.utility.isLow()) "该充值了" else "",
                        tone = BadgeTone.Warn,
                        onClick = { nav.open(Route.Utility) },
                    ),
                    ListRow(
                        title = "办事大厅",
                        summary = when {
                            snapshot.hall.todos.isNotEmpty() -> "广软事务中心"
                            snapshot.hall.ready -> "消息、办事、日程"
                            snapshot.hall.error.isNotBlank() -> snapshot.hall.error
                            snapshot.session.loggedIn -> "要用统一身份认证登录才能同步"
                            else -> "门户登录后同步"
                        },
                        badge = if (snapshot.hall.todos.isNotEmpty()) "${snapshot.hall.todos.size} 条待办" else "",
                        tone = BadgeTone.Accent,
                        onClick = { nav.open(Route.Hall) },
                    ),
                ),
            )
        }
        SmallTitle(text = "教务")
        ListGroup(
            buildList {
                add(
                    ListRow(
                        title = "考试",
                        summary = if (snapshot.exams.isEmpty()) "暂无安排" else "考场、座位号和时间",
                        badge = if (snapshot.exams.isEmpty()) "" else "${snapshot.exams.size} 场",
                        tone = BadgeTone.Accent,
                        onClick = { nav.open(Route.Exams) },
                    ),
                )
                add(
                    ListRow(
                        title = "通知",
                        summary = when {
                            snapshot.notices.isNotEmpty() -> snapshot.notices.first().title
                            snapshot.session.loggedIn -> "同步后出现"
                            else -> "登录后同步"
                        },
                        badge = if (snapshot.notices.isEmpty()) "" else "${snapshot.notices.size} 条",
                        tone = BadgeTone.Accent,
                        onClick = { nav.open(Route.Notices) },
                    ),
                )
                add(
                    ListRow(
                        title = "空教室",
                        summary = when {
                            !school.supportsFreeRooms -> "${jwxt}没有空教室查询"
                            snapshot.session.loggedIn -> "按星期和大节向${jwxt}查询"
                            else -> "登录后才能查"
                        },
                        onClick = { nav.open(Route.EmptyRoom) },
                    ),
                )
                if (school.supportsCoursePick) {
                    add(ListRow(title = "选课", summary = snapshot.coursePickBrief(), onClick = { nav.open(Route.CoursePick) }))
                }
            },
        )
        SmallTitle(text = "工具")
        ListGroup(
            listOf(
                ListRow(
                    title = "导入小爱课程表",
                    summary = xiaoaiImportSummary(snapshot),
                    onClick = { nav.open(Route.XiaoaiImport) },
                ),
            ),
        )
        if (courses.isNotEmpty() || snapshot.practices.isNotEmpty()) {
            SmallTitle(text = "已选课程 · ${courses.size + snapshot.practices.size} 门")
            ListGroup(
                courses.map { course ->
                    ListRow(
                        title = course.courseName,
                        summary = listOf(course.teacher, course.assess).filter { it.isNotBlank() }.joinToString(" · "),
                        badge = course.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" }.orEmpty(),
                        tone = BadgeTone.Muted,
                        onClick = { nav.open(Route.Course(course.courseId)) },
                    )
                } + snapshot.practices.map { item ->
                    ListRow(
                        title = item.name,
                        summary = listOf(item.weeks, item.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                        badge = "实践",
                        tone = BadgeTone.Muted,
                    )
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 服务二级页共用的确认框：提交选课、提交请假、覆盖小爱课表这类发出去就收不回的操作，
 * 先让人看一眼要提交什么再点。文案放 [message]，多行用换行分开。
 */
@Composable
internal fun JwxtConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onBackground)
            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp,
                ) { Text("取消") }
                Button(
                    onClick = {
                        onDismiss()
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(confirmText) }
            }
        }
    }
}
