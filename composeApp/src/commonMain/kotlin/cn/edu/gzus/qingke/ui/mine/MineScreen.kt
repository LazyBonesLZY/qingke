package cn.edu.gzus.qingke.ui.mine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.LoginCaptcha
import cn.edu.gzus.qingke.data.RemindLeadMinutes
import cn.edu.gzus.qingke.data.APP_VERSION_NAME
import cn.edu.gzus.qingke.data.APP_VERSION_CODE
import cn.edu.gzus.qingke.data.AppUpdate
import cn.edu.gzus.qingke.data.CustomJwxt
import cn.edu.gzus.qingke.data.DRIVE_UPDATE_URL
import cn.edu.gzus.qingke.data.GITHUB_RELEASES_URL
import cn.edu.gzus.qingke.data.GZUS_LOGIN_CAS
import cn.edu.gzus.qingke.data.GZUS_LOGIN_JWXT
import cn.edu.gzus.qingke.data.ResolvedSchool
import cn.edu.gzus.qingke.data.School
import cn.edu.gzus.qingke.data.normalizedKind
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.resolvedRemindLead
import cn.edu.gzus.qingke.data.decodeImageBytes
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.school
import cn.edu.gzus.qingke.data.formatLongDate
import cn.edu.gzus.qingke.data.formatSync
import cn.edu.gzus.qingke.data.hasTermStart
import cn.edu.gzus.qingke.data.liveUpdateStatus
import cn.edu.gzus.qingke.data.mondayOf
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.openLiveUpdateSettings
import cn.edu.gzus.qingke.data.resolvedCurrentWeek
import cn.edu.gzus.qingke.data.teachingWeeks
import cn.edu.gzus.qingke.data.termStartMonday
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ProfileCard
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.tabPagePadding
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
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingWeek by remember { mutableStateOf(false) }
    var pickingSchool by remember { mutableStateOf(false) }
    var pickingLead by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmSchool by remember { mutableStateOf<School?>(null) }
    var confirmGzusLogin by remember { mutableStateOf<String?>(null) }
    val selected = snapshot.school()
    val school = snapshot.resolved()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabPagePadding(contentPadding)),
    ) {
        Spacer(Modifier.height(16.dp))
        ScreenHeader(
            "",
            "我的",
            if (snapshot.session.loggedIn) {
                formatSync(snapshot.session.lastSyncAt)
            } else {
                "学号密码只在这里。先选学校，再登录${school.loginName}。"
            },
        )
        Spacer(Modifier.height(12.dp))
        if (snapshot.session.loggedIn) {
            ProfileCard(
                name = snapshot.profile.name.ifBlank { snapshot.session.studentId },
                fields = listOf(
                    "学号" to snapshot.profile.studentId.ifBlank { snapshot.session.studentId },
                    "班级" to snapshot.profile.className,
                    "专业" to snapshot.profile.major,
                    "校区" to snapshot.profile.campus,
                    "学期" to listOf(snapshot.profile.yearName, snapshot.profile.termLabel).filter { it.isNotBlank() }.joinToString(" "),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
        SmallTitle(text = if (snapshot.session.loggedIn) "账号" else "登录")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            SchoolRow(
                selectedLabel = school.label,
                onToggle = {
                    pickingSchool = !pickingSchool
                    pickingDate = false
                    pickingWeek = false
                    pickingLead = false
                    confirmLogout = false
                    confirmGzusLogin = null
                    if (!pickingSchool) confirmSchool = null
                },
            )
            SchoolExtras(
                selected = selected,
                selectedLabel = school.label,
                expanded = pickingSchool,
                confirm = confirmSchool,
                hasLocalData = snapshot.session.loggedIn || snapshot.hasTimetable,
                onPick = { next ->
                    pickingSchool = false
                    confirmLogout = false
                    confirmGzusLogin = null
                    if (next == selected) {
                        confirmSchool = null
                    } else if (snapshot.session.loggedIn || snapshot.hasTimetable) {
                        confirmSchool = next
                    } else {
                        confirmSchool = null
                        onSelectSchool(next)
                    }
                },
                onConfirm = { next ->
                    confirmSchool = null
                    onSelectSchool(next)
                },
                onCancel = { confirmSchool = null },
            )
            if (selected == School.Gzus) {
                GzusChannelRows(
                    channel = snapshot.settings.gzusLoginChannel,
                    confirm = confirmGzusLogin,
                    loggedIn = snapshot.session.loggedIn,
                    onPick = { next ->
                        pickingSchool = false
                        pickingDate = false
                        pickingWeek = false
                        pickingLead = false
                        confirmSchool = null
                        confirmLogout = false
                        if (next == snapshot.settings.gzusLoginChannel) {
                            confirmGzusLogin = null
                        } else if (snapshot.session.loggedIn) {
                            confirmGzusLogin = next
                        } else {
                            confirmGzusLogin = null
                            onSelectGzusLogin(next)
                        }
                    },
                    onConfirm = { next ->
                        confirmGzusLogin = null
                        onSelectGzusLogin(next)
                    },
                    onCancel = { confirmGzusLogin = null },
                )
            }
            if (snapshot.session.loggedIn) {
                SwitchPreference(
                    title = "启动时自动同步",
                    summary = if (snapshot.settings.autoSyncOnStart) "打开应用后自动拉课表和水电" else "只在点立即同步时更新",
                    checked = snapshot.settings.autoSyncOnStart,
                    onCheckedChange = onToggleAutoSync,
                )
                ArrowPreference(
                    title = "立即同步",
                    summary = if (busy) "同步中" else formatSync(snapshot.session.lastSyncAt),
                    onClick = onSync,
                )
                ArrowPreference(
                    title = "退出登录",
                    summary = "清除会话和本机密码，课表留在本地",
                    onClick = {
                        confirmLogout = !confirmLogout
                        pickingSchool = false
                        confirmSchool = null
                        confirmGzusLogin = null
                    },
                )
                if (confirmLogout) {
                    InlineConfirm(
                        title = "退出登录",
                        body = "退出${school.jwxtName}会话和本机保存的自动重登密码。已经同步过的课表留在本地。",
                        confirmLabel = "确定退出",
                        onConfirm = {
                            confirmLogout = false
                            onLogout()
                        },
                        onCancel = { confirmLogout = false },
                    )
                }
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
        SemesterBlock(
            snapshot = snapshot,
            school = school,
            pickingDate = pickingDate,
            pickingWeek = pickingWeek,
            onToggleDate = {
                pickingDate = !pickingDate
                pickingWeek = false
                pickingSchool = false
                pickingLead = false
            },
            onToggleWeek = {
                pickingWeek = !pickingWeek
                pickingDate = false
                pickingSchool = false
                pickingLead = false
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
        SmallTitle(text = "课表")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            val courses = uniqueCourses(snapshot.slots)
            ArrowPreference(
                title = "课表缩写",
                summary = if (courses.isEmpty()) "同步课表后才能改格子里的简称" else "${courses.size} 门课 · 点进去改",
                onClick = { nav.open(Route.CourseAliases) },
            )
            if (snapshot.settings.school() == School.Gzus) {
                val pulled = snapshot.scheduleAdjust
                val on = snapshot.settings.autoPullScheduleAdjust
                SwitchPreference(
                    title = "自动拉取调休",
                    summary = when {
                        !on -> "关闭后只保留手动调课"
                        !snapshot.settings.hasTermStart() -> "已开。先选第1周周一，补课才对得上第几周"
                        pulled.fetchedAt == 0L -> "从 qingke.lazzyy.cn 拉放假和补课"
                        else -> buildString {
                            append(pulled.title.ifBlank { "已拉取" })
                            append(" · ${pulled.offs.size} 天放假 · ${pulled.shifts.size} 条补课")
                        }
                    },
                    checked = on,
                    onCheckedChange = onToggleScheduleAdjust,
                )
            }
            ArrowPreference(
                title = "调课",
                summary = if (snapshot.settings.scheduleShifts.isEmpty()) {
                    "节假日补课按学校通知填写"
                } else {
                    "${snapshot.settings.scheduleShifts.size} 条 · 原上课日的课改到另一天"
                },
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
                pickingLead = !pickingLead
                pickingDate = false
                pickingWeek = false
                pickingSchool = false
            },
            onPickLead = { minutes ->
                pickingLead = false
                onPickRemindLead(minutes)
            },
            onLiveTest = onLiveTest,
            onStopLiveTest = onStopLiveTest,
        )
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
        Spacer(Modifier.height(16.dp))
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
    var studentId by remember { mutableStateOf(snapshot.session.studentId) }
    var password by remember { mutableStateOf("") }
    var captchaCode by remember(school.id, captcha?.id) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val captchaBitmap = remember(captcha?.id, captcha?.bytes?.size) {
        captcha?.bytes?.let { runCatching { decodeImageBytes(it) }.getOrNull() }
    }

    Spacer(Modifier.height(8.dp))
    TextField(
        value = studentId,
        onValueChange = { studentId = it.trim() },
        label = "学号",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    TextField(
        value = password,
        onValueChange = { password = it },
        label = "密码",
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = if (visible) MiuixIcons.Hide else MiuixIcons.Show,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    if (school.showsCaptcha) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = captchaCode,
                onValueChange = { captchaCode = it.trim() },
                label = school.captchaHint.ifBlank { "验证码" },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Card(
                modifier = Modifier
                    .height(48.dp)
                    .aspectRatio(2.4f)
                    .clickable(onClick = onRefreshCaptcha),
                insideMargin = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (captchaBitmap != null) {
                        Image(
                            bitmap = captchaBitmap,
                            contentDescription = "验证码，点一下换一张",
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    } else {
                        Text(
                            if (captchaError.isNullOrBlank()) "点我加载" else "点我重试",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        if (!captchaError.isNullOrBlank()) {
            Text(
                captchaError,
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
    if (firstLogin) {
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("需要首登认证", style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(
                "${school.loginName}要求先改密码，改完才能登录。青课改不了，打开官方页面按提示改。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenOfficialLogin,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 44.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("打开${school.loginName}登录页") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenChangePassword,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 44.dp,
            ) { Text("打开改密页") }
        }
    } else if (!error.isNullOrBlank()) {
        Text(
            error,
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { onLogin(studentId, password, captchaCode) },
        enabled = !busy && studentId.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        minHeight = 50.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        if (busy) InfiniteProgressIndicator()
        Text(if (busy) "正在登录" else "登录并同步")
    }
    if (snapshot.settings.school() == School.Gzus && snapshot.settings.gzusLoginChannel == GZUS_LOGIN_CAS) {
        Text(
            "密码会加密存在本机，用于门户过期后自动重登；退出登录即清除。",
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    if (snapshot.hasTimetable) {
        Spacer(Modifier.height(12.dp))
        InfoCard(
            title = "本地还留着上次课表",
            summary = "${uniqueCourses(snapshot.slots).size} 门课 · ${formatSync(snapshot.session.lastSyncAt)}",
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = { nav.goTab(TabDest.Timetable) },
        )
    }
}

@Composable
private fun SchoolRow(
    selectedLabel: String,
    onToggle: () -> Unit,
) {
    ArrowPreference(
        title = "当前学校",
        summary = selectedLabel,
        onClick = onToggle,
    )
}

@Composable
private fun SchoolExtras(
    selected: School,
    selectedLabel: String,
    expanded: Boolean,
    confirm: School?,
    hasLocalData: Boolean,
    onPick: (School) -> Unit,
    onConfirm: (School) -> Unit,
    onCancel: () -> Unit,
) {
    if (expanded) {
        School.entries.forEach { item ->
            ArrowPreference(
                title = item.label,
                summary = when {
                    item == selected && item == School.Custom -> "当前 · $selectedLabel"
                    item == selected -> "当前 · ${item.jwxtName}"
                    item == School.Custom -> "自己填正方 / 强智 / 联奕地址"
                    else -> item.jwxtName
                },
                onClick = { onPick(item) },
            )
        }
    }
    if (confirm != null) {
        InlineConfirm(
            title = "换成 ${confirm.label}",
            body = if (hasLocalData) {
                "会退出当前登录，清掉课表、成绩和学期周次。课表缩写和调课留着。"
            } else {
                "之后用 ${confirm.jwxtName} 登录。"
            },
            confirmLabel = "换到这所学校",
            onConfirm = { onConfirm(confirm) },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun GzusChannelRows(
    channel: String,
    confirm: String?,
    loggedIn: Boolean,
    onPick: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    ArrowPreference(
        title = "正方教务",
        summary = if (channel != GZUS_LOGIN_CAS) "当前 · jwxt.gzus.edu.cn 直接登录" else "只进教学管理信息服务平台",
        onClick = { onPick(GZUS_LOGIN_JWXT) },
    )
    if (confirm == GZUS_LOGIN_JWXT) {
        InlineConfirm(
            title = "换成正方直接登录",
            body = if (loggedIn) {
                "会退出当前登录。本地课表留下。之后用正方教务重新登录。"
            } else {
                "之后只走正方登录页。"
            },
            confirmLabel = "换到这个渠道",
            onConfirm = { onConfirm(GZUS_LOGIN_JWXT) },
            onCancel = onCancel,
        )
    }
    ArrowPreference(
        title = "统一身份认证",
        summary = if (channel == GZUS_LOGIN_CAS) "当前 · 可进正方和办事大厅" else "cas.gzus.edu.cn，登录后也能同步办事大厅",
        onClick = { onPick(GZUS_LOGIN_CAS) },
    )
    if (confirm == GZUS_LOGIN_CAS) {
        InlineConfirm(
            title = "换成统一身份认证",
            body = if (loggedIn) {
                "会退出当前登录。本地课表留下。之后用统一身份认证重新登录。"
            } else {
                "之后从门户进教务，并能同步办事大厅。"
            },
            confirmLabel = "换到这个渠道",
            onConfirm = { onConfirm(GZUS_LOGIN_CAS) },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun InlineConfirm(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text(confirmLabel) }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
        ) { Text("取消") }
    }
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        ArrowPreference(
            title = "第1周周一",
            summary = start?.let { formatLongDate(it) } ?: "同步后自动写入，也可以自己选",
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
            summary = if (snapshot.settings.hasTermStart()) {
                "${school.jwxtName}周表对齐 · 今天第${week}周"
            } else {
                "登录后自动对齐，也可以点开改"
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
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) {
                Icon(MiuixIcons.ChevronBackward, contentDescription = "上一月")
            }
            Text(
                "${month.year}年${month.monthNumber}月",
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
        Spacer(Modifier.height(8.dp))
        Text(
            "会对齐到 ${formatLongDate(monday)}",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onPick(monday) },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("用这一周当第1周")
        }
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
) {
    val lead = snapshot.settings.resolvedRemindLead()
    val liveStatus = liveUpdateStatus(lead)
    SmallTitle(text = "提醒")
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        SwitchPreference(
            title = "上课提醒",
            summary = if (school.hasPeriodClock) {
                liveStatus
            } else {
                "${school.jwxtName}没有公布节次时间，不会按钟点提醒"
            },
            checked = school.hasPeriodClock && snapshot.settings.remindBeforeClass,
            onCheckedChange = { if (school.hasPeriodClock) onToggleRemind(it) },
        )
        if (school.hasPeriodClock) {
            ArrowPreference(
                title = "提前多久提醒",
                summary = "上课前 $lead 分钟",
                onClick = onToggleLead,
            )
            if (pickingLead) {
                ChoiceChipPanel(title = "上课前多久弹出 Live") {
                    RemindLeadMinutes.chunked(4).forEach { row ->
                        ChoiceChipRow(
                            items = row.map { item -> "${item}分" to (item == lead) },
                            onPick = { index -> onPickLead(row[index]) },
                        )
                    }
                }
            }
        }
        ArrowPreference(
            title = if (liveTesting) "停止 Live 测试" else "测试 Live 通知",
            summary = if (liveTesting) "进度条约 8 分钟，点这里停" else "发一条上课中的进度通知，看看效果",
            onClick = { if (liveTesting) onStopLiveTest() else onLiveTest() },
        )
        if (liveStatus.contains("未开启") || liveStatus.contains("权限") || liveStatus.contains("还没开")) {
            ArrowPreference(
                title = "打开系统通知设置",
                summary = liveStatus,
                onClick = { openLiveUpdateSettings() },
            )
        }
    }
}

@Composable
private fun ChoiceChipPanel(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(
            title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ChoiceChipRow(
    items: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, (label, selected) ->
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MiuixTheme.colorScheme.surface,
                    )
                    .clickable { onPick(index) }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body2,
            )
        }
        repeat(4 - items.size) { Spacer(Modifier.weight(1f)) }
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
    val kindLabel = CustomKinds.firstOrNull { it.first == kind }?.second ?: "正方"
    SmallTitle(text = "开发者")
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        ArrowPreference(
            title = "自定义教务",
            summary = if (origin.isBlank()) "选正方 / 强智 / 联奕，再填学校地址" else "$kindLabel · $origin",
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    "只改请求地址和路径。作息、空教室这些得学校自己有。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "教务类型",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CustomKinds.forEach { (id, label) ->
                        val selected = kind == id
                        Text(
                            label,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MiuixTheme.colorScheme.surface,
                                )
                                .clickable { kind = id }
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
        }
    }
    if (!expanded) return
    Spacer(Modifier.height(8.dp))
    TextField(
        value = name,
        onValueChange = { name = it.take(20) },
        label = "显示名称",
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    TextField(
        value = origin,
        onValueChange = { origin = it.trim() },
        label = "教务地址，例如 https://jwxt.xxx.edu.cn",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    TextField(
        value = loginUrl,
        onValueChange = { loginUrl = it.trim() },
        label = "登录页，可留空",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    TextField(
        value = changePasswordUrl,
        onValueChange = { changePasswordUrl = it.trim() },
        label = "改密页，可留空",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    if (kind == "lyuap") {
        Spacer(Modifier.height(8.dp))
        TextField(
            value = casOrigin,
            onValueChange = { casOrigin = it.trim() },
            label = "门户地址，可留空",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = casService,
            onValueChange = { casService = it.trim() },
            label = "CAS service，可留空",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
    if (kind == "zhengfang") {
        Spacer(Modifier.height(8.dp))
        TextField(
            value = pathsText,
            onValueChange = { pathsText = it },
            label = "路径覆盖 key=value",
            singleLine = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Text(
            CustomPathsHint,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        if (kind == "zhengfang") {
            SwitchPreference(
                title = "空教室",
                summary = "只在正方确认有接口时打开",
                checked = rooms,
                onCheckedChange = { rooms = it },
            )
        }
        SwitchPreference(
            title = "有节次时间",
            summary = if (clock) "按正方常见 16 节提醒，不是学校官方表" else "学校没公布作息就别开",
            checked = clock,
            onCheckedChange = { clock = it },
        )
        SwitchPreference(
            title = "显示验证码",
            summary = if (kind == "lyuap") "联奕门户通常要验证码" else "登录页会出图再开",
            checked = showsCaptcha || kind == "lyuap",
            onCheckedChange = { showsCaptcha = it },
        )
        SwitchPreference(
            title = "必须填验证码",
            summary = if (kind == "lyuap") "联奕默认必须填" else "没有图也能先试登录就关",
            checked = requiresCaptcha || kind == "lyuap",
            onCheckedChange = { requiresCaptcha = it },
        )
    }
    if (showsCaptcha || kind == "lyuap") {
        Spacer(Modifier.height(8.dp))
        TextField(
            value = captchaHint,
            onValueChange = { captchaHint = it.take(30) },
            label = "验证码提示，可留空",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            onSave(
                CustomJwxt(
                    name = name,
                    kind = kind,
                    origin = origin,
                    loginUrl = loginUrl,
                    changePasswordUrl = changePasswordUrl,
                    casOrigin = casOrigin,
                    casService = casService,
                    supportsFreeRooms = rooms,
                    hasPeriodClock = clock,
                    showsCaptcha = showsCaptcha,
                    requiresCaptcha = requiresCaptcha,
                    captchaHint = captchaHint,
                    pathsText = pathsText,
                ),
                false,
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        minHeight = 44.dp,
    ) { Text("只保存配置") }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            onSave(
                CustomJwxt(
                    name = name,
                    kind = kind,
                    origin = origin,
                    loginUrl = loginUrl,
                    changePasswordUrl = changePasswordUrl,
                    casOrigin = casOrigin,
                    casService = casService,
                    supportsFreeRooms = rooms,
                    hasPeriodClock = clock,
                    showsCaptcha = showsCaptcha,
                    requiresCaptcha = requiresCaptcha,
                    captchaHint = captchaHint,
                    pathsText = pathsText,
                ),
                true,
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        minHeight = 44.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) { Text("保存并切到自定义教务") }
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        SwitchPreference(
            title = "自动检查更新",
            summary = if (autoCheck) "打开应用后检查，有新版本会提示需要更新" else "只在点检查 GitHub 时看",
            checked = autoCheck,
            onCheckedChange = onToggleAutoCheck,
        )
        ArrowPreference(
            title = "当前版本",
            summary = "$APP_VERSION_NAME ($APP_VERSION_CODE)",
            onClick = onCheckGithub,
        )
        ArrowPreference(
            title = if (busy) "正在检查 GitHub" else "检查 GitHub Release",
            summary = when {
                error != null -> error
                update == null -> "看 LazyBonesLZY/qingke 有没有更新"
                update.newer -> "有新版本 ${update.versionName}"
                else -> "已是最新 ${update.versionName}"
            },
            onClick = onCheckGithub,
        )
        if (update != null) {
            ArrowPreference(
                title = if (update.newer) "打开 GitHub 下载" else "打开 GitHub Release",
                summary = update.pageUrl.ifBlank { GITHUB_RELEASES_URL },
                onClick = onOpenGithub,
            )
        }
        ArrowPreference(
            title = "网盘更新",
            summary = DRIVE_UPDATE_URL,
            onClick = onOpenDrive,
        )
    }
    val notes = update?.notes.orEmpty()
    if (notes.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                notes,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}
