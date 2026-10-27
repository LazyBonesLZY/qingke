package cn.edu.gzus.qingke.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.HolidayDay
import cn.edu.gzus.qingke.data.School
import cn.edu.gzus.qingke.data.school
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.chip
import cn.edu.gzus.qingke.data.formatLongDate
import cn.edu.gzus.qingke.data.applicableAdjust
import cn.edu.gzus.qingke.data.formatMonthDay
import cn.edu.gzus.qingke.data.formatShift
import cn.edu.gzus.qingke.data.resolvedScheduleShifts
import cn.edu.gzus.qingke.data.formatYearMonth
import cn.edu.gzus.qingke.data.holidayShiftHints
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.parseIsoDate
import cn.edu.gzus.qingke.data.shiftConflict
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.ui.components.ConfirmDialog
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScheduleShiftsScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    onAdd: (LocalDate, LocalDate) -> String?,
    onRemove: (String) -> Unit,
) {
    val today = nowDateTime().date
    val shifts = snapshot.settings.scheduleShifts.sortedBy { it.fromDate }
    val cloud = applicableAdjust(snapshot.settings, snapshot.scheduleAdjust)
    val cloudShifts = resolvedScheduleShifts(snapshot.settings, snapshot.scheduleAdjust, today)
        .filter { shift -> shifts.none { it.id == shift.id } }
    val (offDays, makeupDays) = remember(snapshot.holidays, snapshot.settings.termStart, snapshot.slots) {
        holidayShiftHints(snapshot.holidays, snapshot.settings, snapshot.slots, today)
    }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    // 刚保存成功的那条（"from>to"），列表里标「刚添加」，表单下给成功回执。
    var justAdded by remember { mutableStateOf<String?>(null) }
    var addedText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = Space.xl),
    ) {
        Spacer(Modifier.height(Space.s))
        Text(
            // 自动拉调休只有广软有（MineScreen 里的开关也只对广软显示），别的学校别提它。
            if (snapshot.settings.school() == School.Gzus) {
                "放假日没课。广软的补课可以在「我的」里自动拉，也可以在下面手动加。"
            } else {
                "放假日没课。补课按学校通知，在下面选原上课日和调到哪天。"
            },
            modifier = Modifier.padding(horizontal = Inset.page),
            color = Tone.muted(),
            style = MiuixTheme.textStyles.body2,
        )
        if (cloud.offs.isNotEmpty() || cloudShifts.isNotEmpty()) {
            SmallTitle(text = cloud.title.ifBlank { "云端调休" })
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                insideMargin = PaddingValues(0.dp),
            ) {
                cloud.offs.groupBy { it.name.ifBlank { "放假" } }.forEach { (name, days) ->
                    val dates = days.mapNotNull { parseIsoDate(it.date) }.sorted()
                    val span = when {
                        dates.isEmpty() -> ""
                        dates.size == 1 -> formatMonthDay(dates.first())
                        else -> "${formatMonthDay(dates.first())}–${formatMonthDay(dates.last())}"
                    }
                    BasicComponent(title = name, summary = if (span.isBlank()) "放假没课" else "$span · 放假没课")
                }
                cloudShifts.forEach { shift ->
                    val rule = cloud.shifts.firstOrNull { it.id == shift.id }
                    BasicComponent(
                        title = formatShift(shift),
                        summary = rule?.name?.ifBlank { "云端补课" } ?: "云端补课",
                    )
                }
            }
        }
        SmallTitle(text = if (shifts.isEmpty()) "已有调课" else "已有调课 · ${shifts.size} 条")
        if (shifts.isEmpty()) {
            EmptyState(
                title = "还没有调课",
                detail = "在下面按原上课日和调到哪天各选一天。",
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                insideMargin = PaddingValues(0.dp),
            ) {
                // 点一下不直接删，误触无法撤回；走统一的危险确认框。
                shifts.forEach { shift ->
                    ArrowPreference(
                        title = formatShift(shift),
                        summary = "点按删除",
                        endActions = {
                            if (justAdded == "${shift.fromDate}>${shift.toDate}") StatusTag("刚添加", color = Tone.success())
                        },
                        onClick = { removing = shift.id },
                    )
                }
            }
        }
        if (offDays.isNotEmpty() || makeupDays.isNotEmpty()) {
            SmallTitle(text = "节假日参考")
            // 只有补班日时不要先画一张空卡。
            if (offDays.isNotEmpty()) {
                HolidayHintCard("放假的工作日，常当作原上课日", offDays) { date ->
                    from = date
                    picking = null
                    error = null
                }
            }
            if (makeupDays.isNotEmpty()) {
                if (offDays.isNotEmpty()) Spacer(Modifier.height(Space.s))
                HolidayHintCard("补班日，常当作调到哪天", makeupDays) { date ->
                    to = date
                    picking = null
                    error = null
                }
            }
        }
        SmallTitle(text = "添加调课")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            insideMargin = PaddingValues(0.dp),
        ) {
            ArrowPreference(
                title = "原上课日",
                summary = from?.let { formatLongDate(it) } ?: "放假那天，或学校通知里的原上课日",
                onClick = { picking = if (picking == "from") null else "from" },
            )
            if (picking == "from") {
                ShiftDayPicker(
                    initial = from ?: today,
                    onPick = { date ->
                        from = date
                        picking = null
                        error = null
                    },
                    embedded = true,
                )
            }
            ArrowPreference(
                title = "调到哪天",
                summary = to?.let { formatLongDate(it) } ?: "补班那天，或学校通知里的新上课日",
                onClick = { picking = if (picking == "to") null else "to" },
            )
            if (picking == "to") {
                ShiftDayPicker(
                    initial = to ?: today,
                    onPick = { date ->
                        to = date
                        picking = null
                        error = null
                    },
                    embedded = true,
                )
            }
        }
        // 表单下方只放一行状态：校验失败用危险色，保存成功用成功色。
        val status = error
        if (!status.isNullOrBlank()) {
            Spacer(Modifier.height(Space.s))
            Text(
                status,
                modifier = Modifier.padding(horizontal = Inset.page),
                color = Tone.danger(),
                style = MiuixTheme.textStyles.footnote1,
            )
        } else if (addedText.isNotBlank() && from == null && to == null) {
            // 开始选下一条后回执自动消失。
            Spacer(Modifier.height(Space.s))
            Text(
                addedText,
                modifier = Modifier.padding(horizontal = Inset.page),
                color = Tone.success(),
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Spacer(Modifier.height(Space.m))
        Button(
            onClick = {
                val src = from
                val dest = to
                if (src == null || dest == null) {
                    error = "原上课日和调到哪天都要选"
                    return@Button
                }
                val conflict = snapshot.settings.shiftConflict(src, dest, snapshot.scheduleAdjust, today)
                if (conflict != null) {
                    error = conflict
                    return@Button
                }
                error = onAdd(src, dest)
                if (error == null) {
                    justAdded = "$src>$dest"
                    addedText = "已添加：${formatLongDate(src)} 的课调到 ${formatLongDate(dest)}"
                    from = null
                    to = null
                    picking = null
                } else {
                    addedText = ""
                }
            },
            enabled = from != null && to != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("保存调课") }
    }

    val target = shifts.firstOrNull { it.id == removing }
    if (target != null) {
        ConfirmDialog(
            title = "删除这条调课？",
            message = "${formatShift(target)}\n删除后课表不再按这条调课排。",
            confirmText = "删除",
            onConfirm = {
                if (justAdded == "${target.fromDate}>${target.toDate}") justAdded = null
                onRemove(target.id)
            },
            onDismiss = { removing = null },
            destructive = true,
        )
    }
}

