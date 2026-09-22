package cn.edu.gzus.qingke

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import cn.edu.gzus.qingke.data.AppRepository
import cn.edu.gzus.qingke.data.AppUpdate
import cn.edu.gzus.qingke.data.CoursePickOffer
import cn.edu.gzus.qingke.data.CoursePickScope
import cn.edu.gzus.qingke.data.CoursePickSection
import cn.edu.gzus.qingke.data.ScheduleShift
import cn.edu.gzus.qingke.data.DRIVE_UPDATE_URL
import cn.edu.gzus.qingke.data.FreeRoom
import cn.edu.gzus.qingke.data.LeaveForm
import cn.edu.gzus.qingke.data.LeaveStep
import cn.edu.gzus.qingke.data.UtilityOption
import cn.edu.gzus.qingke.data.GITHUB_RELEASES_URL
import cn.edu.gzus.qingke.data.GZUS_LOGIN_CAS
import cn.edu.gzus.qingke.data.JwxtNeedFirstLogin
import cn.edu.gzus.qingke.data.School
import cn.edu.gzus.qingke.data.gzusUsesCas
import cn.edu.gzus.qingke.data.school
import cn.edu.gzus.qingke.data.hasUtilityBind
import cn.edu.gzus.qingke.data.friendlyNetworkMessage
import cn.edu.gzus.qingke.data.isSessionLost
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.cancelLiveClass
import cn.edu.gzus.qingke.data.mondayOf
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.openUrl
import cn.edu.gzus.qingke.data.openXiaoaiSchedule
import cn.edu.gzus.qingke.data.requestLiveUpdatePermission
import cn.edu.gzus.qingke.data.resetLiveDismiss
import cn.edu.gzus.qingke.data.resolveCourseDetail
import cn.edu.gzus.qingke.data.shiftConflict
import cn.edu.gzus.qingke.data.teachingWeekFromStart
import cn.edu.gzus.qingke.data.termStartFromCurrentWeek
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.nav.Transition
import cn.edu.gzus.qingke.ui.components.QingkeBottomBar
import cn.edu.gzus.qingke.ui.components.qingkeLayer
import cn.edu.gzus.qingke.ui.components.rememberQingkeBackdrop
import cn.edu.gzus.qingke.ui.detail.CourseDetailScreen
import cn.edu.gzus.qingke.ui.grades.GradesScreen
import cn.edu.gzus.qingke.ui.hub.EmptyRoomScreen
import cn.edu.gzus.qingke.ui.hub.ExamsScreen
import cn.edu.gzus.qingke.ui.hub.NoticesScreen
import cn.edu.gzus.qingke.ui.jwxt.CoursePickScreen
import cn.edu.gzus.qingke.ui.jwxt.HallScreen
import cn.edu.gzus.qingke.ui.jwxt.JwxtScreen
import cn.edu.gzus.qingke.ui.jwxt.LeaveScreen
import cn.edu.gzus.qingke.ui.jwxt.UtilityScreen
import cn.edu.gzus.qingke.ui.jwxt.XiaoaiImportScreen
import cn.edu.gzus.qingke.ui.mine.CourseAliasesScreen
import cn.edu.gzus.qingke.ui.mine.MineScreen
import cn.edu.gzus.qingke.ui.mine.ScheduleShiftsScreen
import cn.edu.gzus.qingke.ui.timetable.TimetableScreen
import cn.edu.gzus.qingke.ui.today.TodayScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun App() {
    QingkeTheme {
        val repo = remember { AppRepository() }
        val nav = remember { QingkeNavigator() }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val snapshot by repo.state.collectAsState()
        val liveTick by repo.liveTick.collectAsState()
        val captcha by repo.captcha.collectAsState()
        val captchaError by repo.captchaError.collectAsState()
        val backdrop = rememberQingkeBackdrop()
        var busy by remember { mutableStateOf(false) }
        var loginError by remember { mutableStateOf<String?>(null) }
        var firstLogin by remember { mutableStateOf(false) }
        var roomBusy by remember { mutableStateOf(false) }
        var roomError by remember { mutableStateOf<String?>(null) }
        var rooms by remember { mutableStateOf<List<FreeRoom>>(emptyList()) }
        var utilityOptions by remember { mutableStateOf<List<UtilityOption>>(emptyList()) }
        var utilityOptionsBusy by remember { mutableStateOf(false) }
        var utilityOptionsError by remember { mutableStateOf<String?>(null) }
        var leaveForm by remember { mutableStateOf<LeaveForm?>(null) }
        var leaveFormBusy by remember { mutableStateOf(false) }
        var leaveFormError by remember { mutableStateOf<String?>(null) }
        var leaveTraces by remember { mutableStateOf<Map<String, List<LeaveStep>>>(emptyMap()) }
        var leaveTraceBusy by remember { mutableStateOf("") }
        var update by remember { mutableStateOf<AppUpdate?>(null) }
        var updateBusy by remember { mutableStateOf(false) }
        var updateError by remember { mutableStateOf<String?>(null) }
        var pickScopes by remember { mutableStateOf<List<CoursePickScope>>(emptyList()) }
        var pickOffers by remember { mutableStateOf<List<CoursePickOffer>>(emptyList()) }
        var pickSections by remember { mutableStateOf<List<CoursePickSection>>(emptyList()) }
        var pickChosen by remember { mutableStateOf<List<CoursePickOffer>>(emptyList()) }
        var pickScopesBusy by remember { mutableStateOf(false) }
        var pickSearchBusy by remember { mutableStateOf(false) }
        var pickSectionsBusy by remember { mutableStateOf(false) }
        var pickError by remember { mutableStateOf<String?>(null) }

        fun toast(message: String) {
            scope.launch { snackbar.showSnackbar(message) }
        }

        fun runJob(login: Boolean = false, block: suspend () -> Unit) {
            scope.launch {
                busy = true
                runCatching { block() }
                    .onFailure { failed ->
                        if (failed is JwxtNeedFirstLogin) {
                            firstLogin = true
                            loginError = failed.message
                            return@onFailure
                        }
                        val message = failed.friendlyNetworkMessage()
                        // 登录表单上的红字只留给登录本身和掉线，
                        // 同步水电失败不该一直挂在那里。
                        if (login || isSessionLost(message)) loginError = message
                        toast(message)
                    }
                busy = false
            }
        }

        LaunchedEffect(Unit) {
            if (!repo.state.value.settings.autoCheckUpdate) return@LaunchedEffect
            runCatching { repo.checkGithubUpdate() }
                .onSuccess { found ->
                    update = found
                    if (found.newer) toast("有新版本 ${found.versionName}，需要更新")
                }
        }

        LaunchedEffect(snapshot.settings.autoPullScheduleAdjust, snapshot.settings.schoolId) {
            if (!snapshot.settings.autoPullScheduleAdjust || snapshot.settings.school() != School.Gzus) return@LaunchedEffect
            runCatching { repo.pullScheduleAdjust() }
        }

        LaunchedEffect(snapshot.session.loggedIn) {
            if (snapshot.session.loggedIn) {
                runCatching { repo.checkSession() }
                    .onFailure { failed ->
                        val message = failed.friendlyNetworkMessage()
                        if (isSessionLost(message)) {
                            loginError = message
                            toast(message)
                        }
                    }
                if (repo.state.value.session.loggedIn) {
                    if (repo.state.value.settings.gzusUsesCas()) {
                        runCatching { repo.keepAlive() }
                    }
                    if (repo.state.value.settings.autoSyncOnStart) {
                        runCatching { repo.sync() }
                            .onFailure { failed ->
                                val message = failed.friendlyNetworkMessage()
                                if (isSessionLost(message)) {
                                    loginError = message
                                    toast(message)
                                }
                            }
                    } else {
                        runCatching { repo.syncCalendar() }
                        val now = repo.state.value
                        if (now.settings.gzusUsesCas() && !now.settings.hasUtilityBind()) {
                            runCatching { repo.syncUtility() }
                        }
                    }
                }
            }
        }

        LaunchedEffect(snapshot.session.loggedIn, snapshot.settings.schoolId, snapshot.settings.gzusLoginChannel) {
            if (!snapshot.session.loggedIn || !snapshot.settings.gzusUsesCas()) return@LaunchedEffect
            while (isActive) {
                delay(8 * 60_000L)
                if (!repo.state.value.session.loggedIn || !repo.state.value.settings.gzusUsesCas()) break
                runCatching { repo.keepAlive() }
                    .onFailure { failed ->
                        val message = failed.friendlyNetworkMessage()
                        if (isSessionLost(message)) {
                            loginError = message
                            toast(message)
                        }
                    }
            }
        }

        LaunchedEffect(snapshot.settings.schoolId, snapshot.settings.gzusLoginChannel, snapshot.session.loggedIn) {
            if (!snapshot.session.loggedIn) {
                runCatching { repo.refreshCaptcha() }
            }
        }

        LaunchedEffect(snapshot.settings.termStart, snapshot.settings.weekCount, snapshot.slots.size) {
            runCatching { repo.syncHolidays() }
        }

        val pickSignal = snapshot.settings.coursePickQueue
            .filter { it.status == "waiting" && it.fireAt > 0L }
            .joinToString { "${it.id}:${it.fireAt}" }
        LaunchedEffect(pickSignal, snapshot.session.loggedIn) {
            if (!snapshot.session.loggedIn || pickSignal.isBlank()) return@LaunchedEffect
            while (isActive) {
                val waiting = repo.state.value.settings.coursePickQueue
                    .filter { it.status == "waiting" && it.fireAt > 0L }
                if (waiting.isEmpty()) break
                val wait = waiting.minOf { it.fireAt } - Clock.System.now().toEpochMilliseconds()
                if (wait > 0) {
                    // 离开选还早就慢慢看，快到点了就睡准确的时间，别差十几秒。
                    delay(
                        when {
                            wait > 120_000 -> 60_000
                            wait > 30_000 -> 15_000
                            else -> wait
                        },
                    )
                    continue
                }
                runCatching { repo.runDueCoursePicks() }
                    .onSuccess { lines -> lines.forEach { line -> toast(line) } }
                    .onFailure { failed ->
                        val message = failed.friendlyNetworkMessage()
                        if (isSessionLost(message)) loginError = message
                        toast(message)
                        break
                    }
            }
        }

        LaunchedEffect(snapshot.settings.remindBeforeClass, snapshot.settings.remindLeadMinutes, snapshot.slots, snapshot.settings.currentWeek, snapshot.settings.termStart, liveTick) {
            if (snapshot.settings.remindBeforeClass) requestLiveUpdatePermission()
            while (isActive) {
                val live = repo.refreshLive()
                val testing = repo.hasLiveTest()
                if (!snapshot.settings.remindBeforeClass && !testing) {
                    if (!testing) cancelLiveClass()
                    break
                }
                delay(if (live || testing) 15_000 else 60_000)
            }
        }

        QingkeBackHandler(enabled = nav.canPop) { nav.pop() }

        var heldRoute by remember { mutableStateOf<Route?>(null) }
        SideEffect {
            if (nav.canPop) heldRoute = nav.current
        }
        val overlay = if (nav.canPop) nav.current else heldRoute
        val stackProgress = remember { Animatable(0f) }
        LaunchedEffect(nav.canPop) {
            stackProgress.animateTo(
                if (nav.canPop) 1f else 0f,
                spring(dampingRatio = 0.86f, stiffness = 380f),
            )
            if (!nav.canPop) heldRoute = null
        }
        var pageWidthPx by remember { mutableFloatStateOf(1f) }

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { pageWidthPx = it.width.toFloat().coerceAtLeast(1f) },
        ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = stackProgress.value
                    translationX = -p * pageWidthPx * 0.25f
                    alpha = 1f - 0.1f * p
                },
            containerColor = MiuixTheme.colorScheme.surface,
            bottomBar = {
                QingkeBottomBar(selected = nav.tab, backdrop = backdrop, onSelect = { nav.goTab(it) })
            },
            snackbarHost = { SnackbarHost(state = snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().qingkeLayer(backdrop)) {
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface))
                when (nav.tab) {
                    TabDest.Today -> TodayScreen(snapshot, nav, padding, onSync = {
                        runJob {
                            repo.sync()
                            toast("课表已刷新")
                        }
                    })
                    TabDest.Timetable -> TimetableScreen(snapshot, nav, padding)
                    TabDest.Grades -> GradesScreen(snapshot, nav, padding)
                    TabDest.Jwxt -> JwxtScreen(snapshot, nav, padding)
                    TabDest.Mine -> MineScreen(
                        snapshot = snapshot,
                        nav = nav,
                        contentPadding = padding,
                        busy = busy,
                        error = loginError,
                        firstLogin = firstLogin,
                        onOpenOfficialLogin = {
                            val url = snapshot.resolved().loginUrl
                            if (url.isBlank()) toast("先在开发者选项里填登录地址") else openUrl(url)
                        },
                        onOpenChangePassword = {
                            val url = snapshot.resolved().changePasswordUrl
                            if (url.isBlank()) toast("先在开发者选项里填改密地址") else openUrl(url)
                        },
                        captcha = captcha,
                        captchaError = captchaError,
                        onRefreshCaptcha = {
                            scope.launch { repo.refreshCaptcha() }
                        },
                        onSelectSchool = { school ->
                            firstLogin = false
                            loginError = null
                            repo.setSchool(school)
                            toast("已切换到${school.label}")
                            scope.launch { repo.refreshCaptcha() }
                        },
                        onSelectGzusLogin = { channel ->
                            firstLogin = false
                            loginError = null
                            repo.setGzusLoginChannel(channel)
                            toast(if (channel == GZUS_LOGIN_CAS) "已改用统一身份认证" else "已改用正方直接登录")
                            scope.launch { repo.refreshCaptcha() }
                        },
                        onLogin = { id, pwd, code ->
                            if (id.length < 6) {
                                loginError = "学号太短"
                                firstLogin = false
                                return@MineScreen
                            }
                            if (pwd.isBlank()) {
                                loginError = "请输入密码"
                                firstLogin = false
                                return@MineScreen
                            }
                            if (snapshot.resolved().requiresCaptcha && code.isBlank()) {
                                loginError = "请填写验证码"
                                firstLogin = false
                                return@MineScreen
                            }
                            loginError = null
                            runJob(login = true) {
                                repo.loginAndSync(id, pwd, code, captcha?.id.orEmpty())
                                firstLogin = false
                                val after = repo.state.value
                                toast(
                                    when {
                                        after.slots.isEmpty() -> "已登录，这一学期还没有课表"
                                        after.profile.yearName.isNotBlank() -> "已同步 ${after.profile.yearName}"
                                        else -> "已同步课表"
                                    },
                                )
                            }
                        },
                        onSync = {
                            runJob {
                                repo.sync()
                                toast("已同步")
                            }
                        },
                        onToggleAutoSync = { on ->
                            repo.updateSettings { it.copy(autoSyncOnStart = on) }
                            toast(if (on) "已打开启动自动同步" else "已关闭启动自动同步")
                        },
                        onToggleScheduleAdjust = { on ->
                            repo.updateSettings { it.copy(autoPullScheduleAdjust = on) }
                            if (!on) {
                                toast("已关闭自动拉取调休")
                            } else {
                                scope.launch {
                                    runCatching { repo.pullScheduleAdjust() }
                                        .onSuccess { toast("已拉取调休") }
                                        .onFailure { toast(it.friendlyNetworkMessage()) }
                                }
                            }
                        },
                        onToggleRemind = { on ->
                            repo.updateSettings { it.copy(remindBeforeClass = on) }
                            if (on) {
                                resetLiveDismiss()
                                requestLiveUpdatePermission()
                            }
                            repo.refreshLive()
                        },
                        onPickRemindLead = { minutes ->
                            repo.updateSettings { it.copy(remindLeadMinutes = minutes) }
                            repo.refreshLive()
                        },
                        onPickTermStart = { date ->
                            val monday = mondayOf(date)
                            val today = nowDateTime().date
                            repo.updateSettings {
                                it.copy(
                                    termStart = monday.toString(),
                                    currentWeek = teachingWeekFromStart(today, monday).coerceIn(1, 30),
                                )
                            }
                            repo.refreshLive()
                        },
                        onPickWeek = { week ->
                            val today = nowDateTime().date
                            repo.updateSettings {
                                it.copy(
                                    termStart = termStartFromCurrentWeek(today, week).toString(),
                                    currentWeek = week,
                                )
                            }
                            repo.refreshLive()
                        },
                        onLogout = {
                            runJob {
                                repo.logout()
                                toast("已退出")
                            }
                        },
                        onSaveCustom = { cfg, apply ->
                            repo.saveCustomJwxt(cfg, apply)
                            firstLogin = false
                            loginError = null
                            toast(if (apply) "已切到自定义教务" else "已保存自定义配置")
                            if (apply) scope.launch { repo.refreshCaptcha() }
                        },
                        update = update,
                        updateBusy = updateBusy,
                        updateError = updateError,
                        autoCheckUpdate = snapshot.settings.autoCheckUpdate,
                        onToggleAutoUpdate = { on ->
                            repo.updateSettings { it.copy(autoCheckUpdate = on) }
                            if (!on) {
                                toast("已关闭自动检查更新")
                            } else {
                                scope.launch {
                                    updateBusy = true
                                    updateError = null
                                    runCatching { repo.checkGithubUpdate() }
                                        .onSuccess { found ->
                                            update = found
                                            toast(if (found.newer) "有新版本 ${found.versionName}，需要更新" else "已是最新 ${found.versionName}")
                                        }
                                        .onFailure { updateError = it.friendlyNetworkMessage() }
                                    updateBusy = false
                                }
                            }
                        },
                        onCheckGithubUpdate = {
                            scope.launch {
                                updateBusy = true
                                updateError = null
                                runCatching { repo.checkGithubUpdate() }
                                    .onSuccess {
                                        update = it
                                        toast(if (it.newer) "有新版本 ${it.versionName}" else "已是最新 ${it.versionName}")
                                    }
                                    .onFailure { updateError = it.friendlyNetworkMessage() }
                                updateBusy = false
                            }
                        },
                        onOpenGithubUpdate = {
                            val found = update
                            openUrl(
                                when {
                                    found == null -> GITHUB_RELEASES_URL
                                    found.apkUrl.isNotBlank() -> found.apkUrl
                                    else -> found.pageUrl.ifBlank { GITHUB_RELEASES_URL }
                                },
                            )
                        },
                        onOpenDriveUpdate = { openUrl(DRIVE_UPDATE_URL) },
                        liveTesting = remember(liveTick) { repo.hasLiveTest() },
                        onLiveTest = { toast(repo.startLiveTest()) },
                        onStopLiveTest = {
                            repo.stopLiveTest()
                            toast("已停止测试")
                        },
                    )
                }
            }
        }
            overlay?.let { dest ->
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - stackProgress.value) * pageWidthPx
                        }
                        .background(MiuixTheme.colorScheme.surface),
                    containerColor = MiuixTheme.colorScheme.surface,
                    topBar = {
                        SmallTopAppBar(
                            title = routeTitle(dest),
                            navigationIcon = {
                                IconButton(onClick = { nav.pop() }) {
                                    Icon(MiuixIcons.Back, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                                }
                            },
                        )
                    },
                ) { padding ->
                    AnimatedContent(
                        targetState = dest,
                        transitionSpec = {
                            when (targetState.transition) {
                                Transition.SlideIn ->
                                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                                Transition.SlideOut ->
                                    (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                                Transition.Fade -> fadeIn() togetherWith fadeOut()
                            }
                        },
                        label = "qingke-stack",
                    ) { current ->
                        when (current) {
                            is Route.Tab -> {}
                            is Route.Course -> CourseDetailScreen(
                                detail = resolveCourseDetail(snapshot, current.courseId),
                                contentPadding = padding,
                                hasClock = snapshot.resolved().hasPeriodClock,
                                settings = snapshot.settings,
                                today = nowDateTime().date,
                                adjust = snapshot.scheduleAdjust,
                            )
                            is Route.EmptyRoom -> EmptyRoomScreen(
                                snapshot = snapshot,
                                contentPadding = padding,
                                busy = roomBusy,
                                rooms = rooms,
                                error = roomError,
                                onQuery = { weekday, start, end ->
                                    scope.launch {
                                        roomBusy = true
                                        roomError = null
                                        runCatching { repo.queryRooms(weekday, start, end) }
                                            .onSuccess { rooms = it }
                                            .onFailure { roomError = it.message }
                                        roomBusy = false
                                    }
                                },
                            )
                            is Route.Exams -> ExamsScreen(snapshot, padding)
                            is Route.Hall -> HallScreen(
                                snapshot = snapshot,
                                nav = nav,
                                contentPadding = padding,
                            )
                            is Route.Leave -> LeaveScreen(
                                snapshot = snapshot,
                                nav = nav,
                                contentPadding = padding,
                                busy = busy,
                                form = leaveForm,
                                formBusy = leaveFormBusy,
                                formError = leaveFormError,
                                onLoadForm = { affairId ->
                                    scope.launch {
                                        leaveFormBusy = true
                                        leaveFormError = null
                                        runCatching { repo.loadLeaveForm(affairId) }
                                            .onSuccess { leaveForm = it }
                                            .onFailure { leaveFormError = it.message }
                                        leaveFormBusy = false
                                    }
                                },
                                onSubmit = { form, values ->
                                    runJob {
                                        val message = repo.submitLeave(form, values)
                                        toast(message)
                                    }
                                },
                                onRefresh = {
                                    runJob {
                                        repo.syncHall()
                                        toast("请假已同步")
                                    }
                                },
                                traces = leaveTraces,
                                traceBusy = leaveTraceBusy,
                                onLoadTrace = { id ->
                                    scope.launch {
                                        leaveTraceBusy = id
                                        runCatching { repo.loadLeaveTrace(id) }
                                            .onSuccess { leaveTraces = leaveTraces + (id to it) }
                                            .onFailure { toast(it.friendlyNetworkMessage()) }
                                        leaveTraceBusy = ""
                                    }
                                },
                            )
                            is Route.Utility -> UtilityScreen(
                                snapshot = snapshot,
                                nav = nav,
                                contentPadding = padding,
                                busy = busy,
                                options = utilityOptions,
                                optionsBusy = utilityOptionsBusy,
                                optionsError = utilityOptionsError,
                                onLoadOptions = { level, parentId ->
                                    if (level == "clear" || parentId.isBlank()) {
                                        utilityOptions = emptyList()
                                        utilityOptionsError = null
                                        utilityOptionsBusy = false
                                    } else {
                                        scope.launch {
                                            utilityOptions = emptyList()
                                            utilityOptionsBusy = true
                                            utilityOptionsError = null
                                            runCatching { repo.listUtilityOptions(level, parentId) }
                                                .onSuccess { utilityOptions = it }
                                                .onFailure { utilityOptionsError = it.message }
                                            utilityOptionsBusy = false
                                        }
                                    }
                                },
                                onSaveBind = { bind ->
                                    repo.saveUtilityBind(bind)
                                    utilityOptions = emptyList()
                                    utilityOptionsError = null
                                    toast("已绑定 ${bind.label}")
                                },
                                onSavePrice = { useCustom, water, electric ->
                                    repo.saveUtilityPrice(useCustom, water, electric)
                                    toast(if (useCustom) "已用自定义单价" else "已用江门校区单价")
                                },
                                onRefresh = {
                                    runJob {
                                        repo.syncUtility()
                                        toast("水电已同步")
                                    }
                                },
                                onPeekBind = {
                                    scope.launch { runCatching { repo.syncUtility() } }
                                },
                            )
                            is Route.Notices -> NoticesScreen(
                                snapshot = snapshot,
                                contentPadding = padding,
                                onOpen = { id ->
                                    scope.launch {
                                        runCatching { repo.loadNoticeBody(id) }
                                            .onFailure { toast(it.message ?: "通知正文加载失败") }
                                    }
                                },
                            )
                            is Route.ScheduleShifts -> ScheduleShiftsScreen(
                                snapshot = snapshot,
                                contentPadding = padding,
                                onAdd = { from, to ->
                                    val conflict = snapshot.settings.shiftConflict(
                                        from,
                                        to,
                                        snapshot.scheduleAdjust,
                                        nowDateTime().date,
                                    )
                                    if (conflict != null) return@ScheduleShiftsScreen conflict
                                    repo.updateSettings { settings ->
                                        settings.copy(
                                            scheduleShifts = settings.scheduleShifts + ScheduleShift(
                                                id = "${from}>${to}",
                                                fromDate = from.toString(),
                                                toDate = to.toString(),
                                            ),
                                        )
                                    }
                                    toast("已保存调课")
                                    null
                                },
                                onRemove = { id ->
                                    repo.updateSettings { settings ->
                                        settings.copy(scheduleShifts = settings.scheduleShifts.filterNot { it.id == id })
                                    }
                                    toast("已删除调课")
                                },
                            )
                            is Route.CourseAliases -> CourseAliasesScreen(
                                snapshot = snapshot,
                                contentPadding = padding,
                                onSaveAlias = { courseId, courseName, alias ->
                                    repo.updateSettings { settings ->
                                        val map = settings.courseAliases.toMutableMap()
                                        val key = courseId.ifBlank { courseName }
                                        if (alias.isBlank()) {
                                            map.remove(courseId)
                                            map.remove(courseName)
                                            map.remove(key)
                                        } else {
                                            map[key] = alias.trim().take(4)
                                        }
                                        settings.copy(courseAliases = map)
                                    }
                                    toast(if (alias.isBlank()) "已恢复默认缩写" else "已保存缩写")
                                },
                            )
                            is Route.CoursePick -> CoursePickScreen(
                                snapshot = snapshot,
                                nav = nav,
                                contentPadding = padding,
                                busy = busy,
                                scopes = pickScopes,
                                offers = pickOffers,
                                sections = pickSections,
                                picked = pickChosen,
                                scopesBusy = pickScopesBusy,
                                searchBusy = pickSearchBusy,
                                sectionsBusy = pickSectionsBusy,
                                error = pickError,
                                onLoadScopes = {
                                    scope.launch {
                                        pickScopesBusy = true
                                        pickError = null
                                        runCatching { repo.loadCoursePickScopes() }
                                            .onSuccess { list ->
                                                pickScopes = list
                                                pickChosen = emptyList()
                                                list.firstOrNull()?.let { first ->
                                                    runCatching { repo.loadCoursePicked(first) }
                                                        .onSuccess { pickChosen = it }
                                                }
                                            }
                                            .onFailure { pickError = it.friendlyNetworkMessage() }
                                        pickScopesBusy = false
                                    }
                                },
                                onSearch = { item, keyword ->
                                    scope.launch {
                                        pickSearchBusy = true
                                        pickError = null
                                        pickSections = emptyList()
                                        runCatching { repo.searchCoursePicks(item, keyword) }
                                            .onSuccess { pickOffers = it }
                                            .onFailure { failed ->
                                                pickOffers = emptyList()
                                                pickError = failed.friendlyNetworkMessage()
                                            }
                                        pickSearchBusy = false
                                    }
                                },
                                onOpenSections = { offer ->
                                    scope.launch {
                                        pickSectionsBusy = true
                                        pickSections = emptyList()
                                        runCatching { repo.loadCoursePickSections(offer) }
                                            .onSuccess { pickSections = it }
                                            .onFailure { pickError = it.friendlyNetworkMessage() }
                                        pickSectionsBusy = false
                                    }
                                },
                                onSelectNow = { offer, section ->
                                    runJob {
                                        val message = repo.selectCourseNow(offer, section)
                                        toast(message)
                                    }
                                },
                                onQueue = { offer, section ->
                                    repo.queueCoursePick(offer, section)
                                    toast("已加入队列")
                                },
                                onSchedule = { id, fireAt ->
                                    runCatching { repo.scheduleCoursePick(id, fireAt) }
                                        .onSuccess { toast("已设定到点自动选") }
                                        .onFailure { toast(it.message ?: "时间不对") }
                                },
                                onRemove = { id ->
                                    repo.removeCoursePick(id)
                                    toast("已移出队列")
                                },
                                onRunQueued = { id ->
                                    runJob {
                                        val message = repo.runQueuedCoursePick(id)
                                        toast(message)
                                    }
                                },
                            )
                            is Route.XiaoaiImport -> XiaoaiImportScreen(
                                snapshot = snapshot,
                                contentPadding = padding,
                                busy = busy,
                                onSaveUrl = { url ->
                                    runCatching { repo.saveXiaoaiEditUrl(url) }
                                        .onSuccess { toast("授权已保存") }
                                        .onFailure { toast(it.message ?: "授权无效") }
                                },
                                onClearUrl = {
                                    repo.clearXiaoaiEditUrl()
                                    toast("已清除授权")
                                },
                                onImport = {
                                    runJob {
                                        val msg = repo.importToXiaoai()
                                        openXiaoaiSchedule()
                                        toast(msg)
                                    }
                                },
                                onCopied = { toast("已复制官方 JSON") },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun routeTitle(route: Route): String = when (route) {
    is Route.Course -> "课程详情"
    is Route.EmptyRoom -> "空教室"
    is Route.Exams -> "考试"
    is Route.Notices -> "通知"
    is Route.Hall -> "办事大厅"
    is Route.Leave -> "请假"
    is Route.Utility -> "宿舍水电"
    is Route.XiaoaiImport -> "导入小爱"
    is Route.CourseAliases -> "课表缩写"
    is Route.ScheduleShifts -> "调课"
    is Route.CoursePick -> "选课"
    is Route.Tab -> "青课"
}
