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
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import top.yukonga.miuix.kmp.basic.SmallTitle

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
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoCard(
                    title = "请假",
                    summary = when {
                        snapshot.hall.leaves.isNotEmpty() || snapshot.session.loggedIn -> snapshot.hall.leaveSummary()
                        else -> "门户登录后同步"
                    },
                    onClick = { nav.open(Route.Leave) },
                )
                InfoCard(
                    title = if (snapshot.utility.isLow()) "宿舍水电 · 该充值了" else "宿舍水电",
                    summary = snapshot.utilityBrief(),
                    height = null,
                    center = true,
                    tintTitle = snapshot.utility.isLow(),
                    onClick = { nav.open(Route.Utility) },
                )
            }
        }
        SmallTitle(text = "教务")
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoCard(
                title = "通知",
                summary = when {
                    snapshot.notices.isNotEmpty() -> "${snapshot.notices.size} 条"
                    snapshot.session.loggedIn -> "同步后出现"
                    else -> "登录后同步"
                },
                onClick = { nav.open(Route.Notices) },
            )
            InfoCard(
                title = "空教室",
                summary = when {
                    !school.supportsFreeRooms -> "${jwxt}没有空教室查询"
                    snapshot.session.loggedIn -> "按星期和大节向${jwxt}查询"
                    else -> "登录后才能查"
                },
                onClick = { nav.open(Route.EmptyRoom) },
            )
            InfoCard(
                title = "考试",
                summary = if (snapshot.exams.isEmpty()) "暂无安排" else "${snapshot.exams.size} 场",
                onClick = { nav.open(Route.Exams) },
            )
            if (school.supportsCoursePick) {
                InfoCard(
                    title = "选课",
                    summary = snapshot.coursePickBrief(),
                    onClick = { nav.open(Route.CoursePick) },
                )
            }
            InfoCard(
                title = "导入小爱课程表",
                summary = xiaoaiImportSummary(snapshot),
                onClick = { nav.open(Route.XiaoaiImport) },
            )
        }
        if (snapshot.showsGzusHall()) {
            SmallTitle(text = "办事")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoCard(
                    title = "办事大厅",
                    summary = when {
                        snapshot.hall.todos.isNotEmpty() -> "${snapshot.hall.todos.size} 条待办 · 广软事务中心"
                        snapshot.hall.ready -> "消息、办事、日程"
                        snapshot.hall.error.isNotBlank() -> snapshot.hall.error
                        snapshot.session.loggedIn -> "要用统一身份认证登录才能同步"
                        else -> "门户登录后同步"
                    },
                    onClick = { nav.open(Route.Hall) },
                )
            }
        }
        if (courses.isNotEmpty() || snapshot.practices.isNotEmpty()) {
            SmallTitle(text = "已选课程")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                courses.forEach { course ->
                    InfoCard(
                        title = course.courseName,
                        summary = listOf(course.teacher, course.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" }, course.assess)
                            .filter { !it.isNullOrBlank() }
                            .joinToString(" · "),
                        onClick = { nav.open(Route.Course(course.courseId)) },
                    )
                }
                snapshot.practices.forEach { item ->
                    InfoCard(
                        title = item.name,
                        summary = listOf("实践", item.weeks, item.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
