@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.edu.gzus.qingke.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import cn.edu.gzus.qingke.QingkeTheme
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.LessonSlot
import cn.edu.gzus.qingke.data.PracticeCourse
import cn.edu.gzus.qingke.data.School
import cn.edu.gzus.qingke.data.SessionState
import cn.edu.gzus.qingke.data.mondayOf
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.Route
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.LocalQingkeWide
import cn.edu.gzus.qingke.ui.components.QingkePullRefresh
import cn.edu.gzus.qingke.ui.timetable.TimetableScreen
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 真实离屏分页交互回归；无需 compose-ui-test 依赖，JDK 21+ 运行。 */
class TimetableRenderTest {
    @Test
    fun hiddenWeekendsFillTheSameGridWidth() = onUiThread {
        for (wide in listOf(false, true)) {
            var visible = emptyList<IntRange>()
            var hidden = emptyList<IntRange>()
            fun columns(ui: TimetableScene): List<IntRange> {
                val image = ui.snapshot()
                val top = ui.coursePoint(image).y.toInt() - 6
                val colored = (0 until ui.weekPaneWidth).map { x ->
                    (top until (top + 38).coerceAtMost(image.height)).count { y ->
                        (image.getRGB(x, y) and 0xFFFFFF) == 0x78B56B
                    } >= 4
                }
                val runs = mutableListOf<IntRange>()
                var start: Int? = null
                for (x in 0..colored.size) {
                    if (x < colored.size && colored[x]) {
                        if (start == null) start = x
                    } else start?.let { runs += it until x; start = null }
                }
                return runs
            }
            withTimetable(wide = wide, showWeekend = true) { visible = columns(it) }
            withTimetable(wide = wide, showWeekend = false) { hidden = columns(it) }
            assertEquals(7, visible.size)
            assertEquals(5, hidden.size)
            assertTrue(hidden.first().count() > visible.first().count(),
                "五列应使用更宽的课程列: seven=$visible, five=$hidden")
            assertTrue(abs(hidden.first().first - visible.first().first) <= 2 &&
                abs(hidden.last().last - visible.last().last) <= 2,
                "五列和七列应填满相同区域: seven=$visible, five=$hidden")
        }
    }

    @Test
    fun gridFollowsTheFingerAndHoldsItsOffsetBeforeRelease() = onUiThread {
        withTimetable { ui ->
            val before = ui.snapshot()
            val gridTop = ui.coursePoint(before).y.toInt() - 8
            val y = ui.coursePoint(before).y + 12f
            ui.press(Offset(340f, y))
            ui.moveTo(Offset(270f, y), durationMillis = 160)
            val firstOffset = ui.snapshot("timetable-pager-drag-first")
            ui.moveTo(Offset(170f, y), durationMillis = 160)
            val secondOffset = ui.snapshot("timetable-pager-drag-second")
            assertTrue(changedPixels(before, firstOffset, gridTop, ui.weekPaneWidth) > 100,
                "按住手指移动后、松手前，网格应已经发生位移")
            assertTrue(changedPixels(firstOffset, secondOffset, gridTop, ui.weekPaneWidth) > 100,
                "第二段拖动应继续改变网格位置，而非只播放固定动画")
            ui.hold(320)
            val held = ui.snapshot("timetable-pager-drag-held")
            assertTrue(changedPixels(secondOffset, held, gridTop, ui.weekPaneWidth) < 40,
                "手指保持不动时网格应停在拖动偏移，避免自行播放到下一页")
            // 确定跨过半页再低速松手，不依赖 fling 速度或“每次 swipe 固定 +1”。
            ui.moveTo(Offset(60f, y), durationMillis = 160)
            ui.hold(200)
            ui.release()
            ui.settle()
            ui.snapshot("timetable-pager-settled")
            ui.openCourse(expectedWeek = 3)
        }
    }

