package cn.edu.gzus.qingke.ui.mine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.APP_VERSION_CODE
import cn.edu.gzus.qingke.data.APP_VERSION_NAME
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.AppUpdate
import cn.edu.gzus.qingke.data.CustomJwxt
import cn.edu.gzus.qingke.data.GZUS_LOGIN_CAS
import cn.edu.gzus.qingke.data.GZUS_LOGIN_JWXT
import cn.edu.gzus.qingke.data.LoginCaptcha
import cn.edu.gzus.qingke.data.RemindLeadMinutes
import cn.edu.gzus.qingke.data.ResolvedSchool
import cn.edu.gzus.qingke.data.School
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.decodeImageBytes
import cn.edu.gzus.qingke.data.formatLongDate
import cn.edu.gzus.qingke.data.formatSync
import cn.edu.gzus.qingke.data.hasTermStart
import cn.edu.gzus.qingke.data.liveKeepaliveStatus
import cn.edu.gzus.qingke.data.liveUpdateStatus
import cn.edu.gzus.qingke.data.mondayOf
import cn.edu.gzus.qingke.data.normalizedKind
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.openKeepaliveSettings
import cn.edu.gzus.qingke.data.openLiveUpdateSettings
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.resolvedCurrentWeek
import cn.edu.gzus.qingke.data.resolvedRemindLead
import cn.edu.gzus.qingke.data.school
import cn.edu.gzus.qingke.data.teachingWeeks
import cn.edu.gzus.qingke.data.termStartMonday
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.data.uniqueRooms
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.ConfirmDialog
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Radius
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import cn.edu.gzus.qingke.ui.components.Type
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform

/** 次要按钮（卡片内、表单内）的统一高度，和 ConfirmDialog 一致。 */
private val ButtonHeight = 44.dp

/** 页面主操作（登录）的高度，比次要按钮高一档。 */
private val PrimaryButtonHeight = 48.dp

/** 分组卡片：贴页边、无内边距，行自己带内边距（同系统设置页）。 */
private val GroupModifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page)

/** 表单下方的说明 / 错误小字：比输入框外沿多缩进一点，和输入框里的文字起点接近。 */
private val FieldNotePadding = PaddingValues(start = Inset.page + Space.xs, end = Inset.page, top = Space.s)

/** 展开在设置行下面的选择面板：左右和行标题对齐，上面紧贴所属行。 */
private val PanelPadding = PaddingValues(start = Inset.page, end = Inset.page, top = Space.xs, bottom = Space.l)

