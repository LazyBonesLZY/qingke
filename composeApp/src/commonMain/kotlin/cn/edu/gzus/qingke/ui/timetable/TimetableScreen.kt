package cn.edu.gzus.qingke.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import cn.edu.gzus.qingke.QingkeOnResume
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.gzus.qingke.data.formatPeriod
import cn.edu.gzus.qingke.data.periodText
import cn.edu.gzus.qingke.data.clockRangeIn
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.LessonSlot
import cn.edu.gzus.qingke.data.PeriodBlock
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.activeIn
import cn.edu.gzus.qingke.data.compactCourseLines
import cn.edu.gzus.qingke.data.compactRoomName
import cn.edu.gzus.qingke.data.CloudScheduleAdjust
import cn.edu.gzus.qingke.data.adjustDayLabel
import cn.edu.gzus.qingke.data.HolidayCalendar
import cn.edu.gzus.qingke.data.HolidayDay
import cn.edu.gzus.qingke.data.chip
import cn.edu.gzus.qingke.data.forDate
import cn.edu.gzus.qingke.data.occupiesBlock
import cn.edu.gzus.qingke.data.formatMonthDayRange
import cn.edu.gzus.qingke.data.formatYearMonth
import cn.edu.gzus.qingke.data.hasTermStart
import cn.edu.gzus.qingke.data.lookup
import cn.edu.gzus.qingke.data.mondayOfTeachingWeek
import cn.edu.gzus.qingke.data.weekDateLabel
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.resolvedCurrentWeek
import cn.edu.gzus.qingke.data.resolvedWeekCount
import cn.edu.gzus.qingke.data.teachingWeekOn
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.data.weeksInUse
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.LocalQingkeWide
import cn.edu.gzus.qingke.ui.components.Radius
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import cn.edu.gzus.qingke.ui.components.Type
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val CourseTintsLight = listOf(
    Color(0xFFDCE9FF),
    Color(0xFFD8F3E4),
    Color(0xFFFFE6CC),
    Color(0xFFEADBFF),
    Color(0xFFFFD9D6),
    Color(0xFFD4F1F6),
    Color(0xFFDDE1FF),
    Color(0xFFFFF1C7),
    Color(0xFFFFDCEA),
    Color(0xFFD8F0D4),
)

private val CourseTintsDark = listOf(
    Color(0xFF2B4570),
    Color(0xFF2A5340),
    Color(0xFF6A4A28),
    Color(0xFF4A3868),
    Color(0xFF6A3836),
    Color(0xFF2A5358),
    Color(0xFF3A4070),
    Color(0xFF6A5A28),
    Color(0xFF6A3A50),
    Color(0xFF3A5336),
)

private val CourseInkLight = Color(0xFF1A1A1A)
private val CourseInkDark = Color(0xFFF3F3F3)

private val SlotCol = 32.dp
private val SlotColCompact = 24.dp
private val SlotColWithClock = 40.dp
private val SlotColWithClockCompact = 30.dp
private val WeekNumCol = 28.dp
// 网格是全应用唯一比 Space 刻度更密的地方：格间距 3/2dp，单独定义、只在本文件用。
private val CellGap = 3.dp
private val CellGapCompact = 2.dp
private val WeekCellHeight = 64.dp
private val WeekCellHeightCompact = 46.dp
private val MonthCellHeight = 58.dp
/** 格子圆角：常规取 Radius.small 的 8dp，紧凑模式格子窄，收到 6dp。卡片内边距按同心关系配（见 WeekGrid）。 */
private val CellRadius = 8.dp
private val CellRadiusCompact = 6.dp

/**
 * 课表网格专用字号。网格一格只有四十来 dp 宽，Type.tiny（11sp）放教室会挤掉课名，
 * 所以网格内部单独收一套，只有三级：表头/节次 → 课名 → 教室/时间/标记。
 */
private object GridType {
    val head = Type.tiny
    val headCompact = 10.sp
    val name = 12.sp
    val nameCompact = 10.sp
    val meta = 9.sp
    val metaCompact = 8.sp
}

private fun courseTint(key: String, dark: Boolean): Color {
    val hash = key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    val tints = if (dark) CourseTintsDark else CourseTintsLight
    return tints[hash % tints.size]
}

/** 解析 "#RRGGBB" / "RRGGBB" / "#AARRGGBB" / "AARRGGBB"，非法输入返回 null。 */
private fun parseHexColor(hex: String): Color? {
    val raw = hex.trim().removePrefix("#").trim()
    if (raw.length != 6 && raw.length != 8) return null
    if (!raw.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) Color(0xFF000000L or value) else Color(value)
}

/**
 * 自动取字色：亮底用深字，暗底用浅字。
 *
 * 必须按**合成后**的颜色算亮度——色块带透明度时会和卡片底色叠在一起，
 * 只按不透明基色判断，会在「深色底 + 低透明度」时选出和实画底色同色系的字
 * （例如强调蓝 30% 透明 → 实画是淡蓝，却按深蓝选出浅字，对比度掉到 1.3:1）。
 */
private fun autoInk(tint: Color, backdrop: Color): Color =
    if (tint.compositeOver(backdrop).luminance() > 0.6f) CourseInkLight else CourseInkDark

/** 课程色块底色：settings.courseTintHex 非空时全局覆盖，否则按 key 哈希取默认调色板，再套 settings.courseTintAlpha。 */
private fun resolveTint(settings: AppSettings, key: String, dark: Boolean): Color {
    val hex = settings.courseTintHex
    val base = if (hex.isNotBlank()) parseHexColor(hex) ?: courseTint(key, dark) else courseTint(key, dark)
    return base.copy(alpha = settings.courseTintAlpha.coerceIn(0.2f, 1f))
}