    @Test
    fun shortSlowDragSpringsBackAndStillAllowsCourseTap() = onUiThread {
        withTimetable { ui ->
            val before = ui.snapshot("timetable-pager-short-drag-before")
            val point = ui.coursePoint(before)
            // 从下一节的空网格拖动，隔离课程 clickable 获焦后的固定变暗叠层；仍对整张网格严格比图。
            val dragY = point.y + 72f
            ui.press(Offset(270f, dragY))
            ui.moveTo(Offset(218f, dragY), durationMillis = 320)
            val dragged = ui.snapshot()
            assertTrue(changedPixels(before, dragged, point.y.toInt() - 8, ui.weekPaneWidth) > 100,
                "短拖也应跟手，不能等松手后才改变画面")
            ui.hold(240)
            ui.release()
            ui.settle()
            val returned = ui.snapshot("timetable-pager-short-drag-returned")
            val changed = changedPixels(before, returned, point.y.toInt() - 8, ui.weekPaneWidth)
            assertTrue(changed < 40,
                "未跨过半页的慢短拖应回弹到原来的完整网格: changed=$changed, before=$point, after=${ui.coursePoint(returned)}")
            ui.openCourse(expectedWeek = 2)
        }
    }

    @Test
    fun arrowsWeekChipsAndReturnToCurrentWeekNavigateThePager() = onUiThread {
        withTimetable { ui ->
            ui.nextWeek()
            ui.settle()
            ui.openCourse(expectedWeek = 3)
            ui.previousWeek()
            ui.settle()
            ui.openCourse(expectedWeek = 2)
            ui.pickWeek(5)
            ui.settle()
            ui.openCourse(expectedWeek = 5)
            ui.returnToCurrentWeek()
            ui.settle()
            ui.openCourse(expectedWeek = 2)
        }
    }

    @Test
    fun rapidNavigationAndReversalKeepTheLatestRequestedWeek() = onUiThread {
        withTimetable { ui ->
            // 2→5 的弹簧尚未完成就反向请求 3，再连续点下一周、上一周。
            ui.pickWeek(5)
            ui.advance(64)
            ui.pickWeek(3)
            ui.advance(32)
            ui.nextWeek()
            ui.previousWeek()
            ui.settle()
            ui.openCourse(expectedWeek = 3)
        }
    }

    @Test
    fun touchTakesOverProgrammaticNavigationWithoutDriftingWhileHeld() = onUiThread {
        withTimetable { ui ->
            val y = ui.coursePoint().y + 12f
            ui.pickWeek(5)
            ui.advance(64)
            ui.press(Offset(310f, y))
            ui.moveTo(Offset(250f, y), durationMillis = 32)
            val heldStart = ui.snapshot()
            val gridTop = y.toInt() - 20
            ui.hold(320)
            assertTrue(changedPixels(heldStart, ui.snapshot(), gridTop, ui.weekPaneWidth) < 40,
                "手势接管后，旧导航弹簧应停止推动持手中的网格")
            ui.release()
            ui.settle()
            // 手势接管时具体在哪个中间周取决于弹簧进度；通过明确的新请求验证没有旧任务抢回目标。
            ui.pickWeek(3)
            ui.settle()
            ui.openCourse(expectedWeek = 3)
        }
    }

    @Test
    fun swipesStopAtTheFirstAndLastWeek() = onUiThread {
        withTimetable(initialWeek = 1) { ui ->
            ui.swipe(forward = false)
            ui.settle()
            ui.openCourse(expectedWeek = 1)
        }
        withTimetable(initialWeek = 16) { ui ->
            ui.swipe(forward = true)
            ui.settle()
            ui.openCourse(expectedWeek = 16)
        }
    }

    @Test
    fun verticalDragScrollsWithoutChangingWeek() = onUiThread {
        withTimetable(height = 520) { ui ->
            val before = ui.coursePoint()
            ui.drag(Offset(200f, before.y + 24f), Offset(200f, 90f), durationMillis = 320)
            ui.settle()
            val after = ui.coursePoint()
            assertTrue(after.y < before.y - 20f,
                "纵向手势应滚动页面，而不是被翻周手势截走: before=$before, after=$after")
            ui.openCourse(expectedWeek = 2)
        }
    }