@Composable
private fun HolidayHintCard(
    title: String,
    days: List<HolidayDay>,
    onPick: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = PaddingValues(0.dp),
    ) {
        days.forEach { day ->
            val date = parseIsoDate(day.date) ?: return@forEach
            ArrowPreference(
                title = "${formatMonthDay(date)} 周${WeekdayNames.getOrElse(weekdayIndex(date) - 1) { "?" }} · ${day.chip()}",
                summary = title,
                onClick = { onPick(date) },
            )
        }
    }
}

@Composable
private fun ShiftDayPicker(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    embedded: Boolean = false,
) {
    var month by remember(initial) { mutableStateOf(LocalDate(initial.year, initial.monthNumber, 1)) }
    var selected by remember(initial) { mutableStateOf(initial) }
    val today = nowDateTime().date
    val first = LocalDate(month.year, month.monthNumber, 1)
    val lead = weekdayIndex(first) - 1
    val daysInMonth = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
    val total = ((lead + daysInMonth + 6) / 7) * 7
    val gridStart = first.minus(DatePeriod(days = lead))
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) {
                Icon(MiuixIcons.ChevronBackward, contentDescription = "上一月")
            }
            Text(
                formatYearMonth(month),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { month = month.plus(DatePeriod(months = 1)) }) {
                Icon(MiuixIcons.ChevronForward, contentDescription = "下一月")
            }
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth()) {
            WeekdayNames.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MiuixTheme.textStyles.footnote1,
                    color = Tone.muted(),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        for (row in 0 until total / 7) {
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                for (col in 0..6) {
                    val date = gridStart.plus(DatePeriod(days = row * 7 + col))
                    val inMonth = date.monthNumber == month.monthNumber
                    val isSelected = date == selected
                    val isToday = date == today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { selected = date },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MiuixTheme.colorScheme.primary
                                        isToday -> MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSelected -> MiuixTheme.colorScheme.onPrimary
                                    !inMonth -> MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = 0.35f)
                                    else -> MiuixTheme.colorScheme.onBackground
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        Button(
            onClick = { onPick(selected) },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("用 ${formatLongDate(selected)}") }
    }
    if (embedded) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.m)) {
            body()
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            insideMargin = PaddingValues(horizontal = Space.m, vertical = Space.m),
        ) {
            body()
        }
    }
}
