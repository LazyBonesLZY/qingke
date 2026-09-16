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
}

class QingkeNavigator(start: TabDest = TabDest.Today) {
    var tab by mutableStateOf(start)
        private set
    private val stack = mutableStateListOf<Route>()
    val current: Route get() = stack.lastOrNull() ?: Route.Tab(tab)
    val canPop: Boolean get() = stack.isNotEmpty()

    fun goTab(dest: TabDest) {
        tab = dest
        stack.clear()
    }

    fun open(route: Route) {
        if (route is Route.Tab) {
            goTab(route.dest)
            return
        }
        if (stack.lastOrNull() == route) return
        stack += route
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun swipeTab(delta: Int) {
        val order = TabDest.entries
        val next = (order.indexOf(tab) + delta).coerceIn(0, order.lastIndex)
        goTab(order[next])
    }

}