    @Test
    fun weekMonthTogglePreservesTheSelectedWeekAndLayout() = onUiThread {
        withTimetable { ui ->
            val before = ui.coursePoint()
            ui.pickWeek(5)
            ui.settle()
            val weekView = ui.snapshot()
            ui.showMonth()
            ui.settle()
            val monthView = ui.snapshot("timetable-pager-month")
            assertTrue(changedPixels(weekView, monthView, before.y.toInt() - 8, ui.weekPaneWidth) > 1_000,
                "点月标签应实际切换到月历，避免标签点击无效仍通过保留周次断言")
            ui.showWeek()
            ui.settle()
            val after = ui.coursePoint()
            assertTrue(abs(after.y - before.y) < 2f, "周月来回切后网格布局应保持稳定")
            ui.openCourse(expectedWeek = 5)
        }
    }

    @Test
    fun selectingADateWhileThePagerIsHiddenNavigatesAfterReturningToWeek() = onUiThread {
        withTimetable { ui ->
            val today = nowDateTime().date
            val weekOneMonday = mondayOf(today).minus(DatePeriod(days = 7))
            // 选同月但不在第2周的有课日期；月初向后选，月末向前选，全年皆适用。
            val (selectedWeek, selected) = (1..6).filter { it != 2 }.flatMap { week ->
                (0..6).map { day -> week to weekOneMonday.plus(DatePeriod(days = (week - 1) * 7 + day)) }
            }.first { (_, date) -> date.year == today.year && date.monthNumber == today.monthNumber }
            ui.showMonth()
            ui.settle()
            ui.selectDateInCurrentMonth(selected)
            ui.settle()
            ui.snapshot("timetable-pager-month-picked")
            ui.showWeek()
            ui.settle()
            ui.openCourse(expectedWeek = selectedWeek)
        }
    }

    @Test
    fun switchingToMonthDuringNavigationKeepsThePendingDestination() = onUiThread {
        withTimetable { ui ->
            ui.pickWeek(5)
            ui.advance(32)
            ui.showMonth()
            ui.settle()
            ui.showWeek()
            ui.settle()
            ui.openCourse(expectedWeek = 5)
        }
    }

    @Test
    fun hidingThePagerBeforeGestureSnapFinishesRestoresACompletePage() = onUiThread {
        withTimetable { ui ->
            val before = ui.coursePoint()
            // 空节次行隔离焦点叠层；慢拖过半页再保持，目标明确是第3周而非速度推导。
            val y = before.y + 72f
            ui.press(Offset(340f, y))
            ui.moveTo(Offset(75f, y), durationMillis = 160)
            ui.hold(240)
            ui.release()
            // 不等待吸附结束即隐藏 Pager，回来后也不发送任何新翻周请求来掩盖悬空偏移。
            ui.showMonth()
            ui.settle()
            ui.showWeek()
            ui.settle()
            val restored = ui.snapshot("timetable-pager-gesture-snap-restored")
            val after = ui.coursePoint(restored)
            assertTrue(abs(after.x - before.x) < 2f && abs(after.y - before.y) < 2f,
                "隐藏时取消的手势吸附应恢复完整网格，不能停在两页之间: before=$before, after=$after")
            ui.openCourse(expectedWeek = 3)
        }
    }

    @Test
    fun wideLayoutKeepsTheWeightedGridVisibleDuringPaging() = onUiThread {
        withTimetable(wide = true) { ui ->
            val before = ui.coursePoint()
            ui.swipe(forward = true)
            ui.settle()
            ui.snapshot("timetable-pager-wide")
            val after = ui.coursePoint()
            assertTrue(abs(after.y - before.y) < 2f, "wide 分页前后网格应保持铺满布局")
            ui.openCourse(expectedWeek = 3)
        }
    }

    @Test
    fun repeatedDestinationAfterReversalKeepsTheNewestRequest() = onUiThread {
        withTimetable { ui ->
            // A→B→A 和同目标重试，均发生于前一弹簧尚未结束时。
            ui.pickWeek(5)
            ui.pickWeek(3)
            ui.pickWeek(5)
            ui.pickWeek(5)
            ui.settle()
            ui.openCourse(expectedWeek = 5)
        }
    }

