package cn.edu.gzus.qingke.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.gzus.qingke.data.formatPeriod
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
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.LocalQingkeWide
import cn.edu.gzus.qingke.ui.components.ScreenHeader
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
private val CellGap = 3.dp
private val CellGapCompact = 2.dp
private val WeekCellHeight = 64.dp
private val WeekCellHeightCompact = 46.dp
private val MonthCellHeight = 58.dp
private val CellRadius = 6.dp
private val CellRadiusCompact = 4.dp

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

/** 课程色块字色：auto 按合成后底色亮度、light/dark 强制、custom 用 courseInkHex。 */
private fun resolveInk(settings: AppSettings, tint: Color, backdrop: Color): Color =
    when (settings.courseInkMode) {
        "light" -> CourseInkLight
        "dark" -> CourseInkDark
        "custom" -> parseHexColor(settings.courseInkHex) ?: autoInk(tint, backdrop)
        else -> autoInk(tint, backdrop)
    }

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
) {
    val today = nowDateTime().date
    val settings = snapshot.settings
    val currentWeek = resolvedCurrentWeek(settings, today)
    val used = weeksInUse(snapshot.slots)
    val weekLo = 1
    val weekHi = settings.resolvedWeekCount(snapshot.slots).coerceAtLeast(1)
    var mode by remember { mutableIntStateOf(0) }
    var viewWeek by remember(currentWeek, weekLo, weekHi) {
        mutableIntStateOf(currentWeek.coerceIn(weekLo, weekHi))
    }
    var monthAnchor by remember(today) { mutableStateOf(LocalDate(today.year, today.monthNumber, 1)) }
    var selectedDate by remember(today) { mutableStateOf(today) }

    val monday = mondayOfTeachingWeek(viewWeek.coerceAtLeast(1), settings, today)
    val sunday = monday.plus(DatePeriod(days = 6))
    val weekSessions = (0..6).sumOf { offset ->
        snapshot.slots.forDate(
            monday.plus(DatePeriod(days = offset)),
            settings,
            today,
            snapshot.scheduleAdjust,
        ).size
    }
    val weekPractices = snapshot.practices.filter { it.activeIn(viewWeek) }
    val dated = settings.hasTermStart()
    val wide = LocalQingkeWide.current
    val subtitle = when {
        !snapshot.hasTimetable -> if (snapshot.session.loggedIn) "同步后再看整周网格" else "登录后从${snapshot.resolved().jwxtName}同步"
        !dated -> "第${viewWeek}周 · 先到「我的」选第1周周一"
        wide || mode == 0 -> "第${viewWeek}周 · ${formatMonthDayRange(monday, sunday)}"
        else -> formatYearMonth(monthAnchor)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (wide) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(if (wide) 8.dp else 16.dp))
        ScreenHeader("", "课表", subtitle)
        if (!snapshot.hasTimetable) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "还没有课表",
                summary = if (snapshot.session.loggedIn) "同步后再看一周和一月。" else "去「我的」登录${snapshot.resolved().jwxtName}。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
            Spacer(Modifier.height(16.dp))
            return
        }
        if (!dated) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "还没选第1周周一",
                summary = "选了之后，周视图和月历才对得上真实日期。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        }
        if (!wide) {
        Spacer(Modifier.height(12.dp))
        TabRowWithContour(
            tabs = listOf("周", "月"),
            selectedTabIndex = mode,
            onTabSelected = { mode = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (wide) Modifier.weight(1f) else Modifier),
            verticalAlignment = Alignment.Top,
        ) {
        if (wide || mode == 0) {
        Column(Modifier.weight(if (wide) 1.55f else 1f).then(if (wide) Modifier.fillMaxHeight() else Modifier)) {
            WeekPager(
                week = viewWeek,
                weekLo = weekLo,
                weekHi = weekHi,
                currentWeek = currentWeek.coerceIn(weekLo, weekHi),
                range = formatMonthDayRange(monday, sunday),
                sessions = weekSessions,
                used = used,
                onPrev = { if (viewWeek > weekLo) viewWeek -= 1 },
                onNext = { if (viewWeek < weekHi) viewWeek += 1 },
                onPick = { viewWeek = it },
                onToday = { viewWeek = currentWeek.coerceIn(weekLo, weekHi) },
            )
            Spacer(Modifier.height(10.dp))
            if (wide && weekPractices.isNotEmpty()) {
                Text(
                    weekPractices.joinToString("、") { it.name }.let { "本周实践 · $it" },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
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
                blocks = snapshot.resolved().periodBlocks,
                hasClock = snapshot.resolved().hasPeriodClock,
                settings = settings,
                adjust = snapshot.scheduleAdjust,
                fit = wide,
                compact = !wide && settings.compactTimetable,
                onOpen = { nav.open(Route.Course(it)) },
            )
            if (!wide && weekSessions == 0 && weekPractices.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                InfoCard("这一周没有理论课", if (wide) "左右换周，或在右边月历里点一天" else "左右换周，或到「月」里扫整月", modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (!wide && weekPractices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    weekPractices.forEach { item ->
                        InfoCard(
                            title = item.name,
                            summary = listOf("实践", item.weeks, item.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                        )
                    }
                }
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
            Spacer(Modifier.height(10.dp))
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
                    if (pickedWeek in weekLo..weekHi) viewWeek = pickedWeek
                    if (date.monthNumber != monthAnchor.monthNumber || date.year != monthAnchor.year) {
                        monthAnchor = LocalDate(date.year, date.monthNumber, 1)
                    }
                },
            )
            if (wide) {
                Text(
                    "点一天，左边换到那一周",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            val daySlots = snapshot.slots.forDate(selectedDate, settings, today, snapshot.scheduleAdjust)
            val dayWeek = teachingWeekOn(selectedDate, settings, today)
            if (!wide) {
            SmallTitle(
                text = buildString {
                    append("${selectedDate.monthNumber}月${selectedDate.dayOfMonth}日 · 周${WeekdayNames.getOrElse(weekdayIndex(selectedDate) - 1) { "?" }}")
                    if (dayWeek in weekLo..weekHi) append(" · 第${dayWeek}周")
                    snapshot.holidays.lookup(selectedDate)?.let { append(" · ").append(it.chip()) }
                    adjustDayLabel(selectedDate, settings, snapshot.scheduleAdjust, today)
                        .takeIf { it.isNotBlank() }
                        ?.let { append(" · ").append(it) }
                },
            )
            if (daySlots.isEmpty()) {
                InfoCard(
                    title = if (dayWeek in weekLo..weekHi) "这天没有课" else "不在课表周次里",
                    summary = if (dated) "这天课表是空的" else "先选第1周周一，日期才准",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    daySlots.forEach { slot ->
                        InfoCard(
                            title = "${snapshot.resolved().formatPeriod(slot.period, slot.periodLabel, snapshot.resolved().hasPeriodClock)}  ${slot.courseName}",
                            summary = listOf(slot.room, slot.teacher, slot.weeks).filter { it.isNotBlank() }.joinToString("\n"),
                            height = null,
                            onClick = { nav.open(Route.Course(slot.courseId)) },
                        )
                    }
                }
            }
            val dayPractices = snapshot.practices.filter { it.activeIn(dayWeek) }
            if (dayPractices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dayPractices.forEach { item ->
                        InfoCard(
                            title = item.name,
                            summary = listOf("本周实践", item.weeks, item.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                        )
                    }
                }
            }
            }
        }
        }
        }
        Spacer(Modifier.height(if (wide) 8.dp else 16.dp))
    }
}

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = week > weekLo) {
                Icon(MiuixIcons.ChevronBackward, contentDescription = "上一周", tint = MiuixTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "第${week}周",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Text(
                    buildString {
                        append(range)
                        if (sessions > 0) append(" · ").append(sessions).append(" 节")
                        if (week == currentWeek) append(" · 本周")
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            IconButton(onClick = onNext, enabled = week < weekHi) {
                Icon(MiuixIcons.ChevronForward, contentDescription = "下一周", tint = MiuixTheme.colorScheme.onBackground)
            }
        }
        if (week != currentWeek) {
            Text(
                "回到本周",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(99.dp))
                    .clickable(onClick = onToday)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        if (used.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                used.forEach { item ->
                    val on = item == week
                    Text(
                        "${item}周",
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (on) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer)
                            .clickable { onPick(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (on) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(MiuixIcons.ChevronBackward, contentDescription = "上个月", tint = MiuixTheme.colorScheme.onBackground)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onBackground)
            if (showToday) {
                Text(
                    "回到今天",
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .clickable(onClick = onToday)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(MiuixIcons.ChevronForward, contentDescription = "下个月", tint = MiuixTheme.colorScheme.onBackground)
        }
    }
}

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
    onOpen: (String) -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) CellRadiusCompact else CellRadius)
    val workColor = if (isSystemInDarkTheme()) Color(0xFFE8B86D) else Color(0xFFC9782A)
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
    // 一周七列，每列算一次就够。原来是每个格子都把整张课表过一遍。
    val dayColumns = remember(slots, monday, settings, today, adjust) {
        (0..6).map { offset -> slots.forDate(monday.plus(DatePeriod(days = offset)), settings, today, adjust) }
    }
    // 自适应：先量出可用高度，再按公式反算单块行高。
    // 用显式高度而不是 weight(span)——连课合并后日列的 runs 数少于标签列的 blocks 数，
    // spacedBy 产生的间隙数不同，weight 会让两侧行高对不齐。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(if (fit) Modifier.weight(1f) else Modifier),
    ) {
        val inside = if (fit) 4.dp else 6.dp
        val headerApprox = if (compact) 46.dp else 54.dp
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                    Spacer(Modifier.width(slotCol))
                    WeekdayNames.forEachIndexed { index, name ->
                        val date = monday.plus(DatePeriod(days = index))
                        val isToday = date == today
                        val holiday = holidays.lookup(date)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                name,
                                textAlign = TextAlign.Center,
                                fontSize = if (compact) 10.sp else 11.sp,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isToday) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            )
                            Text(
                                weekDateLabel(date, monday),
                                textAlign = TextAlign.Center,
                                fontSize = if (compact) 10.sp else 11.sp,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isToday) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                            )
                            if (holiday != null) {
                                Text(
                                    holiday.chip(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (holiday.off) MiuixTheme.colorScheme.primary else workColor,
                                )
                            }
                            val mark = adjustDayLabel(date, settings, adjust, today)
                            if (mark.isNotBlank()) {
                                Text(
                                    if (mark == "放假") "假" else "调",
                                    textAlign = TextAlign.Center,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.primary,
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
                                    fontSize = if (compact) 9.sp else 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                    maxLines = 1,
                                )
                                val clock = clockOf(block.label)
                                if (clockShown && clock.contains("-")) {
                                    Text(
                                        clock.substringBefore("-"),
                                        textAlign = TextAlign.Center,
                                        fontSize = if (compact) 6.sp else 8.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = 0.86f),
                                        maxLines = 1,
                                    )
                                    Text(
                                        clock.substringAfter("-"),
                                        textAlign = TextAlign.Center,
                                        fontSize = if (compact) 6.sp else 8.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = 0.86f),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    for (weekday in 1..7) {
                        val date = monday.plus(DatePeriod(days = weekday - 1))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(cellGap),
                        ) {
                            dayRuns[weekday - 1].forEach { run ->
                                WeekCell(
                                    slots = run.slots,
                                    today = date == today,
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
    today: Boolean,
    shape: RoundedCornerShape,
    aliases: Map<String, String>,
    roomAliases: Map<String, String>,
    settings: AppSettings,
    compact: Boolean = false,
    modifier: Modifier,
    onOpen: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val first = slots.firstOrNull()
    val tint = first?.let { resolveTint(settings, it.courseId.ifBlank { it.courseName }, dark) }
    // 字色按「色块叠在卡片底色上」的合成结果判断，见 autoInk 的注释。
    val ink = resolveInk(settings, tint ?: courseTint("", dark), MiuixTheme.colorScheme.surfaceContainer)
    val empty = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint ?: empty)
            .then(if (today) Modifier.border(1.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.35f), shape) else Modifier)
            .then(
                if (first != null) {
                    Modifier.clickable { onOpen(first.courseId) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 2.dp, vertical = if (compact) 2.dp else 4.dp),
    ) {
        if (first != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                compactCourseLines(first.courseName, aliases, first.courseId).forEach { line ->
                    Text(
                        line,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        lineHeight = if (compact) 11.sp else 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                val room = compactRoomName(first.room, roomAliases)
                if (room.isNotBlank()) {
                    Text(
                        room,
                        fontSize = if (compact) 7.sp else 8.sp,
                        color = ink.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (slots.size > 1) {
                    Text(
                        "+${slots.size - 1}",
                        fontSize = if (compact) 7.sp else 8.sp,
                        color = ink.copy(alpha = 0.55f),
                    )
                }
            }
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
    val first = LocalDate(month.year, month.monthNumber, 1)
    val lead = weekdayIndex(first) - 1
    val daysInMonth = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
    val total = ((lead + daysInMonth + 6) / 7) * 7
    val start = first.minus(DatePeriod(days = lead))
    // 四十多格，每格都重算一次课表太亏，整月一次算完。
    val dots = remember(slots, month, settings, today, dark, adjust) {
        (0 until total).associate { index ->
            val date = start.plus(DatePeriod(days = index))
            date to if (date.monthNumber != month.monthNumber) {
                emptyList()
            } else {
                slots.forDate(date, settings, today, adjust)
                    .map { resolveTint(settings, it.courseId.ifBlank { it.courseName }, dark) }
                    .distinct()
                    .take(4)
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                Spacer(Modifier.width(WeekNumCol))
                WeekdayNames.forEach { name ->
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }
            for (row in 0 until total / 7) {
                val monday = start.plus(DatePeriod(days = row * 7))
                val week = teachingWeekOn(monday, settings, today)
                Row(
                    modifier = Modifier.fillMaxWidth().height(MonthCellHeight),
                    horizontalArrangement = Arrangement.spacedBy(CellGap),
                ) {
                    Text(
                        if (week in weekLo..weekHi) "$week" else "·",
                        modifier = Modifier
                            .width(WeekNumCol)
                            .fillMaxHeight()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        color = if (week in weekLo..weekHi) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    for (col in 0..6) {
                        val date = monday.plus(DatePeriod(days = col))
                        val inMonth = date.monthNumber == month.monthNumber
                        MonthCell(
                            date = date,
                            inMonth = inMonth,
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

@Composable
private fun MonthCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    holiday: HolidayDay?,
    dots: List<Color>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CellRadius)
    val bg = when {
        isSelected -> MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
        isToday -> MiuixTheme.colorScheme.primary.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .then(
                    if (isToday) {
                        Modifier.clip(CircleShape).background(MiuixTheme.colorScheme.primary)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 12.sp,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isToday -> MiuixTheme.colorScheme.onPrimary
                    !inMonth -> MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = 0.4f)
                    else -> MiuixTheme.colorScheme.onBackground
                },
            )
        }
        if (inMonth && holiday != null) {
            Spacer(Modifier.height(1.dp))
            Text(
                if (holiday.off) "休" else "班",
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = if (holiday.off) MiuixTheme.colorScheme.primary
                else if (isSystemInDarkTheme()) Color(0xFFE8B86D) else Color(0xFFC9782A),
            )
        } else if (dots.isNotEmpty() && inMonth) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                dots.forEach { color ->
                    Box(Modifier.size(5.dp).clip(CircleShape).background(color).border(0.5.dp, MiuixTheme.colorScheme.onBackground.copy(alpha = 0.12f), CircleShape))
                }
            }
        }
    }
}
