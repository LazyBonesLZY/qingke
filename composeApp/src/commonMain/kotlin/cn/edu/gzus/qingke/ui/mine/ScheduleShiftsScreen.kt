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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.HolidayDay
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.chip
import cn.edu.gzus.qingke.data.formatLongDate
import cn.edu.gzus.qingke.data.formatMonthDay
import cn.edu.gzus.qingke.data.formatShift
import cn.edu.gzus.qingke.data.formatYearMonth
import cn.edu.gzus.qingke.data.holidayShiftHints
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.parseIsoDate
import cn.edu.gzus.qingke.data.shiftConflict
import cn.edu.gzus.qingke.data.weekdayIndex
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
    val (offDays, makeupDays) = remember(snapshot.holidays, snapshot.settings.termStart, snapshot.slots) {
        holidayShiftHints(snapshot.holidays, snapshot.settings, snapshot.slots, today)
    }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "按学校调课通知填写。国务院安排只标休和班，不会自动改课表。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        SmallTitle(text = "已有调课")
        if (shifts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(title = "还没有调课", summary = "下面按原上课日和调到哪天各选一天", onClick = {})
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                shifts.forEach { shift ->
                    ArrowPreference(
                        title = formatShift(shift),
                        summary = "点按删除",
                        onClick = { onRemove(shift.id) },
                    )
                }
            }
        }
        if (offDays.isNotEmpty() || makeupDays.isNotEmpty()) {
            SmallTitle(text = "节假日参考")
            HolidayHintCard("放假的工作日，常当作原上课日", offDays) { date ->
                from = date
                picking = null
                error = null
            }
            if (makeupDays.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HolidayHintCard("补班日，常当作调到哪天", makeupDays) { date ->
                    to = date
                    picking = null
                    error = null
                }
            }
        }
        SmallTitle(text = "添加调课")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                error.orEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val src = from
                val dest = to
                if (src == null || dest == null) {
                    error = "原上课日和调到哪天都要选"
                    return@Button
                }
                val conflict = snapshot.settings.shiftConflict(src, dest)
                if (conflict != null) {
                    error = conflict
                    return@Button
                }
                error = onAdd(src, dest)
                if (error == null) {
                    from = null
                    to = null
                    picking = null
                }
            },
            enabled = from != null && to != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("保存调课") }
    }
}

@Composable
private fun HolidayHintCard(
    title: String,
    days: List<HolidayDay>,
    onPick: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            WeekdayNames.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (row in 0 until total / 7) {
            Row(Modifier.fillMaxWidth().height(40.dp)) {
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
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MiuixTheme.colorScheme.primary
                                        isToday -> MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                fontSize = 13.sp,
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
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onPick(selected) },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("用 ${formatLongDate(selected)}") }
    }
    if (embedded) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            body()
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            body()
        }
    }
}