@Composable
fun MineScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
    busy: Boolean,
    error: String?,
    firstLogin: Boolean = false,
    onOpenOfficialLogin: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
    captcha: LoginCaptcha? = null,
    captchaError: String? = null,
    onRefreshCaptcha: () -> Unit = {},
    onSelectSchool: (School) -> Unit = {},
    onSelectGzusLogin: (String) -> Unit = {},
    onLogin: (String, String, String) -> Unit,
    onSync: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit = {},
    onToggleScheduleAdjust: (Boolean) -> Unit = {},
    onToggleRemind: (Boolean) -> Unit,
    onPickRemindLead: (Int) -> Unit,
    onPickTermStart: (LocalDate) -> Unit,
    onPickWeek: (Int) -> Unit,
    onLogout: () -> Unit,
    onSaveCustom: (CustomJwxt, Boolean) -> Unit = { _, _ -> },
    update: AppUpdate? = null,
    updateBusy: Boolean = false,
    updateError: String? = null,
    autoCheckUpdate: Boolean = true,
    onToggleAutoUpdate: (Boolean) -> Unit = {},
    onCheckGithubUpdate: () -> Unit = {},
    onOpenGithubUpdate: () -> Unit = {},
    onOpenDriveUpdate: () -> Unit = {},
    liveTesting: Boolean,
    onLiveTest: () -> Unit,
    onStopLiveTest: () -> Unit,
    onToggleCountdown: (Boolean) -> Unit = {},
) {
    val focusTermStart = remember { nav.consumeTermStartFocus() }
    var pickingDate by remember { mutableStateOf(focusTermStart) }
    val termStartRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focusTermStart) {
        // 等日期面板展开、布局稳定后再滚过去。
        if (focusTermStart) {
            delay(250)
            termStartRequester.bringIntoView()
        }
    }
    var pickingWeek by remember { mutableStateOf(false) }
    var pickingSchool by remember { mutableStateOf(false) }
    var pickingLead by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmSchool by remember { mutableStateOf<School?>(null) }
    var confirmGzusLogin by remember { mutableStateOf<String?>(null) }
    val selected = snapshot.school()
    val school = snapshot.resolved()
    val hasLocalData = snapshot.session.loggedIn || snapshot.hasTimetable
    // 同一时间只展开一个选择面板，和系统设置页一样。
    fun collapsePanels() {
        pickingSchool = false
        pickingDate = false
        pickingWeek = false
        pickingLead = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(Space.l))
        ScreenHeader(
            "",
            "我的",
            // 已登录时同步时间在账号头卡里，这里不再重复。
            if (snapshot.session.loggedIn) "" else "选好学校，用${school.loginName}账号登录",
        )
        Spacer(Modifier.height(Space.m))
        if (snapshot.session.loggedIn) {
            AccountHeader(
                name = snapshot.profile.name.ifBlank { snapshot.session.studentId }.ifBlank { "已登录" },
                lines = listOf(
                    listOf(snapshot.profile.studentId.ifBlank { snapshot.session.studentId }, snapshot.profile.className),
                    listOf(snapshot.profile.major, snapshot.profile.campus),
                ).map { row -> row.filter { it.isNotBlank() }.joinToString(" · ") }.filter { it.isNotBlank() },
                sync = formatSync(snapshot.session.lastSyncAt),
                busy = busy,
                onSync = onSync,
            )
        }
        SmallTitle(text = if (snapshot.session.loggedIn) "账号" else "登录")
        Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(
                title = "当前学校",
                summary = school.label,
                onClick = {
                    val open = !pickingSchool
                    collapsePanels()
                    pickingSchool = open
                },
            )
            if (pickingSchool) {
                SchoolOptions(
                    selected = selected,
                    selectedLabel = school.label,
                    onPick = { next ->
                        pickingSchool = false
                        when {
                            next == selected -> Unit
                            hasLocalData -> confirmSchool = next
                            else -> onSelectSchool(next)
                        }
                    },
                )
            }
            if (selected == School.Gzus) {
                GzusChannelRows(
                    channel = snapshot.settings.gzusLoginChannel,
                    onPick = { next ->
                        collapsePanels()
                        when {
                            next == snapshot.settings.gzusLoginChannel -> Unit
                            snapshot.session.loggedIn -> confirmGzusLogin = next
                            else -> onSelectGzusLogin(next)
                        }
                    },
                )
            }
            if (snapshot.session.loggedIn) {
                SwitchPreference(
                    title = "启动时自动同步",
                    summary = if (snapshot.settings.autoSyncOnStart) "打开应用时更新课表和水电" else "仅手动同步",
                    checked = snapshot.settings.autoSyncOnStart,
                    onCheckedChange = onToggleAutoSync,
                )
            }
        }
        if (!snapshot.session.loggedIn) {
            LoggedOutMine(
                snapshot = snapshot,
                nav = nav,
                busy = busy,
                error = error,
                firstLogin = firstLogin,
                school = school,
                captcha = captcha,
                captchaError = captchaError,
                onRefreshCaptcha = onRefreshCaptcha,
                onOpenOfficialLogin = onOpenOfficialLogin,
                onOpenChangePassword = onOpenChangePassword,
                onLogin = onLogin,
            )
        }
        Column(Modifier.bringIntoViewRequester(termStartRequester)) {
            SemesterBlock(
                snapshot = snapshot,
                school = school,
                pickingDate = pickingDate,
                pickingWeek = pickingWeek,
                onToggleDate = {
                    val open = !pickingDate
                    collapsePanels()
                    pickingDate = open
                },
                onToggleWeek = {
                    val open = !pickingWeek
                    collapsePanels()
                    pickingWeek = open
                },
                onPickTermStart = { date ->
                    pickingDate = false
                    onPickTermStart(date)
                },
                onPickWeek = { week ->
                    pickingWeek = false
                    onPickWeek(week)
                },
            )
        }
        // 顺序按使用频率：学期 → 课表 → 提醒 → 外观 → 更新 → 开发者，退出登录单独放最底下。
        SmallTitle(text = "课表")
        Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
            val courses = uniqueCourses(snapshot.slots)
            val rooms = uniqueRooms(snapshot.slots)
            ArrowPreference(
                title = "课表缩写",
                summary = if (courses.isEmpty()) "同步课表后可用" else "${courses.size} 门课程",
                onClick = { nav.open(Route.CourseAliases) },
            )
            ArrowPreference(
                title = "教室缩写",
                summary = if (rooms.isEmpty()) "同步课表后可用" else "${rooms.size} 间教室",
                onClick = { nav.open(Route.RoomAliases) },
            )
            val customized = snapshot.settings.periodTimeOverrides.size
            ArrowPreference(
                title = "节次时间",
                summary = if (customized == 0) "使用学校作息" else "已自定义 $customized 节",
                onClick = { nav.open(Route.PeriodTime) },
            )
            if (snapshot.settings.school() == School.Gzus) {
                val pulled = snapshot.scheduleAdjust
                val on = snapshot.settings.autoPullScheduleAdjust
                SwitchPreference(
                    title = "自动拉取调休",
                    summary = when {
                        !on -> "仅使用手动调课"
                        !snapshot.settings.hasTermStart() -> "需先设置第1周周一，补课才能对上周次"
                        pulled.fetchedAt == 0L -> "从 qingke.lazzyy.cn 获取放假与补课安排"
                        else -> "${pulled.title.ifBlank { "已拉取" }} · 放假 ${pulled.offs.size} 天 · 补课 ${pulled.shifts.size} 条"
                    },
                    checked = on,
                    onCheckedChange = onToggleScheduleAdjust,
                )
            }
            val shifts = snapshot.settings.scheduleShifts.size
            ArrowPreference(
                title = "调课",
                summary = if (shifts == 0) "按学校通知添加补课安排" else "$shifts 条",
                onClick = { nav.open(Route.ScheduleShifts) },
            )
        }
        RemindBlock(
            snapshot = snapshot,
            school = school,
            pickingLead = pickingLead,
            liveTesting = liveTesting,
            onToggleRemind = onToggleRemind,
            onToggleLead = {
                val open = !pickingLead
                collapsePanels()
                pickingLead = open
            },
            onPickLead = { minutes ->
                pickingLead = false
                onPickRemindLead(minutes)
            },
            onLiveTest = onLiveTest,
            onStopLiveTest = onStopLiveTest,
            onToggleCountdown = onToggleCountdown,
        )
        SmallTitle(text = "外观")
        Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(
                title = "主题设置",
                summary = "背景、底栏、界面缩放、课程配色",
                onClick = { nav.open(Route.ThemeSettings) },
            )
        }
        UpdateBlock(
            update = update,
            busy = updateBusy,
            error = updateError,
            autoCheck = autoCheckUpdate,
            onToggleAutoCheck = onToggleAutoUpdate,
            onCheckGithub = onCheckGithubUpdate,
            onOpenGithub = onOpenGithubUpdate,
            onOpenDrive = onOpenDriveUpdate,
        )
        DeveloperBlock(
            snapshot = snapshot,
            onSave = onSaveCustom,
        )
        if (snapshot.session.loggedIn) {
            // 危险操作单独一组放在最底下，不和常用开关挤在一起，避免误点；点了先弹确认框。
            Spacer(Modifier.height(Space.xl))
            Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Inset.rowMin)
                        .clickable(enabled = !busy) {
                            collapsePanels()
                            confirmLogout = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "退出登录",
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.Medium,
                        color = if (busy) Tone.muted() else Tone.danger(),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.l))
    }

    // —— 确认框：不可逆操作统一走 ConfirmDialog，确认前不改任何状态 ——
    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录？",
            message = "将退出${school.jwxtName}，并清除本机保存的密码。已同步的课表会保留。",
            confirmText = "退出登录",
            destructive = true,
            onConfirm = onLogout,
            onDismiss = { confirmLogout = false },
        )
    }
    confirmSchool?.let { next ->
        ConfirmDialog(
            title = "切换到${next.label}？",
            message = if (hasLocalData) {
                "将退出当前账号，并清除课表、成绩和学期设置。课表缩写与调课会保留。"
            } else {
                "之后使用${next.jwxtName}登录。"
            },
            confirmText = "切换",
            destructive = hasLocalData,
            onConfirm = { onSelectSchool(next) },
            onDismiss = { confirmSchool = null },
        )
    }
    confirmGzusLogin?.let { next ->
        ConfirmDialog(
            title = if (next == GZUS_LOGIN_CAS) "改用统一身份认证？" else "改用正方教务登录？",
            message = "将退出当前账号，需要重新登录。本地课表会保留。",
            confirmText = "切换",
            destructive = true,
            onConfirm = { onSelectGzusLogin(next) },
            onDismiss = { confirmGzusLogin = null },
        )
    }
}