/**
 * 课程的「标识色」：列表竖条、月历圆点用。色块底色是很淡的马卡龙色，原样画成 3dp 竖条/5dp 圆点
 * 在白卡上几乎看不见，所以浅色下往黑压一点、深色下往白提一点，色相不变。
 */
private fun courseAccent(settings: AppSettings, key: String, dark: Boolean): Color {
    val base = resolveTint(settings, key, dark).copy(alpha = 1f)
    return if (dark) lerp(base, Color.White, 0.22f) else lerp(base, Color.Black, 0.16f)
}

/** 课程色块字色：auto 按合成后底色亮度、light/dark 强制、custom 用 courseInkHex。 */
private fun resolveInk(settings: AppSettings, tint: Color, backdrop: Color): Color =
    when (settings.courseInkMode) {
        "light" -> CourseInkLight
        "dark" -> CourseInkDark
        "custom" -> parseHexColor(settings.courseInkHex) ?: autoInk(tint, backdrop)
        else -> autoInk(tint, backdrop)
    }

/** 判断「是不是同一门课」用的键：courseId 缺失时退回课名，和课程配色的取键一致。 */
private fun LessonSlot.courseKey(): String = courseId.ifBlank { courseName }

/** 实践课周次补上「周」字，和首页的写法一致。 */
private fun weeksText(weeks: String): String =
    weeks.trim().let { if (it.isBlank() || it.endsWith("周")) it else "${it}周" }

/** 某一列里连续占着同一组课的一段（span 为跨了几个节次块）。 */
private class DayRun(val span: Int, val slots: List<LessonSlot>)

/**
 * 把一天切成若干 run：相邻节次块里是**同一组课**就并成一段，渲染成一条长块。
 * 一组课判等用 courseId（缺则课名）**排序后**的列表：连着两节的高数会合并，
 * "高数 1-2 节 + 英语 3-4 节"这种不同课不会被并。
 * 排序是必要的——同一块里有多门冲突课时，教务给的源顺序在不同行可能不同，
 * 不排序就会把本该合并的一组课判成不同。
 */
private fun runsForDay(daySlots: List<LessonSlot>, blocks: List<PeriodBlock>): List<DayRun> {
    val cells = blocks.map { block -> daySlots.filter { it.occupiesBlock(block) } }
    val keys = cells.map { cell -> cell.map { it.courseId.ifBlank { it.courseName } }.sorted() }
    val runs = mutableListOf<DayRun>()
    var i = 0
    while (i < cells.size) {
        var j = i
        // 课名为空的脏数据不参与合并，免得把不相干的课并到一起。
        if (keys[i].isNotEmpty() && keys[i].all { it.isNotBlank() }) {
            while (j + 1 < cells.size && keys[j + 1] == keys[i]) j++
        }
        runs += DayRun(j - i + 1, cells[i])
        i = j + 1
    }
    return runs
}

