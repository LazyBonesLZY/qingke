package cn.edu.gzus.qingke.ui.today

import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.delay
import cn.edu.gzus.qingke.data.periodText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import kotlinx.coroutines.isActive
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.periodStartOf
import cn.edu.gzus.qingke.data.periodEndOf
import cn.edu.gzus.qingke.data.clockKnown
import cn.edu.gzus.qingke.data.clockRangeOf
import cn.edu.gzus.qingke.QingkeOnResume
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.WeekdayFull
import cn.edu.gzus.qingke.data.activeIn
import cn.edu.gzus.qingke.data.combineMillis
import cn.edu.gzus.qingke.data.examSortKey
import cn.edu.gzus.qingke.data.forDate
import cn.edu.gzus.qingke.data.liveTiming
import cn.edu.gzus.qingke.data.greeting
import cn.edu.gzus.qingke.data.hasTermStart
import cn.edu.gzus.qingke.data.isLow
import cn.edu.gzus.qingke.data.nextExam
import cn.edu.gzus.qingke.data.nextLesson
import cn.edu.gzus.qingke.data.nowDateTime
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
    // 首页显示的是「下一节还有几分钟」「还剩几分」和上课进度，必须自己按时间重算。
    // 以前 now 只在 composition 时取一次，整屏就冻住了（切 tab 或跨天才刷新）。
    var tick by remember { mutableIntStateOf(0) }
    // 从后台切回来立刻推一次：帧时钟在后台是停的，回来虽然也会自己推进，
    // 但显式再推一次能保证第一帧就是当前时间，不会闪一下旧数据。
    QingkeOnResume { tick++ }
    val now = remember(tick) { nowDateTime() }
    val week = resolvedCurrentWeek(snapshot.settings, now.date)
    val weekCount = snapshot.settings.resolvedWeekCount(snapshot.slots)
    val weekday = weekdayIndex(now.date)
    // 这几个只跟「哪一天/哪份数据」有关，跟秒无关，按天缓存，免得每秒重算。
    val school = remember(snapshot) { snapshot.resolved() }
    val todaySlots = remember(snapshot, now.date) {
        val day = snapshot.slots.forDate(now.date, snapshot.settings, now.date, snapshot.scheduleAdjust)
        // 按真实开课时间排：「中午」这类没编号的块按节次号排会落到全天最后。
        if (school.hasPeriodClock) {
            day.sortedBy { clockToMinutes(school.periodStartOf(it.period)) ?: Int.MAX_VALUE }
        } else {
            day
        }
    }
    val weekPractices = remember(snapshot, week) {
        if (week >= 1) snapshot.practices.filter { it.activeIn(week) } else emptyList()
    }
    val hasClock = school.hasPeriodClock
    val next = if (hasClock) {
        nextLesson(
            snapshot.slots, now.date, snapshot.settings, now.date, now.time,
            snapshot.scheduleAdjust, school.periodBlocks,
        )
    } else {
        null
    }
    // 学校只有部分节次带时间时，没时间的课算不出「下一节」，但也不能因此说今天的课上完了。
    val hero = next?.first ?: todaySlots.firstOrNull { !hasClock || !school.clockKnown(it.period) }
    val remain = next?.second
    val startMillis = if (hasClock) hero?.let { combineMillis(now.date, school.periodStartOf(it.period)) } ?: 0L else 0L
    val endMillis = if (hasClock) hero?.let { combineMillis(now.date, school.periodEndOf(it.period)) } ?: 0L else 0L
    val nowMs = remember(tick) { Clock.System.now().toEpochMilliseconds() }
    val timing = if (hasClock && hero != null) liveTiming(nowMs, startMillis, endMillis) else null
    val inClass = timing?.inClass == true
    // 页面上没有秒：分钟文案、问候语、日期都只在整分钟变（作息都是整分钟），
    // 只有上课进度条要更细，上课时每 10 秒推一次就够看。以前每秒整页重组。
    // 用对齐的 delay 而不是 withFrameNanos：帧时钟循环会让前台每个 vsync 都产一帧。
    val inClassNow by rememberUpdatedState(inClass)
    LaunchedEffect(Unit) {
        while (isActive) {
            val ms = Clock.System.now().toEpochMilliseconds()
            val step = if (inClassNow) 10_000L else 60_000L
            delay(step - ms % step)
            tick++
        }
    }
    val progress = when {
        !inClass || endMillis <= startMillis -> 0f
        nowMs <= startMillis -> 0f
        nowMs >= endMillis -> 1f
        else -> ((nowMs - startMillis).toFloat() / (endMillis - startMillis).toFloat()).coerceIn(0f, 1f)
    }
    val weekdayLabel = WeekdayFull.getOrElse(weekday - 1) { "" }.replace("星期", "周")
    val courseCount = remember(snapshot.slots) { uniqueCourses(snapshot.slots).size }
    val termWeeks = remember(snapshot.slots) { teachingWeeks(snapshot.slots) }
    val soonestExam = remember(snapshot.exams, now.date) { nextExam(snapshot.exams, now.date) }
    // 首页只露 5 场：没考的按日期在前，日期待定的随后，已考的最近的排前面放最后。
    // 以前纯按日期升序，学期末已考的会把真正要准备的挤出前 5 条。
    val sortedExams = remember(snapshot.exams, now.date) {
        val todayKey = now.date.toEpochDays().toLong()
        val (past, coming) = snapshot.exams.partition { examSortKey(it.time) < todayKey }
        coming.sortedBy { examSortKey(it.time) } + past.sortedByDescending { examSortKey(it.time) }
    }
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
            eyebrow = "",
            title = "${now.date.monthNumber}月${now.date.dayOfMonth}日 $weekdayLabel",
            // 没课表时副标题已经写了「登录后同步…/已登录，还没有课表」，再挂来源就重复甚至矛盾（已同步 · 还没有课表）。
            subtitle = listOf(greeting(now.hour), if (snapshot.hasTimetable) source else "", subtitle)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        )
        Spacer(Modifier.height(12.dp))
        // 没选第1周周一时下面的「今天的课」可能整个对错了天，这张提示要放在最前面。
        if (snapshot.hasTimetable && !snapshot.settings.hasTermStart()) {
            InfoCard(
                title = "先选第1周周一",
                summary = "点这里选开学那周的周一，日期才对得上。",
                modifier = Modifier.padding(horizontal = 16.dp),
                tintTitle = true,
                onClick = { nav.openTermStart() },
            )
            Spacer(Modifier.height(12.dp))
        }
        when {
            hero != null -> {
                val clockRange = if (hasClock) school.clockRangeOf(hero.period) else ""
                val minutes = timing?.remainingMinutes?.toInt() ?: remain
                NextLessonHero(
                    model = HeroModel(
                        title = hero.courseName,
                        status = when {
                            remain == null -> if (todaySlots.size > 1) "今天 ${todaySlots.size} 节" else "今天的课"
                            inClass -> "上课中"
                            else -> "下一节"
                        },
                        periodLine = listOf(periodText(hero.period, hero.periodLabel), clockRange.replace("-", "–"))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        facts = listOf("地点" to hero.room, "老师" to hero.teacher),
                        countdown = if (remain == null || minutes == null) emptyList() else countdownParts(minutes),
                        countdownSuffix = if (inClass) "后下课" else "后上课",
                        progress = if (inClass) progress else null,
                        startClock = clockRange.substringBefore("-", ""),
                        endClock = clockRange.substringAfter("-", ""),
                        inClass = inClass,
                    ),
                    onClick = { nav.open(Route.Course(hero.courseId)) },
                )
            }
            todaySlots.isNotEmpty() -> InfoCard(
                title = "今天的课上完了",
                // 没开学/没选开学日时 week 可能是 0，别显示「第0周」。
                summary = "${if (week >= 1) "第${week}周" else ""}$weekdayLabel 共 ${todaySlots.size} 节。",
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
                title = "这学期的课已结束",
                summary = "${school.jwxtName}这学期共 ${weekCount} 周。",
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
                // 已登录时点卡片就是重新同步，文案要说出来，不然像是一张点了没反应的说明。
                summary = if (snapshot.session.loggedIn) "这学期还没拿到课，点这里重新同步。" else "去「我的」登录${school.jwxtName}。",
                modifier = Modifier.padding(horizontal = 16.dp),
                height = CanvasHeroHeight,
                onClick = { if (snapshot.session.loggedIn) onSync() else nav.goTab(TabDest.Mine) },
            )
        }
        // 今天没课时上面的大卡片已经说清楚了，这里不再重复一张空卡。
        if (todaySlots.isNotEmpty()) {
            SmallTitle(text = "今天的课 · ${todaySlots.size} 节")
            val nowMinutes = now.hour * 60 + now.minute
            TodayTimeline(
                rows = todaySlots.map { slot ->
                    val range = if (hasClock) school.clockRangeOf(slot.period) else ""
                    val endMinutes = range.substringAfter("-", "").let(::clockToMinutes)
                    val isHero = hero != null && slot.courseId == hero.courseId && slot.period == hero.period
                    TimelineRow(
                        title = slot.courseName,
                        detail = listOf(
                            if (range.isNotBlank()) periodText(slot.period, slot.periodLabel) else "",
                            slot.room,
                            slot.teacher,
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        startClock = range.substringBefore("-", "").ifBlank { periodText(slot.period, slot.periodLabel) },
                        endClock = range.substringAfter("-", ""),
                        state = when {
                            isHero && remain != null && inClass -> LessonState.Now
                            isHero && remain != null -> LessonState.Next
                            endMinutes != null && endMinutes <= nowMinutes -> LessonState.Past
                            else -> LessonState.Later
                        },
                        accentKey = slot.courseId.ifBlank { slot.courseName },
                        onClick = { nav.open(Route.Course(slot.courseId)) },
                    )
                },
            )
        }
        if (snapshot.showsGzusHall()) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = if (snapshot.utility.isLow()) "宿舍水电 · 该充值了" else "宿舍水电",
                height = null,
                center = true,
                tintTitle = snapshot.utility.isLow(),
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
        Spacer(Modifier.height(12.dp))
        val stats = buildList {
            add(todaySlots.size.toString() to "今天课程")
            add(courseCount.toString() to "学期课程")
            add(termWeeks.toString() to "教学周")
            if (snapshot.exams.isNotEmpty()) {
                val days = soonestExam?.second
                add(
                    when {
                        days == null -> snapshot.exams.size.toString() to "考试"
                        days == 0 -> "今天" to "最近考试"
                        else -> days.toString() to "天后考试"
                    },
                )
            }
        }
        StatRow(
            items = stats,
            onClicks = listOf(
                { nav.goTab(TabDest.Timetable) },
                null,
                null,
                if (snapshot.exams.isEmpty()) null else ({ nav.open(Route.Exams) }),
            ),
        )
        if (snapshot.exams.isNotEmpty()) {
            SmallTitle(text = "考试 · ${snapshot.exams.size} 场")
            val todayKey = now.date.toEpochDays().toLong()
            ExamList(
                rows = sortedExams.take(5).map { exam ->
                    val key = examSortKey(exam.time)
                    val days = if (key == Long.MAX_VALUE) null else (key - todayKey).toInt()
                    ExamRow(
                        title = exam.courseName,
                        detail = listOf(exam.time, exam.room, exam.seat)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { exam.status },
                        badge = when {
                            days == null -> exam.status.takeIf { it != "未安排" }.orEmpty()
                            days < 0 -> "已考"
                            days == 0 -> "今天"
                            else -> "$days 天"
                        },
                        urgent = days != null && days in 0..3,
                        past = days != null && days < 0,
                    )
                },
                more = (sortedExams.size - 5).coerceAtLeast(0),
                onClick = { nav.open(Route.Exams) },
            )
        }
        if (weekPractices.isNotEmpty()) {
            SmallTitle(text = "本周实践")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weekPractices.forEach { item ->
                    InfoCard(
                        title = item.name,
                        summary = listOf(item.weeks.takeIf { it.isNotBlank() }?.let { if (it.endsWith("周")) it else "${it}周" }, item.teacher, item.note)
                            .filter { !it.isNullOrBlank() }
                            .joinToString("\n"),
                        height = null,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
