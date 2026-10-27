package cn.edu.gzus.qingke.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TabDest { Today, Timetable, Grades, Jwxt, Mine }

enum class Transition { Fade, SlideIn, SlideOut }

sealed class Route(val transition: Transition) {
    data class Tab(val dest: TabDest) : Route(Transition.Fade)
    data class Course(val courseId: String) : Route(Transition.SlideIn)
    data object EmptyRoom : Route(Transition.SlideIn)
    data object Exams : Route(Transition.SlideIn)
    data object XiaoaiImport : Route(Transition.SlideIn)
    data object Notices : Route(Transition.SlideIn)
    data object Hall : Route(Transition.SlideIn)
    data object Leave : Route(Transition.SlideIn)
    data object Utility : Route(Transition.SlideIn)
    data object CourseAliases : Route(Transition.SlideIn)
    data object RoomAliases : Route(Transition.SlideIn)
    data object ScheduleShifts : Route(Transition.SlideIn)
    data object CoursePick : Route(Transition.SlideIn)
    data object ThemeSettings : Route(Transition.SlideIn)
    data object PeriodTime : Route(Transition.SlideIn)
}

class QingkeNavigator(start: TabDest = TabDest.Today) {
    var tab by mutableStateOf(start)
        private set
    private val stack = mutableStateListOf<Route>()
    val current: Route get() = stack.lastOrNull() ?: Route.Tab(tab)
    val canPop: Boolean get() = stack.isNotEmpty()

    /**
     * 最近一次栈变化是不是「返回」。二级页之间互跳（大厅 → 请假 → 返回大厅）时，
     * 转场方向要跟着它走：前进从右推入，返回从左退回，不能一律当成前进。
     */
    var lastWasPop by mutableStateOf(false)
        private set

    fun goTab(dest: TabDest) {
        tab = dest
        lastWasPop = stack.isNotEmpty()
        stack.clear()
    }

    fun open(route: Route) {
        if (route is Route.Tab) {
            goTab(route.dest)
            return
        }
        if (stack.lastOrNull() == route) return
        lastWasPop = false
        stack += route
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        lastWasPop = true
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** 去「我的」并直接展开「第1周周一」的日期选择，不用再自己往下找。 */
    fun openTermStart() {
        termStartFocus = true
        goTab(TabDest.Mine)
    }

    /** 「我的」页进场时取一次：是不是被 [openTermStart] 带过来的。 */
    fun consumeTermStartFocus(): Boolean = termStartFocus.also { termStartFocus = false }

    private var termStartFocus = false

    fun swipeTab(delta: Int) {
        val order = TabDest.entries
        val next = (order.indexOf(tab) + delta).coerceIn(0, order.lastIndex)
        goTab(order[next])
    }

}