@Composable
fun TimetableScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
    onSync: () -> Unit = {},
) {
    // 从后台切回来时重组一次：跨过零点后「今天」高亮、本周和月历才会跟着换天。
    var resumeTick by remember { mutableIntStateOf(0) }
    QingkeOnResume { resumeTick++ }
    val today = resumeTick.let { nowDateTime().date }
    val settings = snapshot.settings
    val school = remember(snapshot) { snapshot.resolved() }
    val currentWeek = resolvedCurrentWeek(settings, today)
    val used = remember(snapshot.slots) { weeksInUse(snapshot.slots) }
    val weekLo = 1
    val weekHi = settings.resolvedWeekCount(snapshot.slots).coerceAtLeast(1)
    val dark = isSystemInDarkTheme()
    // 周/月切换要跨 tab 保留：切去成绩再回来，不该又被打回周视图。
    var mode by rememberSaveable { mutableIntStateOf(0) }
    // 同一格有多门课时，点开在网格下面列出来挑。
    var conflict by remember { mutableStateOf<List<LessonSlot>?>(null) }
    // 面板在网格下面，竖屏课表一长就落在屏幕外，看起来像「点了没反应」，所以弹出后滚过去。
    val conflictRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(conflict) {
        if (conflict != null) {
            withFrameNanos { }
            conflictRequester.bringIntoView()
        }
    }
    var viewWeek by remember(currentWeek, weekLo, weekHi) {
        mutableIntStateOf(currentWeek.coerceIn(weekLo, weekHi))
    }
    var monthAnchor by remember(today) { mutableStateOf(LocalDate(today.year, today.monthNumber, 1)) }
    var selectedDate by remember(today) { mutableStateOf(today) }

    val monday = mondayOfTeachingWeek(viewWeek.coerceAtLeast(1), settings, today)
    val sunday = monday.plus(DatePeriod(days = 6))
    // 只跟数据和所看的周有关，别每次重组都把整张课表过七遍。
    val weekSessions = remember(snapshot.slots, monday, settings, today, snapshot.scheduleAdjust) {
        (0..6).sumOf { offset ->
            snapshot.slots.forDate(
                monday.plus(DatePeriod(days = offset)),
                settings,
                today,
                snapshot.scheduleAdjust,
            ).size
        }
    }
    val weekPractices = remember(snapshot.practices, viewWeek) { snapshot.practices.filter { it.activeIn(viewWeek) } }
    val dated = settings.hasTermStart()
    val wide = LocalQingkeWide.current
    // 所看的周次、日期范围、年月在翻页器里都有，标题下只说「现在是第几周」，翻到别处也知道今天在哪。
    val subtitle = when {
        !snapshot.hasTimetable -> if (snapshot.session.loggedIn) "同步后再看整周网格" else "登录后从${school.jwxtName}同步"
        currentWeek < weekLo -> "学期还没开始 · 共 ${weekHi} 周"
        currentWeek > weekHi -> "这学期 ${weekHi} 周已经上完"
        else -> "现在第${currentWeek}周 · 共 ${weekHi} 周"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (wide) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(if (wide) Space.s else Space.l))
        ScreenHeader("", "课表", subtitle)
        if (!snapshot.hasTimetable) {
            Spacer(Modifier.height(Space.m))
            EmptyState(
                title = "还没有课表",
                detail = if (snapshot.session.loggedIn) "点下面立即同步，也可以下拉刷新" else "先去「我的」登录${school.jwxtName}",
                actionText = if (snapshot.session.loggedIn) "立即同步" else "去登录",
                onAction = { if (snapshot.session.loggedIn) onSync() else nav.goTab(TabDest.Mine) },
            )
            Spacer(Modifier.height(Space.l))
            return
        }
        if (!dated) {
            Spacer(Modifier.height(Space.m))
            HintCard(
                title = "还没选第1周周一",
                detail = "选了之后，周视图和月历才对得上真实日期",
                tone = Tone.warning(),
                onClick = { nav.openTermStart() },
            )
        }
        if (!wide) {
            Spacer(Modifier.height(Space.m))
            TabRowWithContour(
                tabs = listOf("周", "月"),
                selectedTabIndex = mode,
                onTabSelected = { mode = it; conflict = null },
                modifier = Modifier.padding(horizontal = Inset.page),
            )
            Spacer(Modifier.height(Space.s))
        }
        val prevWeek = { if (viewWeek > weekLo) { viewWeek -= 1; conflict = null } }
        val nextWeek = { if (viewWeek < weekHi) { viewWeek += 1; conflict = null } }
        // pointerInput 只按 weekLo/weekHi 重建，里面必须拿最新的回调：
        // viewWeek 的状态会随 currentWeek 重建，旧闭包改的是已经丢掉的那份，滑了没反应。
        val latestPrev by rememberUpdatedState(prevWeek)
        val latestNext by rememberUpdatedState(nextWeek)
        // 只挂在网格上：挂在整列上会和周次条的横向滚动抢手势，滚到头再滑就误翻周。
        val swipeWeeks = Modifier.pointerInput(weekLo, weekHi) {
            // 左右滑换周：累计位移过阈值才算，竖向滚动和点格子不受影响。
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = {
                    val threshold = 64.dp.toPx()
                    if (dragged > threshold) latestPrev() else if (dragged < -threshold) latestNext()
                },
            ) { change, amount ->
                dragged += amount
                change.consume()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (wide) Modifier.weight(1f) else Modifier),
            verticalAlignment = Alignment.Top,
        ) {
            if (wide || mode == 0) {
                Column(
                    Modifier
                        .weight(if (wide) 1.55f else 1f)
                        .then(if (wide) Modifier.fillMaxHeight() else Modifier),
                ) {
                    WeekPager(
                        week = viewWeek,
                        weekLo = weekLo,
                        weekHi = weekHi,
                        currentWeek = currentWeek,
                        range = formatMonthDayRange(monday, sunday),
                        sessions = weekSessions,
                        used = used,
                        onPrev = prevWeek,
                        onNext = nextWeek,
                        onPick = { viewWeek = it; conflict = null },
                        onToday = { viewWeek = currentWeek.coerceIn(weekLo, weekHi); conflict = null },
                    )
                    Spacer(Modifier.height(Space.s))
                    if (wide && weekPractices.isNotEmpty()) {
                        Text(
                            weekPractices.joinToString("、") { it.name }.let { "本周实践 · $it" },
                            modifier = Modifier.padding(start = Inset.page, end = Inset.page, bottom = Space.s),
                            style = MiuixTheme.textStyles.footnote1,
                            color = Tone.muted(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    WeekGrid(
                        slots = snapshot.slots,
                        monday = monday,
                        today = today,
                        holidays = snapshot.holidays,
                        aliases = settings.courseAliases,
                        blocks = school.periodBlocks,
                        hasClock = school.hasPeriodClock,
                        settings = settings,
                        adjust = snapshot.scheduleAdjust,
                        fit = wide,
                        compact = !wide && settings.compactTimetable,
                        modifier = swipeWeeks,
                        onOpen = { picked ->
                            // 同一门课在一格里有多条（单双周拆开、教务重复给）不算冲突，直接打开。
                            if (picked.distinctBy { it.courseKey() }.size == 1) {
                                nav.open(Route.Course(picked.first().courseId))
                            } else {
                                conflict = picked
                            }
                        },
                    )
                    conflict?.let { picked ->
                        Spacer(Modifier.height(Space.m))
                        ConflictPanel(
                            slots = picked,
                            settings = settings,
                            modifier = Modifier.bringIntoViewRequester(conflictRequester),
                            onOpen = { nav.open(Route.Course(it)) },
                            onClose = { conflict = null },
                        )
                    }
                    if (!wide && weekSessions == 0 && weekPractices.isEmpty()) {
                        Spacer(Modifier.height(Space.m))
                        EmptyState(
                            title = "这一周没有理论课",
                            detail = "左右滑动网格换周，或切到「月」看整月",
                        )
                    }
                    if (!wide && weekPractices.isNotEmpty()) {
                        SmallTitle(text = "本周实践")
                        LessonGroup(
                            weekPractices.map { item ->
                                LessonRowData(
                                    title = item.name,
                                    meta = weeksText(item.weeks),
                                    detail = item.teacher,
                                    tag = "实践",
                                )
                            },
                        )
                    }
                }
            }
            if (wide || mode == 1) {
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (wide) Modifier.fillMaxHeight().verticalScroll(rememberScrollState()) else Modifier),
                ) {
                    MonthPager(
                        title = formatYearMonth(monthAnchor),
                        onPrev = { monthAnchor = monthAnchor.minus(DatePeriod(months = 1)) },
                        onNext = { monthAnchor = monthAnchor.plus(DatePeriod(months = 1)) },
                        onToday = {
                            monthAnchor = LocalDate(today.year, today.monthNumber, 1)
                            selectedDate = today
                        },
                        showToday = monthAnchor.year != today.year || monthAnchor.monthNumber != today.monthNumber || selectedDate != today,
                    )
                    Spacer(Modifier.height(Space.s))
                    MonthGrid(
                        month = monthAnchor,
                        today = today,
                        selected = selectedDate,
                        settings = settings,
                        weekLo = weekLo,
                        weekHi = weekHi,
                        slots = snapshot.slots,
                        adjust = snapshot.scheduleAdjust,
                        holidays = snapshot.holidays,
                        onSelect = { date ->
                            selectedDate = date
                            val pickedWeek = teachingWeekOn(date, settings, today)
                            if (pickedWeek in weekLo..weekHi && pickedWeek != viewWeek) {
                                viewWeek = pickedWeek
                                conflict = null
                            }
                            if (date.monthNumber != monthAnchor.monthNumber || date.year != monthAnchor.year) {
                                monthAnchor = LocalDate(date.year, date.monthNumber, 1)
                            }
                        },
                    )
                    if (wide) {
                        Text(
                            "点一天，左边换到那一周",
                            modifier = Modifier.padding(horizontal = Inset.page, vertical = Space.s),
                            style = MiuixTheme.textStyles.footnote1,
                            color = Tone.muted(),
                        )
                    }
                    if (!wide) {
                        val daySlots = remember(snapshot.slots, selectedDate, settings, today, snapshot.scheduleAdjust) {
                            snapshot.slots.forDate(selectedDate, settings, today, snapshot.scheduleAdjust)
                        }
                        val dayWeek = teachingWeekOn(selectedDate, settings, today)
                        val inTerm = dayWeek in weekLo..weekHi
                        SmallTitle(
                            text = buildString {
                                append("${selectedDate.monthNumber}月${selectedDate.dayOfMonth}日 · 周${WeekdayNames.getOrElse(weekdayIndex(selectedDate) - 1) { "?" }}")
                                if (inTerm) append(" · 第${dayWeek}周")
                                snapshot.holidays.lookup(selectedDate)?.let { append(" · ").append(it.chip()) }
                                adjustDayLabel(selectedDate, settings, snapshot.scheduleAdjust, today)
                                    .takeIf { it.isNotBlank() }
                                    ?.let { append(" · ").append(it) }
                            },
                        )
                        if (daySlots.isEmpty()) {
                            // 没选第1周周一时日期本身就不准，直接给去选的入口；标题已说清的就不再重复一句。
                            EmptyState(
                                title = if (inTerm) "这天没有课" else "不在本学期周次里",
                                detail = when {
                                    !dated -> "还没选第1周周一，日期可能对不上"
                                    inTerm -> ""
                                    else -> "这学期共 ${weekHi} 周"
                                },
                                actionText = if (dated) "" else "去选第1周周一",
                                onAction = if (dated) null else ({ nav.openTermStart() }),
                            )
                        } else {
                            LessonGroup(
                                daySlots.map { slot ->
                                    LessonRowData(
                                        title = slot.courseName,
                                        meta = school.formatPeriod(slot.period, slot.periodLabel, school.hasPeriodClock),
                                        detail = listOf(slot.room, slot.teacher, weeksText(slot.weeks))
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                        accent = courseAccent(settings, slot.courseKey(), dark),
                                        onClick = { nav.open(Route.Course(slot.courseId)) },
                                    )
                                },
                            )
                        }
                        val dayPractices = if (inTerm) snapshot.practices.filter { it.activeIn(dayWeek) } else emptyList()
                        if (dayPractices.isNotEmpty()) {
                            Spacer(Modifier.height(Space.s))
                            LessonGroup(
                                dayPractices.map { item ->
                                    // 选中的那天不一定在本周，别写「本周」。
                                    LessonRowData(
                                        title = item.name,
                                        meta = weeksText(item.weeks),
                                        detail = item.teacher,
                                        tag = "实践",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (wide) Space.s else Space.l))
    }
}

/** 可点的提示条：左侧语义色竖条 + 一句结论 + 一句怎么办 + 右箭头。比整张 InfoCard 更轻。 */
@Composable
private fun HintCard(
    title: String,
    detail: String,
    tone: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = Inset.card,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(Radius.pill)
                    .background(tone),
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = MiuixTheme.textStyles.main, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onBackground)
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(detail, style = MiuixTheme.textStyles.body2, color = Tone.muted())
                }
            }
            Spacer(Modifier.width(Space.s))
            Text("›", style = MiuixTheme.textStyles.title3, color = Tone.muted().copy(alpha = 0.6f))
        }
    }
}

