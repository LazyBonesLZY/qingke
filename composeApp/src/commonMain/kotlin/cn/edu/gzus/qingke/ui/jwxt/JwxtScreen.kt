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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import cn.edu.gzus.qingke.ui.components.ConfirmDialog
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.Tone
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
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
        Spacer(Modifier.height(Space.l))
        ScreenHeader(
            "",
            "服务",
            if (snapshot.session.loggedIn) "" else "多数功能要先登录",
        )
        if (!snapshot.session.loggedIn && !snapshot.hasTimetable) {
            Spacer(Modifier.height(Space.m))
            EmptyState(
                title = "还没有服务数据",
                detail = "登录${jwxt}后，考试、通知和选课会同步到这里。",
                actionText = "去登录",
                onAction = { nav.goTab(TabDest.Mine) },
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
                        tone = if (snapshot.utility.isLow()) BadgeTone.Warn else BadgeTone.Neutral,
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
        Spacer(Modifier.height(Space.l))
    }
}

/**
 * 服务二级页共用的确认框：提交选课、提交请假、覆盖小爱课表这类发出去就收不回的操作，
 * 先让人看一眼要提交什么再点。文案放 [message]，多行用换行分开。
 * 样式统一走 [ConfirmDialog]，这里只保留旧名字给各二级页调用。
 */
@Composable
internal fun JwxtConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = title,
        message = message,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * 服务二级页的「正在加载」卡：转圈 + 一句在等什么。
 * 以前用一张固定高度的空提示卡，看不出是在加载还是没数据。
 */
@Composable
internal fun JwxtLoadingCard(text: String, modifier: Modifier = Modifier.padding(horizontal = Inset.page)) {
    Card(modifier = modifier.fillMaxWidth(), insideMargin = Inset.card) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            InfiniteProgressIndicator()
            Text(
                text,
                style = MiuixTheme.textStyles.body2,
                color = Tone.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