    @Test
    fun denseTimetableRecoversAfterFastReverseDragsAndHidingThePager() = onUiThread {
        var refreshes = 0
        withTimetable(dense = true, pullRefresh = true, onRefresh = { refreshes++ }) { ui ->
            val y = ui.coursePoint().y + 12f
            repeat(3) {
                ui.press(Offset(340f, y))
                ui.moveTo(Offset(65f, y), durationMillis = 32)
                ui.release()
                // 不等前一次惯性落稳就从相反方向接管，不假定每次固定加减一周。
                ui.press(Offset(65f, y))
                ui.moveTo(Offset(335f, y), durationMillis = 32)
                ui.release()
            }
            ui.showMonth()
            ui.settle()
            ui.showWeek()
            ui.settle()
            ui.pickWeek(3)
            ui.settle()
            ui.openCourse(expectedWeek = 3)
            ui.previousWeek()
            ui.settle()
            ui.openCourse(expectedWeek = 2)
            assertEquals(0, refreshes, "连续横向拖动不应误触外层下拉同步")
        }
    }

    @Test
    fun crossingHalfAPageDoesNotReplaceMetadataBeforeRelease() = onUiThread {
        withTimetable(height = 600, varyingPractices = true) { ui ->
            val before = ui.snapshot()
            val point = ui.coursePoint(before)
            ui.press(Offset(340f, point.y + 12f))
            ui.moveTo(Offset(75f, point.y + 12f), durationMillis = 160)
            ui.hold(240)
            val held = ui.snapshot("timetable-pager-halfway-metadata")
            // 排除会正常产生焦点反馈的网格，仅比翻页器标题/日期/周次条区域。
            val metadataChanged = changedPixels(before, held, top = 140, width = ui.weekPaneWidth, bottom = 238)
            assertTrue(metadataChanged < 40,
                "过半页持手不应提前替换标题/课次数/实践信息: changed=$metadataChanged")
            assertTrue(abs(ui.coursePoint(held).y - point.y) < 2f,
                "持手期间相邻周实践数量不同也不应改变网格纵向位置")
            ui.moveTo(Offset(335f, point.y + 12f), durationMillis = 160)
            ui.hold(200)
            ui.release()
            ui.settle()
            ui.openCourse(expectedWeek = 2)
        }
    }

    @Test
    fun longJumpComposesTargetNeighborhoodRatherThanEveryIntermediateWeek() = onUiThread {
        val reads = CourseCompositionReads()
        withTimetable(wide = true, width = 1800, dense = true, compositionReads = reads) { ui ->
            reads.clear()
            // 宽屏导航条完整容纳全部周次；点最后一个周次胶囊远跳2→16。
            ui.pickLastWeek()
            ui.settle()
            val composedWeeks = reads.weeks()
            println("timetable far-jump course composition weeks=$composedWeeks, aliasReads=${reads.totalReads()}")
            assertTrue(16 in composedWeeks, "记录器应确实捕获到目标第16周课程组合，不能空记录假通过")
            assertTrue(composedWeeks.none { it in 5..12 },
                "远跳不应在弹簧帧内依次构建沿途所有网格: composedWeeks=$composedWeeks")
            ui.openCourse(expectedWeek = 16)
        }
    }

    /** 只记录 WeekCell 真实执行 compactCourseLines 的键访问，不当作整屏重组或帧耗时指标。 */
    private class CourseCompositionReads : Map<String, String> by emptyMap() {
        private val counts = linkedMapOf<Int, Int>()
        override fun get(key: String): String? {
            val week = key.removePrefix("week-").substringBefore('-').toIntOrNull()
            if (week != null) counts[week] = (counts[week] ?: 0) + 1
            return null
        }
        fun clear() = counts.clear()
        fun weeks(): Set<Int> = counts.keys.toSet()
        fun totalReads(): Int = counts.values.sum()
    }