/** 一行课程 / 实践：左侧课程色竖条、标题、时间节次、地点老师。 */
private class LessonRowData(
    val title: String,
    val meta: String = "",
    val detail: String = "",
    val accent: Color? = null,
    val tag: String = "",
    val onClick: (() -> Unit)? = null,
)

/** 同类条目放进一张卡，行间细分割线——和设置、成绩列表的分组卡同一套写法。 */
@Composable
private fun LessonGroup(rows: List<LessonRowData>, header: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val divider = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = PaddingValues(vertical = Space.xs),
    ) {
        header?.invoke()
        rows.forEachIndexed { index, row ->
            if (index > 0 || header != null) {
                Box(
                    Modifier
                        .padding(start = Inset.page + (if (row.accent != null) Space.m + 3.dp else 0.dp), end = Inset.page)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(divider),
                )
            }
            LessonRow(row)
        }
    }
}

@Composable
private fun LessonRow(row: LessonRowData) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
            .heightIn(min = Inset.rowMin)
            .padding(horizontal = Inset.page, vertical = Space.m)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.accent != null) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(Radius.pill)
                    .background(row.accent),
            )
            Spacer(Modifier.width(Space.m))
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.meta.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    row.meta,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    row.detail,
                    style = MiuixTheme.textStyles.footnote1,
                    color = Tone.muted(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (row.tag.isNotBlank()) {
            Spacer(Modifier.width(Space.s))
            StatusTag(row.tag, color = Tone.muted())
        } else if (row.onClick != null) {
            Spacer(Modifier.width(Space.s))
            Text("›", style = MiuixTheme.textStyles.title3, color = Tone.muted().copy(alpha = 0.6f))
        }
    }
}

