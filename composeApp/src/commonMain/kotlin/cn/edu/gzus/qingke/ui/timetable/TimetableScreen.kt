package cn.edu.gzus.qingke.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.LessonSlot
import cn.edu.gzus.qingke.data.PeriodBlock
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.activeIn
import cn.edu.gzus.qingke.data.compactCourseLines
import cn.edu.gzus.qingke.data.compactRoomName
import cn.edu.gzus.qingke.data.forBlock
import cn.edu.gzus.qingke.data.HolidayCalendar
import cn.edu.gzus.qingke.data.HolidayDay
import cn.edu.gzus.qingke.data.chip
import cn.edu.gzus.qingke.data.forDate
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
private val WeekNumCol = 28.dp
private val CellGap = 3.dp
private val WeekCellHeight = 64.dp
private val MonthCellHeight = 58.dp
private val CellRadius = 6.dp

private fun courseTint(key: String, dark: Boolean): Color {
    val hash = key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    val tints = if (dark) CourseTintsDark else CourseTintsLight
    return tints[hash % tints.size]
}

private fun courseInk(dark: Boolean): Color = if (dark) CourseInkDark else CourseInkLight

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
    val weekSessions = snapshot.slots.count { it.activeIn(viewWeek) }
    val weekPractices = snapshot.practices.filter { it.activeIn(viewWeek) }
    val dated = settings.hasTermStart()
    val subtitle = when {
        !snapshot.hasTimetable -> if (snapshot.session.loggedIn) "同步后再看整周网格" else "登录后从${snapshot.resolved().jwxtName}同步"
        !dated -> "第${viewWeek}周 · 先到「我的」选第1周周一"
        mode == 0 -> "第${viewWeek}周 · ${formatMonthDayRange(monday, sunday)}"
        else -> formatYearMonth(monthAnchor)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(16.dp))
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
        Spacer(Modifier.height(12.dp))
        TabRowWithContour(
            tabs = listOf("周", "月"),
            selectedTabIndex = mode,
            onTabSelected = { mode = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (mode == 0) {
            WeekPager(
                week = viewWeek,
                weekLo = weekLo,
                weekHi = weekHi,
                currentWeek = currentWeek,
                range = formatMonthDayRange(monday, sunday),
                sessions = weekSessions,
                used = used,
                onPrev = { if (viewWeek > weekLo) viewWeek -= 1 },
                onNext = { if (viewWeek < weekHi) viewWeek += 1 },
                onPick = { viewWeek = it },
                onToday = { viewWeek = currentWeek.coerceIn(weekLo, weekHi) },
            )
            Spacer(Modifier.height(10.dp))
            WeekGrid(
                slots = snapshot.slots,
                week = viewWeek,
                monday = monday,
                today = today,
                holidays = snapshot.holidays,
                aliases = settings.courseAliases,
                blocks = snapshot.resolved().periodBlocks,
                onOpen = { nav.open(Route.Course(it)) },
            )
            if (weekSessions == 0 && weekPractices.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                InfoCard("这一周没有理论课", "左右换周，或到「月」里扫整月", modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (weekPractices.isNotEmpty()) {
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
        } else {
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
                holidays = snapshot.holidays,
                onSelect = { date ->
                    selectedDate = date
                    if (date.monthNumber != monthAnchor.monthNumber || date.year != monthAnchor.year) {
                        monthAnchor = LocalDate(date.year, date.monthNumber, 1)
                    }
                },
            )
            val daySlots = snapshot.slots.forDate(selectedDate, settings, today)
            val dayWeek = teachingWeekOn(selectedDate, settings, today)
            SmallTitle(
                text = buildString {
                    append("${selectedDate.monthNumber}月${selectedDate.dayOfMonth}日 · 周${WeekdayNames.getOrElse(weekdayIndex(selectedDate) - 1) { "?" }}")
                    if (dayWeek in weekLo..weekHi) append(" · 第${dayWeek}周")
                    snapshot.holidays.lookup(selectedDate)?.let { append(" · ").append(it.chip()) }
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
                            title = "${slot.periodLabel.ifBlank { slot.period }}  ${slot.courseName}",
                            summary = listOf(slot.room, slot.teacher, slot.weeks).filter { it.isNotBlank() }.joinToString("\n"),
                            height = null,
                            onClick = { nav.open(Route.Course(slot.courseId)) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
                    .padding(horizontal = 10.dp, vertical = 4.dp),
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
                            .padding(horizontal = 10.dp, vertical = 5.dp),
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
                        .padding(horizontal = 8.dp, vertical = 2.dp),
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
private fun WeekGrid(
    slots: List<LessonSlot>,
    week: Int,
    monday: LocalDate,
    today: LocalDate,
    holidays: HolidayCalendar,
    aliases: Map<String, String>,
    blocks: List<PeriodBlock>,
    onOpen: (String) -> Unit,
) {
    val shape = RoundedCornerShape(CellRadius)
    val workColor = if (isSystemInDarkTheme()) Color(0xFFE8B86D) else Color(0xFFC9782A)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                Spacer(Modifier.width(SlotCol))
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
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isToday) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                        Text(
                            weekDateLabel(date, monday),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
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
                    }
                }
            }
            blocks.forEach { block ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(WeekCellHeight),
                    horizontalArrangement = Arrangement.spacedBy(CellGap),
                ) {
                    Text(
                        block.label,
                        modifier = Modifier
                            .width(SlotCol)
                            .fillMaxHeight()
                            .padding(top = 20.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    for (weekday in 1..7) {
                        val date = monday.plus(DatePeriod(days = weekday - 1))
                        val cell = slots.forBlock(week, weekday, block)
                        WeekCell(
                            slots = cell,
                            today = date == today,
                            shape = shape,
                            aliases = aliases,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onOpen = onOpen,
                        )
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
    modifier: Modifier,
    onOpen: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val first = slots.firstOrNull()
    val tint = first?.let { courseTint(it.courseId.ifBlank { it.courseName }, dark) }
    val ink = courseInk(dark)
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
            .padding(horizontal = 3.dp, vertical = 4.dp),
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                val room = compactRoomName(first.room)
                if (room.isNotBlank()) {
                    Text(
                        room,
                        fontSize = 8.sp,
                        color = ink.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                if (slots.size > 1) {
                    Text(
                        "+${slots.size - 1}",
                        fontSize = 8.sp,
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
    holidays: HolidayCalendar,
    onSelect: (LocalDate) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val first = LocalDate(month.year, month.monthNumber, 1)
    val lead = weekdayIndex(first) - 1
    val daysInMonth = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
    val total = ((lead + daysInMonth + 6) / 7) * 7
    val start = first.minus(DatePeriod(days = lead))
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
                        val daySlots = if (inMonth) slots.forDate(date, settings, today) else emptyList()
                        MonthCell(
                            date = date,
                            inMonth = inMonth,
                            isToday = date == today,
                            isSelected = date == selected,
                            holiday = holidays.lookup(date),
                            dots = daySlots.map { courseTint(it.courseId.ifBlank { it.courseName }, dark) }.distinct().take(4),
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
                    Box(Modifier.size(5.dp).clip(CircleShape).background(color).border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape))
                }
            }
        }
    }
}