    private class TimetableScene(
        val scene: ImageComposeScene,
        val nav: QingkeNavigator,
        val weekPaneWidth: Int,
    ) {
        private var timeMillis = 0L
        private var heldPoint: Offset? = null

        fun settle() = advance(1_200)

        fun advance(durationMillis: Int) {
            var remaining = durationMillis
            while (remaining > 0) {
                val step = minOf(16, remaining)
                timeMillis += step
                scene.render(timeMillis * 1_000_000L).close()
                remaining -= step
            }
        }

        fun snapshot(name: String? = null): BufferedImage {
            val bytes = scene.render(timeMillis * 1_000_000L).use { it.encodeToData()!!.bytes }
            if (name != null) {
                val output = File("build/ui-preview/$name.png")
                output.parentFile.mkdirs()
                output.writeBytes(bytes)
            }
            return ImageIO.read(ByteArrayInputStream(bytes))
        }

        fun coursePoint(image: BufferedImage = snapshot()): Offset {
            // 夹具固定使用独特色块，按实际渲染位置找课程，避免硬编码表头/字体高度。
            for (y in 0 until image.height - 8) {
                for (x in 24 until weekPaneWidth - 8) {
                    if ((image.getRGB(x, y) and 0xFFFFFF) == 0x78B56B &&
                        (image.getRGB(x + 6, y + 6) and 0xFFFFFF) == 0x78B56B
                    ) return Offset(x + 6f, y + 6f)
                }
            }
            error("当前周的课程色块未渲染；检查目标周、动画完成及 wide weight 布局")
        }

        fun swipe(forward: Boolean, y: Float = coursePoint().y + 12f) {
            val start = weekPaneWidth * if (forward) 0.85f else 0.15f
            val end = weekPaneWidth * if (forward) 0.15f else 0.85f
            drag(Offset(start, y), Offset(end, y), durationMillis = 320)
        }

        fun drag(from: Offset, to: Offset, durationMillis: Int) {
            press(from)
            moveTo(to, durationMillis)
            hold(160)
            release()
        }

        fun press(point: Offset) {
            check(heldPoint == null)
            heldPoint = point
            pointer(PointerEventType.Press, point)
        }

        fun moveTo(to: Offset, durationMillis: Int) {
            val from = checkNotNull(heldPoint)
            val steps = (durationMillis / 16).coerceAtLeast(1)
            repeat(steps) { index ->
                advance(durationMillis / steps)
                val fraction = (index + 1) / steps.toFloat()
                val point = Offset(
                    from.x + (to.x - from.x) * fraction,
                    from.y + (to.y - from.y) * fraction,
                )
                heldPoint = point
                pointer(PointerEventType.Move, point)
            }
        }

        fun hold(durationMillis: Int) {
            // 用当前坐标发真实时间的静止 Move，清除过去快移的速度，避免松手变成 fling。
            val point = checkNotNull(heldPoint)
            repeat((durationMillis / 32).coerceAtLeast(1)) {
                advance(32)
                pointer(PointerEventType.Move, point)
            }
        }

        fun release() {
            val point = checkNotNull(heldPoint)
            heldPoint = null
            pointer(PointerEventType.Release, point)
        }

        // 导航控件按真实语义边界点击，避免不同宿主字体改变文字宽度后点到相邻周次。
        fun nextWeek() = clickControl("下一周")
        fun previousWeek() = clickControl("上一周")
        fun pickWeek(week: Int) {
            require(week in 1..6)
            clickControl("${week}周")
        }
        fun pickLastWeek() = clickControl("16周")
        fun returnToCurrentWeek() = clickControl("回到本周")
        fun showMonth() = clickControl("月")
        fun showWeek() = clickControl("周")

        private fun clickControl(label: String) {
            fun descendants(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
                yield(node)
                node.children.forEach { yieldAll(descendants(it)) }
            }
            val node = scene.semanticsOwners.asSequence()
                .flatMap { descendants(it.unmergedRootSemanticsNode) }
                .firstOrNull { candidate ->
                    val config = candidate.config
                    val matches = config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
                        config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
                    matches && candidate.boundsInRoot.width > 0f && candidate.boundsInRoot.height > 0f
                } ?: error("未找到可见导航控件：$label")
            click(node.boundsInRoot.center)
        }
        fun selectDateInCurrentMonth(date: LocalDate) {
            val today = nowDateTime().date
            require(date.year == today.year && date.monthNumber == today.monthNumber)
            val first = LocalDate(date.year, date.monthNumber, 1)
            val cell = weekdayIndex(first) - 1 + date.dayOfMonth - 1
            // 月历卡 x=16..384，内边距8，周次列28，7列间隔3；日期行58+3高。
            val dayWidth = (352f - 28f - 7 * 3f) / 7f
            val x = 24f + 28f + 3f + (cell % 7) * (dayWidth + 3f) + dayWidth / 2f
            val y = 257f + (cell / 7) * 61f
            click(Offset(x, y))
        }

        fun openCourse(expectedWeek: Int) {
            nav.pop()
            click(coursePoint())
            assertEquals(Route.Course("week-$expectedWeek"), nav.current)
        }

        private fun click(point: Offset) {
            press(point)
            advance(32)
            release()
        }

        private fun pointer(type: PointerEventType, point: Offset) {
            // Desktop 默认 Mouse 不通过按住拖拽驱动 verticalScroll；这里模拟手机触摸。
            scene.sendPointerEvent(type, point, timeMillis = timeMillis, type = PointerType.Touch)
            advance(16)
        }
    }

