package cn.edu.gzus.qingke.ui.today

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
import cn.edu.gzus.qingke.data.WeekdayFull
import cn.edu.gzus.qingke.data.activeIn
import cn.edu.gzus.qingke.data.combineMillis
import cn.edu.gzus.qingke.data.examSortKey
import cn.edu.gzus.qingke.data.forDate
import cn.edu.gzus.qingke.data.formatRemain
import cn.edu.gzus.qingke.data.greeting
import cn.edu.gzus.qingke.data.hasTermStart
import cn.edu.gzus.qingke.data.nextLesson
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.formatPeriodWithClock
import cn.edu.gzus.qingke.data.periodClockRange
import cn.edu.gzus.qingke.data.periodEnd
import cn.edu.gzus.qingke.data.periodStart
import cn.edu.gzus.qingke.data.resolvedCurrentWeek
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.isLeave
import cn.edu.gzus.qingke.data.showsGzusHall
import cn.edu.gzus.qingke.data.utilityBrief
import cn.edu.gzus.qingke.data.resolvedWeekCount
import cn.edu.gzus.qingke.data.teachingWeeks
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.CanvasHeroHeight
import cn.edu.gzus.qingke.ui.components.HeroCard
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.StatRow
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun TodayScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
    onSync: () -> Unit,
) {
    val now = nowDateTime()
    val week = resolvedCurrentWeek(snapshot.settings, now.date)
    val weekCount = snapshot.settings.resolvedWeekCount(snapshot.slots)
    val weekday = weekdayIndex(now.date)
    val todaySlots = snapshot.slots.forDate(now.date, snapshot.settings, now.date)
    val weekPractices = if (week >= 1) snapshot.practices.filter { it.activeIn(week) } else emptyList()
    val school = snapshot.resolved()
    val hasClock = school.hasPeriodClock
    val next = if (hasClock) nextLesson(snapshot.slots, now.date, snapshot.settings, now.date, now.time) else null
    val hero = next?.first ?: if (!hasClock) todaySlots.firstOrNull() else null
    val remain = next?.second
    val startMillis = if (hasClock) hero?.let { combineMillis(now.date, periodStart(it.period)) } ?: 0L else 0L
    val endMillis = if (hasClock) hero?.let { combineMillis(now.date, periodEnd(it.period)) } ?: 0L else 0L
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val inClass = hasClock && remain != null && remain <= 0 && hero != null
    val progress = when {
        !inClass || endMillis <= startMillis -> 0f
        nowMs <= startMillis -> 0f
        nowMs >= endMillis -> 1f
        else -> ((nowMs - startMillis).toFloat() / (endMillis - startMillis).toFloat()).coerceIn(0f, 1f)
    }
    val weekdayLabel = WeekdayFull.getOrElse(weekday - 1) { "" }.replace("星期", "周")
    val source = when {
        snapshot.session.loggedIn -> "已同步"
        snapshot.hasTimetable -> "本地课表"
        else -> "未登录"
    }
    val subtitle = buildList {
        val term = listOf(snapshot.profile.yearName, snapshot.profile.termLabel).filter { it.isNotBlank() }.joinToString(" ")
        if (term.isNotBlank()) add(term)
        if (snapshot.hasTimetable && week >= 1) add("第${week}周")
        if (snapshot.profile.campus.isNotBlank()) add(snapshot.profile.campus)
    }.joinToString(" · ").ifBlank {
        if (snapshot.session.loggedIn) "已登录，还没有课表" else "登录后同步${school.jwxtName}课表"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(16.dp))
        ScreenHeader(
            eyebrow = "${greeting(now.hour)} · $source",
            title = "${now.date.monthNumber}月${now.date.dayOfMonth}日 $weekdayLabel",
            subtitle = subtitle,
        )
        Spacer(Modifier.height(12.dp))
        when {
            hero != null -> {
                val eta = when {
                    remain == null -> "今日课程"
                    remain <= 0 -> "正在上课"
                    else -> "下一节 · ${formatRemain(remain)}后"
                }
                HeroCard(
                    title = hero.courseName,
                    eyebrow = eta,
                    facts = listOf(
                        "节次" to (hero.periodLabel.ifBlank { hero.period }.let { if (it.endsWith("节")) it else "${it}节" }),
                        "时间" to if (hasClock) periodClockRange(hero.period).replace("-", "–") else "",
                        "地点" to hero.room,
                        "老师" to hero.teacher,
                    ),
                    progress = if (inClass) progress else null,
                    progressLabel = if (inClass) {
                        val left = ((endMillis - nowMs) / 60_000L).toInt().coerceAtLeast(0)
                        "还剩 ${left} 分"
                    } else {
                        ""
                    },
                    onClick = { nav.open(Route.Course(hero.courseId)) },
                )
            }
            todaySlots.isNotEmpty() -> InfoCard(
                title = "今天的课上完了",
                summary = "第${week}周$weekdayLabel 共 ${todaySlots.size} 节。",
                modifier = Modifier.padding(horizontal = 16.dp),
                height = CanvasHeroHeight,
                onClick = { nav.goTab(TabDest.Timetable) },
            )
            snapshot.hasTimetable && week < 1 -> InfoCard(
                title = "学期还没开始",
                summary = "第1周还没到。到「课表」看后面的周。",
                modifier = Modifier.padding(horizontal = 16.dp),
                height = CanvasHeroHeight,
                onClick = { nav.goTab(TabDest.Timetable) },
            )
            snapshot.hasTimetable && weekCount > 0 && week > weekCount -> InfoCard(
                title = "不在本学期周次",
                summary = "${school.jwxtName}这学期到第${weekCount}周。",
                modifier = Modifier.padding(horizontal = 16.dp),
                height = CanvasHeroHeight,
                onClick = { nav.goTab(TabDest.Timetable) },
            )
            snapshot.hasTimetable -> InfoCard(
                title = "今天没有理论课",
                summary = buildString {
                    append("第${week}周$weekdayLabel 课表是空的。")
                    if (weekPractices.isNotEmpty()) append(" 本周有实践课。")
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                height = CanvasHeroHeight,
                onClick = { nav.goTab(TabDest.Timetable) },
            )
            else -> InfoCard(
                title = "还没有课表",
                summary = if (snapshot.session.loggedIn) "这一学期还没有课。" else "去「我的」登录${school.jwxtName}。",
                modifier = Modifier.padding(horizontal = 16.dp),
                height = CanvasHeroHeight,
                onClick = { if (snapshot.session.loggedIn) onSync() else nav.goTab(TabDest.Mine) },
            )
        }
        if (snapshot.hasTimetable && !snapshot.settings.hasTermStart()) {
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "先选第1周周一",
                summary = "「我的」里选开学那周的周一，首页和课表的日期才准。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        }
        if (snapshot.showsGzusHall()) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "宿舍水电",
                height = null,
                center = true,
                summary = snapshot.utilityBrief(),
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.open(Route.Utility) },
            )
            val hallTodo = snapshot.hall.todos.firstOrNull()
            if (hallTodo != null) {
                Spacer(Modifier.height(8.dp))
                InfoCard(
                    title = hallTodo.title,
                    summary = listOf(
                        hallTodo.status.ifBlank { "办事大厅" },
                        hallTodo.time,
                        if (snapshot.hall.todos.size > 1) "共 ${snapshot.hall.todos.size} 条待办" else null,
                    ).filter { !it.isNullOrBlank() }.joinToString(" · "),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { if (hallTodo.isLeave()) nav.open(Route.Leave) else nav.open(Route.Hall) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val stats = buildList {
            add(todaySlots.size.toString() to "今天课程")
            add(uniqueCourses(snapshot.slots).size.toString() to "学期课程")
            add(teachingWeeks(snapshot.slots).toString() to "教学周")
            if (snapshot.exams.isNotEmpty()) add(snapshot.exams.size.toString() to "考试")
        }
        StatRow(
            items = stats,
            onClicks = if (snapshot.exams.isEmpty()) emptyList() else listOf(null, null, null, { nav.open(Route.Exams) }),
        )
        if (snapshot.exams.isNotEmpty()) {
            SmallTitle(text = "考试")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val exams = snapshot.exams.sortedBy { examSortKey(it.time) }
                exams.take(5).forEach { exam ->
                    InfoCard(
                        title = exam.courseName,
                        summary = listOf(exam.time, exam.room, exam.seat, exam.status)
                            .filter { it.isNotBlank() }
                            .joinToString("\n"),
                        height = null,
                        onClick = { nav.open(Route.Exams) },
                    )
                }
                if (exams.size > 5) {
                    InfoCard(
                        title = "全部考试",
                        summary = "还有 ${exams.size - 5} 场",
                        onClick = { nav.open(Route.Exams) },
                    )
                }
            }
        }
        SmallTitle(text = "今天的课")
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (todaySlots.isEmpty() && weekPractices.isEmpty()) {
                InfoCard(
                    title = if (snapshot.hasTimetable) "这一天没有理论课" else "登录后会出现今日课程",
                    summary = "",
                )
            } else {
                todaySlots.forEach { slot ->
                    InfoCard(
                        title = "${formatPeriodWithClock(slot.period, slot.periodLabel, hasClock)}  ${slot.courseName}",
                        summary = listOf(slot.room, slot.teacher, "第${week}周").filter { it.isNotBlank() }.joinToString("\n"),
                        height = null,
                        onClick = { nav.open(Route.Course(slot.courseId)) },
                    )
                }
                weekPractices.forEach { item ->
                    InfoCard(
                        title = item.name,
                        summary = listOf("实践", item.weeks, item.teacher).filter { it.isNotBlank() }.joinToString("\n"),
                        height = null,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