@Composable
private fun LoggedOutMine(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    busy: Boolean,
    error: String?,
    firstLogin: Boolean,
    school: ResolvedSchool,
    captcha: LoginCaptcha?,
    captchaError: String?,
    onRefreshCaptcha: () -> Unit,
    onOpenOfficialLogin: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onLogin: (String, String, String) -> Unit,
) {
    var studentId by remember(school.id) { mutableStateOf(snapshot.session.studentId) }
    var password by remember(school.id) { mutableStateOf("") }
    var captchaCode by remember(school.id, captcha?.id) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val captchaBitmap = remember(captcha?.id, captcha?.bytes?.size) {
        captcha?.bytes?.let { runCatching { decodeImageBytes(it) }.getOrNull() }
    }
    val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page)

    // 表单：输入框之间统一 Space.s，和上面的分组卡拉开 Space.m。
    Spacer(Modifier.height(Space.m))
    TextField(
        value = studentId,
        onValueChange = { studentId = it.trim() },
        label = "学号",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = fieldModifier,
    )
    Spacer(Modifier.height(Space.s))
    TextField(
        value = password,
        onValueChange = { password = it },
        label = "密码",
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.padding(end = Space.s)) {
                Icon(
                    imageVector = if (visible) MiuixIcons.Hide else MiuixIcons.Show,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                    tint = Tone.muted(),
                )
            }
        },
        modifier = fieldModifier,
    )
    if (school.showsCaptcha) {
        Spacer(Modifier.height(Space.s))
        Row(
            modifier = fieldModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            TextField(
                value = captchaCode,
                onValueChange = { captchaCode = it.trim() },
                label = school.captchaHint.ifBlank { "验证码" },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            // 验证码图：小圆角色块，点一下换一张；深浅色下都用中性底，不刺眼。
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .aspectRatio(2.4f)
                    .clip(Radius.small)
                    .background(Tone.muted().copy(alpha = 0.08f))
                    .clickable(onClick = onRefreshCaptcha),
                contentAlignment = Alignment.Center,
            ) {
                if (captchaBitmap != null) {
                    Image(
                        bitmap = captchaBitmap,
                        contentDescription = "验证码，点按换一张",
                        modifier = Modifier.fillMaxSize().padding(Space.xs),
                    )
                } else {
                    Text(
                        if (captchaError.isNullOrBlank()) "点按加载" else "点按重试",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (!captchaError.isNullOrBlank()) {
            Text(
                captchaError,
                color = Tone.danger(),
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(FieldNotePadding),
            )
        }
    }
    if (firstLogin) {
        // 首登认证：这次登录走不通，说明 + 去处放在登录按钮之前，先看到该做什么。
        Spacer(Modifier.height(Space.m))
        Card(modifier = GroupModifier, insideMargin = Inset.card) {
            Text(
                "需要首登认证",
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "${school.loginName}要求先修改初始密码，改完再回来登录。",
                style = MiuixTheme.textStyles.body2,
                color = Tone.muted(),
            )
            Spacer(Modifier.height(Space.m))
            Button(
                onClick = onOpenOfficialLogin,
                modifier = Modifier.fillMaxWidth(),
                minHeight = ButtonHeight,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("打开${school.loginName}登录页") }
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = onOpenChangePassword,
                modifier = Modifier.fillMaxWidth(),
                minHeight = ButtonHeight,
            ) { Text("打开改密页") }
        }
    } else if (!error.isNullOrBlank()) {
        // 登录错误紧贴在按钮上方：看完错误，手指就在按钮上。
        Text(
            error,
            color = Tone.danger(),
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(FieldNotePadding),
        )
    }
    Spacer(Modifier.height(Space.l))
    Button(
        onClick = { onLogin(studentId, password, captchaCode) },
        enabled = !busy && studentId.isNotBlank() && password.isNotBlank(),
        modifier = fieldModifier,
        minHeight = PrimaryButtonHeight,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        if (busy) {
            InfiniteProgressIndicator()
            Spacer(Modifier.width(Space.s))
        }
        Text(if (busy) "正在登录…" else "登录并同步")
    }
    if (snapshot.settings.school() == School.Gzus && snapshot.settings.gzusLoginChannel == GZUS_LOGIN_CAS) {
        Text(
            "密码加密保存在本机，仅用于会话过期后自动重登；退出登录即清除。",
            color = Tone.muted(),
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(FieldNotePadding),
        )
    }
    if (snapshot.hasTimetable) {
        Spacer(Modifier.height(Space.m))
        InfoCard(
            title = "本地已有课表",
            summary = "${uniqueCourses(snapshot.slots).size} 门课 · ${formatSync(snapshot.session.lastSyncAt)}",
            modifier = Modifier.padding(horizontal = Inset.page),
            onClick = { nav.goTab(TabDest.Timetable) },
        )
    }
}

/** 选中标记：单选列表里当前项右侧的勾，全页同一个样子。 */
@Composable
private fun CheckMark(visible: Boolean) {
    if (visible) {
        Text(
            "✓",
            style = MiuixTheme.textStyles.main,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SchoolOptions(
    selected: School,
    selectedLabel: String,
    onPick: (School) -> Unit,
) {
    // 单选列表：当前项打勾，摘要只写教务系统，不再重复「当前」。
    School.entries.forEach { item ->
        ArrowPreference(
            title = item.label,
            summary = when {
                item == School.Custom && item == selected -> selectedLabel
                item == School.Custom -> "正方 / 强智 / 联奕，自填地址"
                else -> item.jwxtName
            },
            endActions = { CheckMark(item == selected) },
            onClick = { onPick(item) },
        )
    }
}

@Composable
private fun GzusChannelRows(
    channel: String,
    onPick: (String) -> Unit,
) {
    // 两个渠道是单选：当前那条右侧打勾，一眼看出选的是哪个。
    ArrowPreference(
        title = "登录渠道 · 正方教务",
        summary = "jwxt.gzus.edu.cn 直接登录",
        endActions = { CheckMark(channel != GZUS_LOGIN_CAS) },
        onClick = { onPick(GZUS_LOGIN_JWXT) },
    )
    ArrowPreference(
        title = "登录渠道 · 统一身份认证",
        summary = "cas.gzus.edu.cn · 可同步办事大厅",
        endActions = { CheckMark(channel == GZUS_LOGIN_CAS) },
        onClick = { onPick(GZUS_LOGIN_CAS) },
    )
}

@Composable
private fun SemesterBlock(
    snapshot: AppSnapshot,
    school: ResolvedSchool,
    pickingDate: Boolean,
    pickingWeek: Boolean,
    onToggleDate: () -> Unit,
    onToggleWeek: () -> Unit,
    onPickTermStart: (LocalDate) -> Unit,
    onPickWeek: (Int) -> Unit,
) {
    val today = nowDateTime().date
    val start = snapshot.settings.termStartMonday()
    val week = resolvedCurrentWeek(snapshot.settings, today)
    val maxWeek = maxOf(teachingWeeks(snapshot.slots), snapshot.settings.weekCount, 16).coerceIn(1, 30)
    SmallTitle(text = "学期")
    Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
        ArrowPreference(
            title = "第1周周一",
            summary = start?.let { formatLongDate(it) } ?: "未设置 · 同步后自动填入",
            onClick = onToggleDate,
        )
        if (pickingDate) {
            TermStartPicker(
                initial = start ?: mondayOf(today),
                onPick = onPickTermStart,
                embedded = true,
            )
        }
        ArrowPreference(
            title = "当前教学周",
            summary = when {
                !snapshot.settings.hasTermStart() -> "登录后自动对齐"
                week >= 1 -> "第${week}周 · 与${school.jwxtName}对齐"
                else -> "学期未开始"
            },
            onClick = onToggleWeek,
        )
        if (pickingWeek) {
            ChoiceChipPanel(title = "今天是第几周") {
                (1..maxWeek).chunked(4).forEach { row ->
                    ChoiceChipRow(
                        items = row.map { item -> "第${item}周" to (item == week) },
                        onPick = { index -> onPickWeek(row[index]) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TermStartPicker(
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
    val monday = mondayOf(selected)
    val muted = Tone.muted()
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) {
                Icon(MiuixIcons.ChevronBackward, contentDescription = "上一月", tint = muted)
            }
            Text(
                "${month.year}年${month.monthNumber}月",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { month = month.plus(DatePeriod(months = 1)) }) {
                Icon(MiuixIcons.ChevronForward, contentDescription = "下一月", tint = muted)
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
                    fontSize = Type.tiny,
                    color = muted,
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
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
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = if (isSelected || isToday) FontWeight.Medium else FontWeight.Normal,
                                color = when {
                                    isSelected -> MiuixTheme.colorScheme.onPrimary
                                    isToday -> MiuixTheme.colorScheme.primary
                                    !inMonth -> muted.copy(alpha = 0.4f)
                                    else -> MiuixTheme.colorScheme.onBackground
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(
            "将以 ${formatLongDate(monday)} 作为第1周周一",
            style = MiuixTheme.textStyles.footnote1,
            color = muted,
        )
        Spacer(Modifier.height(Space.m))
        Button(
            onClick = { onPick(monday) },
            modifier = Modifier.fillMaxWidth(),
            minHeight = ButtonHeight,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("确定")
        }
    }
    if (embedded) {
        Column(Modifier.fillMaxWidth().padding(PanelPadding)) {
            body()
        }
    } else {
        Card(modifier = GroupModifier, insideMargin = Inset.card) {
            body()
        }
    }
}

@Composable
private fun RemindBlock(
    snapshot: AppSnapshot,
    school: ResolvedSchool,
    pickingLead: Boolean,
    liveTesting: Boolean,
    onToggleRemind: (Boolean) -> Unit,
    onToggleLead: () -> Unit,
    onPickLead: (Int) -> Unit,
    onLiveTest: () -> Unit,
    onStopLiveTest: () -> Unit,
    onToggleCountdown: (Boolean) -> Unit = {},
) {
    val lead = snapshot.settings.resolvedRemindLead()
    val liveStatus = liveUpdateStatus(lead)
    SmallTitle(text = "提醒")
    Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
        SwitchPreference(
            title = "上课提醒",
            summary = if (school.hasPeriodClock) liveStatus else "${school.jwxtName}未公布作息，无法按时提醒",
            checked = school.hasPeriodClock && snapshot.settings.remindBeforeClass,
            onCheckedChange = { if (school.hasPeriodClock) onToggleRemind(it) },
            // 没作息时开关点了也没用，置灰而不是看着能点、点了没反应。
            enabled = school.hasPeriodClock,
        )
        if (school.hasPeriodClock) {
            SwitchPreference(
                title = "上下课倒计时",
                summary = if (snapshot.settings.liveCountdownEnabled) "胶囊显示剩余分钟，展开后按秒倒数" else "仅显示上课状态",
                checked = snapshot.settings.liveCountdownEnabled,
                onCheckedChange = onToggleCountdown,
            )
            ArrowPreference(
                title = "提前提醒",
                summary = if (snapshot.settings.remindBeforeClass) "上课前 $lead 分钟" else "上课前 $lead 分钟 · 提醒已关闭",
                onClick = onToggleLead,
            )
            // 选项面板紧跟在它的那一行下面展开，之前夹在「后台保活」后面，看着像是保活的选项。
            if (pickingLead) {
                ChoiceChipPanel(title = "上课前多久弹出提醒") {
                    RemindLeadMinutes.chunked(4).forEach { row ->
                        ChoiceChipRow(
                            items = row.map { item -> "$item 分钟" to (item == lead) },
                            onPick = { index -> onPickLead(row[index]) },
                        )
                    }
                }
            }
            if (platform() != Platform.Desktop) {
                ArrowPreference(
                    title = "后台保活",
                    summary = liveKeepaliveStatus(),
                    onClick = { openKeepaliveSettings() },
                )
            }
        }
        if (school.hasPeriodClock || liveTesting) {
            ArrowPreference(
                title = if (liveTesting) "停止 Live 测试" else "测试 Live 通知",
                summary = if (liveTesting) "测试进行中 · 课前 1 分钟、上课 2 分钟" else "预览胶囊、展开卡片和上下课切换",
                endActions = { if (liveTesting) StatusTag("进行中", color = Tone.success()) },
                onClick = { if (liveTesting) onStopLiveTest() else onLiveTest() },
            )
        }
        if (school.hasPeriodClock && (liveStatus.contains("权限") || liveStatus.contains("还没开") || liveStatus.contains("已关闭"))) {
            ArrowPreference(
                title = "打开系统通知设置",
                summary = liveStatus,
                endActions = { StatusTag("待处理", color = Tone.warning()) },
                onClick = { openLiveUpdateSettings() },
            )
        }
    }
}

/** 选择面板（周次、提前量、教务类型）：一行小字标题 + 等宽选项格，全页同一套样式。 */
@Composable
private fun ChoiceChipPanel(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(PanelPadding)) {
        Text(
            title,
            style = MiuixTheme.textStyles.footnote1,
            color = Tone.muted(),
        )
        Spacer(Modifier.height(Space.s))
        content()
    }
}

@Composable
private fun ChoiceChipRow(
    items: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    columns: Int = 4,
) {
    // 未选中用中性浅底（深浅色都成立），选中用主色 12% 底 + 主色字，和 StatusTag 同一语言。
    val idle = Tone.muted().copy(alpha = 0.08f)
    val primary = MiuixTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        items.forEachIndexed { index, (label, selected) ->
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .clip(Radius.small)
                    .background(if (selected) primary.copy(alpha = 0.12f) else idle)
                    .clickable { onPick(index) }
                    .padding(vertical = Space.s + Space.xs),
                textAlign = TextAlign.Center,
                color = if (selected) primary else MiuixTheme.colorScheme.onBackground,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
            )
        }
        repeat((columns - items.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

private val CustomKinds = listOf(
    "zhengfang" to "正方",
    "kingosoft" to "强智",
    "lyuap" to "联奕",
)

private const val CustomPathsHint = """# 一行一条，只填和默认不一样的
# loginPage=/jwglxt/xtgl/login_slogin.html
# login=/jwglxt/xtgl/login_slogin.html
# captcha=/jwglxt/kaptcha
# publicKey=/jwglxt/xtgl/login_getPublicKey.html
# calendar=/jwglxt/xtgl/index_cxAreaOne.html
# timetable=/jwglxt/kbcx/xskbcx_cxXsKb.html
# grades=/jwglxt/cjcx/cjcx_cxDgXscj.html
# exams=/jwglxt/kwgl/kscx_cxXsksxxIndex.html
# rooms=/jwglxt/cdjy/cdjy_cxKxcdlb.html
# news=/jwglxt/xtgl/index_cxNews.html
# logout=/jwglxt/xtgl/login_logoutAccount.html"""

@Composable
private fun DeveloperBlock(
    snapshot: AppSnapshot,
    onSave: (CustomJwxt, Boolean) -> Unit,
) {
    val saved = snapshot.settings.customJwxt
    var expanded by remember { mutableStateOf(snapshot.settings.schoolId == School.Custom.id) }
    var name by remember(saved) { mutableStateOf(saved.name) }
    var kind by remember(saved) { mutableStateOf(saved.normalizedKind()) }
    var origin by remember(saved) { mutableStateOf(saved.origin) }
    var loginUrl by remember(saved) { mutableStateOf(saved.loginUrl) }
    var changePasswordUrl by remember(saved) { mutableStateOf(saved.changePasswordUrl) }
    var casOrigin by remember(saved) { mutableStateOf(saved.casOrigin) }
    var casService by remember(saved) { mutableStateOf(saved.casService) }
    var rooms by remember(saved) { mutableStateOf(saved.supportsFreeRooms) }
    var clock by remember(saved) { mutableStateOf(saved.hasPeriodClock) }
    var showsCaptcha by remember(saved) { mutableStateOf(saved.showsCaptcha) }
    var requiresCaptcha by remember(saved) { mutableStateOf(saved.requiresCaptcha) }
    var captchaHint by remember(saved) { mutableStateOf(saved.captchaHint) }
    var pathsText by remember(saved) { mutableStateOf(saved.pathsText) }
    var confirmApply by remember { mutableStateOf(false) }
    val kindLabel = CustomKinds.firstOrNull { it.first == kind }?.second ?: "正方"
    val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page)
    SmallTitle(text = "开发者")
    Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
        ArrowPreference(
            title = "自定义教务",
            summary = if (origin.isBlank()) "未配置 · 支持正方、强智、联奕" else "$kindLabel · $origin",
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            ChoiceChipPanel(title = "教务类型 · 仅改请求地址与路径，作息和空教室需学校系统支持") {
                ChoiceChipRow(
                    items = CustomKinds.map { (id, label) -> label to (kind == id) },
                    onPick = { index -> kind = CustomKinds[index].first },
                    columns = CustomKinds.size,
                )
            }
        }
    }
    if (!expanded) return
    // 不写协议时仓库会自动补 https://（CustomJwxt.originClean），这里只拦明显填错的协议。
    val originValid = origin.isNotBlank() && (
        "://" !in origin ||
            origin.startsWith("http://", ignoreCase = true) ||
            origin.startsWith("https://", ignoreCase = true)
        )
    Spacer(Modifier.height(Space.m))
    TextField(
        value = name,
        onValueChange = { name = it.take(20) },
        label = "显示名称",
        singleLine = true,
        modifier = fieldModifier,
    )
    Spacer(Modifier.height(Space.s))
    TextField(
        value = origin,
        onValueChange = { origin = it.trim() },
        label = "教务地址，例如 https://jwxt.xxx.edu.cn",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = fieldModifier,
    )
    // 错误提示紧跟出错的那个输入框，而不是堆到按钮旁边。
    if (origin.isNotBlank() && !originValid) {
        Text(
            "仅支持 http:// 或 https://，也可以只填域名",
            color = Tone.danger(),
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(FieldNotePadding),
        )
    }
    Spacer(Modifier.height(Space.s))
    TextField(
        value = loginUrl,
        onValueChange = { loginUrl = it.trim() },
        label = "登录页（可选）",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = fieldModifier,
    )
    Spacer(Modifier.height(Space.s))
    TextField(
        value = changePasswordUrl,
        onValueChange = { changePasswordUrl = it.trim() },
        label = "改密页（可选）",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = fieldModifier,
    )
    if (kind == "lyuap") {
        Spacer(Modifier.height(Space.s))
        TextField(
            value = casOrigin,
            onValueChange = { casOrigin = it.trim() },
            label = "门户地址（可选）",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = fieldModifier,
        )
        Spacer(Modifier.height(Space.s))
        TextField(
            value = casService,
            onValueChange = { casService = it.trim() },
            label = "CAS service（可选）",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = fieldModifier,
        )
    }
    if (kind == "zhengfang") {
        Spacer(Modifier.height(Space.s))
        TextField(
            value = pathsText,
            onValueChange = { pathsText = it },
            label = "路径覆盖 key=value",
            singleLine = false,
            modifier = fieldModifier,
        )
        Text(
            CustomPathsHint,
            modifier = Modifier.padding(FieldNotePadding),
            style = MiuixTheme.textStyles.footnote1,
            color = Tone.muted(),
        )
    }
    Spacer(Modifier.height(Space.m))
    Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
        if (kind == "zhengfang") {
            SwitchPreference(
                title = "空教室",
                summary = "确认正方开放了接口再开启",
                checked = rooms,
                onCheckedChange = { rooms = it },
            )
        }
        SwitchPreference(
            title = "有节次时间",
            summary = if (clock) "按正方常见 16 节作息提醒，非学校官方" else "学校未公布作息时保持关闭",
            checked = clock,
            onCheckedChange = { clock = it },
        )
        // 联奕强制要验证码：开关显示为开并置灰，免得点了没反应。
        SwitchPreference(
            title = "显示验证码",
            summary = if (kind == "lyuap") "联奕门户固定需要" else "登录页有验证码图时开启",
            checked = showsCaptcha || kind == "lyuap",
            onCheckedChange = { showsCaptcha = it },
            enabled = kind != "lyuap",
        )
        SwitchPreference(
            title = "必须填验证码",
            summary = if (kind == "lyuap") "联奕门户固定需要" else "不填也能登录时关闭",
            checked = requiresCaptcha || kind == "lyuap",
            onCheckedChange = { requiresCaptcha = it },
            enabled = kind != "lyuap",
        )
    }
    if (showsCaptcha || kind == "lyuap") {
        Spacer(Modifier.height(Space.s))
        TextField(
            value = captchaHint,
            onValueChange = { captchaHint = it.take(30) },
            label = "验证码提示（可选）",
            singleLine = true,
            modifier = fieldModifier,
        )
    }
    // 两个按钮共用同一份草稿；空教室开关只有正方才显示，换成别的类型时不能把隐藏的旧值带进去。
    val draft = CustomJwxt(
        name = name,
        kind = kind,
        origin = origin,
        loginUrl = loginUrl,
        changePasswordUrl = changePasswordUrl,
        casOrigin = casOrigin,
        casService = casService,
        supportsFreeRooms = rooms && kind == "zhengfang",
        hasPeriodClock = clock,
        showsCaptcha = showsCaptcha,
        requiresCaptcha = requiresCaptcha,
        captchaHint = captchaHint,
        pathsText = pathsText,
    )
    val isCustomNow = snapshot.settings.school() == School.Custom
    // 切学校会退出登录并清掉课表（AppRepository.setSchool），和「当前学校」里换学校一样要先确认。
    val switchClearsData = !isCustomNow && (snapshot.session.loggedIn || snapshot.hasTimetable)
    // 按钮层级：主操作（切换 / 保存）在上用主色，「只保存」是次要操作在下。
    Spacer(Modifier.height(Space.l))
    Button(
        onClick = {
            if (switchClearsData) confirmApply = true else onSave(draft, true)
        },
        enabled = originValid,
        modifier = fieldModifier,
        minHeight = ButtonHeight,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) { Text(if (isCustomNow) "保存配置" else "保存并切到自定义教务") }
    if (!isCustomNow) {
        Spacer(Modifier.height(Space.s))
        Button(
            onClick = { onSave(draft, false) },
            enabled = origin.isBlank() || originValid,
            modifier = fieldModifier,
            minHeight = ButtonHeight,
        ) { Text("仅保存配置") }
    }
    if (confirmApply && switchClearsData) {
        ConfirmDialog(
            title = "切换到自定义教务？",
            message = "将退出当前账号，并清除课表、成绩和学期设置。课表缩写与调课会保留。",
            confirmText = "保存并切换",
            destructive = true,
            onConfirm = { onSave(draft, true) },
            onDismiss = { confirmApply = false },
        )
    }
}

@Composable
private fun UpdateBlock(
    update: AppUpdate?,
    busy: Boolean,
    error: String?,
    autoCheck: Boolean,
    onToggleAutoCheck: (Boolean) -> Unit,
    onCheckGithub: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenDrive: () -> Unit,
) {
    SmallTitle(text = "更新")
    Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
        // 「当前版本」和「检查更新」原来是两行、点了效果一样，合成一行；检查中不重复发请求。
        ArrowPreference(
            title = "检查更新",
            summary = when {
                busy -> "正在检查…"
                error != null -> "检查失败 · $error"
                update == null -> "当前版本 $APP_VERSION_NAME ($APP_VERSION_CODE)"
                update.newer -> "当前 $APP_VERSION_NAME · 可更新到 ${update.versionName}"
                else -> "当前 $APP_VERSION_NAME · 已是最新"
            },
            endActions = {
                when {
                    busy -> Unit
                    error != null -> StatusTag("失败", color = Tone.danger())
                    update?.newer == true -> StatusTag("新版本")
                    else -> Unit
                }
            },
            onClick = { if (!busy) onCheckGithub() },
        )
        if (update != null) {
            ArrowPreference(
                title = if (update.newer) "下载 ${update.versionName}" else "更新日志",
                summary = if (update.newer) "在浏览器打开 GitHub Release" else "GitHub Release",
                onClick = onOpenGithub,
            )
        }
        ArrowPreference(
            title = "网盘下载",
            summary = "GitHub 无法访问时使用",
            onClick = onOpenDrive,
        )
        SwitchPreference(
            title = "自动检查更新",
            summary = if (autoCheck) "打开应用时检查" else "仅手动检查",
            checked = autoCheck,
            onCheckedChange = onToggleAutoCheck,
        )
    }
    val notes = update?.notes.orEmpty()
    if (notes.isNotBlank()) {
        Spacer(Modifier.height(Space.s))
        Card(modifier = GroupModifier, insideMargin = Inset.card) {
            Text(
                if (update?.newer == true) "${update.versionName} 更新内容" else "更新内容",
                style = MiuixTheme.textStyles.footnote1,
                color = Tone.muted(),
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                notes,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}

/** 账号头卡：头像首字、姓名、学号班级；同步状态和最常用的「同步」按钮放在一眼能点到的位置。 */
@Composable
private fun AccountHeader(name: String, lines: List<String>, sync: String, busy: Boolean, onSync: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val divider = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
    Card(modifier = GroupModifier, insideMargin = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Inset.card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1), style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Medium, color = primary)
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                lines.forEach { line ->
                    Spacer(Modifier.height(Space.xs / 2))
                    Text(
                        line,
                        style = MiuixTheme.textStyles.footnote1,
                        color = Tone.muted(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // 细分割线把「身份」和「同步状态」分开，和 ListGroup 的行分割同一规格。
        Box(Modifier.padding(horizontal = Inset.page).fillMaxWidth().height(0.5.dp).background(divider))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (busy) "正在同步…" else sync,
                modifier = Modifier.weight(1f).padding(end = Space.s),
                style = MiuixTheme.textStyles.footnote1,
                color = Tone.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (busy) "同步中" else "立即同步",
                modifier = Modifier
                    .clip(Radius.pill)
                    .background(primary.copy(alpha = if (busy) 0.08f else 0.12f))
                    .clickable(enabled = !busy, onClick = onSync)
                    .padding(horizontal = Space.l, vertical = Space.xs + Space.xs / 2),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                color = if (busy) primary.copy(alpha = 0.6f) else primary,
            )
        }
    }
}