    private fun withTimetable(
        wide: Boolean = false,
        height: Int = 900,
        initialWeek: Int = 2,
        width: Int = if (wide) 1200 else 400,
        dense: Boolean = false,
        varyingPractices: Boolean = false,
        compositionReads: CourseCompositionReads? = null,
        showWeekend: Boolean = false,
        pullRefresh: Boolean = false,
        onRefresh: () -> Unit = {},
        block: (TimetableScene) -> Unit,
    ) {
        val base = sample(initialWeek, dense, varyingPractices, compositionReads)
        val snapshot = base.copy(settings = base.settings.copy(showWeekend = showWeekend))
        val nav = QingkeNavigator(TabDest.Timetable)
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            QingkeTheme(snapshot.settings) {
                CompositionLocalProvider(LocalQingkeWide provides wide) {
                    if (pullRefresh) {
                        QingkePullRefresh(refreshing = false, onRefresh = onRefresh) {
                            TimetableScreen(snapshot, nav, PaddingValues())
                        }
                    } else {
                        TimetableScreen(snapshot, nav, PaddingValues())
                    }
                }
            }
        }
        try {
            val ui = TimetableScene(scene, nav, if (wide) (width * 1.55f / 2.55f).toInt() else width)
            ui.advance(320)
            block(ui)
        } finally {
            scene.close()
        }
    }

    private fun sample(
        initialWeek: Int,
        dense: Boolean,
        varyingPractices: Boolean,
        compositionReads: CourseCompositionReads?,
    ): AppSnapshot = AppSnapshot(
        settings = AppSettings(
            schoolId = School.Gzus.id,
            termStart = mondayOf(nowDateTime().date).minus(DatePeriod(days = (initialWeek - 1) * 7)).toString(),
            currentWeek = initialWeek,
            weekCount = 16,
            compactTimetable = true,
            courseTintHex = "#78B56B",
            courseTintAlpha = 1f,
            courseAliases = compositionReads ?: emptyMap(),
        ),
        session = SessionState(loggedIn = true),
        // 常规夹具保留原导航位置；密集夹具16周×7天×8节次，课程不相邻合并。
        slots = (if (dense) (1..16).toList() else (1..6).toList() + 16).flatMap { week ->
            (1..7).flatMap { day ->
                (if (dense) (0..7).toList() else listOf(0)).map { block ->
                    val period = "${block * 2 + 1}-${block * 2 + 2}"
                    LessonSlot(
                        courseId = if (block == 0) "week-$week" else "week-$week-block-$block",
                        courseName = "第${week}周课程${if (block == 0) "" else block}",
                        credit = "2",
                        teacher = "",
                        room = "",
                        weekday = day,
                        weekdayName = "",
                        period = period,
                        periodLabel = period,
                        weeks = week.toString(),
                    )
                }
            }
        },
        practices = if (varyingPractices) (1..12).map {
            PracticeCourse(name = "第3周实践$it", weeks = "3", teacher = "")
        } else emptyList(),
    )

    private fun changedPixels(
        a: BufferedImage,
        b: BufferedImage,
        top: Int,
        width: Int,
        bottom: Int = minOf(a.height, b.height),
    ): Int {
        var changed = 0
        for (y in top.coerceAtLeast(0) until minOf(bottom, a.height, b.height)) {
            for (x in 0 until width) {
                val first = a.getRGB(x, y)
                val second = b.getRGB(x, y)
                if (listOf(0, 8, 16).sumOf { shift ->
                    abs(((first shr shift) and 255) - ((second shr shift) and 255))
                } > 24) changed++
            }
        }
        return changed
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try { block() } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