/** 胶囊文字按钮：「回到本周」「回到今天」「收起」。 */
@Composable
private fun PillAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(Radius.pill)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = 3.dp),
        style = MiuixTheme.textStyles.footnote1,
        fontSize = Type.tiny,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
        maxLines = 1,
    )
}

/** 翻页器第二行固定高度：有没有「回到本周/今天」都不让下面的网格上下跳。 */
private val PagerMetaHeight = 22.dp

@Composable
private fun WeekPager(
    week: Int,
    weekLo: Int,
    weekHi: Int,
    currentWeek: Int,
    range: String,
    sessions: Int,
    used: List<Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (Int) -> Unit,
    onToday: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = week > weekLo) {
                Icon(
                    MiuixIcons.ChevronBackward,
                    contentDescription = "上一周",
                    tint = if (week > weekLo) MiuixTheme.colorScheme.onBackground else Tone.muted().copy(alpha = 0.4f),
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // currentWeek 不在学期范围内（没开学/已结束）时没有「本周」可回，也不标「本周」。
                val isCurrent = week == currentWeek
                val canReturn = currentWeek in weekLo..weekHi && !isCurrent
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第${week}周",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(Space.s))
                        StatusTag("本周")
                    }
                }
                // 「回到本周」放在同一行、行高固定：以前单独占一行，一翻周整张网格就上下跳。
                Row(Modifier.height(PagerMetaHeight), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(range)
                            if (sessions > 0) append(" · ").append(sessions).append(" 节")
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        style = MiuixTheme.textStyles.footnote1,
                        color = Tone.muted(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (canReturn) {
                        PillAction("回到本周", onToday, Modifier.padding(start = Space.s))
                    }
                }
            }
            IconButton(onClick = onNext, enabled = week < weekHi) {
                Icon(
                    MiuixIcons.ChevronForward,
                    contentDescription = "下一周",
                    tint = if (week < weekHi) MiuixTheme.colorScheme.onBackground else Tone.muted().copy(alpha = 0.4f),
                )
            }
        }
        if (used.size > 1) {
            Spacer(Modifier.height(Space.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Inset.page),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                used.forEach { item ->
                    val on = item == week
                    // 选中：主色实底；本周（未选中）：主色描边文字，翻到别处也一眼找得到本周。
                    val isCurrent = item == currentWeek
                    Text(
                        "${item}周",
                        modifier = Modifier
                            .clip(Radius.pill)
                            .background(
                                when {
                                    on -> MiuixTheme.colorScheme.primary
                                    isCurrent -> MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else -> MiuixTheme.colorScheme.surfaceContainer
                                },
                            )
                            .clickable { onPick(item) }
                            .padding(horizontal = Space.m, vertical = 6.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = when {
                            on -> MiuixTheme.colorScheme.onPrimary
                            isCurrent -> MiuixTheme.colorScheme.primary
                            else -> MiuixTheme.colorScheme.onSurface
                        },
                        fontWeight = if (on || isCurrent) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPager(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    showToday: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(MiuixIcons.ChevronBackward, contentDescription = "上个月", tint = MiuixTheme.colorScheme.onBackground)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            // 和周翻页器同高：周/月来回切，标题不上下跳。
            Box(Modifier.height(PagerMetaHeight), contentAlignment = Alignment.Center) {
                if (showToday) PillAction("回到今天", onToday)
            }
        }
        IconButton(onClick = onNext) {
            Icon(MiuixIcons.ChevronForward, contentDescription = "下个月", tint = MiuixTheme.colorScheme.onBackground)
        }
    }
}

/** 今天那一列的底色：浅色下很淡，深色下稍提一点，不然看不出来。 */
@Composable
private fun todayColumnFill(): Color =
    MiuixTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.12f else 0.06f)

@Composable
private fun ColumnScope.WeekGrid(
    slots: List<LessonSlot>,
    monday: LocalDate,
    today: LocalDate,
    holidays: HolidayCalendar,
    aliases: Map<String, String>,
    blocks: List<PeriodBlock>,
    hasClock: Boolean,
    settings: AppSettings,
    adjust: CloudScheduleAdjust,
    fit: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onOpen: (List<LessonSlot>) -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) CellRadiusCompact else CellRadius)
    val offColor = Tone.success()
    val workColor = Tone.warning()
    val muted = Tone.muted()
    val todayFill = todayColumnFill()
    // 左列的起止时间走和今日卡/提醒同一套解析（clockRangeIn 会在整校都没有作息时
    // 回落内置表）。只看 block.start 的话，用户打开「有节次时间」后会出现
    // 「左列没时间、今日卡却有」的自相矛盾。
    fun clockOf(label: String): String = clockRangeIn(label, blocks)
    val clockShown = hasClock && blocks.any { clockOf(it.label).isNotBlank() }
    val slotCol = when {
        compact && clockShown -> SlotColWithClockCompact
        compact -> SlotColCompact
        clockShown -> SlotColWithClock
        else -> SlotCol
    }
    val cellGap = if (compact) CellGapCompact else CellGap
    val cellHeight = if (compact) WeekCellHeightCompact else WeekCellHeight
    val headSize = if (compact) GridType.headCompact else GridType.head
    val metaSize = if (compact) GridType.metaCompact else GridType.meta
    // 一周七列，每列算一次就够。原来是每个格子都把整张课表过一遍。
    val dayColumns = remember(slots, monday, settings, today, adjust) {
        (0..6).map { offset -> slots.forDate(monday.plus(DatePeriod(days = offset)), settings, today, adjust) }
    }
    // 表头每天最多一个标记（节假日优先，其次调课/放假），整周有标记时七列都留这一行，表头高度一致。
    val dayMarks = remember(monday, holidays, settings, adjust, today) {
        (0..6).map { offset ->
            val date = monday.plus(DatePeriod(days = offset))
            val holiday = holidays.lookup(date)
            val mark = adjustDayLabel(date, settings, adjust, today)
            when {
                holiday != null -> holiday.chip() to holiday.off
                mark.isNotBlank() -> (if (mark == "放假") "假" else "调") to (mark == "放假")
                else -> null
            }
        }
    }
    val anyMark = dayMarks.any { it != null }
    // 自适应：先量出可用高度，再按公式反算单块行高。
    // 用显式高度而不是 weight(span)——连课合并后日列的 runs 数少于标签列的 blocks 数，
    // spacedBy 产生的间隙数不同，weight 会让两侧行高对不齐。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Inset.page)
            .then(if (fit) Modifier.weight(1f) else Modifier)
            .then(modifier),
    ) {
        // 卡片内边距 + 格子圆角按「同心」取：外圆角 ≈ 内圆角 + 内边距，四角看着才顺。
        val inside = if (compact) Space.xs + 2.dp else Space.s
        val headerApprox = if (compact) 40.dp else 48.dp
        val minReadable = if (compact) 38.dp else 50.dp
        // 三段式：够高就拉伸铺满整屏；略挤就压到可读下限仍然铺满；再挤才回退固定行高 + 卡片内滚动。
        // 这里只用估算值做「够不够」的判断，真正的行高在下面按实测高度反算。
        val bodyAvail = (maxHeight - headerApprox - inside * 2).coerceAtLeast(0.dp)
        val bySpace = if (blocks.isEmpty()) {
            cellHeight
        } else {
            ((bodyAvail - cellGap * (blocks.size - 1)) / blocks.size).coerceAtLeast(0.dp)
        }
        val useFit = fit && bySpace >= minReadable
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fit) Modifier.fillMaxSize() else Modifier),
            insideMargin = PaddingValues(inside),
        ) {
            Column(
                modifier = when {
                    useFit -> Modifier.fillMaxSize()
                    fit -> Modifier.verticalScroll(rememberScrollState())
                    else -> Modifier
                },
                verticalArrangement = Arrangement.spacedBy(cellGap),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = if (compact) 0.dp else 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(cellGap),
                ) {
                    Spacer(Modifier.width(slotCol))
                    WeekdayNames.forEachIndexed { index, name ->
                        val date = monday.plus(DatePeriod(days = index))
                        val isToday = date == today
                        val weekend = index >= 5
                        // 今天：整列表头铺主色浅底，和下面那一列的浅底连成一条。
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(if (isToday) todayFill else Color.Transparent)
                                .padding(vertical = if (compact) 2.dp else Space.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                name,
                                textAlign = TextAlign.Center,
                                fontSize = headSize,
                                lineHeight = headSize * 1.2f,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    isToday -> MiuixTheme.colorScheme.primary
                                    weekend -> muted.copy(alpha = 0.7f)
                                    else -> muted
                                },
                                maxLines = 1,
                            )
                            Text(
                                weekDateLabel(date, monday),
                                textAlign = TextAlign.Center,
                                fontSize = headSize,
                                lineHeight = headSize * 1.2f,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isToday -> MiuixTheme.colorScheme.primary
                                    weekend -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    else -> MiuixTheme.colorScheme.onBackground
                                },
                                maxLines = 1,
                            )
                            if (anyMark) {
                                val mark = dayMarks[index]
                                Text(
                                    mark?.first ?: "",
                                    textAlign = TextAlign.Center,
                                    fontSize = metaSize,
                                    lineHeight = metaSize * 1.25f,
                                    fontWeight = FontWeight.Medium,
                                    color = if (mark?.second == true) offColor else workColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                // 连课合并：同一门课连着占多个节次块时合成一条长块。
                val dayRuns = remember(dayColumns, blocks) {
                    (0..6).map { index -> runsForDay(dayColumns[index], blocks) }
                }
                // 行高按**实测**的网格体高度反算。表头会因节假日/调课标记变高，
                // 拿估算值去减会把最后一行挤出去，所以这里用 weight 拿到真实剩余高度。
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (useFit) Modifier.weight(1f) else Modifier),
                ) {
                    // 取整到整数 px：Dp 小数会让每个子项各自 roundToPx，n 个块累计
                    // 最多溢出 n/2 px，最后一行被卡片圆角裁掉一点。宁可底部留不到 1px 空隙。
                    val blockHeight = if (useFit && blocks.isNotEmpty()) {
                        with(LocalDensity.current) {
                            val availPx = maxHeight.toPx()
                            val gapPx = cellGap.toPx()
                            val rowPx = (availPx - gapPx * (blocks.size - 1)) / blocks.size
                            rowPx.toInt().coerceAtLeast(2).toDp()
                        }
                    } else {
                        cellHeight
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (useFit) Modifier.fillMaxHeight() else Modifier),
                        horizontalArrangement = Arrangement.spacedBy(cellGap),
                    ) {
                        // 时间轴：节次数字是主信息，起止时间是从属的小字，一深一浅两级。
                        Column(
                            modifier = Modifier.width(slotCol),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(cellGap),
                        ) {
                            blocks.forEach { block ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().height(blockHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        block.label,
                                        textAlign = TextAlign.Center,
                                        fontSize = headSize,
                                        lineHeight = headSize * 1.2f,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                                        maxLines = 1,
                                    )
                                    val clock = clockOf(block.label)
                                    if (clockShown && clock.contains("-")) {
                                        Text(
                                            clock.substringBefore("-"),
                                            textAlign = TextAlign.Center,
                                            fontSize = metaSize,
                                            lineHeight = metaSize * 1.2f,
                                            color = muted,
                                            maxLines = 1,
                                        )
                                        Text(
                                            clock.substringAfter("-"),
                                            textAlign = TextAlign.Center,
                                            fontSize = metaSize,
                                            lineHeight = metaSize * 1.2f,
                                            color = muted.copy(alpha = 0.7f),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        for (weekday in 1..7) {
                            val date = monday.plus(DatePeriod(days = weekday - 1))
                            // 当天整列铺一层主色浅底（和表头连成一条），比描边更安静，也不和课程色块抢。
                            val isTodayCol = date == today
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (isTodayCol) Modifier.clip(shape).background(todayFill) else Modifier,
                                    ),
                                verticalArrangement = Arrangement.spacedBy(cellGap),
                            ) {
                                dayRuns[weekday - 1].forEach { run ->
                                    WeekCell(
                                        slots = run.slots,
                                        shape = shape,
                                        aliases = aliases,
                                        roomAliases = settings.roomAliases,
                                        settings = settings,
                                        compact = compact,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(blockHeight * run.span + cellGap * (run.span - 1)),
                                        onOpen = onOpen,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCell(
    slots: List<LessonSlot>,
    shape: RoundedCornerShape,
    aliases: Map<String, String>,
    roomAliases: Map<String, String>,
    settings: AppSettings,
    compact: Boolean = false,
    modifier: Modifier,
    onOpen: (List<LessonSlot>) -> Unit,
) {
    val first = slots.firstOrNull()
    if (first == null) {
        // 空格子不画底色：只留网格节奏，课程色块才是视觉主角。
        Spacer(modifier)
        return
    }
    val dark = isSystemInDarkTheme()
    val tint = resolveTint(settings, first.courseKey(), dark)
    // 字色按「色块叠在卡片底色上」的合成结果判断，见 autoInk 的注释。
    val ink = resolveInk(settings, tint, MiuixTheme.colorScheme.surfaceContainer)
    val nameSize = if (compact) GridType.nameCompact else GridType.name
    val metaSize = if (compact) GridType.metaCompact else GridType.meta
    // 按课去重再数：同一门课拆成多条不算「还有别的课」，和点开后的判定一致。
    val more = slots.distinctBy { it.courseKey() }.size - 1
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .clickable { onOpen(slots) }
            .padding(horizontal = if (compact) 2.dp else 3.dp, vertical = if (compact) 2.dp else Space.xs),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            compactCourseLines(first.courseName, aliases, first.courseId).forEach { line ->
                Text(
                    line,
                    fontSize = nameSize,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    lineHeight = nameSize * 1.18f,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            val room = compactRoomName(first.room, roomAliases)
            if (room.isNotBlank()) {
                Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
                Text(
                    room,
                    fontSize = metaSize,
                    lineHeight = metaSize * 1.2f,
                    color = ink.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (more > 0) {
            // 同格还有别的课：右上角小角标，不挤占课名行。
            Text(
                "+$more",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(Radius.pill)
                    .background(ink.copy(alpha = 0.16f))
                    .padding(horizontal = 3.dp),
                fontSize = metaSize,
                lineHeight = metaSize * 1.2f,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    settings: AppSettings,
    weekLo: Int,
    weekHi: Int,
    slots: List<LessonSlot>,
    adjust: CloudScheduleAdjust,
    holidays: HolidayCalendar,
    onSelect: (LocalDate) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val muted = Tone.muted()
    val first = LocalDate(month.year, month.monthNumber, 1)
    val lead = weekdayIndex(first) - 1
    val daysInMonth = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
    val total = ((lead + daysInMonth + 6) / 7) * 7
    val start = first.minus(DatePeriod(days = lead))
    val todayWeek = teachingWeekOn(today, settings, today)
    // 四十多格，每格都重算一次课表太亏，整月一次算完。
    val dots = remember(slots, month, settings, today, dark, adjust) {
        (0 until total).associate { index ->
            val date = start.plus(DatePeriod(days = index))
            date to if (date.monthNumber != month.monthNumber) {
                emptyList()
            } else {
                slots.forDate(date, settings, today, adjust)
                    .map { courseAccent(settings, it.courseKey(), dark) }
                    .distinct()
                    .take(4)
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = PaddingValues(Space.s),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(CellGap),
            ) {
                Text(
                    "周",
                    modifier = Modifier.width(WeekNumCol),
                    textAlign = TextAlign.Center,
                    style = MiuixTheme.textStyles.footnote1,
                    fontSize = Type.tiny,
                    color = muted.copy(alpha = 0.7f),
                )
                WeekdayNames.forEachIndexed { index, name ->
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = if (index >= 5) muted.copy(alpha = 0.7f) else muted,
                    )
                }
            }
            for (row in 0 until total / 7) {
                val monday = start.plus(DatePeriod(days = row * 7))
                val week = teachingWeekOn(monday, settings, today)
                val inTerm = week in weekLo..weekHi
                Row(
                    modifier = Modifier.fillMaxWidth().height(MonthCellHeight),
                    horizontalArrangement = Arrangement.spacedBy(CellGap),
                ) {
                    // 周次列和日期数字同一条基线（都在格子顶部 Space.xs 处的 26dp 圆里居中）。
                    Box(
                        Modifier
                            .width(WeekNumCol)
                            .padding(top = Space.xs)
                            .height(MonthDaySize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (inTerm) "$week" else "·",
                            textAlign = TextAlign.Center,
                            fontSize = Type.tiny,
                            fontWeight = if (inTerm && week == todayWeek) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                inTerm && week == todayWeek -> MiuixTheme.colorScheme.primary
                                inTerm -> muted
                                else -> muted.copy(alpha = 0.4f)
                            },
                        )
                    }
                    for (col in 0..6) {
                        val date = monday.plus(DatePeriod(days = col))
                        val inMonth = date.monthNumber == month.monthNumber
                        MonthCell(
                            date = date,
                            inMonth = inMonth,
                            weekend = col >= 5,
                            isToday = date == today,
                            isSelected = date == selected,
                            holiday = holidays.lookup(date),
                            dots = dots[date].orEmpty(),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onSelect(date) },
                        )
                    }
                }
            }
        }
    }
}

/** 月历日期数字的圆底尺寸，周次列按它对齐。 */
private val MonthDaySize = 26.dp

@Composable
private fun MonthCell(
    date: LocalDate,
    inMonth: Boolean,
    weekend: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    holiday: HolidayDay?,
    dots: List<Color>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val muted = Tone.muted()
    // 今天：主色实心圆；选中（非今天）：主色浅底圆；选中的今天再加一圈浅色外环，两种状态都看得出。
    val circleBg = when {
        isToday -> primary
        isSelected -> primary.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .clip(Radius.small)
            .background(if (isToday && isSelected) primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(top = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(MonthDaySize)
                .clip(CircleShape)
                .background(circleBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MiuixTheme.textStyles.body2,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isToday -> MiuixTheme.colorScheme.onPrimary
                    !inMonth -> muted.copy(alpha = 0.4f)
                    isSelected -> primary
                    weekend -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    else -> MiuixTheme.colorScheme.onBackground
                },
                maxLines = 1,
            )
        }
        // 休/班标记和课程点各占固定一行，不管有没有都不让下面的内容上下错位。
        Box(Modifier.height(GridType.meta.value.dp + 4.dp), contentAlignment = Alignment.Center) {
            if (inMonth && holiday != null) {
                Text(
                    if (holiday.off) "休" else "班",
                    fontSize = GridType.meta,
                    lineHeight = GridType.meta * 1.2f,
                    fontWeight = FontWeight.Medium,
                    color = if (holiday.off) Tone.success() else Tone.warning(),
                    maxLines = 1,
                )
            }
        }
        // 调休上班日照样有课，休/班标记和课程点要一起显示，不能互相顶掉。
        if (dots.isNotEmpty() && inMonth) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                dots.forEach { color ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(0.5.dp, MiuixTheme.colorScheme.onBackground.copy(alpha = 0.12f), CircleShape),
                    )
                }
            }
        }
    }
}

/** 同一格叠了几门课（冲突 / 单双周合并显示）时，在网格下面列出来逐个点开。 */
@Composable
private fun ConflictPanel(
    slots: List<LessonSlot>,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val count = slots.distinctBy { it.courseKey() }.size
    LessonGroup(
        rows = slots.distinctBy { it.courseId to it.period }.map { slot ->
            LessonRowData(
                title = slot.courseName,
                meta = periodText(slot.period, slot.periodLabel),
                detail = listOf(slot.room, slot.teacher, weeksText(slot.weeks)).filter { it.isNotBlank() }.joinToString(" · "),
                accent = courseAccent(settings, slot.courseKey(), dark),
                onClick = { onOpen(slot.courseId) },
            )
        },
        modifier = modifier,
        header = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Inset.page, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "这一格有 $count 门课",
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Text("点一门看详情", style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
                }
                PillAction("收起", onClose)
            }
        },
    )
}
